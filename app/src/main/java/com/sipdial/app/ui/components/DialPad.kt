package com.sipdial.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sipdial.app.ui.theme.JetBrainsMono
import com.sipdial.app.util.GeneralPrefs

private data class DialKeySpec(
    val digit: String,
    val letters: String,
    val tag: String
)

private val dialPadRows = listOf(
    listOf(
        DialKeySpec("1", "", "1"),
        DialKeySpec("2", "ABC", "2"),
        DialKeySpec("3", "DEF", "3")
    ),
    listOf(
        DialKeySpec("4", "GHI", "4"),
        DialKeySpec("5", "JKL", "5"),
        DialKeySpec("6", "MNO", "6")
    ),
    listOf(
        DialKeySpec("7", "PQRS", "7"),
        DialKeySpec("8", "TUV", "8"),
        DialKeySpec("9", "WXYZ", "9")
    ),
    listOf(
        DialKeySpec("*", "", "star"),
        DialKeySpec("0", "+", "0"),
        DialKeySpec("#", "", "hash")
    )
)

@Composable
fun DialPad(
    onDigitPressed: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPressZero: (() -> Unit)? = null,
    darkSurface: Boolean = false,
    flat: Boolean = false,
    playDtmfTones: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val generalPrefs by GeneralPrefs.state.collectAsStateWithLifecycle()
    val effectiveSound = playDtmfTones && generalPrefs.soundEffectsEnabled
    val effectiveHaptic = generalPrefs.hapticFeedbackEnabled

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (flat) 8.dp else 10.dp)
    ) {
        dialPadRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    DialKeyButton(
                        spec = key,
                        darkSurface = darkSurface,
                        flat = flat,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dial_key_${key.tag}"),
                        onClick = {
                            if (effectiveHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (effectiveSound) DtmfTonePlayer.play(context, key.digit)
                            onDigitPressed(key.digit)
                        },
                        onLongClick = if (key.digit == "0" && onLongPressZero != null) {
                            {
                                if (effectiveHaptic) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onLongPressZero()
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKeyButton(
    spec: DialKeySpec,
    darkSurface: Boolean,
    flat: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val keyBg = when {
        darkSurface -> Color.White.copy(alpha = 0.14f)
        flat -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val digitColor = if (darkSurface) Color.White else MaterialTheme.colorScheme.onSurface
    val subColor = if (darkSurface) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(keyBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = spec.digit,
                fontFamily = JetBrainsMono,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = digitColor,
                lineHeight = 28.sp
            )
            if (spec.letters.isNotEmpty()) {
                Text(
                    text = spec.letters,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = subColor,
                    lineHeight = 12.sp
                )
            } else {
                Text(
                    text = " ",
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}
