package com.sipdial.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sipdial.app.SipDialApplication
import com.sipdial.app.util.AudioQualityPrefs

private fun codecDisplayName(key: String): String {
    val mime = key.substringBefore("/")
    val rate = key.substringAfter("/", "8000").toIntOrNull() ?: 8000
    val khz = if (mime == "G722") 16 else rate / 1000
    val name = when (mime) {
        "PCMA" -> "PCMA (G.711 A-law)"
        "PCMU" -> "PCMU (G.711 µ-law)"
        "G722" -> "G.722 (HD)"
        "OPUS" -> "Opus (HD)"
        "AMR-WB" -> "AMR-WB (HD)"
        "AMR" -> "AMR-NB"
        "G729" -> "G.729"
        "ILBC" -> "iLBC"
        "SPEEX" -> "Speex"
        else -> mime
    }
    return "$name • ${khz}kHz"
}

@Composable
fun CodecManagerCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    fun load(): List<AudioQualityPrefs.CodecEntry> {
        val corePayloads = try {
            SipDialApplication.coreOrNull()?.audioPayloadTypes?.toList().orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        val usableKeys = corePayloads
            .filter { it.isUsable }
            .map { AudioQualityPrefs.codecKey(it.mimeType, it.clockRate) }
            .toSet()

        AudioQualityPrefs.getCodecConfig(context)?.let { saved ->
            val filtered = if (usableKeys.isNotEmpty()) saved.filter { it.key in usableKeys } else saved
            if (filtered.isNotEmpty()) return filtered
        }
        return corePayloads
            .filter { it.isUsable }
            .map {
                AudioQualityPrefs.CodecEntry(
                    key = AudioQualityPrefs.codecKey(it.mimeType, it.clockRate),
                    enabled = it.enabled()
                )
            }
    }

    var items by remember { mutableStateOf(load()) }
    var customized by remember { mutableStateOf(AudioQualityPrefs.getCodecConfig(context) != null) }
    var warn by remember { mutableStateOf<String?>(null) }

    fun apply(list: List<AudioQualityPrefs.CodecEntry>) {
        items = list
        customized = true
        AudioQualityPrefs.setCodecConfig(context, list)
        try {
            SipDialApplication.configureVoiceAudio(SipDialApplication.core)
        } catch (_: Throwable) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Audio Codecs Priority", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Top codec is offered first during SIP negotiation. Keep at least one codec enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (items.isEmpty()) {
                Text(
                    text = "Codec list will populate when the native SIP stack is initialized on device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                items.forEachIndexed { index, c ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = codecDisplayName(c.key),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (c.enabled) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (c.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val l = items.toMutableList()
                                    val t = l[index]; l[index] = l[index - 1]; l[index - 1] = t
                                    apply(l)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = {
                                if (index < items.lastIndex) {
                                    val l = items.toMutableList()
                                    val t = l[index]; l[index] = l[index + 1]; l[index + 1] = t
                                    apply(l)
                                }
                            },
                            enabled = index < items.lastIndex,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        Switch(
                            checked = c.enabled,
                            onCheckedChange = { on ->
                                if (!on && items.count { it.enabled } <= 1) {
                                    warn = "At least one audio codec must remain enabled."
                                } else {
                                    apply(items.toMutableList().also { it[index] = c.copy(enabled = on) })
                                }
                            }
                        )
                    }
                }
            }

            if (customized) {
                TextButton(
                    onClick = {
                        AudioQualityPrefs.setCodecConfig(context, null)
                        try {
                            SipDialApplication.configureVoiceAudio(SipDialApplication.core)
                        } catch (_: Throwable) {
                        }
                        items = load()
                        customized = false
                    }
                ) { Text("Reset to Default Priority") }
            }
        }
    }

    warn?.let {
        AlertDialog(
            onDismissRequest = { warn = null },
            title = { Text("Audio Codecs") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { warn = null }) { Text("OK") } }
        )
    }
}
