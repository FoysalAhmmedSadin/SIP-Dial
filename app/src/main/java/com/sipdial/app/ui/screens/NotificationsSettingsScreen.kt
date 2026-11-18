package com.sipdial.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sipdial.app.util.NotificationPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var ringtoneTitle by remember { mutableStateOf(NotificationPrefs.getRingtoneTitle(context)) }
    var vibrate by remember { mutableStateOf(NotificationPrefs.isVibrateEnabled(context)) }
    var incomingBanner by remember { mutableStateOf(NotificationPrefs.isIncomingBannerEnabled(context)) }
    var regAlerts by remember { mutableStateOf(NotificationPrefs.isRegistrationAlertsEnabled(context)) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            NotificationPrefs.setRingtoneUri(context, picked)
            ringtoneTitle = NotificationPrefs.getRingtoneTitle(context)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            com.sipdial.app.ui.components.SipDialTopBar(
                title = "Notifications",
                onBack = onBack,
                backTestTag = "notifications_back"
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
                        Text("Incoming Call Alerts", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "SIP Dial Ringtone")
                                        putExtra(
                                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                            NotificationPrefs.getRingtoneUri(context)
                                        )
                                    }
                                    ringtoneLauncher.launch(intent)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SIP Ringtone", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(ringtoneTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }

                        HorizontalDivider()

                        SettingToggleRow(
                            title = "Vibrate on Ring",
                            subtitle = "Vibrate during incoming SIP calls",
                            checked = vibrate,
                            onCheckedChange = {
                                vibrate = it
                                NotificationPrefs.setVibrateEnabled(context, it)
                            }
                        )

                        HorizontalDivider()

                        SettingToggleRow(
                            title = "Incoming Call Banner Overlay",
                            subtitle = "Show floating Answer / Decline popup while unlocked",
                            checked = incomingBanner,
                            onCheckedChange = {
                                incomingBanner = it
                                NotificationPrefs.setIncomingBannerEnabled(context, it)
                            }
                        )

                        HorizontalDivider()

                        SettingToggleRow(
                            title = "Registration Failure Alerts",
                            subtitle = "Notify if an enabled SIP line disconnects or fails authentication",
                            checked = regAlerts,
                            onCheckedChange = {
                                regAlerts = it
                                NotificationPrefs.setRegistrationAlertsEnabled(context, it)
                            }
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            } else {
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Open Android Notification Channels")
                }
            }
        }
    }
}
