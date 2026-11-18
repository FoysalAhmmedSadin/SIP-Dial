package com.sipdial.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.telecom.IncomingUiRequest
import com.sipdial.app.ui.screens.AboutScreen
import com.sipdial.app.ui.screens.PrivacySecuritySettingsScreen
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
class BiometricAndLockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun privacySecuritySettingsDoesNotShowAppLockToggle() {
        composeRule.setContent {
            AppTheme {
                PrivacySecuritySettingsScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Privacy & Security").assertIsDisplayed()
        composeRule.onAllNodesWithText("Biometric Dialer Lock", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("biometric_lock_switch").assertCountEquals(0)
        composeRule.onAllNodesWithTag("biometric_lock_screen").assertCountEquals(0)
    }

    @Test
    fun aboutScreenLicensesDoNotIncludeUpstreamSipDialerCredit() {
        composeRule.setContent {
            AppTheme {
                AboutScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_list")
            .performScrollToNode(hasTestTag("open_source_licenses_button"))
        composeRule.onNodeWithTag("open_source_licenses_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open-Source Licenses & Credits").assertIsDisplayed()
        composeRule.onAllNodesWithText("Upstream SIP Dialer (mehedycse/sipdialer)", substring = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("mehedycse", substring = true, ignoreCase = true)
            .assertCountEquals(0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IncomingCallLockScreenActivityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        IncomingUiRequest.clear()
    }

    @Test
    fun mainActivityOpensDirectlyToDefaultRecentsWithoutAppLockScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("nav_recents").assertIsDisplayed()
            composeRule.onNodeWithTag("open_dialer_fab").assertIsDisplayed()
            composeRule.onAllNodesWithTag("biometric_lock_screen").assertCountEquals(0)
            composeRule.onNodeWithTag("open_dialer_fab").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("keypad_number_display").assertIsDisplayed()
        }
    }

    @Test
    fun incomingCallActivityIntentIncludesLockScreenAndNoUserActionFlags() {
        val intent = IncomingCallActivity.createLaunchIntent(
            context = context,
            callerAddress = "sip:1001@pbx.example.com",
            incoming = true
        )
        assertEquals(IncomingCallActivity::class.java.name, intent.component?.className)
        assertTrue(intent.getBooleanExtra(IncomingCallActivity.EXTRA_SHOW_WHEN_LOCKED, false))
        assertTrue(intent.getBooleanExtra(IncomingCallActivity.EXTRA_TURN_SCREEN_ON, false))
        assertTrue(intent.getBooleanExtra(IncomingCallActivity.EXTRA_INCOMING_CALL, false))
        assertEquals("sip:1001@pbx.example.com", intent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_ADDRESS))
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NO_USER_ACTION) != 0)
    }

    @Test
    fun incomingCallActivityRendersCallScreenDirectlyWithoutKeypadOrBiometricScreen() {
        val intent = IncomingCallActivity.createLaunchIntent(
            context = context,
            callerAddress = "sip:%2B8801712345678@pbx.example.com;user=phone",
            incoming = true
        )
        ActivityScenario.launch<IncomingCallActivity>(intent).use { scenario ->
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                @Suppress("DEPRECATION")
                assertTrue((flags and android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) != 0)
                @Suppress("DEPRECATION")
                assertTrue((flags and android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) != 0)
            }
            composeRule.onNodeWithText("Incoming • SIP", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("+8801712345678").assertIsDisplayed()
            composeRule.onAllNodesWithTag("nav_keypad").assertCountEquals(0)
            composeRule.onAllNodesWithTag("biometric_lock_screen").assertCountEquals(0)
        }
    }

    @Test
    fun incomingCallIntentShowsCallScreenImmediatelyWithoutKeypadOrBiometricFlash() {
        val incomingIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("incoming_call", true)
            putExtra("caller_address", "sip:1001@pbx.example.com")
        }

        ActivityScenario.launch<MainActivity>(incomingIntent).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Incoming • SIP", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("1001", substring = true).assertIsDisplayed()
            composeRule.onAllNodesWithTag("nav_keypad").assertCountEquals(0)
            composeRule.onAllNodesWithTag("biometric_lock_screen").assertCountEquals(0)
        }
    }
}
