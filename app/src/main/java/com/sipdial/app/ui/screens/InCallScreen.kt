package com.sipdial.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipdial.app.sip.AudioRouteOption
import com.sipdial.app.sip.AudioRouteType
import com.sipdial.app.sip.CallQualityStats
import com.sipdial.app.sip.SipCallState
import com.sipdial.app.ui.components.CallControls
import com.sipdial.app.ui.components.ContactAvatar
import com.sipdial.app.ui.components.DialPad
import com.sipdial.app.ui.components.IncomingCallControls
import com.sipdial.app.ui.theme.JetBrainsMono
import com.sipdial.app.ui.theme.LocalSemanticColors
import com.sipdial.app.util.CallerIdFormatter
import com.sipdial.app.util.ContactsHelper
import com.sipdial.app.util.DeviceContact
import kotlinx.coroutines.delay

@Composable
fun InCallScreen(
    callState: SipCallState,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isRecording: Boolean,
    audioRoutes: List<AudioRouteOption>,
    selectedAudioRoute: AudioRouteOption?,
    callDurationProvider: () -> Int,
    quality: CallQualityStats? = null,
    contacts: List<DeviceContact> = emptyList(),
    accountUser: String? = null,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onEndCall: () -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onHoldToggle: () -> Unit,
    onRecordingToggle: () -> Unit,
    onSelectAudioRoute: (AudioRouteOption) -> Unit,
    onDtmf: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val primary = MaterialTheme.colorScheme.primary

    var showDialpad by remember { mutableStateOf(false) }
    var showAudioRoutes by remember { mutableStateOf(false) }
    var callDuration by remember { mutableIntStateOf(0) }

    LaunchedEffect(callState) {
        if (callState is SipCallState.Connected) {
            while (true) {
                callDuration = callDurationProvider()
                delay(1000)
            }
        }
    }

    val remoteAddress = when (callState) {
        is SipCallState.Incoming -> callState.remoteAddress
        is SipCallState.Outgoing -> callState.remoteAddress
        is SipCallState.Connected -> callState.remoteAddress
        is SipCallState.OnHold -> callState.remoteAddress
        is SipCallState.Ended -> callState.remoteAddress
        else -> ""
    }

    val cleanNumber = remember(remoteAddress) { CallerIdFormatter.extractNumber(remoteAddress) }
    val matchedContact = remember(cleanNumber, contacts) {
        ContactsHelper.contactFor(cleanNumber, contacts)
    }

    val accountLabel = accountUser?.trim()?.takeIf { it.isNotEmpty() }
    val statusText = when (callState) {
        is SipCallState.Incoming -> if (accountLabel != null) "Incoming • SIP • $accountLabel" else "Incoming • SIP"
        is SipCallState.Outgoing -> if (accountLabel != null) "Calling • SIP • $accountLabel..." else "Calling • SIP..."
        is SipCallState.Connected -> "Connected • ${formatInCallDuration(callDuration)}"
        is SipCallState.OnHold -> if (accountLabel != null) "Call On Hold • $accountLabel" else "Call On Hold"
        is SipCallState.Ended -> callState.reason.ifBlank { "Call Ended" }
        else -> ""
    }

    val bgTop = if (semantic.isAmoled) Color.Black else Color(0xFF0A101C)
    val bgMid = if (semantic.isAmoled) lerp(Color.Black, primary, 0.16f) else lerp(Color(0xFF0F172A), primary, 0.24f)

    if (showAudioRoutes && audioRoutes.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAudioRoutes = false },
            title = { Text("Select Audio Output") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    audioRoutes.forEach { route ->
                        val selected = selectedAudioRoute?.id == route.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    onSelectAudioRoute(route)
                                    showAudioRoutes = false
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = when (route.type) {
                                    AudioRouteType.BLUETOOTH -> Icons.Filled.BluetoothAudio
                                    AudioRouteType.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                                    AudioRouteType.WIRED_HEADSET -> Icons.Filled.Headset
                                    AudioRouteType.EARPIECE -> Icons.Filled.PhoneInTalk
                                },
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = route.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioRoutes = false }) { Text("Close") }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(bgTop, bgMid, bgTop)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Surface(color = primary.copy(alpha = 0.22f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = if (accountLabel != null) "SIP • ${accountLabel.uppercase()}" else "SIP VOIP HD AUDIO",
                        color = lerp(Color.White, primary, 0.45f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                ContactAvatar(
                    name = matchedContact?.name ?: cleanNumber,
                    photoUri = matchedContact?.photoUri,
                    size = 76.dp,
                    containerColor = primary.copy(alpha = 0.28f),
                    contentColor = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = matchedContact?.name ?: cleanNumber,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                if (matchedContact != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cleanNumber,
                        fontFamily = JetBrainsMono,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    color = if (callState is SipCallState.Connected) semantic.success else Color(0xFFCBD5E1),
                    fontWeight = if (callState is SipCallState.Connected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                if (isRecording && callState is SipCallState.Connected) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val pulse = rememberInfiniteTransition(label = "rec_pulse")
                    val dotAlpha by pulse.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "rec_alpha"
                    )
                    Surface(color = semantic.danger.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(semantic.danger.copy(alpha = dotAlpha))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REC • Recording",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                if (callState is SipCallState.Connected && quality != null && quality.codec.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val qualityColor = when {
                        quality.lossPercent > 5f || quality.rttMs > 400 || quality.jitterMs > 80 -> semantic.danger
                        quality.lossPercent > 1f || quality.rttMs > 200 || quality.jitterMs > 40 -> semantic.warning
                        else -> semantic.success
                    }
                    var showDetails by remember { mutableStateOf(true) }
                    val qualityLabel = when (qualityColor) {
                        semantic.success -> "Good connection"
                        semantic.warning -> "Fair connection"
                        else -> "Poor connection"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showDetails = !showDetails }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(qualityColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showDetails) {
                                "${quality.codec} • RTT ${quality.rttMs}ms • Jitter ${quality.jitterMs}ms • Loss ${"%.1f".format(quality.lossPercent)}%"
                            } else {
                                "$qualityLabel • ${quality.codec}"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center
                        )
                    }
                    if (showDetails) {
                        Text(
                            text = "Mic ${quality.micDb.toInt()} dB • ↑${quality.upKbps} ↓${quality.downKbps} kbps",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (!(showDialpad && callState is SipCallState.Connected)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (callState is SipCallState.Outgoing || callState is SipCallState.Incoming) {
                        SipCallingRipples(primary)
                    }
                }
            }

            if (showDialpad && callState is SipCallState.Connected) {
                DialPad(
                    onDigitPressed = { digit -> onDtmf(digit.first()) },
                    darkSurface = true,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            val speakerActive = isSpeaker
            val hasExternalAudio = audioRoutes.any {
                it.type == AudioRouteType.BLUETOOTH || it.type == AudioRouteType.WIRED_HEADSET
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (callState is SipCallState.Connected || (hasExternalAudio && callState !is SipCallState.Incoming)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (callState is SipCallState.Connected) {
                            FilledTonalIconButton(
                                onClick = { showDialpad = !showDialpad },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("sip_incall_dialpad_toggle")
                            ) {
                                Icon(Icons.Filled.Dialpad, contentDescription = "DTMF Keypad")
                            }
                        }
                        if (hasExternalAudio) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { showAudioRoutes = true }
                                    .testTag("sip_incall_audio_route_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when (selectedAudioRoute?.type) {
                                            AudioRouteType.BLUETOOTH -> Icons.Filled.BluetoothAudio
                                            AudioRouteType.WIRED_HEADSET -> Icons.Filled.Headset
                                            AudioRouteType.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                                            else -> Icons.Filled.PhoneInTalk
                                        },
                                        contentDescription = "Select audio output",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = selectedAudioRoute?.name ?: "Audio Output",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                when (callState) {
                    is SipCallState.Incoming -> {
                        IncomingCallControls(
                            onAnswer = onAnswer,
                            onDecline = onDecline
                        )
                    }
                    is SipCallState.Outgoing,
                    is SipCallState.Connected,
                    is SipCallState.OnHold -> {
                        CallControls(
                            isMuted = isMuted,
                            isSpeaker = speakerActive,
                            isOnHold = callState is SipCallState.OnHold,
                            speakerLabel = if (speakerActive) "Speaker On" else "Speaker",
                            isRecording = if (callState is SipCallState.Connected) isRecording else null,
                            onRecordingToggle = if (callState is SipCallState.Connected) onRecordingToggle else null,
                            onMuteToggle = onMuteToggle,
                            onSpeakerToggle = onSpeakerToggle,
                            onHoldToggle = onHoldToggle,
                            onEndCall = onEndCall
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

private fun formatInCallDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(minutes, secs)
}

@Composable
private fun SipCallingRipples(accent: Color) {
    val transition = rememberInfiniteTransition(label = "sip_ripples")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2400, easing = LinearEasing)),
        label = "sip_ripples_progress"
    )
    val iconPulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "sip_icon_pulse"
    )
    Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
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
                .size((62 * iconPulse).dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
