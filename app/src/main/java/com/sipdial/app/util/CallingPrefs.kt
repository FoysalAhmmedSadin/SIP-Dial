package com.sipdial.app.util

import android.content.Context

object CallingPrefs {
    private const val PREFS = "calling_prefs"
    private const val KEY_CONFIRM_CALL = "confirm_before_call"
    private const val KEY_AUTO_ANSWER = "auto_answer_enabled"
    private const val KEY_AUTO_ANSWER_DELAY = "auto_answer_delay_sec"
    private const val KEY_DTMF_TONES = "dtmf_keypad_tones"
    private const val KEY_DTMF_MODE = "dtmf_mode"

    const val DTMF_AUTO = "AUTO"
    const val DTMF_RFC2833 = "RFC2833"
    const val DTMF_SIP_INFO = "SIP_INFO"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isCallConfirmationEnabled(c: Context): Boolean = p(c).getBoolean(KEY_CONFIRM_CALL, false)
    fun setCallConfirmationEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_CONFIRM_CALL, v).apply()

    fun isAutoAnswerEnabled(c: Context): Boolean = p(c).getBoolean(KEY_AUTO_ANSWER, false)
    fun setAutoAnswerEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_AUTO_ANSWER, v).apply()

    fun getAutoAnswerDelaySeconds(c: Context): Int = p(c).getInt(KEY_AUTO_ANSWER_DELAY, 2).coerceIn(0, 10)
    fun setAutoAnswerDelaySeconds(c: Context, sec: Int) =
        p(c).edit().putInt(KEY_AUTO_ANSWER_DELAY, sec.coerceIn(0, 10)).apply()

    fun isDtmfTonesEnabled(c: Context): Boolean = p(c).getBoolean(KEY_DTMF_TONES, true)
    fun setDtmfTonesEnabled(c: Context, v: Boolean) {
        p(c).edit().putBoolean(KEY_DTMF_TONES, v).apply()
        GeneralPrefs.syncSoundEffectsFromCallingPrefs(c, v)
    }

    fun getDtmfMode(c: Context): String = p(c).getString(KEY_DTMF_MODE, DTMF_AUTO) ?: DTMF_AUTO
    fun setDtmfMode(c: Context, mode: String) = p(c).edit().putString(KEY_DTMF_MODE, mode).apply()
}
