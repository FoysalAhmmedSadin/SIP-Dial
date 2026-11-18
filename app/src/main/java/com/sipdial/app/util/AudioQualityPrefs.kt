package com.sipdial.app.util

import android.content.Context

object AudioQualityPrefs {
    private const val PREFS_NAME = "audio_quality_prefs"
    private const val KEY_HD_CODECS = "hd_codecs_enabled"
    private const val KEY_MIC_BOOST_DB = "mic_boost_db"
    private const val KEY_CODEC_CONFIG = "codec_config"

    const val DEFAULT_MIC_BOOST_DB = 10
    const val MAX_MIC_BOOST_DB = 20

    fun isHdCodecsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HD_CODECS, true)

    fun setHdCodecsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HD_CODECS, enabled).apply()
    }

    fun getMicBoostDb(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MIC_BOOST_DB, DEFAULT_MIC_BOOST_DB).coerceIn(0, MAX_MIC_BOOST_DB)

    fun setMicBoostDb(context: Context, db: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MIC_BOOST_DB, db.coerceIn(0, MAX_MIC_BOOST_DB)).apply()
    }

    data class CodecEntry(val key: String, val enabled: Boolean)

    data class AudioCodecDescriptor(
        val mimeType: String,
        val clockRate: Int,
        val isUsable: Boolean = true
    ) {
        val key: String get() = codecKey(mimeType, clockRate)
    }

    data class PlannedAudioCodec(
        val descriptor: AudioCodecDescriptor,
        val enabled: Boolean
    ) {
        val key: String get() = descriptor.key
    }

    fun codecKey(mimeType: String, clockRate: Int): String =
        mimeType.trim().uppercase() + "/" + clockRate

    fun planAudioCodecs(
        available: List<AudioCodecDescriptor>,
        hdEnabled: Boolean,
        customConfig: List<CodecEntry>?
    ): List<PlannedAudioCodec> {
        if (available.isEmpty()) return emptyList()

        if (!customConfig.isNullOrEmpty()) {
            val byKey = LinkedHashMap<String, AudioCodecDescriptor>(available.size)
            available.forEach { byKey.putIfAbsent(it.key, it) }

            val ordered = ArrayList<PlannedAudioCodec>(available.size)
            val addedKeys = HashSet<String>()
            customConfig.forEach { entry ->
                val normalizedKey = entry.key.trim().uppercase()
                val desc = byKey[normalizedKey] ?: return@forEach
                if (addedKeys.add(desc.key)) {
                    ordered += PlannedAudioCodec(desc, enabled = entry.enabled && desc.isUsable)
                }
            }
            available.forEach { desc ->
                if (addedKeys.add(desc.key)) {
                    ordered += PlannedAudioCodec(desc, enabled = false)
                }
            }
            if (ordered.none { it.enabled }) {
                val firstUsableIdx = ordered.indexOfFirst { it.descriptor.isUsable }
                if (firstUsableIdx >= 0) {
                    ordered[firstUsableIdx] = ordered[firstUsableIdx].copy(enabled = true)
                }
            }
            return ordered
        }

        fun defaultRank(desc: AudioCodecDescriptor): Int {
            val mime = desc.mimeType.trim().uppercase()
            return if (hdEnabled) {
                when (mime) {
                    "OPUS" -> if (desc.clockRate == 48000) 0 else 1
                    "G722" -> 2
                    "AMR-WB" -> 3
                    "PCMU" -> 4
                    "PCMA" -> 5
                    "AMR" -> 6
                    "GSM" -> 7
                    else -> 50
                }
            } else {
                when (mime) {
                    "PCMU" -> 0
                    "PCMA" -> 1
                    "AMR" -> 2
                    "GSM" -> 3
                    "OPUS" -> 10
                    "G722" -> 11
                    "AMR-WB" -> 12
                    else -> 50
                }
            }
        }

        fun shouldEnableByDefault(desc: AudioCodecDescriptor): Boolean {
            if (!desc.isUsable) return false
            val mime = desc.mimeType.trim().uppercase()
            return when (mime) {
                "OPUS", "G722", "AMR-WB" -> hdEnabled
                "PCMU", "PCMA", "AMR", "GSM" -> true
                else -> false
            }
        }

        return available
            .sortedWith(
                compareBy<AudioCodecDescriptor> { !it.isUsable }
                    .thenBy { defaultRank(it) }
                    .thenByDescending { it.clockRate }
            )
            .map { desc ->
                PlannedAudioCodec(desc, enabled = shouldEnableByDefault(desc))
            }
    }

    fun getCodecConfig(context: Context): List<CodecEntry>? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CODEC_CONFIG, null) ?: return null
        val list = raw.split(",").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2 && parts[0].isNotBlank()) CodecEntry(parts[0], parts[1] == "1") else null
        }
        return list.ifEmpty { null }
    }

    fun setCodecConfig(context: Context, list: List<CodecEntry>?) {
        val e = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (list == null) e.remove(KEY_CODEC_CONFIG)
        else e.putString(KEY_CODEC_CONFIG, list.joinToString(",") { it.key + ":" + (if (it.enabled) "1" else "0") })
        e.apply()
    }
}
