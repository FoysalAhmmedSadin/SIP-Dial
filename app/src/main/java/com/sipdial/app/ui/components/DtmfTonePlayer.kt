package com.sipdial.app.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.sipdial.app.util.CallingPrefs

object DtmfTonePlayer {
    @Volatile
    private var generator: ToneGenerator? = null

    private fun toneForDigit(digit: String): Int? = when (digit.firstOrNull()) {
        '0' -> ToneGenerator.TONE_DTMF_0
        '1' -> ToneGenerator.TONE_DTMF_1
        '2' -> ToneGenerator.TONE_DTMF_2
        '3' -> ToneGenerator.TONE_DTMF_3
        '4' -> ToneGenerator.TONE_DTMF_4
        '5' -> ToneGenerator.TONE_DTMF_5
        '6' -> ToneGenerator.TONE_DTMF_6
        '7' -> ToneGenerator.TONE_DTMF_7
        '8' -> ToneGenerator.TONE_DTMF_8
        '9' -> ToneGenerator.TONE_DTMF_9
        '*' -> ToneGenerator.TONE_DTMF_S
        '#' -> ToneGenerator.TONE_DTMF_P
        else -> null
    }

    fun play(context: Context, digit: String) {
        if (!CallingPrefs.isDtmfTonesEnabled(context)) return
        val tone = toneForDigit(digit) ?: return
        try {
            val tg = generator ?: synchronized(this) {
                generator ?: ToneGenerator(AudioManager.STREAM_DTMF, 65).also { generator = it }
            }
            tg.startTone(tone, 90)
        } catch (_: Throwable) {
            // Audio hardware may be unavailable in headless tests or restricted modes.
        }
    }

    fun release() {
        synchronized(this) {
            try {
                generator?.release()
            } catch (_: Throwable) {
            }
            generator = null
        }
    }
}
