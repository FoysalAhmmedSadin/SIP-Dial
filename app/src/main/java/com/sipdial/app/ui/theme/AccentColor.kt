package com.sipdial.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sipdial.app.R

enum class AccentColor(
    val key: String,
    val displayName: String,
    @StringRes val labelRes: Int,
    val color: Color,
    val darkSurfaceTint: Color
) {
    GREEN("green", "Green", R.string.accent_green, Color(0xFF34C759), Color(0xFF28CD53)),
    BLUE("blue", "Blue", R.string.accent_blue, Color(0xFF0A84FF), Color(0xFF3B9CFF)),
    INDIGO("indigo", "Indigo", R.string.accent_indigo, Color(0xFF5856D6), Color(0xFF7A78EB)),
    PURPLE("purple", "Purple", R.string.accent_purple, Color(0xFFBF5AF2), Color(0xFFCF7BF7)),
    CORAL_RED("coral_red", "Coral Red", R.string.accent_coral_red, Color(0xFFFF453A), Color(0xFFFF6259)),
    ORANGE("orange", "Orange", R.string.accent_orange, Color(0xFFFF9F0A), Color(0xFFFFB033)),
    TEAL("teal", "Teal", R.string.accent_teal, Color(0xFF00BFAF), Color(0xFF1FD6C6)),
    PINK("pink", "Pink", R.string.accent_pink, Color(0xFFFF2D55), Color(0xFFFF5273));

    companion object {
        fun fromKey(key: String?): AccentColor =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: GREEN
    }
}
