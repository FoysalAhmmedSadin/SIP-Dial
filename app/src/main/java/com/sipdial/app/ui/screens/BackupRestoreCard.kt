package com.sipdial.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sipdial.app.R
import com.sipdial.app.data.BackupManager
import com.sipdial.app.data.BackupPassVault
import com.sipdial.app.ui.components.bringIntoViewOnFocusWithIme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { BackupPassVault.findActivity(context) }

    var options by remember { mutableStateOf(BackupManager.BackupOptions()) }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var saveWithBiometrics by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var vaultSaved by remember { mutableStateOf(BackupPassVault.hasSaved(context)) }
    val biometricsAvailable = remember(context) { BackupPassVault.available(context) }

    fun resetPassInputs() {
        passphrase = ""
        confirmPassphrase = ""
        showPass = false
        saveWithBiometrics = false
    }

    fun runExport(uri: Uri, pass: String, enrollBiometrics: Boolean) {
        busy = true
        scope.launch {
            try {
                val summary = context.contentResolver.openOutputStream(uri)?.use { out ->
                    BackupManager.export(context, pass.toCharArray(), out, options)
                } ?: throw IllegalStateException(context.getString(R.string.backup_open_dest_error))

                val msg = if (summary.settingsIncluded) {
                    context.getString(
                        R.string.backup_export_summary_settings,
                        summary.accounts,
                        summary.calls,
                        summary.contacts,
                        summary.favorites
                    )
                } else {
                    context.getString(
                        R.string.backup_export_summary,
                        summary.accounts,
                        summary.calls,
                        summary.contacts,
                        summary.favorites
                    )
                }

                if (enrollBiometrics && activity != null) {
                    BackupPassVault.save(activity, pass) { ok ->
                        vaultSaved = BackupPassVault.hasSaved(context)
                        resultTitle = context.getString(R.string.backup_created_title)
                        resultMessage = msg + if (ok) {
                            "\n\n" + context.getString(R.string.backup_biometric_saved_note)
                        } else {
                            ""
                        }
                    }
                } else {
                    resultTitle = context.getString(R.string.backup_created_title)
                    resultMessage = msg
                }
            } catch (e: Exception) {
                resultTitle = context.getString(R.string.backup_failed_title)
                resultMessage = e.message ?: context.getString(R.string.backup_failed_default)
            } finally {
                busy = false
                pendingExportUri = null
                resetPassInputs()
            }
        }
    }

    fun runRestore(uri: Uri, pass: String) {
        busy = true
        scope.launch {
            try {
                val summary = context.contentResolver.openInputStream(uri)?.use { input ->
                    BackupManager.restore(context, pass.toCharArray(), input)
                } ?: throw IllegalStateException(context.getString(R.string.backup_open_src_error))
                resultTitle = context.getString(R.string.backup_restore_complete_title)
                resultMessage = if (summary.settingsIncluded) {
                    context.getString(
                        R.string.backup_restore_summary_settings,
                        summary.accounts,
                        summary.calls,
                        summary.contacts,
                        summary.favorites
                    )
                } else {
                    context.getString(
                        R.string.backup_restore_summary,
                        summary.accounts,
                        summary.calls,
                        summary.contacts,
                        summary.favorites
                    )
                }
            } catch (_: BackupManager.WrongPassphraseException) {
                resultTitle = context.getString(R.string.backup_wrong_passphrase_title)
                resultMessage = context.getString(R.string.backup_wrong_passphrase_msg)
            } catch (e: Exception) {
                resultTitle = context.getString(R.string.backup_restore_failed_title)
                resultMessage = e.message ?: context.getString(R.string.backup_restore_failed_default)
            } finally {
                busy = false
                pendingRestoreUri = null
                resetPassInputs()
            }
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            resetPassInputs()
            pendingExportUri = uri
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            resetPassInputs()
            pendingRestoreUri = uri
        }
    }

    if (pendingExportUri != null) {
        val uri = pendingExportUri!!
        val mismatch = confirmPassphrase.isNotEmpty() && passphrase != confirmPassphrase
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val confirmFocus = remember { FocusRequester() }
        val selectedLabel = localizedSummaryLabel(options)
        AlertDialog(
            onDismissRequest = { if (!busy) { pendingExportUri = null; resetPassInputs() } },
            title = { Text(stringResource(R.string.backup_encrypt_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_encrypt_dialog_desc, selectedLabel),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.backup_passphrase_min6)) },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { confirmFocus.requestFocus() }),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    imageVector = if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (showPass) R.string.backup_hide_passphrase else R.string.backup_show_passphrase
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocusWithIme()
                            .testTag("backup_export_passphrase")
                    )
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it },
                        label = { Text(stringResource(R.string.backup_confirm_passphrase)) },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
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
                            .focusRequester(confirmFocus)
                            .bringIntoViewOnFocusWithIme()
                            .testTag("backup_export_confirm")
                    )
                    if (biometricsAvailable && activity != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = saveWithBiometrics,
                                onCheckedChange = { saveWithBiometrics = it }
                            )
                            Text(
                                text = stringResource(R.string.backup_save_fingerprint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (vaultSaved && biometricsAvailable && activity != null) {
                        OutlinedButton(
                            onClick = {
                                BackupPassVault.unlock(activity) { saved ->
                                    if (saved != null) runExport(uri, saved, false)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.backup_use_biometric))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { runExport(uri, passphrase, saveWithBiometrics) },
                    enabled = !busy && passphrase.length >= 6 && passphrase == confirmPassphrase
                ) {
                    Text(
                        stringResource(
                            if (busy) R.string.backup_encrypting else R.string.backup_export_action
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportUri = null; resetPassInputs() }, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingRestoreUri != null) {
        val uri = pendingRestoreUri!!
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { if (!busy) { pendingRestoreUri = null; resetPassInputs() } },
            title = { Text(stringResource(R.string.backup_decrypt_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_decrypt_dialog_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.backup_passphrase_label)) },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    imageVector = if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (showPass) R.string.backup_hide_passphrase else R.string.backup_show_passphrase
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocusWithIme()
                            .testTag("backup_restore_passphrase")
                    )
                    if (vaultSaved && biometricsAvailable && activity != null) {
                        OutlinedButton(
                            onClick = {
                                BackupPassVault.unlock(activity) { saved ->
                                    if (saved != null) runRestore(uri, saved)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.backup_unlock_fingerprint))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { runRestore(uri, passphrase) },
                    enabled = !busy && passphrase.isNotEmpty()
                ) {
                    Text(
                        stringResource(
                            if (busy) R.string.backup_restoring else R.string.backup_restore_action
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null; resetPassInputs() }, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (resultTitle != null) {
        AlertDialog(
            onDismissRequest = { resultTitle = null; resultMessage = null },
            title = { Text(resultTitle!!) },
            text = { Text(resultMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { resultTitle = null; resultMessage = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_local_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.backup_local_card_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BackupCategoryChips(
                options = options,
                onOptionsChange = { options = it }
            )

            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.backup_processing),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    createLauncher.launch("SipDial-backup-$stamp.sipbak")
                },
                enabled = !busy && options.hasAnySelection(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("export_backup_btn")
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_export_btn))
            }

            OutlinedButton(
                onClick = {
                    openLauncher.launch(arrayOf("*/*"))
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("import_backup_btn")
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_restore_btn))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupCategoryChips(
    options: BackupManager.BackupOptions,
    onOptionsChange: (BackupManager.BackupOptions) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = options.includeCalls,
            onClick = { onOptionsChange(options.copy(includeCalls = !options.includeCalls)) },
            label = { Text(stringResource(R.string.backup_chip_calls)) },
            modifier = Modifier.testTag("chip_backup_calls")
        )
        FilterChip(
            selected = options.includeAccounts,
            onClick = { onOptionsChange(options.copy(includeAccounts = !options.includeAccounts)) },
            label = { Text(stringResource(R.string.backup_chip_accounts)) },
            modifier = Modifier.testTag("chip_backup_accounts")
        )
        FilterChip(
            selected = options.includeContactsAndFavorites,
            onClick = {
                onOptionsChange(
                    options.copy(includeContactsAndFavorites = !options.includeContactsAndFavorites)
                )
            },
            label = { Text(stringResource(R.string.backup_chip_contacts)) },
            modifier = Modifier.testTag("chip_backup_contacts")
        )
        FilterChip(
            selected = options.includeSettings,
            onClick = { onOptionsChange(options.copy(includeSettings = !options.includeSettings)) },
            label = { Text(stringResource(R.string.backup_chip_settings)) },
            modifier = Modifier.testTag("chip_backup_settings")
        )
    }
}

@Composable
private fun localizedSummaryLabel(options: BackupManager.BackupOptions): String {
    val parts = mutableListOf<String>()
    if (options.includeCalls) parts += stringResource(R.string.backup_chip_calls)
    if (options.includeAccounts) parts += stringResource(R.string.backup_chip_accounts)
    if (options.includeContactsAndFavorites) parts += stringResource(R.string.backup_chip_contacts)
    if (options.includeSettings) parts += stringResource(R.string.backup_chip_settings)
    return if (parts.isEmpty()) stringResource(R.string.backup_chip_empty) else parts.joinToString(", ")
}
