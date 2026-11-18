package com.sipdial.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sipdial.app.SipDialApplication
import com.sipdial.app.util.AudioQualityPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCodecSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var micBoost by remember { mutableIntStateOf(AudioQualityPrefs.getMicBoostDb(context)) }
    var hdCodecs by remember { mutableStateOf(AudioQualityPrefs.isHdCodecsEnabled(context)) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            com.sipdial.app.ui.components.SipDialTopBar(
                title = "Audio & Codecs",
                onBack = onBack,
                backTestTag = "audio_settings_back"
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Microphone Boost", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "+$micBoost dB",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = micBoost.toFloat(),
                            onValueChange = { v ->
                                micBoost = v.toInt()
                                AudioQualityPrefs.setMicBoostDb(context, micBoost)
                                try {
                                    SipDialApplication.core.micGainDb = micBoost.toFloat()
                                } catch (_: Throwable) {
                                }
                            },
                            valueRange = 0f..AudioQualityPrefs.MAX_MIC_BOOST_DB.toFloat(),
                            steps = AudioQualityPrefs.MAX_MIC_BOOST_DB - 1
                        )
                        Text(
                            text = "Increase if the far party hears you quietly; decrease if audio distorts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingToggleRow(
                            title = "HD Audio Codecs (Opus / G.722)",
                            subtitle = "Prioritize wideband HD audio when supported by your SIP server",
                            checked = hdCodecs,
                            onCheckedChange = { enabled ->
                                hdCodecs = enabled
                                AudioQualityPrefs.setHdCodecsEnabled(context, enabled)
                                AudioQualityPrefs.setCodecConfig(context, null)
                                try {
                                    SipDialApplication.configureVoiceAudio(SipDialApplication.core)
                                } catch (_: Throwable) {
                                }
                            }
                        )
                    }
                }
            }

            item {
                key(hdCodecs) {
                    CodecManagerCard()
                }
            }
        }
    }
}
