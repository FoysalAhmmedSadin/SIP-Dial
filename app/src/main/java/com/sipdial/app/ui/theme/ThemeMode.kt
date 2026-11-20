package com.sipdial.app.ui.theme

import androidx.annotation.StringRes
import com.sipdial.app.R

enum class ThemeMode(
    val key: String,
    val label: String,
    @StringRes val labelRes: Int
) {
    SYSTEM("system", "System", R.string.theme_mode_system),
    LIGHT("light", "Light", R.string.theme_mode_light),
    DARK("dark", "Dark", R.string.theme_mode_dark),
    AMOLED("amoled", "AMOLED", R.string.theme_mode_amoled);

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}
