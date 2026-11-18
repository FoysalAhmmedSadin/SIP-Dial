package com.sipdial.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sipdial.app.util.SecurityPrefs

@Composable
fun SecuritySettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var verifyTls by remember { mutableStateOf(SecurityPrefs.verifyTls(context)) }
    var srtp by remember { mutableStateOf(SecurityPrefs.srtp(context)) }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Transport & Media Encryption", style = MaterialTheme.typography.titleMedium)

            SettingToggleRow(
                title = "Verify TLS Certificates",
                subtitle = "Validate SIP server SSL/TLS certificates when using TLS transport",
                checked = verifyTls,
                onCheckedChange = {
                    verifyTls = it
                    SecurityPrefs.setVerifyTls(context, it)
                }
            )

            HorizontalDivider()

            SettingToggleRow(
                title = "Require SRTP Media Encryption",
                subtitle = "Encrypt voice RTP streams end-to-server when supported by your provider",
                checked = srtp,
                onCheckedChange = {
                    srtp = it
                    SecurityPrefs.setSrtp(context, it)
                }
            )

            Text(
                text = "Changes take effect the next time your SIP account re-registers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
