package com.sipdial.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val SipDialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AppTheme(
    appearanceOverride: AppearanceState? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    remember(context.applicationContext) {
        ThemePreferences.init(context)
        true
    }

    val persisted by ThemePreferences.state.collectAsStateWithLifecycle()
    val appearance = appearanceOverride ?: persisted
    val systemIsDark = isSystemInDarkTheme()

    val isDark = remember(appearance.themeMode, systemIsDark) {
        DynamicColorScheme.isResolvedDark(appearance.themeMode, systemIsDark)
    }
    val colorScheme = remember(appearance.themeMode, appearance.accentColor, systemIsDark) {
        DynamicColorScheme.buildColorScheme(
            mode = appearance.themeMode,
            accent = appearance.accentColor,
            systemIsDark = systemIsDark
        )
    }
    val semanticColors = remember(appearance.themeMode, systemIsDark) {
        DynamicColorScheme.buildSemanticColors(
            mode = appearance.themeMode,
            systemIsDark = systemIsDark
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity.density, baseDensity.fontScale, appearance.textScale) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * appearance.textScale
        )
    }

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SipDialTypography,
            shapes = SipDialShapes,
            content = content
        )
    }
}
