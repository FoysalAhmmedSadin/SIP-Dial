package com.sipdial.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.ui.theme.AccentColor
import com.sipdial.app.ui.theme.DynamicColorScheme
import com.sipdial.app.ui.theme.ThemeMode
import com.sipdial.app.ui.theme.ThemePreferences
import com.sipdial.app.ui.theme.UiTextScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemePreferencesAndSchemeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ThemePreferences.resetForTest()
    }

    @Test
    fun allThemeModesAndAccentsBuildValidSchemesAndAmoledUsesTrueBlack() {
        for (mode in ThemeMode.entries) {
            for (accent in AccentColor.entries) {
                val lightResolved = DynamicColorScheme.buildColorScheme(mode, accent, systemIsDark = false)
                val darkResolved = DynamicColorScheme.buildColorScheme(mode, accent, systemIsDark = true)
                assertTrue(lightResolved.primary != Color.Unspecified)
                assertTrue(darkResolved.primary != Color.Unspecified)
            }
        }

        val amoled = DynamicColorScheme.buildColorScheme(ThemeMode.AMOLED, AccentColor.BLUE, systemIsDark = false)
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color.White, amoled.onBackground)
        assertTrue(DynamicColorScheme.buildSemanticColors(ThemeMode.AMOLED, systemIsDark = false).isAmoled)
        assertFalse(DynamicColorScheme.buildSemanticColors(ThemeMode.LIGHT, systemIsDark = true).isDark)
    }

    @Test
    fun themePreferencesPersistAcrossResetAndReInit() = runBlocking {
        ThemePreferences.init(context)
        ThemePreferences.setThemeMode(context, ThemeMode.AMOLED)
        ThemePreferences.setAccentColor(context, AccentColor.TEAL)
        ThemePreferences.setTextScale(context, UiTextScale.LARGE)

        // Allow DataStore edit coroutine to flush to disk
        delay(250)

        ThemePreferences.resetForTest()
        ThemePreferences.init(context)

        val restored = ThemePreferences.state.value
        assertEquals(ThemeMode.AMOLED, restored.themeMode)
        assertEquals(AccentColor.TEAL, restored.accentColor)
        assertEquals(UiTextScale.LARGE, restored.textScale, 0.001f)
    }
}
