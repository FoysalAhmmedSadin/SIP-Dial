package com.sipdial.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipdial.app.ui.theme.LocalSemanticColors

@Composable
fun CallControls(
    isMuted: Boolean,
    isSpeaker: Boolean,
    isOnHold: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onHoldToggle: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
    speakerLabel: String = "Speaker",
    isRecording: Boolean? = null,
    onRecordingToggle: (() -> Unit)? = null,
    onAudioRouteClick: (() -> Unit)? = null
) {
    val semantic = LocalSemanticColors.current
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallToggleControl(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                active = isMuted,
                activeColor = Color.White,
                activeContentColor = Color(0xFF0F172A),
                testTag = "call_mute_button",
                onClick = onMuteToggle
            )

            CallToggleControl(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = if (isSpeaker) "Speaker On" else "Speaker",
                active = isSpeaker,
                activeColor = Color.White,
                activeContentColor = Color(0xFF0F172A),
                testTag = "call_speaker_button",
                onClick = onSpeakerToggle
            )

            CallToggleControl(
                icon = if (isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                label = if (isOnHold) "Resume" else "Hold",
                active = isOnHold,
                activeColor = semantic.warning,
                activeContentColor = semantic.onWarning,
                testTag = "call_hold_button",
                onClick = onHoldToggle
            )

            if (isRecording != null && onRecordingToggle != null) {
                CallToggleControl(
                    icon = Icons.Filled.FiberManualRecord,
                    label = if (isRecording) "Recording" else "Record",
                    active = isRecording,
                    activeColor = semantic.danger,
                    activeContentColor = Color.White,
                    testTag = "call_record_button",
                    onClick = onRecordingToggle
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(semantic.danger)
                .clickable(onClick = onEndCall)
                .testTag("call_end_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "End call",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
fun IncomingCallControls(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(semantic.danger)
                    .clickable(onClick = onDecline)
                    .testTag("incoming_decline_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "Decline",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Decline",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.88f)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(semantic.success)
                    .clickable(onClick = onAnswer)
                    .testTag("incoming_answer_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Answer",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Answer",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun CallToggleControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    activeContentColor: Color = Color.White,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (active) activeColor else Color.White.copy(alpha = 0.16f))
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) activeContentColor else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else Color.White.copy(alpha = 0.86f),
            maxLines = 1
        )
    }
}
