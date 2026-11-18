package com.sipdial.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sipdial.app.sip.SipCallState
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.telecom.CallBarOverlay
import com.sipdial.app.telecom.IncomingUiRequest
import com.sipdial.app.ui.screens.InCallScreen
import com.sipdial.app.ui.theme.AppTheme
import com.sipdial.app.util.ContactsHelper
import com.sipdial.app.util.DeviceContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INCOMING_CALL = "incoming_call"
        const val EXTRA_CALLER_ADDRESS = "caller_address"
        const val EXTRA_SHOW_WHEN_LOCKED = "showWhenLocked"
        const val EXTRA_TURN_SCREEN_ON = "turnScreenOn"

        fun createLaunchIntent(
            context: Context,
            callerAddress: String,
            incoming: Boolean = true
        ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(EXTRA_INCOMING_CALL, incoming)
            putExtra(EXTRA_CALLER_ADDRESS, callerAddress)
            putExtra(EXTRA_SHOW_WHEN_LOCKED, true)
            putExtra(EXTRA_TURN_SCREEN_ON, true)
        }
    }

    private lateinit var sipEngine: SipEngine
    private val fallbackCallerAddress = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        applyLockScreenFlags()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()

        sipEngine = SipEngine.getInstance(applicationContext)
        extractCallerFromIntent(intent)

        setContent {
            AppTheme {
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

                LaunchedEffect(recordingError) {
                    val err = recordingError
                    if (!err.isNullOrBlank()) {
                        android.widget.Toast.makeText(this@IncomingCallActivity, err, android.widget.Toast.LENGTH_SHORT).show()
                        sipEngine.clearRecordingError()
                    }
                }

                var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
                var hasSeenLiveCall by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (ContactsHelper.hasContactsPermission(this@IncomingCallActivity)) {
                        deviceContacts = withContext(Dispatchers.IO) {
                            ContactsHelper.loadContacts(this@IncomingCallActivity)
                        }
                    }
                }

                val isLiveCall = callState is SipCallState.Incoming ||
                    callState is SipCallState.Outgoing ||
                    callState is SipCallState.Connected ||
                    callState is SipCallState.OnHold

                LaunchedEffect(callState) {
                    if (isLiveCall) {
                        hasSeenLiveCall = true
                        IncomingUiRequest.clear()
                    } else if (callState is SipCallState.Ended || (hasSeenLiveCall && callState is SipCallState.Idle)) {
                        if (callState is SipCallState.Ended) {
                            delay(450L)
                        }
                        IncomingUiRequest.clear()
                        finishCallScreen()
                    }
                }

                DisposableEffect(Unit) {
                    CallBarOverlay.onCallRoute.value = true
                    onDispose {
                        CallBarOverlay.onCallRoute.value = false
                    }
                }

                val fallbackAddress = fallbackCallerAddress.value
                val effectiveCallState = when {
                    isLiveCall -> callState
                    !fallbackAddress.isNullOrBlank() && callState !is SipCallState.Ended ->
                        SipCallState.Incoming(fallbackAddress)
                    else -> callState
                }

                BackHandler(enabled = effectiveCallState !is SipCallState.Incoming) {
                    finishCallScreen()
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
                        IncomingUiRequest.clear()
                        finishCallScreen()
                    },
                    onEndCall = {
                        sipEngine.endCall()
                        IncomingUiRequest.clear()
                        finishCallScreen()
                    },
                    onMuteToggle = { sipEngine.toggleMute() },
                    onSpeakerToggle = { sipEngine.toggleSpeaker() },
                    onHoldToggle = { sipEngine.toggleHold() },
                    onRecordingToggle = { sipEngine.toggleRecording() },
                    onSelectAudioRoute = { route -> sipEngine.selectAudioRoute(route) },
                    onDtmf = { digit -> sipEngine.sendDtmf(digit) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLockScreenFlags()
        extractCallerFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        SipDialApplication.isIncomingCallActivityVisible = true
        CallBarOverlay.onCallRoute.value = true
    }

    override fun onPause() {
        SipDialApplication.isIncomingCallActivityVisible = false
        super.onPause()
    }

    override fun onDestroy() {
        SipDialApplication.isIncomingCallActivityVisible = false
        CallBarOverlay.onCallRoute.value = false
        super.onDestroy()
    }

    private fun extractCallerFromIntent(intent: Intent?) {
        val caller = intent?.getStringExtra(EXTRA_CALLER_ADDRESS)
        if (!caller.isNullOrBlank()) {
            fallbackCallerAddress.value = caller
        }
    }

    private fun finishCallScreen() {
        if (!isFinishing) {
            finish()
        }
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
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
