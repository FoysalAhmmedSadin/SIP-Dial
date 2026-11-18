package com.sipdial.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
                } ?: throw IllegalStateException("Could not open backup destination")

                val msg = "Exported ${summary.accounts} accounts, ${summary.calls} calls, " +
                    "${summary.contacts} saved contacts, and ${summary.favorites} favorites" +
                    if (summary.settingsIncluded) " (including app settings)." else "."

                if (enrollBiometrics && activity != null) {
                    BackupPassVault.save(activity, pass) { ok ->
                        vaultSaved = BackupPassVault.hasSaved(context)
                        resultTitle = "Backup Created"
                        resultMessage = msg + if (ok) "\n\nPassphrase saved to biometric vault." else ""
                    }
                } else {
                    resultTitle = "Backup Created"
                    resultMessage = msg
                }
            } catch (e: Exception) {
                resultTitle = "Backup Failed"
                resultMessage = e.message ?: "Unable to write encrypted backup file."
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
                } ?: throw IllegalStateException("Could not read backup file")
                resultTitle = "Restore Complete"
                resultMessage = "Imported ${summary.accounts} accounts, ${summary.calls} calls, " +
                    "${summary.contacts} saved contacts, and ${summary.favorites} favorites" +
                    if (summary.settingsIncluded) " (and app settings)." else "."
            } catch (e: BackupManager.WrongPassphraseException) {
                resultTitle = "Wrong Passphrase"
                resultMessage = e.message
            } catch (e: Exception) {
                resultTitle = "Restore Failed"
                resultMessage = e.message ?: "Unable to restore from the selected file."
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
        AlertDialog(
            onDismissRequest = { if (!busy) { pendingExportUri = null; resetPassInputs() } },
            title = { Text("Encrypt Local Backup") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose a strong passphrase to encrypt your selected data (${options.summaryLabel()}).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase (min 6 chars)") },
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
                                    contentDescription = if (showPass) "Hide passphrase" else "Show passphrase"
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
                        label = { Text("Confirm Passphrase") },
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
                            Text("Save passphrase with fingerprint", style = MaterialTheme.typography.bodySmall)
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
                            Text("Use Saved Biometric Passphrase")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { runExport(uri, passphrase, saveWithBiometrics) },
                    enabled = !busy && passphrase.length >= 6 && passphrase == confirmPassphrase
                ) {
                    Text(if (busy) "Encrypting…" else "Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportUri = null; resetPassInputs() }, enabled = !busy) {
                    Text("Cancel")
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
            title = { Text("Decrypt & Restore Backup") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Enter the passphrase used when creating this .sipbak file.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Backup Passphrase") },
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
                                    contentDescription = if (showPass) "Hide passphrase" else "Show passphrase"
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
                            Text("Unlock with Fingerprint")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { runRestore(uri, passphrase) },
                    enabled = !busy && passphrase.isNotEmpty()
                ) {
                    Text(if (busy) "Restoring…" else "Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null; resetPassInputs() }, enabled = !busy) {
                    Text("Cancel")
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
                TextButton(onClick = { resultTitle = null; resultMessage = null }) { Text("OK") }
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
                text = "Local Encrypted File (.sipbak)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Export or restore offline with PBKDF2-HMAC-SHA256 + AES-256-GCM encryption for call history, SIP accounts, contacts, favorites, and settings.",
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
                    Text("Processing encrypted backup…", style = MaterialTheme.typography.bodySmall)
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
                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Local Backup File")
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
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore From Local File")
            }
        }
    }
}
