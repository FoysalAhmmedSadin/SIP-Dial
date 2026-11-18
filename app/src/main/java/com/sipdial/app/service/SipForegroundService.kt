package com.sipdial.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sipdial.app.CHANNEL_SERVICE_ID
import com.sipdial.app.MainActivity
import com.sipdial.app.R
import com.sipdial.app.data.AccountRepository
import com.sipdial.app.sip.NetworkTransportType
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.util.CallNetworkLock
import com.sipdial.app.util.PowerPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SipForegroundService : Service() {

    companion object {
        private const val TAG = "SipForegroundService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.sipdial.app.action.START"
        const val ACTION_STOP = "com.sipdial.app.action.STOP"
        const val ACTION_POWER_REFRESH = "com.sipdial.app.action.POWER_REFRESH"
        const val ACTION_CALL_ACTIVE = "com.sipdial.app.action.CALL_ACTIVE"
        const val ACTION_CALL_ENDED = "com.sipdial.app.action.CALL_ENDED"

        @Volatile
        private var isRunning = false

        fun isActive(): Boolean = isRunning

        fun refreshPower(context: Context) {
            try {
                if (isRunning) {
                    context.startService(
                        Intent(context, SipForegroundService::class.java).apply {
                            action = ACTION_POWER_REFRESH
                        }
                    )
                } else {
                    start(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "refreshPower failed: ${e.message}")
            }
        }

        fun setCallActive(context: Context, active: Boolean) {
            if (!isRunning) return
            try {
                val intent = Intent(context, SipForegroundService::class.java).apply {
                    action = if (active) ACTION_CALL_ACTIVE else ACTION_CALL_ENDED
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "setCallActive($active) failed: ${e.message}")
            }
        }

        fun start(context: Context) {
            KeepAliveState.setStoppedByUser(context, false)
            try {
                val intent = Intent(context, SipForegroundService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SipForegroundService", e)
            }
        }

        fun stop(context: Context) {
            KeepAliveState.setStoppedByUser(context, true)
            try {
                val intent = Intent(context, SipForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SipForegroundService", e)
            }
        }

        internal fun buildStatusContent(
            regState: SipRegistrationState,
            transport: NetworkTransportType,
            fallbackTitle: String,
            fallbackText: String
        ): Pair<String, String> {
            val transportSuffix = when (transport) {
                NetworkTransportType.NONE -> ""
                else -> " (${transport.label})"
            }
            val title = when (regState) {
                SipRegistrationState.REGISTERED -> "SIP Active$transportSuffix"
                SipRegistrationState.REGISTERING -> "Connecting SIP$transportSuffix..."
                SipRegistrationState.NETWORK_UNAVAILABLE -> "Waiting for Network"
                SipRegistrationState.ERROR -> "SIP Connection Issue$transportSuffix"
                SipRegistrationState.DISABLED -> "SIP Disabled"
                SipRegistrationState.UNREGISTERED -> fallbackTitle
            }
            val text = when (regState) {
                SipRegistrationState.REGISTERED -> "Ready for incoming calls via ${transport.label}"
                SipRegistrationState.REGISTERING -> "Updating SIP registration over ${transport.label}..."
                SipRegistrationState.NETWORK_UNAVAILABLE -> "Will re-register automatically when Wi-Fi or Mobile Data returns"
                SipRegistrationState.ERROR -> "Tap to check SIP account settings"
                SipRegistrationState.DISABLED -> "Enable an account in SIP Dial to receive calls"
                SipRegistrationState.UNREGISTERED -> fallbackText
            }
            return title to text
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sipEngine: SipEngine
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkHandle: Long? = null
    private var lastTransport: NetworkTransportType = NetworkTransportType.NONE
    private var isCallActive = false

    private fun acquireStandbyLocks() {
        if (PowerPrefs.mode(this) == PowerPrefs.RELIABLE) {
            acquireCpuLock()
        }
        KeepAliveScheduler.schedule(this)
        CallNetworkLock.acquireStandby(applicationContext)
    }

    private fun acquireCpuLock() {
        try {
            if (cpuWakeLock?.isHeld != true) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SipDial:Registration").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CPU wake lock failed: ${e.message}")
        }
    }

    private fun releaseCpuLock() {
        try { cpuWakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        cpuWakeLock = null
    }

    private fun releaseStandbyLocks() {
        releaseCpuLock()
        KeepAliveScheduler.cancel(this)
        CallNetworkLock.releaseStandby()
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        sipEngine = SipEngine.getInstance(applicationContext)
        sipEngine.onIncomingCall = { callerAddress ->
            TelecomHelper.reportIncomingCall(applicationContext, callerAddress)
        }
        registerNetworkWatch()
        observeRegistrationAndNetwork()
    }

    private fun observeRegistrationAndNetwork() {
        serviceScope.launch {
            combine(sipEngine.registrationState, sipEngine.networkType) { reg, net -> reg to net }
                .collect { (reg, net) ->
                    updateForegroundNotification(reg, net)
                }
        }
    }

    private fun updateForegroundNotification(
        regState: SipRegistrationState = sipEngine.registrationState.value,
        transport: NetworkTransportType = sipEngine.networkType.value
    ) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.notify(NOTIFICATION_ID, createNotification(regState, transport))
        } catch (_: Exception) {
        }
    }

    private fun registerNetworkWatch() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val currentNet = cm.activeNetwork
            val initialCaps = currentNet?.let { cm.getNetworkCapabilities(it) }
            lastNetworkHandle = currentNet?.networkHandle
            lastTransport = sipEngine.resolveTransportType(initialCaps)

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = try { cm.getNetworkCapabilities(network) } catch (_: Exception) { null }
                    val transport = sipEngine.resolveTransportType(caps)
                    val available = transport != NetworkTransportType.NONE
                    val switched = (lastNetworkHandle != null && lastNetworkHandle != network.networkHandle) ||
                        (lastTransport != NetworkTransportType.NONE && lastTransport != transport)
                    lastNetworkHandle = network.networkHandle
                    lastTransport = transport
                    sipEngine.onNetworkChangedFromService(
                        available = available,
                        transport = transport,
                        networkSwitched = switched
                    )
                    if (available && switched) {
                        serviceScope.launch { ensureSavedAccountsRegistered() }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val transport = sipEngine.resolveTransportType(networkCapabilities)
                    val available = transport != NetworkTransportType.NONE
                    val switched = available && (
                        (lastNetworkHandle != null && lastNetworkHandle != network.networkHandle) ||
                            (lastTransport != NetworkTransportType.NONE && lastTransport != transport)
                        )
                    if (!available || switched) {
                        if (available) {
                            lastNetworkHandle = network.networkHandle
                            lastTransport = transport
                        } else {
                            lastTransport = NetworkTransportType.NONE
                        }
                        sipEngine.onNetworkChangedFromService(
                            available = available,
                            transport = transport,
                            networkSwitched = switched
                        )
                    }
                }

                override fun onLost(network: Network) {
                    val nextNet = try { cm.activeNetwork } catch (_: Exception) { null }
                    val nextCaps = nextNet?.let { try { cm.getNetworkCapabilities(it) } catch (_: Exception) { null } }
                    val nextTransport = sipEngine.resolveTransportType(nextCaps)
                    val available = nextTransport != NetworkTransportType.NONE
                    val switched = available && (
                        lastNetworkHandle != nextNet?.networkHandle ||
                            (lastTransport != NetworkTransportType.NONE && lastTransport != nextTransport)
                        )
                    lastNetworkHandle = if (available) nextNet?.networkHandle else null
                    lastTransport = if (available) nextTransport else NetworkTransportType.NONE
                    sipEngine.onNetworkChangedFromService(
                        available = available,
                        transport = nextTransport,
                        networkSwitched = switched
                    )
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "network watch failed: ${e.message}")
        }
    }

    private fun unregisterNetworkWatch() {
        try {
            netCallback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        netCallback = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!KeepAliveState.stoppedByUser(this)) {
            KeepAliveScheduler.schedule(this, 2_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> {
                KeepAliveState.setStoppedByUser(this, false)
                isCallActive = false
                startForegroundSafely(callActive = false)
                acquireStandbyLocks()
                registerSipAccount()
            }
            ACTION_CALL_ACTIVE -> {
                isCallActive = true
                startForegroundSafely(callActive = true)
                acquireCpuLock()
            }
            ACTION_CALL_ENDED -> {
                isCallActive = false
                startForegroundSafely(callActive = false)
                if (PowerPrefs.mode(this) != PowerPrefs.RELIABLE) releaseCpuLock()
            }
            ACTION_POWER_REFRESH -> {
                releaseCpuLock()
                KeepAliveScheduler.cancel(this)
                acquireStandbyLocks()
                startForegroundSafely(callActive = isCallActive)
            }
            ACTION_STOP -> {
                KeepAliveState.setStoppedByUser(this, true)
                releaseStandbyLocks()
                sipEngine.unregister()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundSafely(callActive: Boolean = false) {
        val notification = createNotification()
        val micGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        try {
            if (callActive && micGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                try {
                    startForeground(NOTIFICATION_ID, notification, types)
                    return
                } catch (_: Exception) {
                }
            } else if (callActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                    return
                } catch (_: Exception) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun ensureSavedAccountsRegistered() {
        try {
            val repository = AccountRepository(applicationContext)
            val accounts = repository.getAccounts().first()
            if (accounts.any { it.enabled }) {
                val st = sipEngine.registrationState.value
                if (st != SipRegistrationState.REGISTERED && st != SipRegistrationState.REGISTERING) {
                    sipEngine.registerAllSaved()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure saved SIP accounts registered", e)
        }
    }

    private fun registerSipAccount() {
        serviceScope.launch {
            try {
                TelecomHelper.registerPhoneAccount(applicationContext)
                ensureSavedAccountsRegistered()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SIP account", e)
            }
        }
    }

    private fun createNotification(
        regState: SipRegistrationState = sipEngine.registrationState.value,
        transport: NetworkTransportType = sipEngine.networkType.value
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (title, text) = buildStatusContent(
            regState = regState,
            transport = transport,
            fallbackTitle = getString(R.string.notification_title),
            fallbackText = getString(R.string.notification_text)
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_phone)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterNetworkWatch()
        releaseStandbyLocks()
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
