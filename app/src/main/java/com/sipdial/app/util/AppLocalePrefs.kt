package com.sipdial.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.sipdial.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SupportedLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String,
    val isRtl: Boolean = false
)

object AppLocalePrefs {
    private const val PREFS_NAME = "app_locale_prefs"
    private const val KEY_SELECTED_LOCALE = "selected_locale_tag"
    const val DEFAULT_LOCALE_TAG = "en-US"

    val supportedLanguages: List<SupportedLanguage> = listOf(
        SupportedLanguage("en-US", "English (United States)", "English (United States)"),
        SupportedLanguage("zh-CN", "简体中文", "Chinese (Simplified)"),
        SupportedLanguage("zh-TW", "繁體中文", "Chinese (Traditional)"),
        SupportedLanguage("zh-HK", "繁體中文（香港）", "Chinese (Traditional, Hong Kong)"),
        SupportedLanguage("ar", "العربية", "Arabic", isRtl = true),
        SupportedLanguage("bn", "বাংলা", "Bengali"),
        SupportedLanguage("de", "Deutsch", "German"),
        SupportedLanguage("es", "Español", "Spanish"),
        SupportedLanguage("fa", "فارسی", "Persian", isRtl = true),
        SupportedLanguage("fil", "Filipino", "Filipino"),
        SupportedLanguage("fr", "Français", "French"),
        SupportedLanguage("hi", "हिन्दी", "Hindi"),
        SupportedLanguage("id", "Bahasa Indonesia", "Indonesian"),
        SupportedLanguage("it", "Italiano", "Italian"),
        SupportedLanguage("ja", "日本語", "Japanese"),
        SupportedLanguage("ko", "한국어", "Korean"),
        SupportedLanguage("ms", "Bahasa Melayu", "Malay"),
        SupportedLanguage("nl", "Nederlands", "Dutch"),
        SupportedLanguage("pl", "Polski", "Polish"),
        SupportedLanguage("pt", "Português", "Portuguese"),
        SupportedLanguage("ru", "Русский", "Russian"),
        SupportedLanguage("sw", "Kiswahili", "Swahili"),
        SupportedLanguage("th", "ไทย", "Thai"),
        SupportedLanguage("tr", "Türkçe", "Turkish"),
        SupportedLanguage("uk", "Українська", "Ukrainian"),
        SupportedLanguage("ur", "اردو", "Urdu", isRtl = true),
        SupportedLanguage("vi", "Tiếng Việt", "Vietnamese")
    )

    private val byTag: Map<String, SupportedLanguage> = supportedLanguages.associateBy {
        it.tag.lowercase(Locale.ROOT)
    }

    private val _localeTag = MutableStateFlow(DEFAULT_LOCALE_TAG)
    val localeTag: StateFlow<String> = _localeTag.asStateFlow()

    @Volatile
    private var initialized = false

    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Loads the persisted language preference, defaulting strictly to en-US on fresh install.
     * Never auto-selects non-English locales from the device's OS locale.
     */
    fun init(context: Context): String {
        val saved = prefs(context).getString(KEY_SELECTED_LOCALE, null)
        val resolved = resolveTag(saved)
        if (_localeTag.value != resolved) {
            _localeTag.value = resolved
        }
        applyDefaultLocale(resolved)
        initialized = true
        return resolved
    }

    fun getLocaleTag(context: Context): String {
        val saved = prefs(context).getString(KEY_SELECTED_LOCALE, null)
        return resolveTag(saved)
    }

    fun setLocaleTag(context: Context, tag: String): String {
        val resolved = resolveTag(tag)
        prefs(context).edit().putString(KEY_SELECTED_LOCALE, resolved).apply()
        _localeTag.value = resolved
        applyDefaultLocale(resolved)
        initialized = true
        return resolved
    }

    fun currentLanguage(): SupportedLanguage = findLanguage(_localeTag.value)

    fun findLanguage(tag: String?): SupportedLanguage =
        byTag[resolveTag(tag).lowercase(Locale.ROOT)] ?: supportedLanguages.first()

    fun isRtl(tag: String? = _localeTag.value): Boolean = findLanguage(tag).isRtl

    fun resolveTag(raw: String?): String {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return DEFAULT_LOCALE_TAG
        val lower = cleaned.replace('_', '-').lowercase(Locale.ROOT)
        if (lower == "en" || lower == "en-us") return DEFAULT_LOCALE_TAG
        if (lower == "in" || lower == "id") return "id"
        if (lower == "tl" || lower == "fil") return "fil"
        return byTag[lower]?.tag ?: DEFAULT_LOCALE_TAG
    }

    fun toJavaLocale(tag: String? = _localeTag.value): Locale = when (resolveTag(tag)) {
        "en-US" -> Locale.US
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        "zh-TW" -> Locale.TRADITIONAL_CHINESE
        "zh-HK" -> Locale("zh", "HK")
        "id" -> Locale("in")
        "fil" -> Locale("fil")
        else -> Locale(resolveTag(tag))
    }

    fun buildLocalizedConfiguration(baseConfig: Configuration, tag: String? = _localeTag.value): Configuration {
        val locale = toJavaLocale(tag)
        return Configuration(baseConfig).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                this.locale = locale
            }
            setLayoutDirection(locale)
        }
    }

    fun localizedContext(base: Context, tag: String? = null): Context {
        val activeTag = tag?.let { resolveTag(it) } ?: getLocaleTag(base)
        applyDefaultLocale(activeTag)
        val config = buildLocalizedConfiguration(base.resources.configuration, activeTag)
        return base.createConfigurationContext(config)
    }

    fun wrapContext(base: Context): Context {
        val activeTag = init(base)
        return localizedContext(base, activeTag)
    }

    fun getString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        val localized = localizedContext(context)
        return if (formatArgs.isEmpty()) {
            localized.getString(resId)
        } else {
            localized.getString(resId, *formatArgs)
        }
    }

    fun searchLanguages(query: String): List<SupportedLanguage> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return supportedLanguages
        return supportedLanguages.filter { lang ->
            lang.nativeName.lowercase(Locale.ROOT).contains(q) ||
                lang.englishName.lowercase(Locale.ROOT).contains(q) ||
                lang.tag.lowercase(Locale.ROOT).contains(q)
        }
    }

    @androidx.annotation.StringRes
    fun networkTransportLabelRes(transportName: String): Int = when (transportName) {
        "WIFI" -> R.string.service_transport_wifi
        "MOBILE_DATA" -> R.string.service_transport_mobile_data
        "ETHERNET" -> R.string.service_transport_ethernet
        else -> R.string.service_transport_network
    }

    private fun applyDefaultLocale(tag: String) {
        try {
            Locale.setDefault(toJavaLocale(tag))
        } catch (_: Exception) {
        }
    }

    fun resetForTest() {
        initialized = false
        _localeTag.value = DEFAULT_LOCALE_TAG
        applyDefaultLocale(DEFAULT_LOCALE_TAG)
    }
}
