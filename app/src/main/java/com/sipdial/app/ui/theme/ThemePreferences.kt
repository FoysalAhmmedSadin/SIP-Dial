package com.sipdial.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "appearance_prefs")

data class AppearanceState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.GREEN,
    val textScale: Float = UiTextScale.NORMAL
)

object UiTextScale {
    const val NORMAL = 1.0f
    const val LARGE = 1.12f
    const val EXTRA_LARGE = 1.25f
}

/**
 * Persists user-selected ThemeMode, AccentColor, and font scale in DataStore Preferences.
 * Preloaded synchronously during Application/Activity init so the UI renders with zero theme flash.
 */
object ThemePreferences {
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")
    private val KEY_TEXT_SCALE = floatPreferencesKey("text_scale")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AppearanceState())
    val state: StateFlow<AppearanceState> = _state.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            try {
                val prefs = runBlocking(Dispatchers.IO) {
                    app.appearanceDataStore.data.first()
                }
                _state.value = AppearanceState(
                    themeMode = ThemeMode.fromKey(prefs[KEY_THEME_MODE]),
                    accentColor = AccentColor.fromKey(prefs[KEY_ACCENT_COLOR]),
                    textScale = prefs[KEY_TEXT_SCALE] ?: UiTextScale.NORMAL
                )
            } catch (_: Exception) {
            }
            initialized = true
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val current = _state.value
        _state.value = current.copy(themeMode = mode)
        val app = context.applicationContext
        scope.launch {
            app.appearanceDataStore.edit { it[KEY_THEME_MODE] = mode.key }
        }
    }

    fun setAccentColor(context: Context, accent: AccentColor) {
        val current = _state.value
        _state.value = current.copy(accentColor = accent)
        val app = context.applicationContext
        scope.launch {
            app.appearanceDataStore.edit { it[KEY_ACCENT_COLOR] = accent.key }
        }
    }

    fun setTextScale(context: Context, scale: Float) {
        val clamped = scale.coerceIn(0.9f, 1.4f)
        _state.value = _state.value.copy(textScale = clamped)
        val app = context.applicationContext
        scope.launch {
            app.appearanceDataStore.edit { it[KEY_TEXT_SCALE] = clamped }
        }
    }

    /** Test helper to reset in-memory state cleanly in unit/Robolectric tests */
    internal fun resetForTest() {
        initialized = false
        _state.value = AppearanceState()
    }
}
