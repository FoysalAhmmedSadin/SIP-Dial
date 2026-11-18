package com.sipdial.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.sip.AudioRouteOption
import com.sipdial.app.sip.AudioRouteType
import com.sipdial.app.sip.SipCallState
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.ui.components.CallControls
import com.sipdial.app.ui.screens.SettingsScreen
import com.sipdial.app.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallingUxAndRoutingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("telecom_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultOutgoingCallRoutingModeIsSipOnly() {
        assertEquals(
            TelecomHelper.MODE_ALWAYS_SIP,
            TelecomHelper.getNativeCallRoutingMode(context)
        )
    }

    @Test
    fun callControlsSpeakerButtonTogglesDirectlyInOneTapEvenWithAudioRouteCallback() {
        var speakerToggled = 0
        var routeSheetOpened = 0
        composeRule.setContent {
            AppTheme {
                CallControls(
                    isMuted = false,
                    isSpeaker = false,
                    isOnHold = false,
                    onMuteToggle = {},
                    onSpeakerToggle = { speakerToggled++ },
                    onHoldToggle = {},
                    onEndCall = {},
                    onAudioRouteClick = { routeSheetOpened++ }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("call_speaker_button").assertIsDisplayed()
        composeRule.onNodeWithTag("call_speaker_button").performClick()
        assertEquals(1, speakerToggled)
        assertEquals(0, routeSheetOpened)
    }

    @Test
    fun settingsTopBarBackButtonRendersAndTriggersNavigationBack() {
        var backClicked = false
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    currentAccount = null,
                    accountsCount = 0,
                    registrationState = SipRegistrationState.UNREGISTERED,
                    recordingsCount = 0,
                    onBack = { backClicked = true },
                    onOpenAppearance = {},
                    onOpenSipAccounts = {},
                    onOpenCalling = {},
                    onOpenAudioCodecs = {},
                    onOpenRecordings = {},
                    onOpenNotifications = {},
                    onOpenPowerNetwork = {},
                    onOpenPrivacySecurity = {},
                    onOpenBackupRestore = {},
                    onOpenAbout = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_back").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_back").performClick()
        assertTrue(backClicked)
    }
}
