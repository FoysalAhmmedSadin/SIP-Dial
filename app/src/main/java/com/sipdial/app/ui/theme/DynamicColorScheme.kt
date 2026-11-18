package com.sipdial.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Immutable
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val danger: Color,
    val onDanger: Color,
    val info: Color,
    val neutralBadge: Color,
    val isAmoled: Boolean,
    val isDark: Boolean
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        success = Color(0xFF16A34A),
        onSuccess = Color.White,
        warning = Color(0xFFD97706),
        onWarning = Color.White,
        danger = Color(0xFFDC2626),
        onDanger = Color.White,
        info = Color(0xFF0284C7),
        neutralBadge = Color(0xFF64748B),
        isAmoled = false,
        isDark = false
    )
}

object DynamicColorScheme {

    private fun accessibleOnColor(bg: Color): Color =
        if (bg.luminance() > 0.42f) Color(0xFF0B111A) else Color.White

    fun isResolvedDark(mode: ThemeMode, systemIsDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemIsDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    fun buildColorScheme(
        mode: ThemeMode,
        accent: AccentColor,
        systemIsDark: Boolean
    ): ColorScheme {
        val isDark = isResolvedDark(mode, systemIsDark)
        val isAmoled = mode == ThemeMode.AMOLED

        return when {
            !isDark -> buildLightScheme(accent)
            isAmoled -> buildAmoledScheme(accent)
            else -> buildDarkScheme(accent)
        }
    }

    fun buildSemanticColors(mode: ThemeMode, systemIsDark: Boolean): SemanticColors {
        val isDark = isResolvedDark(mode, systemIsDark)
        val isAmoled = mode == ThemeMode.AMOLED
        return if (isDark) {
            SemanticColors(
                success = Color(0xFF34D399),
                onSuccess = Color(0xFF042F1E),
                warning = Color(0xFFFBBF24),
                onWarning = Color(0xFF2E1A02),
                danger = Color(0xFFF87171),
                onDanger = Color(0xFF3B0808),
                info = Color(0xFF38BDF8),
                neutralBadge = Color(0xFF9CA3AF),
                isAmoled = isAmoled,
                isDark = true
            )
        } else {
            SemanticColors(
                success = Color(0xFF16A34A),
                onSuccess = Color.White,
                warning = Color(0xFFD97706),
                onWarning = Color.White,
                danger = Color(0xFFDC2626),
                onDanger = Color.White,
                info = Color(0xFF0284C7),
                neutralBadge = Color(0xFF64748B),
                isAmoled = false,
                isDark = false
            )
        }
    }

    private fun buildLightScheme(accent: AccentColor): ColorScheme {
        val primary = accent.color
        val onPrimary = accessibleOnColor(primary)
        val primaryContainer = lerp(Color.White, primary, 0.14f)
        val onPrimaryContainer = lerp(Color(0xFF0B111A), primary, 0.55f)
        val secondary = lerp(Color(0xFF475569), primary, 0.35f)
        val secondaryContainer = lerp(Color(0xFFF1F5F9), primary, 0.10f)

        return lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color(0xFF1E293B),
            tertiary = lerp(Color(0xFF0F766E), primary, 0.25f),
            onTertiary = Color.White,
            background = Color(0xFFF6F7FB),
            onBackground = Color(0xFF111827),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111827),
            surfaceVariant = Color(0xFFEEF1F6),
            onSurfaceVariant = Color(0xFF525E70),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF9FAFC),
            surfaceContainer = Color(0xFFF2F4F8),
            surfaceContainerHigh = Color(0xFFEAECEF),
            surfaceContainerHighest = Color(0xFFE2E6EC),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE5E9F0),
            error = Color(0xFFDC2626),
            onError = Color.White,
            errorContainer = Color(0xFFFEE2E2),
            onErrorContainer = Color(0xFF7F1D1D)
        )
    }

    private fun buildDarkScheme(accent: AccentColor): ColorScheme {
        val primary = accent.darkSurfaceTint
        val onPrimary = accessibleOnColor(primary)
        val primaryContainer = lerp(Color(0xFF181A20), primary, 0.26f)
        val onPrimaryContainer = lerp(Color.White, primary, 0.20f)
        val secondary = lerp(Color(0xFF94A3B8), primary, 0.35f)
        val secondaryContainer = lerp(Color(0xFF1E222B), primary, 0.16f)

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = Color(0xFF0F172A),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color(0xFFE2E8F0),
            tertiary = lerp(Color(0xFF2DD4BF), primary, 0.25f),
            onTertiary = Color(0xFF042F2E),
            background = Color(0xFF101217),
            onBackground = Color(0xFFF3F4F6),
            surface = Color(0xFF171A21),
            onSurface = Color(0xFFF3F4F6),
            surfaceVariant = Color(0xFF222630),
            onSurfaceVariant = Color(0xFF9CA3AF),
            surfaceContainerLowest = Color(0xFF0D0F13),
            surfaceContainerLow = Color(0xFF14171D),
            surfaceContainer = Color(0xFF1B1F27),
            surfaceContainerHigh = Color(0xFF232833),
            surfaceContainerHighest = Color(0xFF2B313E),
            outline = Color(0xFF3E4554),
            outlineVariant = Color(0xFF292E3A),
            error = Color(0xFFEF4444),
            onError = Color(0xFF450A0A),
            errorContainer = Color(0xFF4C1111),
            onErrorContainer = Color(0xFFFECACA)
        )
    }

    private fun buildAmoledScheme(accent: AccentColor): ColorScheme {
        val primary = accent.darkSurfaceTint
        val onPrimary = accessibleOnColor(primary)
        val primaryContainer = lerp(Color(0xFF0B0B0D), primary, 0.28f)
        val onPrimaryContainer = lerp(Color.White, primary, 0.15f)
        val secondary = lerp(Color(0xFFA1A1AA), primary, 0.35f)
        val secondaryContainer = lerp(Color(0xFF121215), primary, 0.18f)

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = Color.Black,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color(0xFFF4F4F5),
            tertiary = lerp(Color(0xFF2DD4BF), primary, 0.25f),
            onTertiary = Color.Black,
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF141418),
            onSurfaceVariant = Color(0xFFA1A1AA),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF08080A),
            surfaceContainer = Color(0xFF101014),
            surfaceContainerHigh = Color(0xFF18181E),
            surfaceContainerHighest = Color(0xFF22222A),
            outline = Color(0xFF32323B),
            outlineVariant = Color(0xFF202026),
            error = Color(0xFFFF453A),
            onError = Color.Black,
            errorContainer = Color(0xFF3A0B0B),
            onErrorContainer = Color(0xFFFECACA)
        )
    }
}
