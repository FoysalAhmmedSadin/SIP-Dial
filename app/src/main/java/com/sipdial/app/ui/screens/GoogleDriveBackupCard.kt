package com.sipdial.app.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sipdial.app.data.BackupManager
import com.sipdial.app.data.BackupPassVault
import com.sipdial.app.data.drive.DriveBackupFile
import com.sipdial.app.data.drive.DriveBackupPrefs
import com.sipdial.app.data.drive.GoogleDriveAuthManager
import com.sipdial.app.data.drive.GoogleDriveBackupService
import com.sipdial.app.ui.components.bringIntoViewOnFocusWithIme
import com.sipdial.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DrivePendingAction {
    CONNECT_ONLY,
    BACKUP_NOW,
    REFRESH_LIST,
    RESTORE_FILE,
    DELETE_FILE
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleDriveBackupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current
    val activity = remember(context) { BackupPassVault.findActivity(context) }

    var accountEmail by remember { mutableStateOf(DriveBackupPrefs.getAccountEmail(context)) }
    var lastBackupMs by remember { mutableStateOf(DriveBackupPrefs.getLastBackupTimestamp(context)) }
    var lastBackupSummary by remember { mutableStateOf(DriveBackupPrefs.getLastBackupSummary(context)) }
    var useAppDataFolder by remember { mutableStateOf(DriveBackupPrefs.isUseAppDataFolder(context)) }
    var options by remember { mutableStateOf(DriveBackupPrefs.getBackupOptions(context)) }

    var cloudFiles by remember { mutableStateOf<List<DriveBackupFile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }

    var activeToken by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<DrivePendingAction?>(null) }
    var pendingFileTarget by remember { mutableStateOf<DriveBackupFile?>(null) }
    var triedFallbackSignIn by remember { mutableStateOf(false) }

    var showEncryptDialog by remember { mutableStateOf(false) }
    var restoreDialogFile by remember { mutableStateOf<DriveBackupFile?>(null) }
    var deleteConfirmFile by remember { mutableStateOf<DriveBackupFile?>(null) }

    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var saveWithBiometrics by remember { mutableStateOf(false) }

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

    suspend fun loadAccountAndFiles(token: String) {
        activeToken = token
        val email = GoogleDriveBackupService.fetchAccountEmail(token)
        if (!email.isNullOrBlank()) {
            accountEmail = email
            DriveBackupPrefs.setAccountEmail(context, email)
        }
        cloudFiles = GoogleDriveBackupService.listBackups(token)
    }

    fun executeWithToken(token: String, action: DrivePendingAction, fileTarget: DriveBackupFile?) {
        scope.launch {
            busy = true
            busyLabel = when (action) {
                DrivePendingAction.CONNECT_ONLY -> "Connecting to Google Drive…"
                DrivePendingAction.REFRESH_LIST -> "Loading backups from Google Drive…"
                DrivePendingAction.BACKUP_NOW -> "Preparing Google Drive backup…"
                DrivePendingAction.RESTORE_FILE -> "Preparing restore from Google Drive…"
                DrivePendingAction.DELETE_FILE -> "Removing backup from Google Drive…"
            }
            try {
                loadAccountAndFiles(token)
                when (action) {
                    DrivePendingAction.CONNECT_ONLY,
                    DrivePendingAction.REFRESH_LIST -> Unit
                    DrivePendingAction.BACKUP_NOW -> {
                        resetPassInputs()
                        showEncryptDialog = true
                    }
                    DrivePendingAction.RESTORE_FILE -> {
                        if (fileTarget != null) {
                            resetPassInputs()
                            restoreDialogFile = fileTarget
                        }
                    }
                    DrivePendingAction.DELETE_FILE -> {
                        if (fileTarget != null) {
                            GoogleDriveBackupService.deleteBackup(token, fileTarget.id)
                            cloudFiles = GoogleDriveBackupService.listBackups(token)
                            resultTitle = "Backup Deleted"
                            resultMessage = "Removed ${fileTarget.name} from Google Drive."
                        }
                    }
                }
            } catch (e: Exception) {
                if (e.message?.contains("401") == true) {
                    GoogleDriveAuthManager.invalidateToken(context, activeToken)
                    activeToken = null
                }
                resultTitle = "Google Drive Error"
                resultMessage = e.message ?: "Unable to communicate with Google Drive."
            } finally {
                busy = false
                busyLabel = ""
                pendingAction = null
                pendingFileTarget = null
            }
        }
    }

    val recoveryConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val action = pendingAction ?: DrivePendingAction.CONNECT_ONLY
        val target = pendingFileTarget
        if (activityResult.resultCode != android.app.Activity.RESULT_OK) {
            busy = false
            busyLabel = ""
            pendingAction = null
            pendingFileTarget = null
            resultTitle = "Authorization Cancelled"
            resultMessage = "Google Drive permission consent was declined."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            busyLabel = "Completing Google Drive authorization…"
            when (val outcome = GoogleDriveAuthManager.retryLastFallbackAccountToken(context)) {
                is GoogleDriveAuthManager.FallbackAuthOutcome.Authorized -> {
                    outcome.email?.let {
                        accountEmail = it
                        DriveBackupPrefs.setAccountEmail(context, it)
                    }
                    executeWithToken(outcome.accessToken, action, target)
                }
                is GoogleDriveAuthManager.FallbackAuthOutcome.NeedsConsentIntent -> {
                    busy = false
                    busyLabel = ""
                    pendingAction = null
                    pendingFileTarget = null
                    resultTitle = "Google Drive Connection Failed"
                    resultMessage = "Google Drive permission still requires approval."
                }
                is GoogleDriveAuthManager.FallbackAuthOutcome.Failed -> {
                    busy = false
                    busyLabel = ""
                    pendingAction = null
                    pendingFileTarget = null
                    resultTitle = if (outcome.isCancellation) "Authorization Cancelled" else "Google Drive Connection Failed"
                    resultMessage = outcome.message
                }
            }
        }
    }

    val accountChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val action = pendingAction ?: DrivePendingAction.CONNECT_ONLY
        val target = pendingFileTarget
        scope.launch {
            busy = true
            busyLabel = "Authorizing selected Google account…"
            when (val outcome = GoogleDriveAuthManager.handleAccountChooserResult(context, activityResult.resultCode, activityResult.data)) {
                is GoogleDriveAuthManager.FallbackAuthOutcome.Authorized -> {
                    outcome.email?.let {
                        accountEmail = it
                        DriveBackupPrefs.setAccountEmail(context, it)
                    }
                    executeWithToken(outcome.accessToken, action, target)
                }
                is GoogleDriveAuthManager.FallbackAuthOutcome.NeedsConsentIntent -> {
                    outcome.intent.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)?.let {
                        accountEmail = it
                        DriveBackupPrefs.setAccountEmail(context, it)
                    }
                    busy = false
                    busyLabel = ""
                    recoveryConsentLauncher.launch(outcome.intent)
                }
                is GoogleDriveAuthManager.FallbackAuthOutcome.Failed -> {
                    busy = false
                    busyLabel = ""
                    pendingAction = null
                    pendingFileTarget = null
                    resultTitle = if (outcome.isCancellation) "Authorization Cancelled" else "Google Drive Connection Failed"
                    resultMessage = outcome.message
                }
            }
        }
    }

    val fallbackSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val action = pendingAction ?: DrivePendingAction.CONNECT_ONLY
        val target = pendingFileTarget
        scope.launch {
            busy = true
            busyLabel = "Authorizing Google Drive account…"
            when (val outcome = GoogleDriveAuthManager.handleFallbackSignInResult(context, activityResult.resultCode, activityResult.data)) {
                is GoogleDriveAuthManager.FallbackAuthOutcome.Authorized -> {
                    outcome.email?.let {
                        accountEmail = it
                        DriveBackupPrefs.setAccountEmail(context, it)
                    }
                    executeWithToken(outcome.accessToken, action, target)
                }
                is GoogleDriveAuthManager.FallbackAuthOutcome.NeedsConsentIntent -> {
                    busy = false
                    busyLabel = ""
                    recoveryConsentLauncher.launch(outcome.intent)
                }
                is GoogleDriveAuthManager.FallbackAuthOutcome.Failed -> {
                    if (!outcome.isCancellation) {
                        busy = false
                        busyLabel = ""
                        try {
                            accountChooserLauncher.launch(GoogleDriveAuthManager.buildAccountChooserIntent(context))
                            return@launch
                        } catch (_: Exception) {
                        }
                    }
                    busy = false
                    busyLabel = ""
                    pendingAction = null
                    pendingFileTarget = null
                    resultTitle = if (outcome.isCancellation) "Authorization Cancelled" else "Google Drive Connection Failed"
                    resultMessage = outcome.message
                }
            }
        }
    }

    fun launchAccountFallback(action: DrivePendingAction, fileTarget: DriveBackupFile?) {
        scope.launch {
            busy = true
            busyLabel = "Connecting Google account…"
            val silent = GoogleDriveAuthManager.trySilentFallbackAccountToken(context)
            when (silent) {
                is GoogleDriveAuthManager.FallbackAuthOutcome.Authorized -> {
                    silent.email?.let {
                        accountEmail = it
                        DriveBackupPrefs.setAccountEmail(context, it)
                    }
                    executeWithToken(silent.accessToken, action, fileTarget)
                }
                is GoogleDriveAuthManager.FallbackAuthOutcome.NeedsConsentIntent -> {
                    busy = false
                    busyLabel = ""
                    recoveryConsentLauncher.launch(silent.intent)
                }
                else -> {
                    busy = false
                    busyLabel = ""
                    fallbackSignInLauncher.launch(GoogleDriveAuthManager.buildFallbackSignInIntent(context))
                }
            }
        }
    }

    lateinit var consentLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
    consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val action = pendingAction ?: DrivePendingAction.CONNECT_ONLY
        val target = pendingFileTarget
        when (val outcome = GoogleDriveAuthManager.extractTokenFromResult(context, activityResult.resultCode, activityResult.data)) {
            is GoogleDriveAuthManager.AuthOutcome.Authorized -> {
                executeWithToken(outcome.accessToken, action, target)
            }
            is GoogleDriveAuthManager.AuthOutcome.NeedsUserConsent -> {
                consentLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
            }
            is GoogleDriveAuthManager.AuthOutcome.Failed -> {
                if (outcome.canTryAccountFallback && !triedFallbackSignIn) {
                    triedFallbackSignIn = true
                    launchAccountFallback(action, target)
                } else {
                    busy = false
                    busyLabel = ""
                    pendingAction = null
                    pendingFileTarget = null
                    resultTitle = if (outcome.isCancellation) "Authorization Cancelled" else "Google Drive Connection Failed"
                    resultMessage = outcome.message
                }
            }
        }
    }

    fun startAuthorizedAction(action: DrivePendingAction, fileTarget: DriveBackupFile? = null) {
        pendingAction = action
        pendingFileTarget = fileTarget
        triedFallbackSignIn = false
        scope.launch {
            busy = true
            busyLabel = "Authorizing Google Drive…"
            when (val outcome = GoogleDriveAuthManager.requestAuthorization(context)) {
                is GoogleDriveAuthManager.AuthOutcome.Authorized -> {
                    executeWithToken(outcome.accessToken, action, fileTarget)
                }
                is GoogleDriveAuthManager.AuthOutcome.NeedsUserConsent -> {
                    busy = false
                    busyLabel = ""
                    consentLauncher.launch(
                        IntentSenderRequest.Builder(outcome.intentSender).build()
                    )
                }
                is GoogleDriveAuthManager.AuthOutcome.Failed -> {
                    if (outcome.canTryAccountFallback && !triedFallbackSignIn) {
                        triedFallbackSignIn = true
                        launchAccountFallback(action, fileTarget)
                    } else {
                        busy = false
                        busyLabel = ""
                        pendingAction = null
                        pendingFileTarget = null
                        resultTitle = if (outcome.isCancellation) "Authorization Cancelled" else "Google Drive Connection Failed"
                        resultMessage = outcome.message
                    }
                }
            }
        }
    }

    fun performUpload(pass: String, enrollBiometrics: Boolean) {
        val token = activeToken
        if (token.isNullOrBlank()) {
            showEncryptDialog = false
            startAuthorizedAction(DrivePendingAction.BACKUP_NOW)
            return
        }
        showEncryptDialog = false
        scope.launch {
            busy = true
            busyLabel = "Encrypting & uploading backup to Google Drive…"
            try {
                val (bytes, summary) = BackupManager.exportToBytes(
                    context = context,
                    passphrase = pass.toCharArray(),
                    options = options
                )
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val filename = "SipDial-drive-$stamp.sipbak"
                val desc = "${options.summaryLabel()} • ${summary.calls} calls, ${summary.accounts} accounts, ${summary.contacts} contacts, ${summary.favorites} favorites"
                GoogleDriveBackupService.uploadBackup(
                    accessToken = token,
                    filename = filename,
                    bytes = bytes,
                    useAppDataFolder = useAppDataFolder,
                    description = desc
                )
                val now = System.currentTimeMillis()
                lastBackupMs = now
                lastBackupSummary = desc
                DriveBackupPrefs.recordBackup(context, now, desc)
                cloudFiles = GoogleDriveBackupService.listBackups(token)

                val summaryMessage = "Uploaded $filename to Google Drive.\n" +
                    "Calls: ${summary.calls} • SIP Accounts: ${summary.accounts} • Contacts: ${summary.contacts} • Favorites: ${summary.favorites}" +
                    if (summary.settingsIncluded) " • App Settings included" else ""

                if (enrollBiometrics && activity != null) {
                    BackupPassVault.save(activity, pass) { ok ->
                        vaultSaved = BackupPassVault.hasSaved(context)
                        resultTitle = "Google Drive Backup Complete"
                        resultMessage = summaryMessage + if (ok) "\n\nPassphrase saved to biometric vault." else ""
                    }
                } else {
                    resultTitle = "Google Drive Backup Complete"
                    resultMessage = summaryMessage
                }
            } catch (e: Exception) {
                resultTitle = "Upload Failed"
                resultMessage = e.message ?: "Unable to upload backup to Google Drive."
            } finally {
                busy = false
                busyLabel = ""
                resetPassInputs()
            }
        }
    }

    fun performRestore(file: DriveBackupFile, pass: String) {
        val token = activeToken
        if (token.isNullOrBlank()) {
            restoreDialogFile = null
            startAuthorizedAction(DrivePendingAction.RESTORE_FILE, file)
            return
        }
        restoreDialogFile = null
        scope.launch {
            busy = true
            busyLabel = "Downloading & decrypting ${file.name}…"
            try {
                val bytes = GoogleDriveBackupService.downloadBackup(token, file.id)
                val summary = BackupManager.restoreFromBytes(context, pass.toCharArray(), bytes)
                resultTitle = "Restore Complete"
                resultMessage = "Restored from ${file.name}:\n" +
                    "Calls: ${summary.calls} • SIP Accounts: ${summary.accounts} • Contacts: ${summary.contacts} • Favorites: ${summary.favorites}" +
                    if (summary.settingsIncluded) " • App Settings applied" else ""
            } catch (e: BackupManager.WrongPassphraseException) {
                resultTitle = "Wrong Passphrase"
                resultMessage = e.message
            } catch (e: Exception) {
                resultTitle = "Restore Failed"
                resultMessage = e.message ?: "Unable to restore backup from Google Drive."
            } finally {
                busy = false
                busyLabel = ""
                resetPassInputs()
            }
        }
    }

    // Encryption passphrase dialog before uploading to Google Drive
    if (showEncryptDialog) {
        val mismatch = confirmPassphrase.isNotEmpty() && passphrase != confirmPassphrase
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val confirmFocus = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { if (!busy) { showEncryptDialog = false; resetPassInputs() } },
            title = { Text("Encrypt Google Drive Backup") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your backup is encrypted on-device before uploading to Google Drive. Keep your passphrase safe — you will need it to restore on another phone.",
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
                            .testTag("drive_export_passphrase")
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
                            .testTag("drive_export_confirm")
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
                                    if (saved != null) performUpload(saved, false)
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
                    onClick = { performUpload(passphrase, saveWithBiometrics) },
                    enabled = !busy && passphrase.length >= 6 && passphrase == confirmPassphrase
                ) {
                    Text("Encrypt & Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptDialog = false; resetPassInputs() }) { Text("Cancel") }
            }
        )
    }

    // Passphrase dialog before restoring a Drive backup
    restoreDialogFile?.let { file ->
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { if (!busy) { restoreDialogFile = null; resetPassInputs() } },
            title = { Text("Restore ${file.name}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Enter the encryption passphrase used to protect this Google Drive backup.",
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
                            .testTag("drive_restore_passphrase")
                    )
                    if (vaultSaved && biometricsAvailable && activity != null) {
                        OutlinedButton(
                            onClick = {
                                BackupPassVault.unlock(activity) { saved ->
                                    if (saved != null) performRestore(file, saved)
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
                    onClick = { performRestore(file, passphrase) },
                    enabled = !busy && passphrase.isNotEmpty()
                ) {
                    Text("Download & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreDialogFile = null; resetPassInputs() }) { Text("Cancel") }
            }
        )
    }

    deleteConfirmFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFile = null },
            title = { Text("Delete Drive Backup?") },
            text = { Text("Permanently remove ${file.name} from your Google Drive?") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = file
                        deleteConfirmFile = null
                        val token = activeToken
                        if (token != null) {
                            executeWithToken(token, DrivePendingAction.DELETE_FILE, target)
                        } else {
                            startAuthorizedAction(DrivePendingAction.DELETE_FILE, target)
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFile = null }) { Text("Cancel") }
            }
        )
    }

    if (resultTitle != null) {
        val msg = resultMessage.orEmpty()
        val hasOAuthDiagnostics = msg.contains("SHA-1", ignoreCase = true) ||
            msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
            msg.contains("OAuth", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { resultTitle = null; resultMessage = null },
            title = { Text(resultTitle!!) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { resultTitle = null; resultMessage = null }) { Text("OK") }
            },
            dismissButton = if (hasOAuthDiagnostics) {
                {
                    TextButton(
                        onClick = {
                            val diag = GoogleDriveAuthManager.buildOAuthDiagnosticText(context)
                            clipboard.setText(AnnotatedString(diag))
                            android.widget.Toast.makeText(
                                context,
                                "OAuth diagnostics copied",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy SHA")
                    }
                }
            } else null
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (accountEmail != null) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                        contentDescription = null,
                        tint = if (accountEmail != null) semantic.success else MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Google Drive Cloud Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = accountEmail ?: "Not connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (accountEmail != null) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (accountEmail != null) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                GoogleDriveAuthManager.signOut(context)
                                accountEmail = null
                                activeToken = null
                                cloudFiles = emptyList()
                            }
                        },
                        modifier = Modifier.testTag("drive_disconnect_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Disconnect Google Drive")
                    }
                }
            }

            if (lastBackupMs > 0L) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val timeText = remember(lastBackupMs) {
                            SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.US).format(Date(lastBackupMs))
                        }
                        Text(
                            text = "Last backup: $timeText",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (lastBackupSummary.isNotBlank()) {
                            Text(
                                text = lastBackupSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(
                text = "What to include in backup",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            DriveBackupCategoryChips(
                options = options,
                onOptionsChange = { updated ->
                    options = updated
                    DriveBackupPrefs.setBackupOptions(context, updated)
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Private App Data Folder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (useAppDataFolder) "Hidden inside Google Drive App Data" else "Saved to \"SIP Dial Backups\" in My Drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useAppDataFolder,
                    onCheckedChange = {
                        useAppDataFolder = it
                        DriveBackupPrefs.setUseAppDataFolder(context, it)
                    }
                )
            }

            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = busyLabel.ifBlank { "Syncing with Google Drive…" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { startAuthorizedAction(DrivePendingAction.BACKUP_NOW) },
                    enabled = !busy && options.hasAnySelection(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("drive_backup_now_btn")
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back Up Now")
                }

                OutlinedButton(
                    onClick = { startAuthorizedAction(DrivePendingAction.REFRESH_LIST) },
                    enabled = !busy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("drive_sync_list_btn")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Load Drive backups")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (accountEmail == null) "Connect" else "Refresh")
                }
            }

            if (cloudFiles.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "Backups on Google Drive (${cloudFiles.size})",
                    style = MaterialTheme.typography.labelLarge
                )
                cloudFiles.forEach { file ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        text = if (file.isAppData) "App Data" else "My Drive",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                val sizeStr = Formatter.formatShortFileSize(context, file.sizeBytes)
                                val dateStr = file.modifiedTime.take(16).replace('T', ' ')
                                Text(
                                    text = "$sizeStr • $dateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (file.description.isNotBlank()) {
                                    Text(
                                        text = file.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    resetPassInputs()
                                    restoreDialogFile = file
                                },
                                modifier = Modifier.testTag("drive_restore_btn_${file.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "Restore ${file.name}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { deleteConfirmFile = file },
                                modifier = Modifier.testTag("drive_delete_btn_${file.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete ${file.name}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DriveBackupCategoryChips(
    options: BackupManager.BackupOptions,
    onOptionsChange: (BackupManager.BackupOptions) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilterChip(
            selected = options.includeCalls,
            onClick = { onOptionsChange(options.copy(includeCalls = !options.includeCalls)) },
            label = { Text("Call History") },
            modifier = Modifier.testTag("chip_backup_calls")
        )
        FilterChip(
            selected = options.includeAccounts,
            onClick = { onOptionsChange(options.copy(includeAccounts = !options.includeAccounts)) },
            label = { Text("SIP Accounts") },
            modifier = Modifier.testTag("chip_backup_accounts")
        )
        FilterChip(
            selected = options.includeContactsAndFavorites,
            onClick = {
                onOptionsChange(
                    options.copy(includeContactsAndFavorites = !options.includeContactsAndFavorites)
                )
            },
            label = { Text("Contacts & Favorites") },
            modifier = Modifier.testTag("chip_backup_contacts")
        )
        FilterChip(
            selected = options.includeSettings,
            onClick = { onOptionsChange(options.copy(includeSettings = !options.includeSettings)) },
            label = { Text("App Settings") },
            modifier = Modifier.testTag("chip_backup_settings")
        )
    }
}
