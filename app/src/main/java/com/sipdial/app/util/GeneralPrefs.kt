package com.sipdial.app.util

import android.content.Context
import com.sipdial.app.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GeneralSettingsState(
    val defaultOpeningTab: String = GeneralPrefs.TAB_RECENTS,
    val soundEffectsEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val numberFormattingEnabled: Boolean = true
)

object GeneralPrefs {
    private const val PREFS = "general_prefs"
    private const val KEY_DEFAULT_OPENING_TAB = "default_opening_tab"
    private const val KEY_SOUND_EFFECTS = "sound_effects_enabled"
    private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
    private const val KEY_NUMBER_FORMATTING = "number_formatting_enabled"

    const val TAB_FAVORITES = "favorites"
    const val TAB_RECENTS = "recents"
    const val TAB_CONTACTS = "contacts"
    const val TAB_KEYPAD = "keypad"

    val allTabOptions: List<Pair<String, String>> = listOf(
        TAB_CONTACTS to "Contacts",
        TAB_RECENTS to "Recents",
        TAB_KEYPAD to "Keypad"
    )

    private val _state = MutableStateFlow(GeneralSettingsState())
    val state: StateFlow<GeneralSettingsState> = _state.asStateFlow()

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val prefs = p(context)
        val tab = normalizeTab(prefs.getString(KEY_DEFAULT_OPENING_TAB, TAB_RECENTS))
        val sound = if (prefs.contains(KEY_SOUND_EFFECTS)) {
            prefs.getBoolean(KEY_SOUND_EFFECTS, true)
        } else {
            CallingPrefs.isDtmfTonesEnabled(context)
        }
        val haptic = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        val formatting = prefs.getBoolean(KEY_NUMBER_FORMATTING, true)
        _state.value = GeneralSettingsState(
            defaultOpeningTab = tab,
            soundEffectsEnabled = sound,
            hapticFeedbackEnabled = haptic,
            numberFormattingEnabled = formatting
        )
    }

    fun getDefaultOpeningTab(context: Context): String {
        return normalizeTab(p(context).getString(KEY_DEFAULT_OPENING_TAB, TAB_RECENTS))
    }

    fun setDefaultOpeningTab(context: Context, tab: String) {
        val normalized = normalizeTab(tab)
        p(context).edit().putString(KEY_DEFAULT_OPENING_TAB, normalized).apply()
        _state.value = _state.value.copy(defaultOpeningTab = normalized)
    }

    fun getDefaultOpeningRoute(context: Context): String = routeForTab(getDefaultOpeningTab(context))

    fun routeForTab(tab: String): String = when (normalizeTab(tab)) {
        TAB_CONTACTS -> Screen.Contacts.route
        TAB_KEYPAD -> Screen.Keypad.route
        else -> Screen.Recents.route
    }

    fun isSoundEffectsEnabled(context: Context): Boolean {
        val prefs = p(context)
        return if (prefs.contains(KEY_SOUND_EFFECTS)) {
            prefs.getBoolean(KEY_SOUND_EFFECTS, true)
        } else {
            CallingPrefs.isDtmfTonesEnabled(context)
        }
    }

    fun setSoundEffectsEnabled(context: Context, enabled: Boolean) {
        p(context).edit().putBoolean(KEY_SOUND_EFFECTS, enabled).apply()
        CallingPrefs.setDtmfTonesEnabled(context, enabled)
        _state.value = _state.value.copy(soundEffectsEnabled = enabled)
    }

    internal fun syncSoundEffectsFromCallingPrefs(context: Context, enabled: Boolean) {
        p(context).edit().putBoolean(KEY_SOUND_EFFECTS, enabled).apply()
        _state.value = _state.value.copy(soundEffectsEnabled = enabled)
    }

    fun isHapticFeedbackEnabled(context: Context): Boolean =
        p(context).getBoolean(KEY_HAPTIC_FEEDBACK, true)

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        p(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        _state.value = _state.value.copy(hapticFeedbackEnabled = enabled)
    }

    fun isNumberFormattingEnabled(context: Context): Boolean =
        p(context).getBoolean(KEY_NUMBER_FORMATTING, true)

    fun setNumberFormattingEnabled(context: Context, enabled: Boolean) {
        p(context).edit().putBoolean(KEY_NUMBER_FORMATTING, enabled).apply()
        _state.value = _state.value.copy(numberFormattingEnabled = enabled)
    }

    fun normalizeTab(raw: String?): String = when (raw?.trim()?.lowercase()) {
        TAB_CONTACTS -> TAB_CONTACTS
        TAB_KEYPAD -> TAB_KEYPAD
        else -> TAB_RECENTS
    }

    fun resetForTest() {
        _state.value = GeneralSettingsState()
    }
}
