package com.sipdial.app.util

import android.content.Context

object PowerPrefs {
    const val RELIABLE = "reliable"
    const val BALANCED = "balanced"
    private const val PREFS = "power_prefs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): String {
        val p = prefs(context)
        return when (val m = p.getString("mode", BALANCED)) {
            RELIABLE, BALANCED -> m
            else -> {
                p.edit().putString("mode", BALANCED).apply()
                BALANCED
            }
        }
    }

    fun setMode(context: Context, mode: String) = prefs(context).edit().putString("mode", mode).apply()
}
