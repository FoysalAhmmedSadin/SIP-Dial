package com.sipdial.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sipdial.app.R
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.ui.components.SipDialTopBar
import com.sipdial.app.ui.components.StatusIndicator
import com.sipdial.app.ui.theme.LocalSemanticColors
import com.sipdial.app.ui.theme.ThemePreferences
import com.sipdial.app.util.GeneralPrefs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentAccount: SipAccountData?,
    accountsCount: Int,
    registrationState: SipRegistrationState,
    recordingsCount: Int,
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit = {},
    onOpenAppearance: () -> Unit,
    onOpenSipAccounts: () -> Unit,
    onOpenCalling: () -> Unit,
    onOpenAudioCodecs: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPowerNetwork: () -> Unit,
    onOpenPrivacySecurity: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val semantic = LocalSemanticColors.current
    val appearance by ThemePreferences.state.collectAsStateWithLifecycle()
    val generalState by GeneralPrefs.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SipDialTopBar(
                title = "Settings",
                onBack = onBack,
                backTestTag = "settings_back"
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenSipAccounts)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentAccount?.let { it.name.ifBlank { it.username } } ?: "No SIP Account",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (currentAccount != null) {
                                    "${currentAccount.username}@${currentAccount.domain} ($accountsCount saved)"
                                } else {
                                    "Tap to add your SIP credentials"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusIndicator(state = registrationState, compact = true)
                    }
                }
            }

            item {
                SettingsCategoryHeader("Personalization")
            }
            item {
                val defaultTabTitle = GeneralPrefs.allTabOptions
                    .firstOrNull { it.first == generalState.defaultOpeningTab }?.second ?: "Recents"
                SettingsHubRow(
                    icon = Icons.Filled.Tune,
                    title = "General",
                    subtitle = "Default screen: $defaultTabTitle • Sounds & haptics",
                    iconTint = MaterialTheme.colorScheme.primary,
                    testTag = "settings_row_general",
                    onClick = onOpenGeneral
                )
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.Palette,
                    title = "Appearance",
                    subtitle = "${appearance.themeMode.label} • ${appearance.accentColor.displayName}",
                    iconTint = MaterialTheme.colorScheme.primary,
                    testTag = "settings_row_appearance",
                    onClick = onOpenAppearance
                )
            }

            item {
                SettingsCategoryHeader("SIP & Calling")
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.Dns,
                    title = "SIP Accounts",
                    subtitle = "$accountsCount account(s) configured",
                    iconTint = MaterialTheme.colorScheme.primary,
                    testTag = "settings_row_sip_accounts",
                    onClick = onOpenSipAccounts
                )
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.PhoneInTalk,
                    title = "Calling Settings",
                    subtitle = "Default dialer, call redirection, auto-answer, DTMF, forwarding",
                    iconTint = semantic.success,
                    testTag = "settings_row_calling",
                    onClick = onOpenCalling
                )
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.GraphicEq,
                    title = "Audio & Codecs",
                    subtitle = "Mic boost, HD audio, codec priority",
                    iconTint = semantic.info,
                    testTag = "settings_row_audio",
                    onClick = onOpenAudioCodecs
                )
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.RecordVoiceOver,
                    title = "Call Recordings",
                    subtitle = "$recordingsCount recording(s) available",
                    iconTint = semantic.danger,
                    testTag = "settings_row_recordings",
                    onClick = onOpenRecordings
                )
            }

            item {
                SettingsCategoryHeader("System & Data")
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.Notifications,
                    title = "Notifications",
                    subtitle = "Ringtone, vibration, incoming call banner",
                    iconTint = semantic.warning,
                    testTag = "settings_row_notifications",
                    onClick = onOpenNotifications
                )
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.BatteryChargingFull,
                    title = "Power & Network",
                    subtitle = "Battery mode, setup checklist, TLS & SRTP",
                    iconTint = semantic.warning,
                    testTag = "settings_row_power",
                    onClick = onOpenPowerNetwork
                )
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.Security,
                    title = "Privacy & Security",
                    subtitle = "Blocked numbers, Keystore vault, clear call logs",
                    iconTint = semantic.danger,
                    testTag = "settings_row_privacy",
                    onClick = onOpenPrivacySecurity
                )
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.Save,
                    title = stringResource(R.string.settings_backup_restore),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    iconTint = MaterialTheme.colorScheme.primary,
                    testTag = "settings_row_backup",
                    onClick = onOpenBackupRestore
                )
            }
            item {
                SettingsHubRow(
                    icon = Icons.Filled.Info,
                    title = "About SIP Dial",
                    subtitle = "Version, developer, GPL-3.0 license & credits",
                    iconTint = semantic.info,
                    testTag = "settings_row_about",
                    onClick = onOpenAbout
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsHubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
