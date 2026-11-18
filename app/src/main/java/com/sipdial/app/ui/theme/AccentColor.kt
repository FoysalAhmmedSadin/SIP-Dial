package com.sipdial.app.ui.theme

import androidx.compose.ui.graphics.Color

enum class AccentColor(
    val key: String,
    val displayName: String,
    val color: Color,
    val darkSurfaceTint: Color
) {
    GREEN("green", "Green", Color(0xFF34C759), Color(0xFF28CD53)),
    BLUE("blue", "Blue", Color(0xFF0A84FF), Color(0xFF3B9CFF)),
    INDIGO("indigo", "Indigo", Color(0xFF5856D6), Color(0xFF7A78EB)),
    PURPLE("purple", "Purple", Color(0xFFBF5AF2), Color(0xFFCF7BF7)),
    CORAL_RED("coral_red", "Coral Red", Color(0xFFFF453A), Color(0xFFFF6259)),
    ORANGE("orange", "Orange", Color(0xFFFF9F0A), Color(0xFFFFB033)),
    TEAL("teal", "Teal", Color(0xFF00BFAF), Color(0xFF1FD6C6)),
    PINK("pink", "Pink", Color(0xFFFF2D55), Color(0xFFFF5273));

    companion object {
        fun fromKey(key: String?): AccentColor =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: GREEN
    }
}
