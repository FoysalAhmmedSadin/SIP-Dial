package com.sipdial.app.ui.theme

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    AMOLED("amoled", "AMOLED");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}
