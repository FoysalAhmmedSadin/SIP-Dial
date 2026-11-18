package com.sipdial.app.data.drive

import android.content.Context
import com.sipdial.app.data.BackupManager

object DriveBackupPrefs {
    private const val PREFS = "google_drive_backup_prefs"

    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_LAST_BACKUP_MS = "last_backup_ms"
    private const val KEY_LAST_BACKUP_SUMMARY = "last_backup_summary"
    private const val KEY_USE_APP_DATA = "use_app_data_folder"
    private const val KEY_INC_CALLS = "inc_calls"
    private const val KEY_INC_ACCOUNTS = "inc_accounts"
    private const val KEY_INC_CONTACTS = "inc_contacts"
    private const val KEY_INC_SETTINGS = "inc_settings"

    private fun p(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAccountEmail(context: Context): String? =
        p(context).getString(KEY_ACCOUNT_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun setAccountEmail(context: Context, email: String?) {
        p(context).edit().putString(KEY_ACCOUNT_EMAIL, email.orEmpty()).apply()
    }

    fun getLastBackupTimestamp(context: Context): Long =
        p(context).getLong(KEY_LAST_BACKUP_MS, 0L)

    fun getLastBackupSummary(context: Context): String =
        p(context).getString(KEY_LAST_BACKUP_SUMMARY, "").orEmpty()

    fun recordBackup(context: Context, timestampMs: Long, summaryText: String) {
        p(context).edit()
            .putLong(KEY_LAST_BACKUP_MS, timestampMs)
            .putString(KEY_LAST_BACKUP_SUMMARY, summaryText)
            .apply()
    }

    fun isUseAppDataFolder(context: Context): Boolean =
        p(context).getBoolean(KEY_USE_APP_DATA, false)

    fun setUseAppDataFolder(context: Context, appDataOnly: Boolean) {
        p(context).edit().putBoolean(KEY_USE_APP_DATA, appDataOnly).apply()
    }

    fun getBackupOptions(context: Context): BackupManager.BackupOptions {
        val prefs = p(context)
        return BackupManager.BackupOptions(
            includeCalls = prefs.getBoolean(KEY_INC_CALLS, true),
            includeAccounts = prefs.getBoolean(KEY_INC_ACCOUNTS, true),
            includeContactsAndFavorites = prefs.getBoolean(KEY_INC_CONTACTS, true),
            includeSettings = prefs.getBoolean(KEY_INC_SETTINGS, true)
        )
    }

    fun setBackupOptions(context: Context, options: BackupManager.BackupOptions) {
        p(context).edit()
            .putBoolean(KEY_INC_CALLS, options.includeCalls)
            .putBoolean(KEY_INC_ACCOUNTS, options.includeAccounts)
            .putBoolean(KEY_INC_CONTACTS, options.includeContactsAndFavorites)
            .putBoolean(KEY_INC_SETTINGS, options.includeSettings)
            .apply()
    }

    fun clearAccountState(context: Context) {
        p(context).edit()
            .remove(KEY_ACCOUNT_EMAIL)
            .apply()
    }
}
