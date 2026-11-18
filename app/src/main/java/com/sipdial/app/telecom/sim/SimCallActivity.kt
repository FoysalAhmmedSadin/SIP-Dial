package com.sipdial.app.telecom.sim

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipdial.app.ui.components.CallControls
import com.sipdial.app.ui.components.DialPad
import com.sipdial.app.ui.components.IncomingCallControls
import com.sipdial.app.ui.theme.AppTheme
import com.sipdial.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay

class SimCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "sim_call_action"
        const val ACTION_ANSWER = "answer"
        const val ACTION_DECLINE = "decline"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        handleAction(intent)
        setContent {
            AppTheme {
                SimCallScreen(onClose = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SimCallManager.activityVisible.value = true
    }

    override fun onUserLeaveHint() {
        SimCallManager.activityVisible.value = false
        super.onUserLeaveHint()
    }

    override fun onPause() {
        SimCallManager.activityVisible.value = false
        super.onPause()
    }

    override fun onDestroy() {
        SimCallManager.activityVisible.value = false
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAction(intent)
    }

    private fun handleAction(i: Intent?) {
        val action = i?.getStringExtra(EXTRA_ACTION) ?: return
        i.removeExtra(EXTRA_ACTION)
        val call = SimCallManager.ringingCall() ?: return
        when (action) {
            ACTION_ANSWER -> SimCallManager.answer(call)
            ACTION_DECLINE -> SimCallManager.reject(call)
        }
    }
}

@Composable
private fun SimCallScreen(onClose: () -> Unit) {
    val calls by SimCallManager.calls.collectAsState()
    val tick by SimCallManager.tick.collectAsState()
    val audio by SimCallManager.audioState.collectAsState()
    val semantic = LocalSemanticColors.current
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(calls) {
        if (calls.isEmpty()) {
            delay(600)
            if (SimCallManager.calls.value.isEmpty()) onClose()
        }
    }

    val call: Call? = calls.firstOrNull { it.compatState() == Call.STATE_RINGING } ?: calls.firstOrNull()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call, tick) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    var showDialpad by remember { mutableStateOf(false) }

    val bgTop = if (semantic.isAmoled) Color.Black else Color(0xFF0A101C)
    val bgMid = if (semantic.isAmoled) lerp(Color.Black, primary, 0.14f) else lerp(Color(0xFF0F172A), primary, 0.20f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(bgTop, bgMid, bgTop)))
    ) {
        if (call == null) {
            Text(
                "Call Ended",
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        val state = call.compatState()
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val title = callTitle(call)
        val simCtx = LocalContext.current
        val simLabel = remember(call) { simSlotLabel(simCtx, call) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Surface(color = primary.copy(alpha = 0.22f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = simLabel.uppercase() + " CALL",
                        color = lerp(Color.White, primary, 0.45f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(36.dp))
                Text(
                    text = title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                if (number.isNotBlank() && number != title) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = number,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF94A3B8)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stateLabel(state, call, now, simLabel),
                    fontSize = 16.sp,
                    color = if (state == Call.STATE_ACTIVE) semantic.success else Color(0xFFCBD5E1),
                    fontWeight = if (state == Call.STATE_ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                if (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING) {
                    Spacer(Modifier.height(10.dp))
                    SimQualityLine(call)
                }
            }

            val isActive = state == Call.STATE_ACTIVE
            if (!(showDialpad && isActive)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (state == Call.STATE_RINGING || state == Call.STATE_DIALING ||
                        state == Call.STATE_CONNECTING || state == Call.STATE_PULLING_CALL
                    ) {
                        SimCallingRipples(primary)
                    }
                }
            }
            if (showDialpad && isActive) {
                DialPad(
                    onDigitPressed = { d -> SimCallManager.playDtmf(call, d.first()) },
                    darkSurface = true,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isActive) {
                    FilledTonalIconButton(
                        onClick = { showDialpad = !showDialpad },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sim_incall_dialpad_toggle")
                    ) {
                        Icon(Icons.Filled.Dialpad, contentDescription = "DTMF Keypad")
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (state == Call.STATE_RINGING) {
                    IncomingCallControls(
                        onAnswer = { SimCallManager.answer(call) },
                        onDecline = { SimCallManager.reject(call) }
                    )
                } else {
                    val holding = state == Call.STATE_HOLDING
                    CallControls(
                        isMuted = audio?.isMuted == true,
                        isSpeaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
                        isOnHold = holding,
                        onMuteToggle = { SimCallManager.setMuted(audio?.isMuted != true) },
                        onSpeakerToggle = { SimCallManager.setSpeaker(audio?.route != CallAudioState.ROUTE_SPEAKER) },
                        onHoldToggle = { if (holding) SimCallManager.unhold(call) else SimCallManager.hold(call) },
                        onEndCall = { SimCallManager.disconnect(call) }
                    )
                }
            }
        }
    }
}

private data class SimQuality(
    val network: String,
    val hd: Boolean,
    val wifi: Boolean,
    val dbm: Int?,
    val level: Int
)

private fun networkName(type: Int): String = when (type) {
    TelephonyManager.NETWORK_TYPE_NR -> "5G"
    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
    TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi Calling"
    TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
    TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_GSM -> "2G"
    else -> "Mobile"
}

private fun readSimQuality(context: Context, call: Call): SimQuality? {
    return try {
        var tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        call.details?.accountHandle?.id?.toIntOrNull()?.let { subId ->
            tm = tm.createForSubscriptionId(subId)
        }
        val hd = call.details?.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO) == true
        val wifi = call.details?.hasProperty(Call.Details.PROPERTY_WIFI) == true
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        @Suppress("DEPRECATION")
        var network = if (hasPhoneState) networkName(tm.voiceNetworkType) else "Mobile"
        if (wifi) network = "Wi-Fi Calling"
        var dbm: Int? = null
        var level = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ss = tm.signalStrength
            if (ss != null) {
                level = ss.level
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dbm = ss.cellSignalStrengths.firstOrNull()?.dbm?.takeIf { it != Int.MAX_VALUE }
                }
            }
        }
        SimQuality(network, hd, wifi, dbm, level)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun SimQualityLine(call: Call) {
    val context = LocalContext.current
    val semantic = LocalSemanticColors.current
    var q by remember { mutableStateOf<SimQuality?>(null) }
    LaunchedEffect(call) {
        while (true) {
            q = readSimQuality(context, call)
            delay(2000)
        }
    }
    val info = q ?: return
    val color = when {
        info.level >= 3 -> semantic.success
        info.level == 2 -> semantic.warning
        else -> semantic.danger
    }
    val label = when {
        info.level >= 3 -> "Good signal"
        info.level == 2 -> "Fair signal"
        else -> "Weak signal"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildString {
                append(if (info.hd) "HD Voice" else "Standard voice")
                append(" • ").append(info.network)
                append(" • ").append(label)
                info.dbm?.let { append(" (").append(it).append(" dBm)") }
            },
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1),
            textAlign = TextAlign.Center
        )
    }
}

private fun stateLabel(state: Int, call: Call, now: Long, sim: String): String = when (state) {
    Call.STATE_RINGING -> "Incoming call via $sim"
    Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> "Calling via $sim…"
    Call.STATE_HOLDING -> "Call On Hold"
    Call.STATE_DISCONNECTING -> "Ending…"
    Call.STATE_DISCONNECTED -> "Call Ended"
    Call.STATE_ACTIVE -> {
        val start = call.details?.connectTimeMillis ?: 0L
        val secs = if (start > 0) ((now - start) / 1000).coerceAtLeast(0) else 0L
        "Connected • %02d:%02d".format(secs / 60, secs % 60)
    }
    else -> ""
}

@Composable
private fun SimCallingRipples(accent: Color) {
    val transition = rememberInfiniteTransition(label = "sim_ripples")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2400, easing = LinearEasing)),
        label = "sim_ripples_progress"
    )
    val iconPulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "sim_icon_pulse"
    )
    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minR = 34.dp.toPx()
            val maxR = size.minDimension / 2f
            for (i in 0 until 3) {
                val p = (progress + i / 3f) % 1f
                drawCircle(
                    color = accent.copy(alpha = 0.38f * (1f - p)),
                    radius = minR + (maxR - minR) * p,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        Box(
            modifier = Modifier
                .size((64 * iconPulse).dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
