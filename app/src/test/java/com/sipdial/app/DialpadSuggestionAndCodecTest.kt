package com.sipdial.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.ui.screens.KeypadScreen
import com.sipdial.app.ui.screens.RecentsScreen
import com.sipdial.app.ui.theme.AppTheme
import com.sipdial.app.util.AudioQualityPrefs
import com.sipdial.app.util.AudioQualityPrefs.AudioCodecDescriptor
import com.sipdial.app.util.AudioQualityPrefs.CodecEntry
import com.sipdial.app.util.CallingPrefs
import com.sipdial.app.util.DeviceContact
import com.sipdial.app.util.GeneralPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class DialpadSuggestionAndCodecTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("general_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("calling_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("audio_quality_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("telecom_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        GeneralPrefs.resetForTest()
        GeneralPrefs.init(context)
        CallingPrefs.setCallConfirmationEnabled(context, false)
        TelecomHelper.setNativeCallRoutingMode(context, TelecomHelper.MODE_ALWAYS_SIP)
    }

    @Test
    fun clickingSuggestedContactCallButtonDialsContactActualNumberInsteadOfDialpadPrefix() {
        val dialedCalls = mutableListOf<Pair<String, String?>>()
        var dialInput = TextFieldValue("01940", TextRange(5))

        val contacts = listOf(
            DeviceContact(
                id = "101",
                name = "Foysal Ahmmed Sadin",
                number = "01940-401566",
                normalizedDigits = "01940401566"
            ),
            DeviceContact(
                id = "101",
                name = "Foysal Ahmmed Sadin",
                number = "01940-998877",
                normalizedDigits = "01940998877"
            )
        )

        val account = SipAccountData(
            id = "acc-primary",
            name = "Main Line",
            username = "1001",
            domain = "sip.example.com"
        )

        composeRule.setContent {
            AppTheme {
                KeypadScreen(
                    registrationState = SipRegistrationState.REGISTERED,
                    currentAccount = account,
                    dialableSipAccounts = listOf(account),
                    contacts = contacts,
                    favoriteNumbers = emptySet(),
                    prefillNumber = null,
                    onPrefillConsumed = {},
                    onSipCall = { dialedCalls += it to null },
                    onSipCallWithAccount = { num, accId -> dialedCalls += num to accId },
                    onToggleFavorite = { _, _, _ -> },
                    onOpenAccounts = {},
                    dialInput = dialInput,
                    onDialInputChange = { dialInput = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("keypad_suggestion_call_101_01940-401566")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("01940401566" to "acc-primary"), dialedCalls)
        assertEquals("01940", dialInput.text)

        ShadowSystemClock.advanceBy(Duration.ofMillis(600))
        composeRule.onNodeWithTag("keypad_suggestion_call_101_01940-998877")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("01940401566" to "acc-primary", "01940998877" to "acc-primary"),
            dialedCalls
        )
        assertEquals("01940", dialInput.text)
    }

    @Test
    fun recentsSearchMatchesSipAccountIdAccountUserContactNameAndFormattedDigits() {
        val officeCall = CallRecord(
            id = 1L,
            remoteAddress = "+8801940401566",
            displayName = "",
            isIncoming = false,
            isAnswered = true,
            duration = 45,
            timestamp = 1_700_000_000_000L,
            accountUser = "sipuser_bd",
            accountId = "office-gateway-77",
            accountName = "Dhaka Trunk"
        )
        val homeCall = CallRecord(
            id = 2L,
            remoteAddress = "01711-223344",
            displayName = "Karim",
            isIncoming = true,
            isAnswered = false,
            duration = 0,
            timestamp = 1_700_000_020_000L,
            accountUser = "2002",
            accountId = "home-pbx",
            accountName = "Home Line"
        )
        val contacts = listOf(
            DeviceContact(
                id = "1",
                name = "Foysal Ahmmed Sadin",
                number = "+880 1940-401566",
                normalizedDigits = "+8801940401566"
            )
        )

        composeRule.setContent {
            AppTheme {
                RecentsScreen(
                    recentCalls = listOf(officeCall, homeCall),
                    contacts = contacts,
                    blockedNumbers = emptySet(),
                    onCall = {},
                    onOpenDetails = {},
                    onDeleteCalls = {},
                    onClearHistory = {},
                    onBlockNumber = {},
                    onUnblockNumber = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Search by SIP accountId
        composeRule.onNodeWithTag("recents_search_input").performTextReplacement("office-gateway-77")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Foysal Ahmmed Sadin", substring = true).assertIsDisplayed()

        // Search by SIP accountUser
        composeRule.onNodeWithTag("recents_search_input").performTextReplacement("sipuser_bd")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Foysal Ahmmed Sadin", substring = true).assertIsDisplayed()

        // Search by raw unformatted phone digits
        composeRule.onNodeWithTag("recents_search_input").performTextReplacement("1711223344")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Karim", substring = true).assertIsDisplayed()
    }

    @Test
    fun audioQualityPrefsDefaultsHdCodecsTrueAndPlansPrioritySafely() {
        assertTrue(AudioQualityPrefs.isHdCodecsEnabled(context))
        AudioQualityPrefs.setHdCodecsEnabled(context, false)
        assertFalse(AudioQualityPrefs.isHdCodecsEnabled(context))

        val deviceCodecs = listOf(
            AudioCodecDescriptor("PCMA", 8000, isUsable = true),
            AudioCodecDescriptor("GSM", 8000, isUsable = true),
            AudioCodecDescriptor("G729", 8000, isUsable = false),
            AudioCodecDescriptor("PCMU", 8000, isUsable = true),
            AudioCodecDescriptor("G722", 8000, isUsable = true),
            AudioCodecDescriptor("opus", 48000, isUsable = true),
            AudioCodecDescriptor("L16", 44100, isUsable = true)
        )

        val hdPlan = AudioQualityPrefs.planAudioCodecs(
            available = deviceCodecs,
            hdEnabled = true,
            customConfig = null
        )
        assertEquals("OPUS/48000", hdPlan.first().key)
        assertTrue(hdPlan.first().enabled)
        assertEquals(
            listOf("OPUS/48000", "G722/8000", "PCMU/8000", "PCMA/8000", "GSM/8000"),
            hdPlan.filter { it.enabled }.map { it.key }
        )
        // Unusable G.729 and low-priority L16 remain disabled
        assertFalse(hdPlan.first { it.key == "G729/8000" }.enabled)
        assertFalse(hdPlan.first { it.key == "L16/44100" }.enabled)

        val narrowbandPlan = AudioQualityPrefs.planAudioCodecs(
            available = deviceCodecs,
            hdEnabled = false,
            customConfig = null
        )
        assertEquals(
            listOf("PCMU/8000", "PCMA/8000", "GSM/8000"),
            narrowbandPlan.filter { it.enabled }.map { it.key }
        )
        assertFalse(narrowbandPlan.first { it.key == "OPUS/48000" }.enabled)

        // Custom config never enables an unusable codec
        val customPlan = AudioQualityPrefs.planAudioCodecs(
            available = deviceCodecs,
            hdEnabled = true,
            customConfig = listOf(CodecEntry("G729/8000", true))
        )
        assertFalse(customPlan.first { it.key == "G729/8000" }.enabled)
        assertTrue(customPlan.any { it.enabled && it.descriptor.isUsable })
    }
}
