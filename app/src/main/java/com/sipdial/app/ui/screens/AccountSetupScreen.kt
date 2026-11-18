package com.sipdial.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.ui.components.StatusIndicator
import com.sipdial.app.ui.components.bringIntoViewOnFocusWithIme
import com.sipdial.app.ui.theme.LocalSemanticColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountSetupScreen(
    currentAccount: SipAccountData?,
    accounts: List<SipAccountData>,
    registrationState: SipRegistrationState,
    registrationError: String?,
    accountStates: Map<String, SipRegistrationState>,
    accountErrors: Map<String, String>,
    onBack: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onSaveAndRegister: (SipAccountData) -> Unit,
    onUnregister: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val semantic = LocalSemanticColors.current

    var editingAccount by remember { mutableStateOf<SipAccountData?>(null) }
    var showForm by remember(accounts.isEmpty()) { mutableStateOf(accounts.isEmpty()) }
    var confirmDelete by remember { mutableStateOf<SipAccountData?>(null) }
    var formOpenSignal by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    val formItemIndex = (if (!registrationError.isNullOrBlank()) 1 else 0) +
        (if (accounts.isNotEmpty()) 1 else 0)

    LaunchedEffect(formOpenSignal) {
        if (formOpenSignal > 0 && showForm) {
            listState.animateScrollToItem(formItemIndex)
        }
    }

    confirmDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete SIP account?") },
            text = { Text("Remove ${acc.name.ifBlank { acc.username }} (${acc.username}@${acc.domain})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        if (editingAccount?.id == acc.id) {
                            editingAccount = null
                            showForm = false
                        }
                        onDeleteAccount(acc.id)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            com.sipdial.app.ui.components.SipDialTopBar(
                title = "SIP Accounts",
                onBack = onBack,
                backTestTag = "sip_accounts_back",
                actions = {
                    TextButton(
                        onClick = {
                            editingAccount = null
                            showForm = true
                            formOpenSignal++
                        },
                        modifier = Modifier.testTag("add_sip_account_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
                .imeNestedScroll()
                .testTag("sip_accounts_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!registrationError.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = semantic.danger.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, semantic.danger.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = semantic.danger)
                            Text(
                                text = registrationError,
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.danger
                            )
                        }
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Saved Accounts", style = MaterialTheme.typography.titleMedium)

                            accounts.forEachIndexed { idx, acc ->
                                val isSelected = acc.id == currentAccount?.id
                                val st = when {
                                    !acc.enabled -> SipRegistrationState.DISABLED
                                    else -> accountStates[acc.id] ?: if (isSelected) registrationState else SipRegistrationState.UNREGISTERED
                                }
                                val err = accountErrors[acc.id]

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSelectAccount(acc.id) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                                contentDescription = if (isSelected) "Active account" else "Select account",
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = acc.name.ifBlank { acc.username },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${acc.username}@${acc.domain}:${acc.port} (${acc.transport})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Switch(
                                                checked = acc.enabled,
                                                onCheckedChange = { onToggleEnabled(acc.id, it) }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            StatusIndicator(state = st, compact = true)
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        editingAccount = acc
                                                        showForm = true
                                                        formOpenSignal++
                                                    },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("edit_sip_account_${acc.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Edit,
                                                        contentDescription = "Edit account",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { confirmDelete = acc },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.DeleteOutline,
                                                        contentDescription = "Delete account",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }

                                        if (!err.isNullOrBlank() && acc.enabled && st == SipRegistrationState.ERROR) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = err,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = semantic.danger
                                            )
                                        }
                                    }
                                }
                                if (idx < accounts.lastIndex) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (showForm) {
                item(key = "sip_account_editor_card") {
                    SipAccountEditorCard(
                        initial = editingAccount,
                        existingAccounts = accounts,
                        accountStates = accountStates,
                        onSaveAndRegister = { account ->
                            onSaveAndRegister(account)
                            editingAccount = account
                            showForm = false
                        },
                        onUnregister = onUnregister,
                        onCancel = if (accounts.isNotEmpty()) {
                            { showForm = false; editingAccount = null }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun SipAccountEditorCard(
    initial: SipAccountData?,
    existingAccounts: List<SipAccountData>,
    accountStates: Map<String, SipRegistrationState>,
    onSaveAndRegister: (SipAccountData) -> Unit,
    onUnregister: (String) -> Unit,
    onCancel: (() -> Unit)?
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var displayName by remember(initial) { mutableStateOf(initial?.displayName ?: "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var authUsername by remember(initial) { mutableStateOf(initial?.authUsername ?: "") }
    var password by remember(initial) { mutableStateOf(initial?.password ?: "") }
    var domain by remember(initial) { mutableStateOf(initial?.domain ?: "") }
    var port by remember(initial) { mutableStateOf((initial?.port ?: 5060).toString()) }
    var transport by remember(initial) { mutableStateOf(initial?.transport ?: "UDP") }
    var proxy by remember(initial) { mutableStateOf(initial?.proxy ?: "") }
    var expiry by remember(initial) { mutableStateOf((initial?.registrationExpiry ?: 60).toString()) }
    var autoStart by remember(initial) { mutableStateOf(initial?.autoStart ?: true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val domainFocus = remember { FocusRequester() }
    val portFocus = remember { FocusRequester() }
    val displayNameFocus = remember { FocusRequester() }
    val authUsernameFocus = remember { FocusRequester() }
    val proxyFocus = remember { FocusRequester() }
    val expiryFocus = remember { FocusRequester() }

    val trimmedName = name.trim()
    val nameTaken = trimmedName.isNotEmpty() && existingAccounts.any {
        it.id != initial?.id && it.name.equals(trimmedName, ignoreCase = true)
    }
    val canSubmit = username.trim().isNotEmpty() && domain.trim().isNotEmpty() && !nameTaken
    val currentState = initial?.let { accountStates[it.id] } ?: SipRegistrationState.UNREGISTERED

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sip_account_editor_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (initial == null) "Add SIP Account" else "Edit SIP Account",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Account Label (e.g. Office)") },
                isError = nameTaken,
                supportingText = if (nameTaken) {
                    { Text("An account with this name already exists") }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { usernameFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
                    .bringIntoViewOnFocusWithIme()
                    .testTag("sip_form_name")
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("SIP Username / Extension *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(usernameFocus)
                    .bringIntoViewOnFocusWithIme()
                    .testTag("sip_form_username")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { domainFocus.requestFocus() }),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
                    .bringIntoViewOnFocusWithIme()
                    .testTag("sip_form_password")
            )

            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("SIP Domain / Server IP *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { portFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(domainFocus)
                    .bringIntoViewOnFocusWithIme()
                    .testTag("sip_form_domain")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (showAdvanced) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { displayNameFocus.requestFocus() },
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(portFocus)
                        .bringIntoViewOnFocusWithIme()
                        .testTag("sip_form_port")
                )
                Column(modifier = Modifier.weight(1.6f)) {
                    Text(
                        "Transport",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("UDP", "TCP", "TLS").forEach { tr ->
                            FilterChip(
                                selected = transport.equals(tr, ignoreCase = true),
                                onClick = {
                                    transport = tr
                                    if (tr == "TLS" && port == "5060") port = "5061"
                                    else if (tr != "TLS" && port == "5061") port = "5060"
                                },
                                label = { Text(tr) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 6.dp)
                    .testTag("sip_form_advanced_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Advanced Options",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Toggle advanced options",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name (Caller ID)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { authUsernameFocus.requestFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(displayNameFocus)
                            .bringIntoViewOnFocusWithIme()
                            .testTag("sip_form_display_name")
                    )
                    OutlinedTextField(
                        value = authUsername,
                        onValueChange = { authUsername = it },
                        label = { Text("Auth Username (if different)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { proxyFocus.requestFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(authUsernameFocus)
                            .bringIntoViewOnFocusWithIme()
                            .testTag("sip_form_auth_username")
                    )
                    OutlinedTextField(
                        value = proxy,
                        onValueChange = { proxy = it },
                        label = { Text("Outbound Proxy (Optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { expiryFocus.requestFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(proxyFocus)
                            .bringIntoViewOnFocusWithIme()
                            .testTag("sip_form_proxy")
                    )
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it.filter { c -> c.isDigit() } },
                        label = { Text("Registration Expiry (seconds)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(expiryFocus)
                            .bringIntoViewOnFocusWithIme()
                            .testTag("sip_form_expiry")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-register on Boot", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Reconnect automatically when phone restarts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onCancel != null) {
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onCancel()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("sip_form_cancel_button")
                    ) { Text("Cancel") }
                }
                if (initial != null && (currentState == SipRegistrationState.REGISTERED || currentState == SipRegistrationState.REGISTERING)) {
                    OutlinedButton(
                        onClick = { onUnregister(initial.id) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) { Text("Disconnect") }
                }
                Button(
                    enabled = canSubmit,
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val parsedPort = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 5060
                        val parsedExp = expiry.toIntOrNull()?.coerceIn(30, 3600) ?: 60
                        onSaveAndRegister(
                            SipAccountData(
                                id = initial?.id ?: "",
                                name = trimmedName.ifBlank { username.trim() },
                                username = username.trim(),
                                authUsername = authUsername.trim(),
                                password = password,
                                domain = domain.trim(),
                                port = parsedPort,
                                transport = transport,
                                proxy = proxy.trim(),
                                displayName = displayName.trim(),
                                registrationExpiry = parsedExp,
                                autoStart = autoStart,
                                enabled = true
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp)
                        .testTag("sip_form_save_button")
                ) { Text("Save & Register") }
            }
        }
    }
}
