package com.sipdial.app.util

import android.content.Context

object SecurityPrefs {
    private const val PREFS = "security_prefs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun verifyTls(context: Context): Boolean = prefs(context).getBoolean("verify_tls", true)
    fun setVerifyTls(context: Context, v: Boolean) = prefs(context).edit().putBoolean("verify_tls", v).apply()

    fun srtp(context: Context): Boolean = prefs(context).getBoolean("srtp", false)
    fun setSrtp(context: Context, v: Boolean) = prefs(context).edit().putBoolean("srtp", v).apply()
}
