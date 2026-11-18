package com.sipdial.app.sip

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.sipdial.app.SipDialApplication
import com.sipdial.app.data.AccountRepository
import com.sipdial.app.data.AppDatabase
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.service.SipForegroundService
import com.sipdial.app.telecom.CallBarOverlay
import com.sipdial.app.telecom.NativeDialerOverlay
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.telecom.sim.CallForward
import com.sipdial.app.util.BlockList
import com.sipdial.app.util.CallNetworkLock
import com.sipdial.app.util.CallRinger
import com.sipdial.app.util.CallerIdFormatter
import com.sipdial.app.util.CallingPrefs
import com.sipdial.app.util.NotificationHelper
import com.sipdial.app.util.RecordingStorage
import com.sipdial.app.util.SecurityPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.AuthInfo
import org.linphone.core.AuthMethod
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.ErrorInfo
import org.linphone.core.Factory
import org.linphone.core.GlobalState
import org.linphone.core.LogLevel
import org.linphone.core.LoggingService
import org.linphone.core.LoggingServiceListener
import org.linphone.core.MediaEncryption
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SipEngine(private val context: Context) {

    data class ResolvedCallAccount(
        val id: String,
        val displayName: String,
        val username: String
    )

    companion object {
        private const val TAG = "SipEngine"
        private const val REGISTRATION_TIMEOUT_MS = 15_000L
        private const val MAX_FAILURES = 2

        internal fun resolveAccountDisplayName(account: SipAccountData): String {
            return account.name.trim()
                .ifEmpty { account.username.trim().substringBefore("@") }
                .ifEmpty { account.authUsername.trim() }
                .ifEmpty { account.domain.trim() }
                .ifEmpty { "SIP" }
        }

        @Volatile
        private var INSTANCE: SipEngine? = null

        fun getInstance(context: Context): SipEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SipEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val core: Core get() = SipDialApplication.core

    fun keepAliveTick() {
        mainHandler.post {
            try {
                val activeCore = SipDialApplication.coreOrNull() ?: return@post
                val online = isNetworkAvailable()
                activeCore.isNetworkReachable = online
                if (online && activeCore.callsNb == 0 && activeCore.accountList.isNotEmpty()) {
                    SipDialApplication.applyUserAgent(activeCore)
                    activeCore.refreshRegisters()
                } else if (online && activeCore.accountList.isEmpty()) {
                    ioScope.launch { registerAllSaved() }
                }
            } catch (_: Exception) {
            }
        }
    }

    private val _registrationState = MutableStateFlow(SipRegistrationState.UNREGISTERED)
    val registrationState: StateFlow<SipRegistrationState> = _registrationState.asStateFlow()

    private val _registrationError = MutableStateFlow<String?>(null)
    val registrationError: StateFlow<String?> = _registrationError.asStateFlow()

    private val _networkType = MutableStateFlow(NetworkTransportType.NONE)
    val networkType: StateFlow<NetworkTransportType> = _networkType.asStateFlow()

    private val _accountStates = MutableStateFlow<Map<String, SipRegistrationState>>(emptyMap())
    val accountStates: StateFlow<Map<String, SipRegistrationState>> = _accountStates.asStateFlow()

    private val _accountErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val accountErrors: StateFlow<Map<String, String>> = _accountErrors.asStateFlow()

    private val _callAccountId = MutableStateFlow<String?>(null)
    val callAccountId: StateFlow<String?> = _callAccountId.asStateFlow()

    private val _callAccountName = MutableStateFlow<String?>(null)
    val callAccountName: StateFlow<String?> = _callAccountName.asStateFlow()

    private val _callAccountUser = MutableStateFlow<String?>(null)
    val callAccountUser: StateFlow<String?> = _callAccountUser.asStateFlow()

    private val _callState = MutableStateFlow<SipCallState>(SipCallState.Idle)
    val callState: StateFlow<SipCallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaker = MutableStateFlow(false)
    val isSpeaker: StateFlow<Boolean> = _isSpeaker.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingError = MutableStateFlow<String?>(null)
    val recordingError: StateFlow<String?> = _recordingError.asStateFlow()

    fun clearRecordingError() {
        _recordingError.value = null
    }

    private val _audioRoutes = MutableStateFlow<List<AudioRouteOption>>(emptyList())
    val audioRoutes: StateFlow<List<AudioRouteOption>> = _audioRoutes.asStateFlow()

    private val _selectedAudioRoute = MutableStateFlow<AudioRouteOption?>(null)
    val selectedAudioRoute: StateFlow<AudioRouteOption?> = _selectedAudioRoute.asStateFlow()

    private val _callQuality = MutableStateFlow<CallQualityStats?>(null)
    val callQuality: StateFlow<CallQualityStats?> = _callQuality.asStateFlow()

    var onIncomingCall: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentAccount: SipAccountData? = null
    private val allKnownAccounts = HashMap<String, SipAccountData>()
    private val registeredData = HashMap<String, SipAccountData>()
    private val authInfos = HashMap<String, MutableList<AuthInfo>>()
    private val failureCounts = HashMap<String, Int>()
    private val authChallenges = HashMap<String, Int>()
    private val stoppedByError = HashSet<String>()
    private val regTimeouts = HashMap<String, Runnable>()
    private val lastProblemNotifyAt = HashMap<String, Long>()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var networkOnline = true
    @Volatile
    private var activeNetworkId: Long? = null
    @Volatile
    private var lastTransportType: NetworkTransportType = NetworkTransportType.NONE

    private var activeCallConnected = false
    private var activeCallRecorded = false
    private var activeCallAccountSnapshot: ResolvedCallAccount? = null
    private var activeRecordingPath: String = ""
    private val completedRecordingPaths = mutableListOf<String>()
    private var manualAudioRouteId: String? = null
    private var speakerWanted = false
    private var autoAnswerRunnable: Runnable? = null

    private val qualityPoller = object : Runnable {
        override fun run() {
            try {
                val call = core.currentCall
                if (call == null || (call.state != Call.State.StreamsRunning && call.state != Call.State.Connected)) {
                    _callQuality.value = null
                    return
                }
                val pt = call.currentParams.usedAudioPayloadType
                val codec = pt?.let {
                    val khz = if (it.mimeType.equals("G722", ignoreCase = true)) 16 else it.clockRate / 1000
                    "${it.mimeType.uppercase()} ${khz}k"
                }.orEmpty()
                val clockKhz = (pt?.clockRate ?: 8000).coerceAtLeast(8000) / 1000f
                val stats = call.audioStats
                if (stats != null) {
                    _callQuality.value = CallQualityStats(
                        codec = codec,
                        rttMs = (stats.roundTripDelay * 1000f).toInt().coerceAtLeast(0),
                        jitterMs = (stats.receiverInterarrivalJitter / clockKhz).toInt().coerceAtLeast(0),
                        lossPercent = maxOf(stats.localLossRate, stats.receiverLossRate).coerceAtLeast(0f),
                        downKbps = stats.downloadBandwidth.toInt(),
                        upKbps = stats.uploadBandwidth.toInt(),
                        rating = call.currentQuality,
                        micDb = call.recordVolume,
                        inputDevice = core.inputAudioDevice?.deviceName.orEmpty(),
                        micEnabled = core.isMicEnabled
                    )
                }
                mainHandler.postDelayed(this, 1000L)
            } catch (e: Exception) {
                Log.w(TAG, "Quality poll failed: ${e.message}")
            }
        }
    }

    private fun startQualityPolling() {
        mainHandler.removeCallbacks(qualityPoller)
        mainHandler.postDelayed(qualityPoller, 1000L)
    }

    private fun stopQualityPolling() {
        mainHandler.removeCallbacks(qualityPoller)
        _callQuality.value = null
    }

    internal fun resolveTransportType(caps: NetworkCapabilities?): NetworkTransportType {
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkTransportType.NONE
        }
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransportType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportType.MOBILE_DATA
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransportType.ETHERNET
            else -> NetworkTransportType.OTHER
        }
    }

    private fun currentNetworkTransport(): NetworkTransportType {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return NetworkTransportType.NONE
            resolveTransportType(cm.getNetworkCapabilities(network))
        } catch (_: Exception) {
            if (networkOnline) _networkType.value else NetworkTransportType.NONE
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            networkOnline
        }
    }

    private fun cleanUser(a: SipAccountData) = a.username.trim().substringBefore("@")
    private fun authUser(a: SipAccountData) = a.authUsername.trim().ifEmpty { cleanUser(a) }
    private fun cleanDomain(a: SipAccountData) = parseHostAndPort(a.domain, a.port).first

    private fun setAccState(
        id: String,
        st: SipRegistrationState,
        error: String? = null,
        clearError: Boolean = false
    ) {
        _accountStates.value = _accountStates.value + (id to st)
        if (error != null) {
            _accountErrors.value = _accountErrors.value + (id to error)
        } else if (clearError || st == SipRegistrationState.REGISTERED ||
            st == SipRegistrationState.REGISTERING || st == SipRegistrationState.DISABLED
        ) {
            _accountErrors.value = _accountErrors.value - id
        }
        publishSelected()
        try {
            if (st == SipRegistrationState.ERROR && error != null) {
                val now = System.currentTimeMillis()
                if (now - (lastProblemNotifyAt[id] ?: 0L) > 10 * 60 * 1000L) {
                    lastProblemNotifyAt[id] = now
                    val name = registeredData[id]?.let { it.name.ifBlank { it.username } } ?: "SIP account"
                    NotificationHelper.showAccountProblem(context, id, name, error)
                }
            } else if (st == SipRegistrationState.REGISTERED) {
                lastProblemNotifyAt.remove(id)
                NotificationHelper.clearAccountProblem(context, id)
            }
        } catch (_: Exception) {
        }
    }

    private fun publishSelected() {
        val acc = currentAccount
        if (acc == null) {
            _registrationState.value = SipRegistrationState.UNREGISTERED
            _registrationError.value = null
            return
        }
        if (!acc.enabled) {
            _registrationState.value = SipRegistrationState.DISABLED
            _registrationError.value = null
            return
        }
        if (!isNetworkAvailable() && _accountStates.value[acc.id] != SipRegistrationState.REGISTERED) {
            _registrationState.value = SipRegistrationState.NETWORK_UNAVAILABLE
            _registrationError.value = "No active internet connection"
            return
        }
        _registrationState.value = _accountStates.value[acc.id] ?: SipRegistrationState.UNREGISTERED
        _registrationError.value = _accountErrors.value[acc.id]
    }

    @Volatile private var inviteArrivals = 0
    @Volatile private var lastInviteAtMs = 0L
    @Volatile private var lastInviteGapMs = 0L
    @Volatile private var inviteBacklogBurst = false

    private fun onInviteArrived() {
        val now = SystemClock.elapsedRealtime()
        if (inviteArrivals > 0) {
            lastInviteGapMs = now - lastInviteAtMs
            if (inviteArrivals == 1 && lastInviteGapMs < 800L) inviteBacklogBurst = true
        }
        lastInviteAtMs = now
        inviteArrivals++
    }

    private fun resetInviteTracking() {
        inviteArrivals = 0
        lastInviteAtMs = 0L
        lastInviteGapMs = 0L
        inviteBacklogBurst = false
    }

    private val sipLogListener = object : LoggingServiceListener {
        override fun onLogMessageWritten(
            service: LoggingService,
            domain: String,
            level: LogLevel,
            message: String
        ) {
            try {
                val isInboundInvite = (message.contains("new bytes from") && message.contains("INVITE sip:")) ||
                    message.startsWith("INVITE sip:")
                if (isInboundInvite && _callState.value is SipCallState.Incoming) {
                    onInviteArrived()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun inviteRetransmitsStopped(): Boolean {
        if (inviteArrivals < 2 || lastInviteGapMs < 0L) return false
        val threshold = if (inviteBacklogBurst) 5000L else maxOf(6000L, (lastInviteGapMs * 22) / 10)
        return SystemClock.elapsedRealtime() - lastInviteAtMs > threshold
    }

    private val ringingWatchdog = object : Runnable {
        override fun run() {
            try {
                core.iterate()
                val current = _callState.value
                if (current is SipCallState.Incoming) {
                    if (inviteRetransmitsStopped()) {
                        Log.i(TAG, "RingingWatchdog: INVITE retransmits stopped -> caller hung up")
                        val ringingCall = core.calls.firstOrNull {
                            it.state == Call.State.IncomingReceived || it.state == Call.State.IncomingEarlyMedia
                        }
                        resetInviteTracking()
                        try {
                            ringingCall?.decline(Reason.NotAnswered)
                        } catch (_: Exception) {
                            try { ringingCall?.terminate() } catch (_: Exception) {}
                        }
                    }
                    val stillRinging = core.calls.any {
                        it.state == Call.State.IncomingReceived || it.state == Call.State.IncomingEarlyMedia
                    }
                    if (!stillRinging) {
                        CallRinger.stop()
                        try { core.stopRinging() } catch (_: Exception) {}
                        NotificationHelper.cancelIncomingCallNotification(context)
                        NativeDialerOverlay.dismiss()
                        _callState.value = SipCallState.Ended(current.remoteAddress, "Caller hung up")
                        return
                    }
                    mainHandler.postDelayed(this, 200L)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun prepareRecordingPath(remote: String): String {
        return try {
            val baseDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "CallRecordings")
            if (!baseDir.exists()) baseDir.mkdirs()
            val cleanNum = CallerIdFormatter.extractNumber(remote)
                .filter { it.isLetterOrDigit() || it == '+' }
                .ifEmpty { "call" }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            File(baseDir, "SIP_${cleanNum}_$stamp.wav").absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare recording path", e)
            ""
        }
    }

    private fun makeRegistrationTimeout(id: String): Runnable = Runnable {
        if (_accountStates.value[id] == SipRegistrationState.REGISTERING) {
            if (!isNetworkAvailable()) {
                setAccState(id, SipRegistrationState.NETWORK_UNAVAILABLE, error = "No active internet connection")
            } else {
                val data = registeredData[id]
                val transport = data?.transport ?: "UDP"
                val port = data?.port ?: 5060
                stopWithError(
                    id,
                    "No response from SIP server ($transport port $port). " +
                        "Please check the Domain/IP, Port, and internet connection."
                )
            }
        }
    }

    private fun stopWithError(id: String, message: String) {
        stoppedByError.add(id)
        regTimeouts.remove(id)?.let { mainHandler.removeCallbacks(it) }
        try {
            core.getAccountByIdkey(id)?.let { acc ->
                val params = acc.params.clone()
                params.isRegisterEnabled = false
                acc.params = params
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop registration retries", e)
        }
        setAccState(id, SipRegistrationState.ERROR, error = message)
    }

    private fun removeLinphoneAccount(id: String) {
        try {
            core.getAccountByIdkey(id)?.let { acc ->
                val params = acc.params.clone()
                params.isRegisterEnabled = false
                acc.params = params
                core.removeAccount(acc)
            }
        } catch (e: Exception) {
            Log.w(TAG, "removeLinphoneAccount failed: ${e.message}")
        }
        authInfos.remove(id)?.forEach {
            try { core.removeAuthInfo(it) } catch (_: Exception) {}
        }
    }

    private fun describeFailure(errorInfo: ErrorInfo?, reason: Reason?, message: String): Pair<String, Boolean> {
        if (!isNetworkAvailable()) {
            return "Network unavailable" to false
        }
        val code = errorInfo?.protocolCode ?: 0
        val phrase = errorInfo?.phrase?.takeIf { it.isNotBlank() } ?: message
        val lower = phrase.lowercase()

        return when {
            code == 401 || code == 407 || reason == Reason.Unauthorized ->
                "Invalid username or password (Code $code: $phrase)." to true
            code == 403 || reason == Reason.Forbidden ->
                "Access denied by server (403 Forbidden: $phrase)." to true
            code == 404 || reason == Reason.NotFound ->
                "Account/extension not found on server (404 Not Found)." to true
            lower.contains("certificate") || lower.contains("ssl") || lower.contains("tls") ->
                "TLS connection failed ($phrase). Switch Transport to UDP/5060 or disable strict TLS verification." to true
            code == 408 || code == 503 || reason == Reason.NoResponse ||
                reason == Reason.ServerTimeout || reason == Reason.IOError ->
                "Cannot reach server (${if (code > 0) "Code $code" else phrase}). Check Domain/IP, Port, and Transport." to false
            code > 0 -> "Registration failed (Code $code: $phrase)" to false
            else -> "Registration failed: ${phrase.ifBlank { "Unknown error" }}" to false
        }
    }

    private fun parseHostAndPort(rawInput: String, defaultPort: Int): Pair<String, Int> {
        val cleaned = rawInput.trim()
            .removePrefix("sip:")
            .removePrefix("sips:")
            .substringBefore(";")
            .substringBefore("/")
            .trim()

        if (cleaned.contains(":")) {
            val hostPart = cleaned.substringBefore(":").trim()
            val portPart = cleaned.substringAfter(":").trim().toIntOrNull()
            if (hostPart.isNotEmpty() && portPart != null && portPart in 1..65535) {
                return hostPart to portPart
            }
        }
        return cleaned to defaultPort
    }

    private val coreListener = object : CoreListenerStub() {

        override fun onAudioDevicesListUpdated(core: Core) {
            mainHandler.post {
                refreshAudioRoutes()
                applyAudioRoute()
            }
        }

        override fun onAuthenticationRequested(
            core: Core,
            authInfo: AuthInfo,
            method: AuthMethod
        ) {
            val challengeUser = authInfo.username.orEmpty()
            val challengeDomain = authInfo.domain.orEmpty()
            val account = registeredData.values.firstOrNull {
                (cleanUser(it) == challengeUser || authUser(it) == challengeUser) &&
                    (challengeDomain.isEmpty() || cleanDomain(it).equals(challengeDomain, true))
            } ?: registeredData.values.firstOrNull {
                cleanUser(it) == challengeUser || authUser(it) == challengeUser
            }
            if (account == null) return

            val id = account.id
            val count = (authChallenges[id] ?: 0) + 1
            authChallenges[id] = count

            if (count <= 1 && account.password.isNotEmpty()) {
                val cleanDom = cleanDomain(account)
                val cleanUsr = cleanUser(account)
                val authId = authUser(account)
                val realmAuth = Factory.instance().createAuthInfo(
                    cleanUsr,
                    authId,
                    account.password,
                    null,
                    authInfo.realm,
                    authInfo.domain?.ifEmpty { cleanDom } ?: cleanDom
                )
                core.addAuthInfo(realmAuth)
                authInfos.getOrPut(id) { mutableListOf() }.add(realmAuth)
                core.refreshRegisters()
                return
            }

            if (core.callsNb == 0 && _accountStates.value[id] != SipRegistrationState.REGISTERED) {
                mainHandler.post {
                    stopWithError(id, "Invalid username or password (401 Unauthorized).")
                }
            }
        }

        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            val id = try { account.params.idkey } catch (_: Exception) { null }
            if (id.isNullOrEmpty()) return
            handleRegistrationState(id, state, account.errorInfo, account.error, message)
        }

        private fun handleRegistrationState(
            id: String,
            state: RegistrationState,
            errorInfo: ErrorInfo?,
            reason: Reason?,
            message: String
        ) {
            when (state) {
                RegistrationState.Ok, RegistrationState.Refreshing -> {
                    regTimeouts.remove(id)?.let { mainHandler.removeCallbacks(it) }
                    failureCounts[id] = 0
                    authChallenges[id] = 0
                    stoppedByError.remove(id)
                    setAccState(id, SipRegistrationState.REGISTERED, clearError = true)
                }
                RegistrationState.Progress -> {
                    if (id !in stoppedByError && registeredData.containsKey(id)) {
                        setAccState(id, SipRegistrationState.REGISTERING)
                    }
                }
                RegistrationState.Failed -> {
                    if (!registeredData.containsKey(id)) return
                    if (!isNetworkAvailable()) {
                        regTimeouts.remove(id)?.let { mainHandler.removeCallbacks(it) }
                        setAccState(id, SipRegistrationState.NETWORK_UNAVAILABLE, error = "Network unavailable")
                        return
                    }
                    val n = (failureCounts[id] ?: 0) + 1
                    failureCounts[id] = n
                    val (msg, fatal) = describeFailure(errorInfo, reason, message)
                    if (fatal || n >= MAX_FAILURES) {
                        mainHandler.post { stopWithError(id, msg) }
                    } else {
                        _accountErrors.value = _accountErrors.value + (id to msg)
                        _accountStates.value = _accountStates.value + (id to SipRegistrationState.REGISTERING)
                        publishSelected()
                    }
                }
                RegistrationState.Cleared, RegistrationState.None -> {
                    if (id !in stoppedByError && registeredData.containsKey(id)) {
                        val acc = registeredData[id]
                        if (acc != null && !acc.enabled) {
                            setAccState(id, SipRegistrationState.DISABLED)
                        } else if (!isNetworkAvailable()) {
                            setAccState(id, SipRegistrationState.NETWORK_UNAVAILABLE)
                        } else {
                            setAccState(id, SipRegistrationState.UNREGISTERED)
                        }
                    }
                }
            }
        }

        private fun forwardSipCall(call: Call, number: String) {
            try {
                val domain = call.toAddress?.domain
                val target = if (number.contains("@")) "sip:$number" else "sip:$number@$domain"
                val targetAddr = Factory.instance().createAddress(target)
                if (targetAddr == null) {
                    call.decline(Reason.Declined)
                } else {
                    call.redirectTo(targetAddr)
                }
            } catch (e: Exception) {
                Log.w(TAG, "forward failed", e)
                try { call.decline(Reason.Declined) } catch (_: Exception) {}
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            val remoteAddress = CallerIdFormatter.resolveCallAddress(call)
            when (state) {
                Call.State.IncomingReceived -> {
                    if (core.callsNb > 1) {
                        val fwBusy = CallForward.activeNumber(context)
                        val fwMode = CallForward.mode(context)
                        if (fwBusy != null && (fwMode == CallForward.BUSY || fwMode == CallForward.ALL)) {
                            forwardSipCall(call, fwBusy)
                            return
                        }
                        try { call.decline(Reason.Busy) } catch (_: Exception) {}
                        return
                    }
                    activeCallConnected = false
                    activeCallRecorded = false
                    completedRecordingPaths.clear()
                    _recordingError.value = null
                    if (BlockList.isBlocked(context, remoteAddress)) {
                        activeRecordingPath = ""
                        publishActiveCallAccount(resolveCallAccount(call))
                        try { call.decline(Reason.Declined) } catch (_: Exception) {}
                        return
                    }
                    val fwNum = CallForward.activeNumber(context)
                    val fwType = CallForward.mode(context)
                    if (fwNum != null && fwType == CallForward.ALL) {
                        forwardSipCall(call, fwNum)
                        return
                    }
                    activeRecordingPath = ""
                    try { call.notifyRinging() } catch (_: Exception) {}
                    resetInviteTracking()
                    onInviteArrived()
                    publishActiveCallAccount(resolveCallAccount(call))
                    _callState.value = SipCallState.Incoming(remoteAddress, call)
                    CallRinger.start(context)
                    TelecomHelper.reportIncomingCall(context, remoteAddress)
                    mainHandler.removeCallbacks(ringingWatchdog)
                    mainHandler.postDelayed(ringingWatchdog, 200L)

                    if (CallingPrefs.isAutoAnswerEnabled(context)) {
                        val delayMs = CallingPrefs.getAutoAnswerDelaySeconds(context) * 1000L
                        autoAnswerRunnable?.let { mainHandler.removeCallbacks(it) }
                        val r = Runnable {
                            val st = call.state
                            if (st == Call.State.IncomingReceived || st == Call.State.IncomingEarlyMedia) {
                                answerCall()
                            }
                        }
                        autoAnswerRunnable = r
                        mainHandler.postDelayed(r, delayMs)
                    }

                    if (fwNum != null && fwType == CallForward.NO_ANSWER) {
                        mainHandler.postDelayed({
                            val still = call.state == Call.State.IncomingReceived ||
                                call.state == Call.State.IncomingEarlyMedia
                            if (still) {
                                CallRinger.stop()
                                forwardSipCall(call, fwNum)
                            }
                        }, CallForward.seconds(context) * 1000L)
                    }
                }
                Call.State.IncomingEarlyMedia -> {
                    _callState.value = SipCallState.Incoming(remoteAddress, call)
                }
                Call.State.OutgoingInit -> {
                    mainHandler.removeCallbacks(ringingWatchdog)
                    activeCallConnected = false
                    activeCallRecorded = false
                    completedRecordingPaths.clear()
                    activeRecordingPath = ""
                    _recordingError.value = null
                    manualAudioRouteId = null
                    CallRinger.stop()
                    CallNetworkLock.acquire(context)
                    SipForegroundService.setCallActive(context, true)
                    publishActiveCallAccount(resolveCallAccount(call))
                    _callState.value = SipCallState.Outgoing(remoteAddress, call)
                    refreshAudioRoutes()
                }
                Call.State.OutgoingProgress,
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> {
                    _callState.value = SipCallState.Outgoing(remoteAddress, call)
                    applyAudioRoute()
                }
                Call.State.Connected -> {
                    mainHandler.removeCallbacks(ringingWatchdog)
                    autoAnswerRunnable?.let { mainHandler.removeCallbacks(it) }
                    autoAnswerRunnable = null
                    refreshAudioRoutes()
                    applyAudioRoute()
                    activeCallConnected = true
                    CallNetworkLock.acquire(context)
                    SipForegroundService.setCallActive(context, true)
                    CallRinger.stop()
                    try { core.stopRinging() } catch (_: Exception) {}
                    NotificationHelper.cancelIncomingCallNotification(context)
                    NativeDialerOverlay.dismissIncomingIfVisible()
                    if (activeCallAccountSnapshot == null) {
                        publishActiveCallAccount(resolveCallAccount(call))
                    }
                    try { core.isMicEnabled = !_isMuted.value } catch (_: Exception) {}
                    _callState.value = SipCallState.Connected(remoteAddress, call)
                }
                Call.State.StreamsRunning -> {
                    mainHandler.removeCallbacks(ringingWatchdog)
                    autoAnswerRunnable?.let { mainHandler.removeCallbacks(it) }
                    autoAnswerRunnable = null
                    refreshAudioRoutes()
                    applyAudioRoute()
                    mainHandler.postDelayed({ if (core.currentCall != null) forceBluetoothSwitch() }, 400)
                    mainHandler.postDelayed({ if (core.currentCall != null) forceBluetoothSwitch() }, 1500)
                    mainHandler.postDelayed({ if (core.currentCall != null) applyAudioRoute() }, 3000)
                    activeCallConnected = true
                    CallNetworkLock.acquire(context)
                    SipForegroundService.setCallActive(context, true)
                    startQualityPolling()
                    CallRinger.stop()
                    try { core.stopRinging() } catch (_: Exception) {}
                    NotificationHelper.cancelIncomingCallNotification(context)
                    NativeDialerOverlay.dismissIncomingIfVisible()
                    if (activeCallAccountSnapshot == null) {
                        publishActiveCallAccount(resolveCallAccount(call))
                    }
                    try {
                        core.isMicEnabled = !_isMuted.value
                        if (TelecomHelper.isAutoRecordEnabled(context) && !_isRecording.value) {
                            startCallRecordingInternal(call, remoteAddress)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-record failed: ${e.message}")
                        _recordingError.value = "Auto call recording failed: ${e.message ?: "Stream unavailable"}"
                    }
                    _callState.value = SipCallState.Connected(remoteAddress, call)
                }
                Call.State.Paused, Call.State.PausedByRemote -> {
                    _callState.value = SipCallState.OnHold(remoteAddress, call)
                }
                Call.State.End, Call.State.Error, Call.State.Released -> {
                    mainHandler.removeCallbacks(ringingWatchdog)
                    autoAnswerRunnable?.let { mainHandler.removeCallbacks(it) }
                    autoAnswerRunnable = null
                    stopQualityPolling()
                    resetInviteTracking()
                    if (core.callsNb <= 1) {
                        CallNetworkLock.release()
                        SipForegroundService.setCallActive(context, false)
                    }
                    CallRinger.stop()
                    try { core.stopRinging() } catch (_: Exception) {}
                    NotificationHelper.cancelIncomingCallNotification(context)
                    NotificationHelper.cancelOngoingCallNotification(context)
                    NativeDialerOverlay.dismiss()
                    if (_isRecording.value || activeRecordingPath.isNotEmpty()) {
                        try { call.stopRecording() } catch (_: Exception) {}
                    }
                    _isRecording.value = false
                    if (!activeCallRecorded) {
                        activeCallRecorded = true
                        val durationSec = try { call.duration.coerceAtLeast(0) } catch (_: Exception) { 0 }
                        val isIncoming = try { call.dir == Call.Dir.Incoming } catch (_: Exception) { false }
                        val wasAnswered = activeCallConnected || durationSec > 0
                        val rawDisplay = try { call.remoteAddress?.displayName.orEmpty().trim() } catch (_: Exception) { "" }
                        val displayName = if (rawDisplay.lowercase() in setOf(
                                "anonymous", "unknown", "restricted", "private", "unavailable", "withheld"
                            )
                        ) {
                            ""
                        } else {
                            rawDisplay
                        }
                        val resolvedAcc = activeCallAccountSnapshot ?: resolveCallAccount(call)
                        val accountIdForRecord = resolvedAcc?.id.orEmpty()
                        val accountNameForRecord = resolvedAcc?.displayName.orEmpty()
                        val accountUserForRecord = resolvedAcc?.username?.ifBlank { _callAccountUser.value.orEmpty() }
                            ?: _callAccountUser.value.orEmpty()
                        val recCandidates = (completedRecordingPaths + listOfNotNull(activeRecordingPath.takeIf { it.isNotEmpty() }))
                            .distinct()
                        completedRecordingPaths.clear()
                        activeRecordingPath = ""
                        activeCallAccountSnapshot = null
                        ioScope.launch {
                            try {
                                val validRecPath = if (wasAnswered) {
                                    var chosen = ""
                                    for (path in recCandidates.asReversed()) {
                                        val f = File(path)
                                        if (f.exists() && f.length() > 44L) {
                                            chosen = RecordingStorage.exportToPublicStorage(context, f) ?: path
                                            break
                                        } else if (f.exists()) {
                                            try { f.delete() } catch (_: Exception) {}
                                        }
                                    }
                                    chosen
                                } else {
                                    recCandidates.forEach { path ->
                                        try { File(path).delete() } catch (_: Exception) {}
                                    }
                                    ""
                                }
                                AppDatabase.getInstance(context).callHistoryDao().insertCallRecord(
                                    CallRecord(
                                        remoteAddress = remoteAddress,
                                        displayName = displayName,
                                        isIncoming = isIncoming,
                                        isAnswered = wasAnswered,
                                        duration = if (wasAnswered) durationSec else 0,
                                        recordingPath = validRecPath,
                                        accountUser = accountUserForRecord,
                                        accountId = accountIdForRecord,
                                        accountName = accountNameForRecord
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save call record", e)
                            }
                        }
                    }
                    val failure = try {
                        val ei = call.errorInfo
                        if (state == Call.State.Error || ei.protocolCode >= 300) {
                            val code = ei.protocolCode
                            val phrase = ei.phrase.orEmpty().ifBlank { call.reason.toString() }
                            if (code > 0) "Call failed: $code $phrase" else "Call failed: ${message.ifBlank { phrase }}"
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                    _callState.value = SipCallState.Ended(remoteAddress, failure ?: message)
                    _isMuted.value = false
                    _isSpeaker.value = false
                    speakerWanted = false
                    manualAudioRouteId = null
                }
                else -> {}
            }
        }
    }

    init {
        val activeCore = SipDialApplication.coreOrNull()
        if (activeCore != null) {
            activeCore.addListener(coreListener)
            try {
                Factory.instance().loggingService.addListener(sipLogListener)
            } catch (e: Exception) {
                Log.w(TAG, "Could not attach SIP log listener: ${e.message}")
            }
            refreshAudioRoutes()
        }
        CallBarOverlay.attach(context, this)
        val initialTransport = currentNetworkTransport()
        networkOnline = initialTransport != NetworkTransportType.NONE
        _networkType.value = initialTransport
        lastTransportType = initialTransport
        registerNetworkObserver()
    }

    internal fun onNetworkChangedFromService(
        available: Boolean,
        transport: NetworkTransportType,
        networkSwitched: Boolean
    ) {
        mainHandler.post {
            handleNetworkConnectivityChange(available, transport, networkSwitched)
        }
    }

    internal fun handleNetworkConnectivityChange(
        available: Boolean,
        transport: NetworkTransportType,
        networkSwitched: Boolean
    ) {
        val wasOffline = !networkOnline
        val effectiveTransport = if (available) transport else NetworkTransportType.NONE
        networkOnline = available
        _networkType.value = effectiveTransport
        lastTransportType = effectiveTransport

        val activeCore = SipDialApplication.coreOrNull()
        try {
            activeCore?.isNetworkReachable = available
        } catch (_: Exception) {
        }

        if (!available) {
            val ids = (registeredData.keys + allKnownAccounts.filterValues { it.enabled }.keys).toSet()
            ids.forEach { id ->
                regTimeouts.remove(id)?.let { mainHandler.removeCallbacks(it) }
                setAccState(id, SipRegistrationState.NETWORK_UNAVAILABLE, error = "No active internet connection")
            }
            publishSelected()
            return
        }

        val knownEnabled = registeredData.values.filter { it.enabled }
            .ifEmpty { allKnownAccounts.values.filter { it.enabled } }

        if (knownEnabled.isEmpty()) {
            ioScope.launch {
                try {
                    val hasEnabledSaved = AccountRepository(context).getAccounts().first().any { it.enabled }
                    if (hasEnabledSaved) registerAllSaved()
                } catch (_: Exception) {
                }
            }
            publishSelected()
            return
        }

        knownEnabled.forEach { acc ->
            val id = acc.id
            val st = _accountStates.value[id]
            val shouldFullReRegister = st == SipRegistrationState.NETWORK_UNAVAILABLE ||
                st == SipRegistrationState.UNREGISTERED ||
                (networkSwitched && st == SipRegistrationState.ERROR)
            if (shouldFullReRegister) {
                register(acc, select = id == currentAccount?.id, assumeNetworkAvailable = true)
            } else if ((networkSwitched || wasOffline) && id !in stoppedByError) {
                setAccState(id, SipRegistrationState.REGISTERING, clearError = true)
            }
        }

        if ((networkSwitched || wasOffline) && activeCore != null) {
            try {
                if (activeCore.callsNb == 0) {
                    if (networkSwitched) {
                        activeCore.isNetworkReachable = false
                        activeCore.isNetworkReachable = true
                    }
                    if (activeCore.accountList.isNotEmpty()) {
                        SipDialApplication.applyUserAgent(activeCore)
                        activeCore.refreshRegisters()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "refreshRegisters on network change failed: ${e.message}")
            }
        }
        publishSelected()
    }

    private fun registerNetworkObserver() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = try { cm.getNetworkCapabilities(network) } catch (_: Exception) { null }
                    val transport = resolveTransportType(caps)
                    val switched = (activeNetworkId != null && activeNetworkId != network.networkHandle) ||
                        (lastTransportType != NetworkTransportType.NONE && lastTransportType != transport)
                    activeNetworkId = network.networkHandle
                    mainHandler.post {
                        handleNetworkConnectivityChange(
                            available = transport != NetworkTransportType.NONE,
                            transport = transport,
                            networkSwitched = switched
                        )
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val transport = resolveTransportType(networkCapabilities)
                    val available = transport != NetworkTransportType.NONE
                    val switched = available && (
                        (activeNetworkId != null && activeNetworkId != network.networkHandle) ||
                            (lastTransportType != NetworkTransportType.NONE && lastTransportType != transport)
                        )
                    if (!available || switched || !networkOnline) {
                        if (available) activeNetworkId = network.networkHandle
                        mainHandler.post {
                            handleNetworkConnectivityChange(
                                available = available,
                                transport = transport,
                                networkSwitched = switched
                            )
                        }
                    }
                }

                override fun onLost(network: Network) {
                    mainHandler.post {
                        val currentActive = try { cm.activeNetwork } catch (_: Exception) { null }
                        if (currentActive == null) {
                            activeNetworkId = null
                            handleNetworkConnectivityChange(
                                available = false,
                                transport = NetworkTransportType.NONE,
                                networkSwitched = false
                            )
                        } else {
                            val nextCaps = try { cm.getNetworkCapabilities(currentActive) } catch (_: Exception) { null }
                            val nextTransport = resolveTransportType(nextCaps)
                            val available = nextTransport != NetworkTransportType.NONE
                            val switched = available && (
                                activeNetworkId != currentActive.networkHandle ||
                                    (lastTransportType != NetworkTransportType.NONE && lastTransportType != nextTransport)
                                )
                            activeNetworkId = if (available) currentActive.networkHandle else null
                            handleNetworkConnectivityChange(
                                available = available,
                                transport = nextTransport,
                                networkSwitched = switched
                            )
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        } catch (_: Exception) {
        }
    }

    private fun publishActiveCallAccount(resolved: ResolvedCallAccount?) {
        activeCallAccountSnapshot = resolved
        _callAccountId.value = resolved?.id
        _callAccountName.value = resolved?.displayName
        _callAccountUser.value = resolved?.username
    }

    private fun resolveCallAccount(call: Call): ResolvedCallAccount? {
        return try {
            val idKey = try {
                call.params.account?.params?.idkey
            } catch (_: Exception) {
                null
            }
            val knownById = if (!idKey.isNullOrBlank()) {
                registeredData[idKey] ?: allKnownAccounts[idKey]
            } else {
                null
            }
            val local = call.callLog?.localAddress
                ?: try { call.params.account?.params?.identityAddress } catch (_: Exception) { null }
                ?: if (call.dir == Call.Dir.Incoming) call.toAddress else call.callLog?.fromAddress
            val user = local?.username.orEmpty()
            val dom = local?.domain.orEmpty()

            val pool = (registeredData + allKnownAccounts).values
            val matched = knownById
                ?: pool.firstOrNull { cleanUser(it) == user && cleanDomain(it).equals(dom, true) }
                ?: pool.firstOrNull { cleanUser(it) == user }
                ?: currentAccount

            if (matched != null) {
                ResolvedCallAccount(
                    id = matched.id,
                    displayName = resolveAccountDisplayName(matched),
                    username = cleanUser(matched).ifEmpty { matched.authUsername.trim() }
                )
            } else if (user.isNotBlank() || dom.isNotBlank()) {
                ResolvedCallAccount(
                    id = idKey.orEmpty(),
                    displayName = user.ifBlank { dom },
                    username = user
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun registerAllSavedAsync() {
        ioScope.launch {
            try {
                registerAllSaved()
            } catch (_: Throwable) {
            }
        }
    }

    suspend fun registerAllSaved() {
        val repo = AccountRepository(context)
        val all = repo.getAccounts().first()
        val selected = repo.getAccount().first()
        withContext(Dispatchers.Main) {
            all.forEach { acc ->
                allKnownAccounts[acc.id] = acc
                if (!acc.enabled) {
                    setAccState(acc.id, SipRegistrationState.DISABLED)
                }
            }
            all.filter { it.enabled && it.id != selected?.id }.forEach { register(it, select = false) }
            selected?.let {
                if (it.enabled) register(it, select = true)
                else {
                    currentAccount = it
                    setAccState(it.id, SipRegistrationState.DISABLED)
                }
            }
        }
    }

    fun register(account: SipAccountData, select: Boolean = true, assumeNetworkAvailable: Boolean = false) {
        val id = account.id
        allKnownAccounts[id] = account
        if (select) currentAccount = account

        if (!account.enabled) {
            unregisterAccount(id, markDisabled = true)
            return
        }

        if (!com.sipdial.app.service.KeepAliveState.stoppedByUser(context) &&
            !SipForegroundService.isActive()
        ) {
            SipForegroundService.start(context)
        }

        if (!assumeNetworkAvailable && !isNetworkAvailable()) {
            networkOnline = false
            _networkType.value = NetworkTransportType.NONE
            lastTransportType = NetworkTransportType.NONE
            registeredData[id] = account
            setAccState(id, SipRegistrationState.NETWORK_UNAVAILABLE, error = "No active internet connection")
            return
        }

        val activeCore = SipDialApplication.coreOrNull()
        if (activeCore == null) {
            registeredData[id] = account
            setAccState(id, SipRegistrationState.REGISTERING, clearError = true)
            return
        }

        val st = _accountStates.value[id]
        if (registeredData[id] == account &&
            (st == SipRegistrationState.REGISTERED || st == SipRegistrationState.REGISTERING)
        ) {
            if (select) {
                try { activeCore.getAccountByIdkey(id)?.let { activeCore.defaultAccount = it } } catch (_: Exception) {}
            }
            publishSelected()
            return
        }

        regTimeouts.remove(id)?.let { mainHandler.removeCallbacks(it) }
        failureCounts[id] = 0
        authChallenges[id] = 0
        stoppedByError.remove(id)
        _accountErrors.value = _accountErrors.value - id
        registeredData.remove(id)
        removeLinphoneAccount(id)
        publishSelected()

        try {
            SipDialApplication.applyUserAgent(activeCore)
            if (core.globalState != GlobalState.On) {
                core.start()
                SipDialApplication.applyUserAgent(core)
            }

            val verifyTls = SecurityPrefs.verifyTls(SipDialApplication.instance)
            core.verifyServerCertificates(verifyTls)
            core.verifyServerCn(verifyTls)
            core.mediaEncryption = if (SecurityPrefs.srtp(SipDialApplication.instance)) {
                MediaEncryption.SRTP
            } else {
                MediaEncryption.None
            }
            core.isMediaEncryptionMandatory = false
            core.isIpv6Enabled = false
            core.isKeepAliveEnabled = true
            core.guessHostname = true
            core.isPushNotificationEnabled = false
            core.isAutoSendRingingEnabled = true
            core.incTimeout = 35

            try {
                val dtmfMode = CallingPrefs.getDtmfMode(context)
                core.useRfc2833ForDtmf = (dtmfMode != CallingPrefs.DTMF_SIP_INFO)
                core.useInfoForDtmf = (dtmfMode == CallingPrefs.DTMF_SIP_INFO || dtmfMode == CallingPrefs.DTMF_AUTO)
                core.config.setInt("sip", "keepalive_period", 15000)
                core.config.setInt("sip", "use_rport", 1)
                core.config.setInt("sip", "contact_autorewrite", 1)
                core.config.setInt("sip", "guess_hostname", 1)
                if (core.callsNb == 0) SipDialApplication.configureVoiceAudio(core)
            } catch (_: Exception) {
            }

            if (core.accountList.isEmpty()) {
                core.transports = core.transports.apply {
                    udpPort = -1
                    tcpPort = -1
                    tlsPort = -1
                }
            }

            val cleanUsername = cleanUser(account)
            val authUsername = authUser(account)
            val (cleanDomain, domainEmbeddedPort) = parseHostAndPort(account.domain, account.port)

            val rawServer = account.proxy.trim().ifEmpty { account.domain }
            val (cleanServerHost, serverEmbeddedPort) = parseHostAndPort(rawServer, domainEmbeddedPort)
            val effectivePort = when {
                serverEmbeddedPort != account.port -> serverEmbeddedPort
                domainEmbeddedPort != account.port -> domainEmbeddedPort
                else -> account.port
            }

            if (cleanUsername.isEmpty() || cleanDomain.isEmpty() || cleanServerHost.isEmpty()) {
                setAccState(id, SipRegistrationState.ERROR, error = "Username and SIP Domain cannot be empty.")
                return
            }

            val authInfo = Factory.instance().createAuthInfo(
                cleanUsername,
                authUsername,
                account.password,
                null,
                null,
                cleanDomain
            )
            core.addAuthInfo(authInfo)
            authInfos.getOrPut(id) { mutableListOf() }.add(authInfo)

            val transportType = when (account.transport.uppercase()) {
                "TCP" -> TransportType.Tcp
                "TLS" -> TransportType.Tls
                else -> TransportType.Udp
            }

            val accountParams = core.createAccountParams()
            accountParams.idkey = id

            val identityAddr = Factory.instance().createAddress("sip:$cleanUsername@$cleanDomain")
            if (identityAddr == null) {
                setAccState(id, SipRegistrationState.ERROR, error = "Invalid username or domain format: $cleanUsername@$cleanDomain")
                return
            }
            identityAddr.displayName = account.displayName.trim().ifEmpty { cleanUsername }
            accountParams.identityAddress = identityAddr

            val serverAddr = Factory.instance().createAddress("sip:$cleanServerHost:$effectivePort")
            if (serverAddr == null) {
                setAccState(id, SipRegistrationState.ERROR, error = "Invalid server address format: $cleanServerHost:$effectivePort")
                return
            }
            serverAddr.transport = transportType
            accountParams.serverAddress = serverAddr
            accountParams.transport = transportType
            accountParams.isRegisterEnabled = true
            accountParams.isPublishEnabled = false
            accountParams.expires = account.registrationExpiry.coerceIn(30, 3600)
            accountParams.pushNotificationAllowed = false
            accountParams.remotePushNotificationAllowed = false
            accountParams.isOutboundProxyEnabled = true
            accountParams.setRoutesAddresses(arrayOf(serverAddr))

            val natPolicy = core.createNatPolicy().apply {
                isStunEnabled = false
                isIceEnabled = false
                isTurnEnabled = false
                isUpnpEnabled = false
            }
            accountParams.natPolicy = natPolicy

            val linphoneAccount = core.createAccount(accountParams)
            registeredData[id] = account
            core.addAccount(linphoneAccount)
            if (select || core.defaultAccount == null) {
                core.defaultAccount = linphoneAccount
            }

            setAccState(id, SipRegistrationState.REGISTERING)
            val timeout = makeRegistrationTimeout(id)
            regTimeouts[id] = timeout
            mainHandler.postDelayed(timeout, REGISTRATION_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            setAccState(id, SipRegistrationState.ERROR, error = "Configuration error: ${e.message ?: "Unknown error"}")
        }
    }

    fun unregisterAccount(id: String, markDisabled: Boolean = false) {
        regTimeouts.remove(id)?.let { mainHandler.removeCallbacks(it) }
        failureCounts.remove(id)
        authChallenges.remove(id)
        stoppedByError.remove(id)
        registeredData.remove(id)
        removeLinphoneAccount(id)
        if (markDisabled) {
            setAccState(id, SipRegistrationState.DISABLED, clearError = true)
        } else {
            _accountStates.value = _accountStates.value + (id to SipRegistrationState.UNREGISTERED)
            _accountErrors.value = _accountErrors.value - id
            if (currentAccount?.id == id) currentAccount = null
        }
        publishSelected()
    }

    fun unregister() {
        registeredData.keys.toList().forEach { unregisterAccount(it) }
        currentAccount = null
        try {
            core.clearAccounts()
            core.clearProxyConfig()
            core.clearAllAuthInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Unregister failed", e)
        }
        _accountStates.value = emptyMap()
        _accountErrors.value = emptyMap()
        publishSelected()
    }

    fun getActiveDialableAccounts(): List<SipAccountData> {
        val states = _accountStates.value
        val source = (allKnownAccounts + registeredData).values.filter { it.enabled }
        return source.sortedWith(
            compareByDescending<SipAccountData> { states[it.id] == SipRegistrationState.REGISTERED }
                .thenByDescending { it.id == currentAccount?.id }
                .thenBy { resolveAccountDisplayName(it).lowercase() }
        )
    }

    fun getSelectedAccount(): SipAccountData? = currentAccount

    fun makeCall(rawTarget: String, accountId: String? = null): Boolean {
        return try {
            val chosenData = accountId?.let { registeredData[it] ?: allKnownAccounts[it] } ?: currentAccount
            val selectedLinphoneAccount = chosenData?.let { core.getAccountByIdkey(it.id) }
            val decoded = Uri.decode(rawTarget)
                .removePrefix("sip:")
                .removePrefix("tel:")
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .trim()

            if (decoded.isEmpty()) return false

            val address = if (decoded.contains("@")) {
                "sip:$decoded"
            } else {
                val domain = selectedLinphoneAccount?.params?.identityAddress?.domain
                    ?: chosenData?.domain?.takeIf { it.isNotBlank() }
                if (domain.isNullOrEmpty()) {
                    Log.e(TAG, "Cannot make call: selected SIP account is not registered")
                    return false
                }
                "sip:$decoded@$domain"
            }

            if (chosenData != null) {
                publishActiveCallAccount(
                    ResolvedCallAccount(
                        id = chosenData.id,
                        displayName = resolveAccountDisplayName(chosenData),
                        username = cleanUser(chosenData).ifEmpty { chosenData.authUsername.trim() }
                    )
                )
            }

            val addr = Factory.instance().createAddress(address) ?: return false
            val params = core.createCallParams(null)
            params?.mediaEncryption = if (SecurityPrefs.srtp(context)) MediaEncryption.SRTP else MediaEncryption.None
            if (selectedLinphoneAccount != null) params?.account = selectedLinphoneAccount
            params?.isAudioEnabled = true
            params?.isVideoEnabled = false
            activeRecordingPath = ""

            SipDialApplication.applyUserAgent(core)
            val call = core.inviteAddressWithParams(addr, params!!)
            call != null
        } catch (e: Exception) {
            Log.e(TAG, "Make call failed", e)
            false
        }
    }

    fun answerCall() {
        SipForegroundService.setCallActive(context, true)
        mainHandler.removeCallbacks(ringingWatchdog)
        autoAnswerRunnable?.let { mainHandler.removeCallbacks(it) }
        autoAnswerRunnable = null
        CallRinger.stop()
        try { core.stopRinging() } catch (_: Exception) {}
        NotificationHelper.cancelIncomingCallNotification(context)
        NativeDialerOverlay.dismiss()
        try {
            val targetCall = core.currentCall ?: core.calls.firstOrNull()
            targetCall?.let { call ->
                val remoteAddr = CallerIdFormatter.resolveCallAddress(call)
                val params = core.createCallParams(call)
                params?.isAudioEnabled = true
                params?.isVideoEnabled = false
                call.acceptWithParams(params!!)
                _callState.value = SipCallState.Connected(remoteAddr, call)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Answer call failed", e)
        }
    }

    fun endCall() {
        mainHandler.removeCallbacks(ringingWatchdog)
        autoAnswerRunnable?.let { mainHandler.removeCallbacks(it) }
        autoAnswerRunnable = null
        CallRinger.stop()
        try { core.stopRinging() } catch (_: Exception) {}
        NotificationHelper.cancelIncomingCallNotification(context)
        NativeDialerOverlay.dismiss()
        try {
            for (c in core.calls) {
                try {
                    if (c.state == Call.State.IncomingReceived || c.state == Call.State.IncomingEarlyMedia) {
                        c.decline(Reason.Declined)
                    } else {
                        c.terminate()
                    }
                } catch (_: Exception) {}
            }
            core.terminateAllCalls()
        } catch (e: Exception) {
            Log.e(TAG, "End call failed", e)
        }
        val currentRemote = when (val s = _callState.value) {
            is SipCallState.Incoming -> s.remoteAddress
            is SipCallState.Outgoing -> s.remoteAddress
            is SipCallState.Connected -> s.remoteAddress
            is SipCallState.OnHold -> s.remoteAddress
            else -> ""
        }
        if (currentRemote.isNotEmpty()) {
            _callState.value = SipCallState.Ended(currentRemote, "Call ended")
        }
    }

    fun declineCall() {
        mainHandler.removeCallbacks(ringingWatchdog)
        autoAnswerRunnable?.let { mainHandler.removeCallbacks(it) }
        autoAnswerRunnable = null
        CallRinger.stop()
        try { core.stopRinging() } catch (_: Exception) {}
        NotificationHelper.cancelIncomingCallNotification(context)
        NativeDialerOverlay.dismiss()
        try {
            for (c in core.calls) {
                try { c.decline(Reason.Declined) } catch (_: Exception) {
                    try { c.terminate() } catch (_: Exception) {}
                }
            }
            core.terminateAllCalls()
        } catch (e: Exception) {
            Log.e(TAG, "Decline call failed", e)
        }
        val currentRemote = (_callState.value as? SipCallState.Incoming)?.remoteAddress ?: ""
        _callState.value = SipCallState.Ended(currentRemote, "Declined")
    }

    fun setMute(muted: Boolean) {
        core.isMicEnabled = !muted
        _isMuted.value = muted
    }

    fun toggleMute() {
        setMute(!_isMuted.value)
    }

    private fun startCallRecordingInternal(call: Call, remote: String): Boolean {
        val path = prepareRecordingPath(remote)
        if (path.isEmpty()) {
            _recordingError.value = "Cannot access recording storage directory."
            return false
        }
        val params = call.params
        params.recordFile = path
        call.params = params
        activeRecordingPath = path
        call.startRecording()
        _isRecording.value = true
        _recordingError.value = null
        return true
    }

    fun toggleRecording() {
        val call = core.currentCall ?: core.calls.firstOrNull {
            it.state == Call.State.StreamsRunning || it.state == Call.State.Connected
        }
        if (call == null) {
            _recordingError.value = "No active call to record."
            return
        }
        val st = call.state
        if (!_isRecording.value && st != Call.State.StreamsRunning && st != Call.State.Connected) {
            _recordingError.value = "Start recording after the call connects."
            return
        }
        try {
            if (_isRecording.value) {
                call.stopRecording()
                _isRecording.value = false
                if (activeRecordingPath.isNotEmpty()) {
                    completedRecordingPaths.add(activeRecordingPath)
                    activeRecordingPath = ""
                }
            } else {
                val remote = CallerIdFormatter.resolveCallAddress(call)
                startCallRecordingInternal(call, remote)
            }
        } catch (e: Exception) {
            Log.w(TAG, "toggleRecording failed: ${e.message}")
            _isRecording.value = false
            _recordingError.value = "Recording failed: ${e.message ?: "Audio stream unavailable"}"
        }
    }

    private fun refreshAudioRoutes() {
        try {
            val options = core.audioDevices
                .filter { it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) }
                .mapNotNull { dev ->
                    val type = when (dev.type) {
                        AudioDevice.Type.Speaker -> AudioRouteType.SPEAKER
                        AudioDevice.Type.Bluetooth -> AudioRouteType.BLUETOOTH
                        AudioDevice.Type.Headset, AudioDevice.Type.Headphones -> AudioRouteType.WIRED_HEADSET
                        AudioDevice.Type.Earpiece -> AudioRouteType.EARPIECE
                        else -> null
                    } ?: return@mapNotNull null
                    val label = when (type) {
                        AudioRouteType.SPEAKER -> "Speaker"
                        AudioRouteType.EARPIECE -> "Earpiece"
                        AudioRouteType.BLUETOOTH -> dev.deviceName.takeIf { it.isNotBlank() } ?: "Bluetooth"
                        AudioRouteType.WIRED_HEADSET -> "Headset"
                    }
                    AudioRouteOption(
                        id = "${dev.type.name}:${dev.deviceName}",
                        name = label,
                        type = type
                    )
                }
                .distinctBy { it.id }
            _audioRoutes.value = options

            val currentOut = core.currentCall?.outputAudioDevice ?: core.outputAudioDevice
            _selectedAudioRoute.value = options.firstOrNull { opt ->
                currentOut != null && opt.id == "${currentOut.type.name}:${currentOut.deviceName}"
            } ?: if (speakerWanted) {
                options.firstOrNull { it.type == AudioRouteType.SPEAKER }
            } else {
                options.firstOrNull { it.type == AudioRouteType.BLUETOOTH }
                    ?: options.firstOrNull { it.type == AudioRouteType.WIRED_HEADSET }
                    ?: options.firstOrNull { it.type == AudioRouteType.EARPIECE }
            } ?: options.firstOrNull()
        } catch (_: Exception) {
        }
    }

    fun selectAudioRoute(option: AudioRouteOption) {
        manualAudioRouteId = option.id
        speakerWanted = option.type == AudioRouteType.SPEAKER
        _isSpeaker.value = speakerWanted
        val target = core.audioDevices.firstOrNull {
            "${it.type.name}:${it.deviceName}" == option.id
        } ?: pickOutputDevice()
        if (target != null) {
            routeTo(target)
        }
        refreshAudioRoutes()
    }

    fun setSpeaker(enableSpeaker: Boolean) {
        manualAudioRouteId = null
        speakerWanted = enableSpeaker
        applyAudioRoute()
        _isSpeaker.value = enableSpeaker
        refreshAudioRoutes()
    }

    private fun pickOutputDevice(): AudioDevice? {
        val devices = core.audioDevices
        manualAudioRouteId?.let { id ->
            devices.firstOrNull { "${it.type.name}:${it.deviceName}" == id }?.let { return it }
        }
        if (speakerWanted) return devices.find { it.type == AudioDevice.Type.Speaker }
        return devices.firstOrNull {
            it.type == AudioDevice.Type.Bluetooth && it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
        } ?: devices.firstOrNull {
            it.type == AudioDevice.Type.Headset || it.type == AudioDevice.Type.Headphones
        } ?: devices.firstOrNull { it.type == AudioDevice.Type.Earpiece }
    }

    fun applyAudioRoute() {
        try {
            if (core.callsNb == 0) return
            val out = pickOutputDevice() ?: return
            routeTo(out)
            refreshAudioRoutes()
        } catch (e: Exception) {
            Log.w(TAG, "applyAudioRoute failed", e)
        }
    }

    private fun routeTo(out: AudioDevice) {
        core.outputAudioDevice = out
        core.currentCall?.outputAudioDevice = out
        val input = if (out.hasCapability(AudioDevice.Capabilities.CapabilityRecord)) out
        else core.audioDevices.firstOrNull { it.type == AudioDevice.Type.Microphone }
        if (input != null) {
            core.inputAudioDevice = input
            core.currentCall?.inputAudioDevice = input
        }
    }

    private fun forceBluetoothSwitch() {
        try {
            val target = pickOutputDevice() ?: return
            if (speakerWanted || target.type != AudioDevice.Type.Bluetooth) {
                routeTo(target)
                return
            }
            val ear = core.audioDevices.firstOrNull { it.type == AudioDevice.Type.Earpiece }
            if (ear != null) routeTo(ear)
            mainHandler.postDelayed({ if (core.currentCall != null) applyAudioRoute() }, 200)
        } catch (e: Exception) {
            Log.w(TAG, "forceBluetoothSwitch failed", e)
        }
    }

    fun toggleSpeaker() {
        setSpeaker(!_isSpeaker.value)
    }

    fun toggleHold() {
        val call = core.currentCall
            ?: core.calls.firstOrNull { it.state == Call.State.Paused || it.state == Call.State.Pausing }
            ?: return
        if (call.state == Call.State.Paused || call.state == Call.State.Pausing) {
            call.resume()
        } else {
            call.pause()
        }
    }

    fun sendDtmf(digit: Char) {
        val call = core.currentCall ?: return
        try {
            val mode = CallingPrefs.getDtmfMode(context)
            core.useRfc2833ForDtmf = (mode != CallingPrefs.DTMF_SIP_INFO)
            core.useInfoForDtmf = (mode == CallingPrefs.DTMF_SIP_INFO || mode == CallingPrefs.DTMF_AUTO)
        } catch (_: Exception) {
        }
        call.sendDtmf(digit)
    }

    fun getCallDuration(): Int = SipDialApplication.coreOrNull()?.currentCall?.duration ?: 0

    fun destroy() {
        regTimeouts.values.forEach { mainHandler.removeCallbacks(it) }
        regTimeouts.clear()
        try {
            networkCallback?.let {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }
        core.removeListener(coreListener)
        try {
            Factory.instance().loggingService.removeListener(sipLogListener)
        } catch (_: Exception) {
        }
        core.stop()
        INSTANCE = null
    }
}
