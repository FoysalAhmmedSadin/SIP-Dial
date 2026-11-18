@file:Suppress("DEPRECATION")

package com.sipdial.app.data.drive

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume

object GoogleDriveAuthManager {
    private const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    private const val OAUTH2_DRIVE_SCOPE = "oauth2:$SCOPE_DRIVE_APPDATA $SCOPE_DRIVE_FILE"

    val DRIVE_SCOPES: List<Scope> = listOf(
        Scope(SCOPE_DRIVE_APPDATA),
        Scope(SCOPE_DRIVE_FILE),
        Scope(SCOPE_USERINFO_EMAIL)
    )

    sealed class AuthOutcome {
        data class Authorized(val accessToken: String) : AuthOutcome()
        data class NeedsUserConsent(val intentSender: IntentSender) : AuthOutcome()
        data class Failed(
            val message: String,
            val isCancellation: Boolean = false,
            val canTryAccountFallback: Boolean = true
        ) : AuthOutcome()
    }

    sealed class FallbackAuthOutcome {
        data class Authorized(val accessToken: String, val email: String?) : FallbackAuthOutcome()
        data class NeedsConsentIntent(val intent: Intent) : FallbackAuthOutcome()
        data class Failed(val message: String, val isCancellation: Boolean = false) : FallbackAuthOutcome()
    }

    private tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }

    fun isPlayServicesAvailable(context: Context): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (_: Exception) {
        false
    }

    suspend fun requestAuthorization(context: Context): AuthOutcome {
        if (!isPlayServicesAvailable(context)) {
            return AuthOutcome.Failed(
                message = "Google Play Services is unavailable or outdated on this device.",
                isCancellation = false,
                canTryAccountFallback = false
            )
        }
        val host = findActivity(context) ?: context
        val client = Identity.getAuthorizationClient(host)
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(DRIVE_SCOPES)
            .build()

        return suspendCancellableCoroutine { cont ->
            client.authorize(request)
                .addOnSuccessListener { result ->
                    if (!cont.isActive) return@addOnSuccessListener
                    val token = result.accessToken
                    if (result.hasResolution()) {
                        val pi = result.pendingIntent
                        if (pi != null) {
                            cont.resume(AuthOutcome.NeedsUserConsent(pi.intentSender))
                        } else {
                            cont.resume(
                                AuthOutcome.Failed(
                                    message = "Google Drive consent prompt could not be opened.",
                                    canTryAccountFallback = true
                                )
                            )
                        }
                    } else if (!token.isNullOrBlank()) {
                        cont.resume(AuthOutcome.Authorized(token))
                    } else {
                        cont.resume(
                            AuthOutcome.Failed(
                                message = "No access token returned by Google Play Services.",
                                canTryAccountFallback = true
                            )
                        )
                    }
                }
                .addOnFailureListener { e ->
                    if (!cont.isActive) return@addOnFailureListener
                    cont.resume(
                        AuthOutcome.Failed(
                            message = describeAuthFailure(context, e),
                            isCancellation = isCancellationException(e),
                            canTryAccountFallback = true
                        )
                    )
                }
        }
    }

    fun extractTokenFromResult(context: Context, resultCode: Int, data: Intent?): AuthOutcome {
        val host = findActivity(context) ?: context
        if (data != null) {
            try {
                val authResult: AuthorizationResult =
                    Identity.getAuthorizationClient(host).getAuthorizationResultFromIntent(data)
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    return AuthOutcome.Authorized(token)
                }
                val pi = authResult.pendingIntent
                if (resultCode == Activity.RESULT_OK && pi != null) {
                    return AuthOutcome.NeedsUserConsent(pi.intentSender)
                }
            } catch (e: ApiException) {
                return AuthOutcome.Failed(
                    message = describeAuthFailure(context, e),
                    isCancellation = isCancellationException(e),
                    canTryAccountFallback = !isCancellationException(e)
                )
            } catch (e: Exception) {
                return AuthOutcome.Failed(
                    message = e.message ?: "Google Drive authorization failed.",
                    isCancellation = false,
                    canTryAccountFallback = true
                )
            }
        }
        return if (resultCode == Activity.RESULT_CANCELED) {
            AuthOutcome.Failed(
                message = "Google Play Services authorization could not complete (connection or consent cancelled).",
                isCancellation = true,
                canTryAccountFallback = true
            )
        } else {
            AuthOutcome.Failed(
                message = "Google Drive did not return an access token.",
                isCancellation = false,
                canTryAccountFallback = true
            )
        }
    }

    private fun buildGoogleSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_DRIVE_APPDATA), Scope(SCOPE_DRIVE_FILE))
            .build()

    fun buildFallbackSignInIntent(context: Context): Intent {
        val host = findActivity(context) ?: context
        return GoogleSignIn.getClient(host, buildGoogleSignInOptions()).signInIntent
    }

    fun buildAccountChooserIntent(context: Context): Intent {
        val currentEmail = DriveBackupPrefs.getAccountEmail(context)
        val selectedAccount = currentEmail?.takeIf { it.isNotBlank() }?.let { Account(it, "com.google") }
        return AccountManager.newChooseAccountIntent(
            selectedAccount,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )
    }

    suspend fun trySilentFallbackAccountToken(context: Context): FallbackAuthOutcome? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val storedEmail = DriveBackupPrefs.getAccountEmail(context)
        val androidAccount = account?.account
            ?: account?.email?.let { Account(it, "com.google") }
            ?: storedEmail?.takeIf { it.isNotBlank() }?.let { Account(it, "com.google") }
            ?: return null
        return fetchDriveTokenForAccount(context, androidAccount, account?.email ?: storedEmail)
    }

    suspend fun handleAccountChooserResult(
        context: Context,
        resultCode: Int,
        data: Intent?
    ): FallbackAuthOutcome {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return FallbackAuthOutcome.Failed(
                message = "Google account selection was cancelled.",
                isCancellation = true
            )
        }
        val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
        val accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)?.trim().ifNullOrBlank { "com.google" }
        if (accountName.isBlank()) {
            return FallbackAuthOutcome.Failed("No Google account was selected.")
        }
        return fetchDriveTokenForAccount(context, Account(accountName, accountType), accountName)
    }

    private inline fun String?.ifNullOrBlank(defaultValue: () -> String): String =
        if (this.isNullOrBlank()) defaultValue() else this

    suspend fun handleFallbackSignInResult(
        context: Context,
        resultCode: Int,
        data: Intent?
    ): FallbackAuthOutcome {
        if (data == null && resultCode == Activity.RESULT_CANCELED) {
            return FallbackAuthOutcome.Failed(
                message = "Google sign-in was cancelled before selecting an account.",
                isCancellation = true
            )
        }
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val androidAccount = account.account ?: account.email?.let { Account(it, "com.google") }
            if (androidAccount == null) {
                FallbackAuthOutcome.Failed("Selected Google account has no email address.")
            } else {
                fetchDriveTokenForAccount(context, androidAccount, account.email)
            }
        } catch (e: ApiException) {
            FallbackAuthOutcome.Failed(
                message = describeAuthFailure(context, e),
                isCancellation = isCancellationException(e)
            )
        } catch (e: Exception) {
            FallbackAuthOutcome.Failed(e.message ?: "Google Drive sign-in failed.")
        }
    }

    suspend fun retryLastFallbackAccountToken(context: Context): FallbackAuthOutcome {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val storedEmail = DriveBackupPrefs.getAccountEmail(context)
        val androidAccount = account?.account
            ?: account?.email?.let { Account(it, "com.google") }
            ?: storedEmail?.takeIf { it.isNotBlank() }?.let { Account(it, "com.google") }
            ?: return FallbackAuthOutcome.Failed("Google account session expired. Please reconnect.")
        return fetchDriveTokenForAccount(context, androidAccount, account?.email ?: storedEmail)
    }

    suspend fun fetchDriveTokenForAccount(
        context: Context,
        account: Account,
        emailHint: String? = null
    ): FallbackAuthOutcome = withContext(Dispatchers.IO) {
        try {
            val token = GoogleAuthUtil.getToken(context.applicationContext, account, OAUTH2_DRIVE_SCOPE)
            if (token.isBlank()) {
                FallbackAuthOutcome.Failed("Google returned an empty Drive access token.")
            } else {
                FallbackAuthOutcome.Authorized(token, emailHint ?: account.name)
            }
        } catch (e: UserRecoverableAuthException) {
            val intent = e.intent
            if (intent != null) {
                FallbackAuthOutcome.NeedsConsentIntent(intent)
            } else {
                FallbackAuthOutcome.Failed("Google Drive permission consent is required.")
            }
        } catch (e: IOException) {
            FallbackAuthOutcome.Failed("Network error while connecting to Google Drive: ${e.message ?: "Connection failed"}")
        } catch (e: GoogleAuthException) {
            FallbackAuthOutcome.Failed(
                "Google Drive OAuth connection failed (${e.message ?: "Auth error"}).\n\n" +
                    buildOAuthDiagnosticText(context)
            )
        } catch (e: Exception) {
            FallbackAuthOutcome.Failed(e.message ?: "Unable to obtain Google Drive token.")
        }
    }

    suspend fun invalidateToken(context: Context, token: String?) {
        if (token.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.clearToken(context.applicationContext, token)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun signOut(context: Context) {
        try {
            val client = Identity.getSignInClient(context)
            suspendCancellableCoroutine<Unit> { cont ->
                client.signOut()
                    .addOnCompleteListener {
                        if (cont.isActive) cont.resume(Unit)
                    }
            }
        } catch (_: Exception) {
        }
        try {
            val legacyClient = GoogleSignIn.getClient(context, buildGoogleSignInOptions())
            suspendCancellableCoroutine<Unit> { cont ->
                legacyClient.signOut()
                    .addOnCompleteListener {
                        if (cont.isActive) cont.resume(Unit)
                    }
            }
        } catch (_: Exception) {
        }
        DriveBackupPrefs.clearAccountState(context)
    }

    fun getAppSigningSha1(context: Context): String? = getAppSigningDigest(context, "SHA-1")

    fun getAppSigningSha256(context: Context): String? = getAppSigningDigest(context, "SHA-256")

    private fun getAppSigningDigest(context: Context, algorithm: String): String? {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val digest = MessageDigest.getInstance(algorithm).digest(certBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun buildOAuthDiagnosticText(context: Context): String {
        val sha1 = getAppSigningSha1(context)
        val sha256 = getAppSigningSha256(context)
        return buildString {
            append("Package: ").append(context.packageName)
            if (!sha1.isNullOrBlank()) append("\nSHA-1: ").append(sha1)
            if (!sha256.isNullOrBlank()) append("\nSHA-256: ").append(sha256)
        }
    }

    private fun isCancellationException(e: Exception): Boolean {
        val code = (e as? ApiException)?.statusCode ?: return false
        return code == CommonStatusCodes.CANCELED || code == 12501
    }

    internal fun describeStatusCode(packageName: String, sha1: String?, statusCode: Int, detail: String?): String {
        val shaHint = if (!sha1.isNullOrBlank()) "\nPackage: $packageName\nSHA-1: $sha1" else "\nPackage: $packageName"
        return when (statusCode) {
            10 -> "Google OAuth client is not registered for this Android app (DEVELOPER_ERROR 10). Register the Android OAuth Client ID & SHA-1 in Google Cloud Console.$shaHint"
            12500 -> "Google sign-in connection failed (12500). Verify Google Play Services, Google Cloud OAuth consent screen & Android client SHA-1.$shaHint"
            CommonStatusCodes.NETWORK_ERROR -> "Network error while contacting Google Play Services. Check your internet connection and retry."
            CommonStatusCodes.CANCELED, 12501 -> "Google Drive authorization was cancelled."
            12502 -> "Google sign-in is already in progress. Please wait and try again."
            else -> "Google authorization failed (code $statusCode): ${detail ?: "Connection failed"}$shaHint"
        }
    }

    private fun describeAuthFailure(context: Context, e: Exception): String {
        if (e is ApiException) {
            return describeStatusCode(
                packageName = context.packageName,
                sha1 = getAppSigningSha1(context),
                statusCode = e.statusCode,
                detail = e.localizedMessage
            )
        }
        return e.message ?: "Google Drive authorization failed."
    }
}
