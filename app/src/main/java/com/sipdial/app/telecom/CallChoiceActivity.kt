package com.sipdial.app.telecom

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipdial.app.ui.theme.AppTheme
import com.sipdial.app.ui.theme.LocalSemanticColors

class CallChoiceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = intent?.getStringExtra("phone_number") ?: ""
        val simAccounts = TelecomHelper.getAvailableSimAccounts(this)

        setContent {
            AppTheme {
                val semantic = LocalSemanticColors.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Select Calling Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (simAccounts.isNotEmpty()) {
                                    simAccounts.forEach { sim ->
                                        ChoicePillButton(
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("choice_sim_${sim.slotIndex}"),
                                            label = "SIM ${sim.slotIndex}",
                                            bgColor = if (sim.slotIndex == 1) semantic.success else semantic.info,
                                            contentColor = Color.White,
                                            onClick = {
                                                if (number.isNotBlank()) {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                        SipCallRedirectionService.bypassNextNumber = number
                                                        SipCallRedirectionService.bypassUntilMs =
                                                            System.currentTimeMillis() + 8000L
                                                    }
                                                    TelecomHelper.placeSimCallWithHandle(
                                                        this@CallChoiceActivity,
                                                        number,
                                                        sim.handle
                                                    )
                                                }
                                                finish()
                                            }
                                        )
                                    }
                                } else {
                                    ChoicePillButton(
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("choice_sim_1"),
                                        label = "SIM 1",
                                        bgColor = semantic.success,
                                        contentColor = Color.White,
                                        onClick = {
                                            if (number.isNotBlank()) {
                                                TelecomHelper.placeSimCallWithHandle(this@CallChoiceActivity, number, null)
                                            }
                                            finish()
                                        }
                                    )
                                }

                                ChoicePillButton(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("choice_sip"),
                                    label = "SIP",
                                    bgColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    onClick = {
                                        if (number.isNotBlank()) {
                                            TelecomHelper.placeSipCallFallback(applicationContext, number)
                                        }
                                        finish()
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { finish() }
                                    .testTag("choice_cancel"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoicePillButton(
    modifier: Modifier = Modifier,
    label: String,
    bgColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
