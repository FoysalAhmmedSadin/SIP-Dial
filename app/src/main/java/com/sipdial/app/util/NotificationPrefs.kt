package com.sipdial.app.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object NotificationPrefs {
    private const val PREFS = "notification_prefs"
    private const val KEY_RINGTONE_URI = "ringtone_uri"
    private const val KEY_VIBRATE = "vibrate_incoming"
    private const val KEY_INCOMING_BANNER = "incoming_banner_enabled"
    private const val KEY_REG_ALERTS = "registration_alerts_enabled"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getRingtoneUri(c: Context): Uri? {
        val raw = p(c).getString(KEY_RINGTONE_URI, null)
        return if (raw.isNullOrBlank()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } else {
            try {
                Uri.parse(raw)
            } catch (_: Exception) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
        }
    }

    fun getRingtoneTitle(c: Context): String {
        return try {
            val uri = getRingtoneUri(c) ?: return "Silent"
            RingtoneManager.getRingtone(c, uri)?.getTitle(c) ?: "Default ringtone"
        } catch (_: Exception) {
            "Default ringtone"
        }
    }

    fun setRingtoneUri(c: Context, uri: Uri?) {
        p(c).edit().putString(KEY_RINGTONE_URI, uri?.toString().orEmpty()).apply()
    }

    fun isVibrateEnabled(c: Context): Boolean = p(c).getBoolean(KEY_VIBRATE, true)
    fun setVibrateEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_VIBRATE, v).apply()

    fun isIncomingBannerEnabled(c: Context): Boolean = p(c).getBoolean(KEY_INCOMING_BANNER, true)
    fun setIncomingBannerEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_INCOMING_BANNER, v).apply()

    fun isRegistrationAlertsEnabled(c: Context): Boolean = p(c).getBoolean(KEY_REG_ALERTS, true)
    fun setRegistrationAlertsEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_REG_ALERTS, v).apply()
}
