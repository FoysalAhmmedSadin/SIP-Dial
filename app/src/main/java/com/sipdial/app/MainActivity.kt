package com.sipdial.app

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.WifiCalling3
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sipdial.app.data.AccountRepository
import com.sipdial.app.data.AppDatabase
import com.sipdial.app.data.models.FavoriteContact
import com.sipdial.app.data.models.SavedContact
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.service.KeepAliveState
import com.sipdial.app.service.SipForegroundService
import com.sipdial.app.sip.NetworkTransportType
import com.sipdial.app.sip.SipCallState
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.telecom.CallBarOverlay
import com.sipdial.app.telecom.IncomingUiRequest
import com.sipdial.app.telecom.NativeDialerOverlay
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.ui.components.StatusIndicator
import com.sipdial.app.ui.navigation.Screen
import com.sipdial.app.ui.screens.AboutScreen
import com.sipdial.app.ui.screens.AccountSetupScreen
import com.sipdial.app.ui.screens.AddContactRequest
import com.sipdial.app.ui.screens.AppearanceSettingsScreen
import com.sipdial.app.ui.screens.AudioCodecSettingsScreen
import com.sipdial.app.ui.screens.BackupRestoreScreen
import com.sipdial.app.ui.screens.CallDetailsScreen
import com.sipdial.app.ui.screens.CallingSettingsScreen
import com.sipdial.app.ui.screens.ContactsScreen
import com.sipdial.app.ui.screens.GeneralSettingsScreen
import com.sipdial.app.ui.screens.InCallScreen
import com.sipdial.app.ui.screens.KeypadScreen
import com.sipdial.app.ui.screens.NotificationsSettingsScreen
import com.sipdial.app.ui.screens.PowerNetworkSettingsScreen
import com.sipdial.app.ui.screens.PrivacySecuritySettingsScreen
import com.sipdial.app.ui.screens.RecentsScreen
import com.sipdial.app.ui.screens.RecordingsScreen
import com.sipdial.app.ui.screens.SettingsScreen
import com.sipdial.app.ui.screens.callNumberKey
import com.sipdial.app.ui.screens.normalizeDialInput
import com.sipdial.app.ui.theme.AppTheme
import com.sipdial.app.ui.theme.LocalSemanticColors
import com.sipdial.app.util.BlockList
import com.sipdial.app.util.CallLogSync
import com.sipdial.app.util.CallerIdFormatter
import com.sipdial.app.util.ContactsHelper
import com.sipdial.app.util.DeviceContact
import com.sipdial.app.util.GeneralPrefs
import com.sipdial.app.util.PermissionHelper
import com.sipdial.app.util.RecordingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private lateinit var sipEngine: SipEngine
    private lateinit var accountRepository: AccountRepository
    private lateinit var database: AppDatabase

    private val incomingDialPrefill = mutableStateOf<String?>(null)
    private val openKeypadRequest = mutableIntStateOf(0)
    private val openInCallRequest = mutableIntStateOf(0)
    private val callUiVisible = mutableStateOf(false)
    private val fallbackIncomingAddress = mutableStateOf<String?>(null)

    private fun isActiveCallState(state: SipCallState): Boolean =
        state is SipCallState.Incoming ||
            state is SipCallState.Outgoing ||
            state is SipCallState.Connected ||
            state is SipCallState.OnHold

    private fun isCallIntent(intent: Intent?): Boolean =
        intent?.getBooleanExtra("incoming_call", false) == true ||
            intent?.getBooleanExtra("from_native_dialer", false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()

        sipEngine = SipEngine.getInstance(applicationContext)
        accountRepository = AccountRepository(applicationContext)
        database = AppDatabase.getInstance(applicationContext)
        BlockList.init(applicationContext)
        GeneralPrefs.init(applicationContext)

        val launchedForCall = isCallIntent(intent) ||
            IncomingUiRequest.pending ||
            isActiveCallState(sipEngine.callState.value)
        callUiVisible.value = launchedForCall
        configureLockScreenDisplay(false)

        sipEngine.onIncomingCall = { callerAddress ->
            TelecomHelper.reportIncomingCall(applicationContext, callerAddress)
        }
        CallBarOverlay.attach(applicationContext, sipEngine)
        TelecomHelper.registerPhoneAccount(this)
        TelecomHelper.refreshDefaultDialer(this)

        handleIntent(intent)

        setContent {
            AppTheme {
                SipDialRoot(
                    sipEngine = sipEngine,
                    accountRepository = accountRepository,
                    database = database,
                    incomingDialPrefill = incomingDialPrefill.value,
                    onIncomingPrefillConsumed = { incomingDialPrefill.value = null },
                    openKeypadSignal = openKeypadRequest.intValue,
                    openInCallSignal = openInCallRequest.intValue,
                    callUiVisible = callUiVisible.value,
                    fallbackIncomingAddress = fallbackIncomingAddress.value,
                    onShowCallUiChange = { visible ->
                        callUiVisible.value = visible
                        if (!visible) {
                            IncomingUiRequest.clear()
                            fallbackIncomingAddress.value = null
                        }
                        configureLockScreenDisplay(false)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        CallBarOverlay.appForeground.value = true
        TelecomHelper.refreshDefaultDialer(this)
    }

    override fun onPause() {
        CallBarOverlay.appForeground.value = false
        super.onPause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        CallBarOverlay.appForeground.value = false
    }

    override fun onDestroy() {
        CallBarOverlay.appForeground.value = false
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val incomingFlag = intent.getBooleanExtra("incoming_call", false)
        val fromDialerFlag = intent.getBooleanExtra("from_native_dialer", false)
        if (incomingFlag || fromDialerFlag) {
            if (incomingFlag) {
                IncomingUiRequest.request()
                val caller = intent.getStringExtra("caller_address")
                if (!caller.isNullOrBlank()) {
                    fallbackIncomingAddress.value = caller
                }
            }
            callUiVisible.value = true
            configureLockScreenDisplay(false)
            openInCallRequest.intValue++
        }
        val action = intent.action
        val data = intent.data
        if (data != null && (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW || action == Intent.ACTION_CALL)) {
            val raw = when (data.scheme?.lowercase()) {
                "tel" -> data.schemeSpecificPart.orEmpty()
                "sip" -> data.schemeSpecificPart.orEmpty().substringBefore("@")
                else -> ""
            }
            val normalized = normalizeDialInput(Uri.decode(raw))
            if (normalized.isNotEmpty()) {
                incomingDialPrefill.value = normalized
                openKeypadRequest.intValue++
            }
        }
    }

    private fun configureLockScreenDisplay(showOverLock: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(showOverLock)
            setTurnScreenOn(showOverLock)
        } else {
            @Suppress("DEPRECATION")
            if (showOverLock) {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }

    private fun requestHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val modes = display?.supportedModes ?: return
                val best = modes.maxByOrNull { it.refreshRate } ?: return
                val lp = window.attributes
                lp.preferredDisplayModeId = best.modeId
                window.attributes = lp
            } catch (_: Exception) {
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SipDialRoot(
    sipEngine: SipEngine,
    accountRepository: AccountRepository,
    database: AppDatabase,
    incomingDialPrefill: String?,
    onIncomingPrefillConsumed: () -> Unit,
    openKeypadSignal: Int,
    openInCallSignal: Int,
    callUiVisible: Boolean,
    fallbackIncomingAddress: String?,
    onShowCallUiChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val semantic = LocalSemanticColors.current

    val accountStatePair by accountRepository.getAccountsState()
        .collectAsStateWithLifecycle(initialValue = emptyList<SipAccountData>() to null)
    val accounts = accountStatePair.first
    val currentAccount = accountStatePair.second
    val recentCalls by database.callHistoryDao().getRecentCallRecords(300).collectAsStateWithLifecycle(initialValue = emptyList())
    val recordedCalls by database.callHistoryDao().getRecordedCalls().collectAsStateWithLifecycle(initialValue = emptyList())
    val savedContacts by database.savedContactDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by database.favoritesDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())

    val registrationState by sipEngine.registrationState.collectAsStateWithLifecycle()
    val registrationError by sipEngine.registrationError.collectAsStateWithLifecycle()
    val networkType by sipEngine.networkType.collectAsStateWithLifecycle()
    val accountStates by sipEngine.accountStates.collectAsStateWithLifecycle()
    val accountErrors by sipEngine.accountErrors.collectAsStateWithLifecycle()
    val callState by sipEngine.callState.collectAsStateWithLifecycle()
    val isMuted by sipEngine.isMuted.collectAsStateWithLifecycle()
    val isSpeaker by sipEngine.isSpeaker.collectAsStateWithLifecycle()
    val isRecording by sipEngine.isRecording.collectAsStateWithLifecycle()
    val audioRoutes by sipEngine.audioRoutes.collectAsStateWithLifecycle()
    val selectedAudioRoute by sipEngine.selectedAudioRoute.collectAsStateWithLifecycle()
    val callQuality by sipEngine.callQuality.collectAsStateWithLifecycle()
    val callAccountName by sipEngine.callAccountName.collectAsStateWithLifecycle()
    val callAccountUser by sipEngine.callAccountUser.collectAsStateWithLifecycle()
    val recordingError by sipEngine.recordingError.collectAsStateWithLifecycle()
    val blockedNumbers by BlockList.numbers.collectAsStateWithLifecycle()
    val incomingUiTick by IncomingUiRequest.tick.collectAsStateWithLifecycle()

    LaunchedEffect(recordingError) {
        val err = recordingError
        if (!err.isNullOrBlank()) {
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            sipEngine.clearRecordingError()
        }
    }

    var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var hasContactsPermission by remember { mutableStateOf(ContactsHelper.hasContactsPermission(context)) }
    var dialPrefill by remember { mutableStateOf<String?>(null) }
    var dialInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var pendingAddContact by remember { mutableStateOf<AddContactRequest?>(null) }
    var pendingCallChooserNumber by remember { mutableStateOf<String?>(null) }
    var lastSeenRecentsAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val startRoute = remember { GeneralPrefs.getDefaultOpeningRoute(context) }
    var activePrimaryTab by rememberSaveable { mutableStateOf(startRoute) }
    var warmAllPrimaryTabs by remember { mutableStateOf(false) }

    val recentsListState = rememberLazyListState()
    val contactsPhoneListState = rememberLazyListState()
    val contactsClientListState = rememberLazyListState()
    val contactsOfficeListState = rememberLazyListState()

    val contactsPhoneIndex by produceState(
        initialValue = ContactsHelper.buildPhoneIndex(emptyList()),
        deviceContacts
    ) {
        value = if (deviceContacts.size <= 40) {
            ContactsHelper.buildPhoneIndex(deviceContacts)
        } else {
            withContext(Dispatchers.Default) {
                ContactsHelper.buildPhoneIndex(deviceContacts)
            }
        }
    }

    val dialableSipAccounts = remember(accounts, accountStates, currentAccount) {
        val engineAccounts = sipEngine.getActiveDialableAccounts()
        if (engineAccounts.isNotEmpty()) engineAccounts
        else accounts.filter { it.enabled }
    }

    val favoriteNumbers = remember(favorites) {
        favorites.map { it.normalizedNumber }.toSet()
    }

    val existingRecordingsCount by produceState(initialValue = 0, recordedCalls) {
        value = withContext(Dispatchers.IO) {
            recordedCalls.count { RecordingStorage.exists(context, it.recordingPath) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasContactsPermission = ContactsHelper.hasContactsPermission(context)
        scope.launch {
            if (hasContactsPermission) {
                deviceContacts = ContactsHelper.loadContacts(context)
            }
            if (CallLogSync.hasPermission(context)) {
                CallLogSync.syncFromSystem(context)
            }
        }
    }

    // Warm inactive primary tabs after the first frame renders so cold startup stays fast
    // while every tab switch (Contacts <-> Recents <-> Keypad) becomes instant with zero teardown.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        warmAllPrimaryTabs = true
    }

    // Startup contact loading, call log sync, and auto-registration
    LaunchedEffect(Unit) {
        if (ContactsHelper.hasContactsPermission(context)) {
            deviceContacts = ContactsHelper.loadContacts(context)
        }
        if (CallLogSync.hasPermission(context)) {
            CallLogSync.syncFromSystem(context)
        }
        val initialAccounts = accountRepository.getAccounts().first()
        if (initialAccounts.any { it.enabled }) {
            sipEngine.registerAllSaved()
            if (!KeepAliveState.stoppedByUser(context)) {
                SipForegroundService.start(context)
            }
        }
    }

    // Request runtime permissions when no incoming/active call is blocking UI
    LaunchedEffect(callUiVisible) {
        if (!callUiVisible) {
            val missing = PermissionHelper.getMissingPermissions(context)
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing)
            }
        }
    }

    LaunchedEffect(incomingDialPrefill) {
        if (!incomingDialPrefill.isNullOrBlank()) {
            dialPrefill = incomingDialPrefill
            onIncomingPrefillConsumed()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isPrimaryTab = currentRoute == null || currentRoute == Screen.ROUTE_PRIMARY_TABS

    val isCallActiveNow = callState is SipCallState.Incoming ||
        callState is SipCallState.Outgoing ||
        callState is SipCallState.Connected ||
        callState is SipCallState.OnHold

    val effectiveCallState = when {
        isCallActiveNow -> callState
        callUiVisible && !fallbackIncomingAddress.isNullOrBlank() ->
            SipCallState.Incoming(fallbackIncomingAddress)
        else -> callState
    }

    val showCallScreenRoot = callUiVisible ||
        callState is SipCallState.Incoming ||
        callState is SipCallState.Outgoing ||
        IncomingUiRequest.pending

    DisposableEffect(showCallScreenRoot) {
        CallBarOverlay.onCallRoute.value = showCallScreenRoot
        onDispose { }
    }

    val isViewingRecents = isPrimaryTab && activePrimaryTab == Screen.Recents.route

    // Track unread missed calls when visiting Recents
    LaunchedEffect(isViewingRecents, recentCalls) {
        if (isViewingRecents) {
            lastSeenRecentsAt = System.currentTimeMillis()
        }
    }

    val missedBadgeCount = remember(recentCalls, lastSeenRecentsAt, isViewingRecents) {
        if (isViewingRecents) 0
        else recentCalls.count { it.isIncoming && !it.isAnswered && it.timestamp > lastSeenRecentsAt }
    }

    // Navigate to Keypad when deep-linked with dial URI
    LaunchedEffect(openKeypadSignal) {
        if (openKeypadSignal > 0) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
            activePrimaryTab = Screen.Keypad.route
            if (currentRoute != Screen.ROUTE_PRIMARY_TABS) {
                navController.popBackStack(Screen.ROUTE_PRIMARY_TABS, inclusive = false)
            }
        }
    }

    // Sync callUiVisible with active/ended call lifecycle without flashing Keypad first
    LaunchedEffect(callState, incomingUiTick, openInCallSignal) {
        if (isCallActiveNow) {
            if (IncomingUiRequest.pending || callState is SipCallState.Incoming || callState is SipCallState.Outgoing || openInCallSignal > 0) {
                IncomingUiRequest.clear()
                onShowCallUiChange(true)
            }
        } else {
            if (callState is SipCallState.Ended || (!IncomingUiRequest.pending && fallbackIncomingAddress.isNullOrBlank())) {
                onShowCallUiChange(false)
            }
        }
    }

    fun placeSipCall(number: String, accountId: String? = null) {
        if (number.isBlank()) return
        if (sipEngine.makeCall(number, accountId)) {
            onShowCallUiChange(true)
        } else {
            Toast.makeText(
                context,
                "Register a SIP account first to place SIP calls",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun placeSmartCall(number: String, preferredRoute: String? = null) {
        val clean = number.trim()
        if (clean.isEmpty()) return
        when (preferredRoute) {
            FavoriteContact.ROUTE_SIP -> {
                placeSipCall(clean)
                return
            }
            FavoriteContact.ROUTE_SIM -> {
                val sims = TelecomHelper.getAvailableSimAccounts(context)
                TelecomHelper.placeSimCallWithHandle(context, clean, sims.firstOrNull()?.handle)
                return
            }
        }

        val hasSip = registrationState == SipRegistrationState.REGISTERED
        val sims = TelecomHelper.getAvailableSimAccounts(context)
        when (TelecomHelper.getNativeCallRoutingMode(context)) {
            TelecomHelper.MODE_ALWAYS_SIP -> {
                placeSipCall(clean)
            }
            TelecomHelper.MODE_SIM_ONLY -> {
                TelecomHelper.placeSimCallWithHandle(context, clean, sims.firstOrNull()?.handle)
            }
            else -> {
                if (hasSip && sims.isNotEmpty()) {
                    pendingCallChooserNumber = clean
                } else if (hasSip) {
                    placeSipCall(clean)
                } else {
                    TelecomHelper.placeSimCallWithHandle(context, clean, sims.firstOrNull()?.handle)
                }
            }
        }
    }

    fun toggleFavorite(name: String, number: String, photoUri: String?) {
        val norm = FavoriteContact.normalize(number)
        if (norm.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val dao = database.favoritesDao()
            if (dao.countByNormalized(norm) > 0) {
                dao.deleteByNormalized(norm)
            } else {
                dao.insert(
                    FavoriteContact(
                        name = name.ifBlank { number },
                        number = number,
                        normalizedNumber = norm
                    )
                )
            }
        }
    }

    fun openChat(app: String, number: String) {
        val digits = ContactsHelper.chatDigits(number)
        if (digits.isEmpty()) return
        val uri = when {
            app.equals("WhatsApp", ignoreCase = true) -> Uri.parse("https://wa.me/$digits")
            app.equals("Telegram", ignoreCase = true) -> Uri.parse("https://t.me/+$digits")
            else -> return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "$app is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    fun openSms(number: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No SMS app found", Toast.LENGTH_SHORT).show()
        }
    }

    // Chooser dialog when both SIP & SIM are available and routing is Ask
    pendingCallChooserNumber?.let { target ->
        val sims = remember(target) { TelecomHelper.getAvailableSimAccounts(context) }
        AlertDialog(
            onDismissRequest = { pendingCallChooserNumber = null },
            title = { Text("Call $target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                pendingCallChooserNumber = null
                                placeSipCall(target)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.WifiCalling3, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Column {
                                Text("SIP Call", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    currentAccount?.let { "${it.username}@${it.domain}" } ?: "Active SIP account",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    sims.forEach { sim ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    pendingCallChooserNumber = null
                                    TelecomHelper.placeSimCallWithHandle(context, target, sim.handle)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Filled.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(sim.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(sim.subLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingCallChooserNumber = null }) { Text("Cancel") }
            }
        )
    }

    if (showCallScreenRoot) {
        androidx.activity.compose.BackHandler(
            enabled = effectiveCallState !is SipCallState.Incoming
        ) {
            onShowCallUiChange(false)
        }
        InCallScreen(
            callState = effectiveCallState,
            isMuted = isMuted,
            isSpeaker = isSpeaker,
            isRecording = isRecording,
            audioRoutes = audioRoutes,
            selectedAudioRoute = selectedAudioRoute,
            callDurationProvider = { sipEngine.getCallDuration() },
            quality = callQuality,
            contacts = deviceContacts,
            accountUser = callAccountName ?: callAccountUser,
            onAnswer = { sipEngine.answerCall() },
            onDecline = {
                sipEngine.declineCall()
                onShowCallUiChange(false)
            },
            onEndCall = {
                sipEngine.endCall()
                onShowCallUiChange(false)
            },
            onMuteToggle = { sipEngine.toggleMute() },
            onSpeakerToggle = { sipEngine.toggleSpeaker() },
            onHoldToggle = { sipEngine.toggleHold() },
            onRecordingToggle = { sipEngine.toggleRecording() },
            onSelectAudioRoute = { route -> sipEngine.selectAudioRoute(route) },
            onDtmf = { digit -> sipEngine.sendDtmf(digit) }
        )
        return
    }

    fun navigatePrimaryTab(route: String) {
        if (activePrimaryTab == route && isPrimaryTab) return
        focusManager.clearFocus(force = true)
        softwareKeyboardController?.hide()
        activePrimaryTab = route
        if (currentRoute != Screen.ROUTE_PRIMARY_TABS) {
            navController.popBackStack(Screen.ROUTE_PRIMARY_TABS, inclusive = false)
        }
    }

    val imeOpen = WindowInsets.isImeVisible
    val showGlobalDialerFab = isPrimaryTab && activePrimaryTab in setOf(
        Screen.Contacts.route,
        Screen.Recents.route
    ) && !imeOpen

    Scaffold(
        contentWindowInsets = if (isPrimaryTab) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (showGlobalDialerFab) {
                FloatingActionButton(
                    onClick = { navigatePrimaryTab(Screen.Keypad.route) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("open_dialer_fab")
                ) {
                    Icon(Icons.Filled.Dialpad, contentDescription = "Open Keypad")
                }
            }
        },
        topBar = {
            if (isPrimaryTab) {
                PrimaryTopBar(
                    accounts = accounts,
                    currentAccount = currentAccount,
                    accountStates = accountStates,
                    registrationState = registrationState,
                    networkType = networkType,
                    onSelectAccount = { acc ->
                        scope.launch {
                            accountRepository.selectAccount(acc.id)
                            sipEngine.register(acc, select = true)
                        }
                    },
                    onOpenAccounts = { navController.navigate(Screen.SipAccounts.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
        },
        bottomBar = {
            if (isPrimaryTab && !imeOpen) {
                PrimaryBottomNavigationBar(
                    activePrimaryTab = activePrimaryTab,
                    missedBadgeCount = missedBadgeCount,
                    onSelectTab = ::navigatePrimaryTab
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Active/Incoming Call Fallback Banner when browsing outside InCall screen
                val isCallActive = callState is SipCallState.Incoming ||
                    callState is SipCallState.Outgoing ||
                    callState is SipCallState.Connected ||
                    callState is SipCallState.OnHold
                AnimatedVisibility(visible = isCallActive && !showCallScreenRoot) {
                    val label = when (val cs = callState) {
                        is SipCallState.Incoming -> "Incoming call • ${CallerIdFormatter.extractNumber(cs.remoteAddress)}"
                        is SipCallState.Outgoing -> "Calling ${CallerIdFormatter.extractNumber(cs.remoteAddress)}…"
                        is SipCallState.OnHold -> "On hold • ${CallerIdFormatter.extractNumber(cs.remoteAddress)}"
                        is SipCallState.Connected -> "Active SIP call • ${CallerIdFormatter.extractNumber(cs.remoteAddress)}"
                        else -> ""
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onShowCallUiChange(true)
                            }
                            .testTag("active_call_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, tint = semantic.success)
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (callState is SipCallState.Incoming) {
                                    OutlinedButton(
                                        onClick = { sipEngine.answerCall() },
                                        modifier = Modifier.heightIn(min = 40.dp)
                                    ) {
                                        Text("Answer")
                                    }
                                } else {
                                    IconButton(
                                        onClick = { sipEngine.toggleSpeaker() },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("banner_speaker_toggle")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = if (isSpeaker) "Turn speaker off" else "Turn speaker on",
                                            tint = if (isSpeaker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (callState is SipCallState.Incoming) sipEngine.declineCall()
                                        else sipEngine.endCall()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CallEnd,
                                        contentDescription = "Hang up",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Screen.ROUTE_PRIMARY_TABS,
                    enterTransition = { fadeIn(animationSpec = tween(110)) },
                    exitTransition = { fadeOut(animationSpec = tween(80)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(110)) },
                    popExitTransition = { fadeOut(animationSpec = tween(80)) },
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Screen.ROUTE_PRIMARY_TABS) {
                        BackHandler(enabled = activePrimaryTab != startRoute) {
                            activePrimaryTab = startRoute
                        }

                        PrimaryTabsContainer(
                            activePrimaryTab = activePrimaryTab,
                            warmAllTabs = warmAllPrimaryTabs,
                            contactsContent = {
                                ContactsScreen(
                                    contacts = deviceContacts,
                                    hasContactsPermission = hasContactsPermission,
                                    onRequestPermission = {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                    },
                                    onCallContact = { number -> placeSmartCall(number) },
                                    savedContacts = savedContacts,
                                    favoriteNumbers = favoriteNumbers,
                                    onToggleFavorite = ::toggleFavorite,
                                    onSaveContact = { contact ->
                                        scope.launch(Dispatchers.IO) {
                                            database.savedContactDao().insert(contact)
                                        }
                                    },
                                    onDeleteSavedContact = { contact ->
                                        scope.launch(Dispatchers.IO) {
                                            database.savedContactDao().delete(contact)
                                        }
                                    },
                                    onOpenChat = ::openChat,
                                    detectMessengers = { number -> ContactsHelper.detectMessengers(context, number) },
                                    pendingAdd = pendingAddContact,
                                    onPendingConsumed = { pendingAddContact = null },
                                    phoneListState = contactsPhoneListState,
                                    clientListState = contactsClientListState,
                                    officeListState = contactsOfficeListState
                                )
                            },
                            recentsContent = {
                                RecentsScreen(
                                    recentCalls = recentCalls,
                                    contacts = deviceContacts,
                                    blockedNumbers = blockedNumbers,
                                    onCall = { number -> placeSmartCall(number) },
                                    onOpenDetails = { number ->
                                        navController.navigate(Screen.CallDetails.createRoute(number))
                                    },
                                    onDeleteCalls = { records ->
                                        scope.launch(Dispatchers.IO) {
                                            val dao = database.callHistoryDao()
                                            records.forEach { rec ->
                                                CallLogSync.markDeleted(context, rec)
                                                dao.deleteCallRecord(rec)
                                            }
                                        }
                                    },
                                    onClearHistory = {
                                        scope.launch(Dispatchers.IO) {
                                            CallLogSync.markAllCleared(context)
                                            database.callHistoryDao().deleteAllCallRecords()
                                        }
                                    },
                                    onBlockNumber = { number -> BlockList.add(context, number) },
                                    onUnblockNumber = { number -> BlockList.remove(context, number) },
                                    listState = recentsListState,
                                    contactsIndex = contactsPhoneIndex,
                                    isTabActive = activePrimaryTab == Screen.Recents.route
                                )
                            },
                            keypadContent = {
                                KeypadScreen(
                                    registrationState = registrationState,
                                    currentAccount = currentAccount,
                                    contacts = deviceContacts,
                                    favoriteNumbers = favoriteNumbers,
                                    prefillNumber = dialPrefill,
                                    onPrefillConsumed = {
                                        dialPrefill?.let { pre ->
                                            dialInput = TextFieldValue(pre, TextRange(pre.length))
                                        }
                                        dialPrefill = null
                                    },
                                    onSipCall = { number -> placeSipCall(number) },
                                    onToggleFavorite = ::toggleFavorite,
                                    onOpenAccounts = { navController.navigate(Screen.SipAccounts.route) },
                                    dialInput = dialInput,
                                    onDialInputChange = { dialInput = it },
                                    dialableSipAccounts = dialableSipAccounts,
                                    onSipCallWithAccount = { number, accountId -> placeSipCall(number, accountId) },
                                    isTabActive = activePrimaryTab == Screen.Keypad.route
                                )
                            }
                        )
                    }

                    composable(
                        route = Screen.CallDetails.route,
                        arguments = listOf(navArgument("number") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encoded = backStackEntry.arguments?.getString("number").orEmpty()
                        val number = Uri.decode(encoded)
                        val key = callNumberKey(number)
                        val filteredRecords = remember(recentCalls, key) {
                            recentCalls.filter { callNumberKey(it.remoteAddress) == key }
                        }
                        val matchedContact = remember(contactsPhoneIndex, number) {
                            contactsPhoneIndex.find(number)
                        }
                        val displayName = matchedContact?.name
                            ?: filteredRecords.firstOrNull { it.displayName.isNotBlank() }?.displayName
                        val isFav = FavoriteContact.normalize(number) in favoriteNumbers
                        val isBlk = BlockList.isBlocked(number, blockedNumbers)

                        CallDetailsScreen(
                            number = number,
                            name = displayName,
                            photoUri = matchedContact?.photoUri,
                            records = filteredRecords,
                            isFavorite = isFav,
                            isBlocked = isBlk,
                            detectMessengers = { num -> ContactsHelper.detectMessengers(context, num) },
                            onBack = { navController.popBackStack() },
                            onCall = { placeSmartCall(number) },
                            onMessage = { openSms(number) },
                            onOpenChat = ::openChat,
                            onToggleFavorite = {
                                toggleFavorite(displayName ?: number, number, matchedContact?.photoUri)
                            },
                            onAddToCategory = { category ->
                                pendingAddContact = AddContactRequest(
                                    category = category,
                                    number = number,
                                    name = displayName.orEmpty()
                                )
                                activePrimaryTab = Screen.Contacts.route
                                navController.popBackStack(Screen.ROUTE_PRIMARY_TABS, inclusive = false)
                            },
                            onToggleBlock = {
                                if (isBlk) BlockList.remove(context, number)
                                else BlockList.add(context, number)
                            },
                            onDeleteRecording = { rec ->
                                scope.launch(Dispatchers.IO) {
                                    RecordingStorage.deleteRecording(context, rec.recordingPath)
                                    database.callHistoryDao().clearRecordingPath(rec.id)
                                }
                            }
                        )
                    }

                    composable(Screen.InCall.route) {
                        InCallScreen(
                            callState = callState,
                            isMuted = isMuted,
                            isSpeaker = isSpeaker,
                            isRecording = isRecording,
                            audioRoutes = audioRoutes,
                            selectedAudioRoute = selectedAudioRoute,
                            callDurationProvider = { sipEngine.getCallDuration() },
                            quality = callQuality,
                            contacts = deviceContacts,
                            accountUser = callAccountName ?: callAccountUser,
                            onAnswer = { sipEngine.answerCall() },
                            onDecline = { sipEngine.declineCall() },
                            onEndCall = { sipEngine.endCall() },
                            onMuteToggle = { sipEngine.toggleMute() },
                            onSpeakerToggle = { sipEngine.toggleSpeaker() },
                            onHoldToggle = { sipEngine.toggleHold() },
                            onRecordingToggle = { sipEngine.toggleRecording() },
                            onSelectAudioRoute = { route -> sipEngine.selectAudioRoute(route) },
                            onDtmf = { digit -> sipEngine.sendDtmf(digit) }
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            currentAccount = currentAccount,
                            accountsCount = accounts.size,
                            registrationState = registrationState,
                            recordingsCount = existingRecordingsCount,
                            onBack = { navController.popBackStack() },
                            onOpenGeneral = { navController.navigate(Screen.GeneralSettings.route) },
                            onOpenAppearance = { navController.navigate(Screen.Appearance.route) },
                            onOpenSipAccounts = { navController.navigate(Screen.SipAccounts.route) },
                            onOpenCalling = { navController.navigate(Screen.CallingSettings.route) },
                            onOpenAudioCodecs = { navController.navigate(Screen.AudioCodecSettings.route) },
                            onOpenRecordings = { navController.navigate(Screen.Recordings.route) },
                            onOpenNotifications = { navController.navigate(Screen.NotificationSettings.route) },
                            onOpenPowerNetwork = { navController.navigate(Screen.PowerNetworkSettings.route) },
                            onOpenPrivacySecurity = { navController.navigate(Screen.PrivacySecuritySettings.route) },
                            onOpenBackupRestore = { navController.navigate(Screen.BackupRestore.route) },
                            onOpenAbout = { navController.navigate(Screen.About.route) }
                        )
                    }

                    composable(Screen.GeneralSettings.route) {
                        GeneralSettingsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(Screen.Appearance.route) {
                        AppearanceSettingsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(Screen.SipAccounts.route) {
                        AccountSetupScreen(
                            currentAccount = currentAccount,
                            accounts = accounts,
                            registrationState = registrationState,
                            registrationError = registrationError,
                            accountStates = accountStates,
                            accountErrors = accountErrors,
                            onBack = { navController.popBackStack() },
                            onSelectAccount = { id ->
                                scope.launch {
                                    accountRepository.selectAccount(id)
                                    accountRepository.getAccounts().first()
                                        .firstOrNull { it.id == id }
                                        ?.let { sipEngine.register(it, select = true) }
                                }
                            },
                            onToggleEnabled = { id, enabled ->
                                scope.launch {
                                    accountRepository.setEnabled(id, enabled)
                                    val acc = accountRepository.getAccounts().first().firstOrNull { it.id == id }
                                    if (enabled && acc != null) {
                                        sipEngine.register(acc)
                                        SipForegroundService.start(context)
                                    } else {
                                        sipEngine.unregisterAccount(id)
                                    }
                                }
                            },
                            onDeleteAccount = { id ->
                                scope.launch {
                                    sipEngine.unregisterAccount(id)
                                    accountRepository.deleteAccount(id)
                                }
                            },
                            onSaveAndRegister = { account ->
                                scope.launch {
                                    val saved = accountRepository.saveAccount(account)
                                    if (saved.enabled) {
                                        sipEngine.register(saved)
                                        SipForegroundService.start(context)
                                    } else {
                                        sipEngine.unregisterAccount(saved.id)
                                    }
                                }
                            },
                            onUnregister = { id ->
                                sipEngine.unregisterAccount(id)
                            }
                        )
                    }

                    composable(Screen.CallingSettings.route) {
                        CallingSettingsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(Screen.AudioCodecSettings.route) {
                        AudioCodecSettingsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(Screen.Recordings.route) {
                        RecordingsScreen(
                            recordedCalls = recordedCalls,
                            contacts = deviceContacts,
                            onBack = { navController.popBackStack() },
                            onDeleteRecording = { rec ->
                                scope.launch(Dispatchers.IO) {
                                    RecordingStorage.deleteRecording(context, rec.recordingPath)
                                    database.callHistoryDao().clearRecordingPath(rec.id)
                                }
                            }
                        )
                    }

                    composable(Screen.NotificationSettings.route) {
                        NotificationsSettingsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(Screen.PowerNetworkSettings.route) {
                        PowerNetworkSettingsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(Screen.PrivacySecuritySettings.route) {
                        PrivacySecuritySettingsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(Screen.BackupRestore.route) {
                        BackupRestoreScreen(onBack = { navController.popBackStack() })
                    }

                    composable(Screen.About.route) {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrimaryTopBar(
    accounts: List<SipAccountData>,
    currentAccount: SipAccountData?,
    accountStates: Map<String, SipRegistrationState>,
    registrationState: SipRegistrationState,
    networkType: NetworkTransportType,
    onSelectAccount: (SipAccountData) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    var accountMenuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SIP Dial",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (accounts.isNotEmpty()) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { accountMenuOpen = true }
                                .testTag("top_account_switcher")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = currentAccount?.let {
                                        it.name.ifBlank { it.username }
                                    } ?: "Account",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Filled.ExpandMore,
                                    contentDescription = "Switch SIP account",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = accountMenuOpen,
                            onDismissRequest = { accountMenuOpen = false }
                        ) {
                            accounts.forEach { acc ->
                                val st = accountStates[acc.id] ?: SipRegistrationState.UNREGISTERED
                                val dot = when (st) {
                                    SipRegistrationState.REGISTERED -> semantic.success
                                    SipRegistrationState.REGISTERING -> semantic.warning
                                    SipRegistrationState.ERROR -> MaterialTheme.colorScheme.error
                                    SipRegistrationState.NETWORK_UNAVAILABLE -> semantic.warning
                                    SipRegistrationState.DISABLED,
                                    SipRegistrationState.UNREGISTERED -> MaterialTheme.colorScheme.outline
                                }
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = acc.name.ifBlank { acc.username },
                                                fontWeight = if (acc.id == currentAccount?.id) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "${acc.username}@${acc.domain}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(dot)
                                        )
                                    },
                                    onClick = {
                                        accountMenuOpen = false
                                        onSelectAccount(acc)
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Manage SIP Accounts") },
                                onClick = {
                                    accountMenuOpen = false
                                    onOpenAccounts()
                                }
                            )
                        }
                    }
                }
            }
        },
        actions = {
            StatusIndicator(
                state = registrationState,
                compact = true,
                networkType = networkType,
                onClick = onOpenAccounts,
                modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("open_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun PrimaryBottomNavigationBar(
    activePrimaryTab: String,
    missedBadgeCount: Int,
    onSelectTab: (String) -> Unit
) {
    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        NavigationBarItem(
            selected = activePrimaryTab == Screen.Contacts.route,
            onClick = { onSelectTab(Screen.Contacts.route) },
            colors = navItemColors,
            icon = {
                Icon(
                    imageVector = if (activePrimaryTab == Screen.Contacts.route) Icons.Filled.Contacts else Icons.Outlined.Contacts,
                    contentDescription = "Contacts",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Contacts", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_contacts")
        )

        NavigationBarItem(
            selected = activePrimaryTab == Screen.Recents.route,
            onClick = { onSelectTab(Screen.Recents.route) },
            colors = navItemColors,
            icon = {
                BadgedBox(
                    badge = {
                        if (missedBadgeCount > 0) {
                            Badge { Text(if (missedBadgeCount > 99) "99+" else "$missedBadgeCount") }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (activePrimaryTab == Screen.Recents.route) Icons.Filled.History else Icons.Outlined.History,
                        contentDescription = "Recents",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            label = { Text("Recents", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_recents")
        )

        NavigationBarItem(
            selected = activePrimaryTab == Screen.Keypad.route,
            onClick = { onSelectTab(Screen.Keypad.route) },
            colors = navItemColors,
            icon = {
                Icon(
                    imageVector = if (activePrimaryTab == Screen.Keypad.route) Icons.Filled.Dialpad else Icons.Outlined.Dialpad,
                    contentDescription = "Keypad",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Keypad", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_keypad")
        )
    }
}

private fun Modifier.retainedTabVisibility(isActive: Boolean): Modifier =
    this
        .focusProperties { canFocus = isActive }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                if (isActive) {
                    placeable.placeRelative(0, 0)
                }
            }
        }

@Composable
private fun PrimaryTabsContainer(
    activePrimaryTab: String,
    warmAllTabs: Boolean,
    contactsContent: @Composable () -> Unit,
    recentsContent: @Composable () -> Unit,
    keypadContent: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (warmAllTabs || activePrimaryTab == Screen.Contacts.route) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .retainedTabVisibility(activePrimaryTab == Screen.Contacts.route)
            ) {
                contactsContent()
            }
        }
        if (warmAllTabs || activePrimaryTab == Screen.Recents.route) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .retainedTabVisibility(activePrimaryTab == Screen.Recents.route)
            ) {
                recentsContent()
            }
        }
        if (warmAllTabs || activePrimaryTab == Screen.Keypad.route) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .retainedTabVisibility(activePrimaryTab == Screen.Keypad.route)
            ) {
                keypadContent()
            }
        }
    }
}
