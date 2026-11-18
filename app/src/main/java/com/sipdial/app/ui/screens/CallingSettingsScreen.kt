package com.sipdial.app.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sipdial.app.telecom.CallChoiceActivity
import com.sipdial.app.telecom.NativeDialerOverlay
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.telecom.sim.CallForward
import com.sipdial.app.telecom.sim.CallForwardingDialog
import com.sipdial.app.ui.components.SipDialTopBar
import com.sipdial.app.ui.theme.LocalSemanticColors
import com.sipdial.app.util.CallingPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallingSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val semantic = LocalSemanticColors.current

    val isDefaultDialer by TelecomHelper.defaultDialerHeld.collectAsState()
    var isCallRedirectionHeld by remember { mutableStateOf(TelecomHelper.isCallRedirectionRoleHeld(context)) }
    var hasOverlay by remember { mutableStateOf(TelecomHelper.hasOverlayPermission(context)) }
    var routingMode by remember { mutableIntStateOf(TelecomHelper.getNativeCallRoutingMode(context)) }
    var autoRecord by remember { mutableStateOf(TelecomHelper.isAutoRecordEnabled(context)) }
    var confirmCall by remember { mutableStateOf(CallingPrefs.isCallConfirmationEnabled(context)) }
    var autoAnswer by remember { mutableStateOf(CallingPrefs.isAutoAnswerEnabled(context)) }
    var autoAnswerDelay by remember { mutableIntStateOf(CallingPrefs.getAutoAnswerDelaySeconds(context)) }
    var dtmfTones by remember { mutableStateOf(CallingPrefs.isDtmfTonesEnabled(context)) }
    var dtmfMode by remember { mutableStateOf(CallingPrefs.getDtmfMode(context)) }
    var showForwarding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { TelecomHelper.refreshDefaultDialer(context) }

    var dialerLaunchAt by remember { mutableLongStateOf(0L) }
    val dialerRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val held = TelecomHelper.refreshDefaultDialer(context)
        if (!held && System.currentTimeMillis() - dialerLaunchAt < 800L) {
            Toast.makeText(context, "Select SIP Dial under Phone app in Default Apps", Toast.LENGTH_LONG).show()
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlay = TelecomHelper.hasOverlayPermission(context)
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isCallRedirectionHeld = TelecomHelper.isCallRedirectionRoleHeld(context)
        if (isCallRedirectionHeld && !TelecomHelper.hasOverlayPermission(context)) {
            overlayLauncher.launch(TelecomHelper.createOverlayPermissionIntent(context))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TelecomHelper.refreshDefaultDialer(context)
                isCallRedirectionHeld = TelecomHelper.isCallRedirectionRoleHeld(context)
                hasOverlay = TelecomHelper.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    if (showForwarding) {
        CallForwardingDialog(onDismiss = { showForwarding = false })
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SipDialTopBar(
                title = "Calling Settings",
                onBack = onBack,
                backTestTag = "calling_settings_back"
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
            // Telecom Integration & Permissions
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
                        Text("Phone Dialer Integration", style = MaterialTheme.typography.titleMedium)

                        RoleTile(
                            icon = Icons.Filled.Call,
                            title = "Default Phone App",
                            subtitle = if (isDefaultDialer) "Active — SIP Dial manages SIM & SIP calls" else "Tap to set as default dialer",
                            enabled = isDefaultDialer,
                            onClick = {
                                TelecomHelper.createDefaultDialerRoleIntent(context)?.let {
                                    dialerLaunchAt = System.currentTimeMillis()
                                    dialerRoleLauncher.launch(it)
                                }
                            }
                        )

                        RoleTile(
                            icon = Icons.AutoMirrored.Filled.PhoneForwarded,
                            title = "Call Redirection",
                            subtitle = if (isCallRedirectionHeld) "Enabled — intercepts outgoing dialer calls" else "Tap to enable call redirection",
                            enabled = isCallRedirectionHeld,
                            onClick = {
                                TelecomHelper.createCallRedirectionRoleIntent(context)?.let {
                                    roleLauncher.launch(it)
                                }
                            }
                        )

                        RoleTile(
                            icon = Icons.Filled.Layers,
                            title = "Display Over Other Apps",
                            subtitle = if (hasOverlay) "Granted — floating incoming banner & chooser active" else "Tap to allow overlay popups",
                            enabled = hasOverlay,
                            onClick = {
                                overlayLauncher.launch(TelecomHelper.createOverlayPermissionIntent(context))
                            }
                        )

                        OutlinedButton(
                            onClick = {
                                if (NativeDialerOverlay.canShowOverlay(context)) {
                                    NativeDialerOverlay.showAccountChooser(
                                        context = context,
                                        number = "",
                                        onSimSelected = {},
                                        onSipSelected = {},
                                        onCancel = {}
                                    )
                                } else {
                                    context.startActivity(
                                        Intent(context, CallChoiceActivity::class.java).putExtra("phone_number", "")
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Test Calling Line Chooser Popup")
                        }
                    }
                }
            }

            // Outgoing Routing Mode
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
                        Text("Outgoing Call Routing", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    TelecomHelper.MODE_ALWAYS_SIP to "SIP Only",
                                    TelecomHelper.MODE_ASK_EVERY_TIME to "Ask",
                                    TelecomHelper.MODE_SIM_ONLY to "SIM Only"
                                ).forEach { (modeVal, label) ->
                                    val selected = routingMode == modeVal
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                routingMode = modeVal
                                                TelecomHelper.setNativeCallRoutingMode(context, modeVal)
                                            }
                                            .padding(vertical = 10.dp)
                                            .testTag("routing_mode_$label"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Call Handling & Auto-Answer & Recording
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
                        Text("Call Handling", style = MaterialTheme.typography.titleMedium)

                        SettingToggleRow(
                            title = "Confirm Before Calling",
                            subtitle = "Show a confirmation prompt before placing calls",
                            checked = confirmCall,
                            onCheckedChange = {
                                confirmCall = it
                                CallingPrefs.setCallConfirmationEnabled(context, it)
                            }
                        )
                        HorizontalDivider()
                        SettingToggleRow(
                            title = "Auto-Answer Incoming SIP Calls",
                            subtitle = if (autoAnswer) "Answers automatically after ${autoAnswerDelay}s" else "Manual answer",
                            checked = autoAnswer,
                            onCheckedChange = {
                                autoAnswer = it
                                CallingPrefs.setAutoAnswerEnabled(context, it)
                            }
                        )
                        if (autoAnswer) {
                            Column {
                                Text(
                                    text = "Auto-answer delay: $autoAnswerDelay sec",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Slider(
                                    value = autoAnswerDelay.toFloat(),
                                    onValueChange = {
                                        autoAnswerDelay = it.toInt()
                                        CallingPrefs.setAutoAnswerDelaySeconds(context, autoAnswerDelay)
                                    },
                                    valueRange = 0f..10f,
                                    steps = 9
                                )
                            }
                        }
                        HorizontalDivider()
                        SettingToggleRow(
                            title = "Auto Call Recording",
                            subtitle = "Automatically record connected SIP calls to Recordings/SIP Dial",
                            checked = autoRecord,
                            onCheckedChange = {
                                autoRecord = it
                                TelecomHelper.setAutoRecordEnabled(context, it)
                            }
                        )
                    }
                }
            }

            // DTMF & Call Forwarding
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
                        Text("DTMF & Forwarding", style = MaterialTheme.typography.titleMedium)

                        SettingToggleRow(
                            title = "Keypad Dial Tones",
                            subtitle = "Play audible DTMF feedback when tapping keypad keys",
                            checked = dtmfTones,
                            onCheckedChange = {
                                dtmfTones = it
                                CallingPrefs.setDtmfTonesEnabled(context, it)
                            }
                        )

                        Text(
                            text = "In-call DTMF Signaling Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                CallingPrefs.DTMF_AUTO to "Auto",
                                CallingPrefs.DTMF_RFC2833 to "RFC 2833",
                                CallingPrefs.DTMF_SIP_INFO to "SIP INFO"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = dtmfMode == mode,
                                    onClick = {
                                        dtmfMode = mode
                                        CallingPrefs.setDtmfMode(context, mode)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        HorizontalDivider()

                        OutlinedButton(
                            onClick = { showForwarding = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = if (CallForward.mode(context) != CallForward.OFF) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Configure Call Forwarding (SIM & SIP)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) semantic.success.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (enabled) semantic.success.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) semantic.success else MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
