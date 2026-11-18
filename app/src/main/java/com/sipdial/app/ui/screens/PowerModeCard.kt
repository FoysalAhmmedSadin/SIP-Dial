package com.sipdial.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sipdial.app.service.SipForegroundService
import com.sipdial.app.util.PowerPrefs

@Composable
fun PowerModeCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(PowerPrefs.mode(context)) }

    @Composable
    fun OptionRow(value: String, title: String, desc: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    mode = value
                    PowerPrefs.setMode(context, value)
                    SipForegroundService.refreshPower(context)
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = mode == value,
                onClick = {
                    mode = value
                    PowerPrefs.setMode(context, value)
                    SipForegroundService.refreshPower(context)
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Background Power Mode", style = MaterialTheme.typography.titleMedium)
            OptionRow(
                value = PowerPrefs.BALANCED,
                title = "Balanced (Recommended)",
                desc = "Wakes periodically every 30s to keep SIP registration alive while conserving battery."
            )
            OptionRow(
                value = PowerPrefs.RELIABLE,
                title = "Reliable (Always Awake)",
                desc = "Holds a partial wake lock continuously. Use if your device delays incoming SIP rings."
            )
        }
    }
}
