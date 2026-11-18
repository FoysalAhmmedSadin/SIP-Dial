package com.sipdial.app

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.sip.SipCallState
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.ui.navigation.Screen
import com.sipdial.app.ui.screens.CallDetailsScreen
import com.sipdial.app.ui.screens.GeneralSettingsScreen
import com.sipdial.app.ui.screens.InCallScreen
import com.sipdial.app.ui.screens.KeypadScreen
import com.sipdial.app.ui.screens.RecentsScreen
import com.sipdial.app.ui.screens.SettingsScreen
import com.sipdial.app.ui.screens.backspaceAtSelection
import com.sipdial.app.ui.screens.insertAtSelection
import com.sipdial.app.ui.screens.sanitizeDialFieldChange
import com.sipdial.app.ui.theme.AppTheme
import com.sipdial.app.util.CallingPrefs
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RedesignAndAccountDisplayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("general_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("calling_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        GeneralPrefs.resetForTest()
        GeneralPrefs.init(context)
    }

    @Test
    fun generalPrefsDefaultsToRecentsAndPersistsOpeningTabAndFeedbackFlags() {
        assertEquals(GeneralPrefs.TAB_RECENTS, GeneralPrefs.getDefaultOpeningTab(context))
        assertEquals(Screen.Recents.route, GeneralPrefs.getDefaultOpeningRoute(context))
        assertTrue(GeneralPrefs.state.value.soundEffectsEnabled)
        assertTrue(GeneralPrefs.state.value.hapticFeedbackEnabled)
        assertTrue(GeneralPrefs.state.value.numberFormattingEnabled)

        GeneralPrefs.setDefaultOpeningTab(context, GeneralPrefs.TAB_CONTACTS)
        GeneralPrefs.setSoundEffectsEnabled(context, false)
        GeneralPrefs.setHapticFeedbackEnabled(context, false)
        GeneralPrefs.setNumberFormattingEnabled(context, false)

        GeneralPrefs.resetForTest()
        GeneralPrefs.init(context)

        assertEquals(GeneralPrefs.TAB_CONTACTS, GeneralPrefs.getDefaultOpeningTab(context))
        assertEquals(Screen.Contacts.route, GeneralPrefs.getDefaultOpeningRoute(context))
        assertFalse(GeneralPrefs.state.value.soundEffectsEnabled)
        assertFalse(CallingPrefs.isDtmfTonesEnabled(context))
        assertFalse(GeneralPrefs.state.value.hapticFeedbackEnabled)
        assertFalse(GeneralPrefs.state.value.numberFormattingEnabled)

        CallingPrefs.setDtmfTonesEnabled(context, true)
        assertTrue(GeneralPrefs.isSoundEffectsEnabled(context))
    }

    @Test
    fun sipEngineResolveAccountDisplayNameUsesHumanReadableFallbacks() {
        assertEquals(
            "Office PBX",
            SipEngine.resolveAccountDisplayName(
                SipAccountData(name = "Office PBX", username = "1001@pbx.io", authUsername = "auth1", domain = "pbx.io")
            )
        )
        assertEquals(
            "1001",
            SipEngine.resolveAccountDisplayName(
                SipAccountData(name = "", username = "1001@pbx.io", authUsername = "auth1", domain = "pbx.io")
            )
        )
        assertEquals(
            "auth1",
            SipEngine.resolveAccountDisplayName(
                SipAccountData(name = "", username = "", authUsername = "auth1", domain = "pbx.io")
            )
        )
        assertEquals(
            "pbx.io",
            SipEngine.resolveAccountDisplayName(
                SipAccountData(name = "", username = "", authUsername = "", domain = "pbx.io")
            )
        )
        assertEquals(
            "SIP",
            SipEngine.resolveAccountDisplayName(
                SipAccountData(name = "", username = "", authUsername = "", domain = "")
            )
        )
    }

    @Test
    fun keypadScreenHasNoFavoriteButtonBesideNumberAndPreservesLongEditableNumbers() {
        val longNumber = "+8801712345678901234567"
        var dialValue = TextFieldValue(longNumber, TextRange(longNumber.length))

        composeRule.setContent {
            AppTheme {
                KeypadScreen(
                    registrationState = SipRegistrationState.REGISTERED,
                    currentAccount = SipAccountData(
                        id = "acc1",
                        name = "Office Line",
                        username = "1001",
                        domain = "pbx.example.com"
                    ),
                    contacts = emptyList(),
                    favoriteNumbers = emptySet(),
                    prefillNumber = null,
                    onPrefillConsumed = {},
                    onSipCall = {},
                    onToggleFavorite = { _, _, _ -> },
                    onOpenAccounts = {},
                    dialInput = dialValue,
                    onDialInputChange = { dialValue = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("keypad_favorite_toggle").assertCountEquals(0)
        composeRule.onAllNodesWithTag("keypad_paste_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("keypad_copy_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("keypad_add_contact_button").assertCountEquals(0)
        composeRule.onNodeWithTag("keypad_number_display").assertIsDisplayed()

        // Verify cursor insert, backspace, and native selection range preservation
        val midInserted = insertAtSelection(TextFieldValue("1245", TextRange(2)), "3")
        assertEquals("12345", midInserted.text)
        assertEquals(TextRange(3), midInserted.selection)

        val midDeleted = backspaceAtSelection(midInserted)
        assertEquals("1245", midDeleted.text)
        assertEquals(TextRange(2), midDeleted.selection)

        val selected = sanitizeDialFieldChange(TextFieldValue("+8801712", TextRange(1, 5)))
        assertEquals("+8801712", selected.text)
        assertEquals(TextRange(1, 5), selected.selection)

        val pasted = sanitizeDialFieldChange(TextFieldValue("+880 1940-401566", TextRange(16)))
        assertEquals("+8801940401566", pasted.text)
        assertEquals(TextRange(14), pasted.selection)
    }

    @Test
    fun recentsScreenDisplaysSipAccountNameAndFiltersByAccountName() {
        val sampleRecord = CallRecord(
            id = 42L,
            remoteAddress = "+15551234567",
            displayName = "Alice",
            isIncoming = false,
            isAnswered = true,
            duration = 75,
            timestamp = 1_700_000_000_000L,
            accountUser = "1001",
            accountId = "acc-office",
            accountName = "Office PBX"
        )
        val simRecord = CallRecord(
            id = 43L,
            remoteAddress = "+15559990000",
            displayName = "Bob",
            isIncoming = true,
            isAnswered = true,
            duration = 20,
            timestamp = 1_700_000_010_000L,
            accountUser = "SIM",
            accountId = "SIM",
            accountName = "SIM"
        )

        composeRule.setContent {
            AppTheme {
                RecentsScreen(
                    recentCalls = listOf(sampleRecord, simRecord),
                    contacts = emptyList(),
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
        composeRule.onAllNodesWithTag("recents_clear_all_button").assertCountEquals(0)
        composeRule.onNodeWithText("Office PBX", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("SIM • +15559990000", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("recents_search_input").performTextInput("Office PBX")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Alice", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("recent_item_5559990000").assertCountEquals(0)
    }

    @Test
    fun callDetailsScreenDisplaysSipAccountNameAndShareRecordingButton() {
        val recFile = File(context.cacheDir, "test_call_rec.wav").apply {
            writeBytes(ByteArray(128) { 1 })
        }
        try {
            val sampleRecord = CallRecord(
                id = 42L,
                remoteAddress = "+15551234567",
                displayName = "Alice",
                isIncoming = false,
                isAnswered = true,
                duration = 75,
                timestamp = 1_700_000_000_000L,
                recordingPath = recFile.absolutePath,
                accountUser = "1001",
                accountId = "acc-office",
                accountName = "Office PBX"
            )
            composeRule.setContent {
                AppTheme {
                    CallDetailsScreen(
                        number = "+15551234567",
                        name = "Alice",
                        photoUri = null,
                        records = listOf(sampleRecord),
                        isFavorite = false,
                        isBlocked = false,
                        detectMessengers = { false to false },
                        onBack = {},
                        onCall = {},
                        onMessage = {},
                        onOpenChat = { _, _ -> },
                        onToggleFavorite = {},
                        onAddToCategory = {},
                        onToggleBlock = {},
                        onDeleteRecording = {}
                    )
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("SIP • Office PBX").assertIsDisplayed()
            composeRule.onNodeWithTag("call_details_share_rec_42").assertIsDisplayed()
        } finally {
            recFile.delete()
        }
    }

    @Test
    fun inCallScreenDisplaysSipAccountNameBadgeAndIncomingSubtitle() {
        composeRule.setContent {
            AppTheme {
                InCallScreen(
                    callState = SipCallState.Incoming("+15551234567"),
                    isMuted = false,
                    isSpeaker = false,
                    isRecording = false,
                    audioRoutes = emptyList(),
                    selectedAudioRoute = null,
                    callDurationProvider = { 0 },
                    accountUser = "Office PBX",
                    onAnswer = {},
                    onDecline = {},
                    onEndCall = {},
                    onMuteToggle = {},
                    onSpeakerToggle = {},
                    onHoldToggle = {},
                    onRecordingToggle = {},
                    onSelectAudioRoute = {},
                    onDtmf = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SIP • OFFICE PBX").assertIsDisplayed()
        composeRule.onNodeWithText("Incoming • SIP • Office PBX").assertIsDisplayed()
    }

    @Test
    fun settingsShowsGeneralRow() {
        var openedGeneral = false
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    currentAccount = null,
                    accountsCount = 0,
                    registrationState = SipRegistrationState.UNREGISTERED,
                    recordingsCount = 0,
                    onBack = {},
                    onOpenGeneral = { openedGeneral = true },
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
        composeRule.onNodeWithTag("settings_row_general").assertIsDisplayed().performClick()
        assertTrue(openedGeneral)
    }

    @Test
    fun generalSettingsScreenUpdatesDefaultOpeningTabAndFormattingPreferences() {
        composeRule.setContent {
            AppTheme {
                GeneralSettingsScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("general_tab_contacts").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(GeneralPrefs.TAB_CONTACTS, GeneralPrefs.getDefaultOpeningTab(context))
        assertEquals(Screen.Contacts.route, GeneralPrefs.getDefaultOpeningRoute(context))
        assertEquals(GeneralPrefs.TAB_RECENTS, GeneralPrefs.normalizeTab("favorites"))

        composeRule.onNodeWithTag("general_list")
            .performScrollToNode(hasTestTag("general_formatting_switch"))
        composeRule.onNodeWithTag("general_formatting_switch").performClick()
        composeRule.waitForIdle()
        assertFalse(GeneralPrefs.isNumberFormattingEnabled(context))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultOpeningScreenNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("general_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        GeneralPrefs.resetForTest()
        GeneralPrefs.init(context)
    }

    @Test
    fun configuredOpeningTabKeypadOpensKeypadDirectlyHidesFabAndHasThreeTabsWithoutFavorites() {
        GeneralPrefs.setDefaultOpeningTab(context, GeneralPrefs.TAB_KEYPAD)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("keypad_number_display").assertIsDisplayed()
            composeRule.onAllNodesWithTag("open_dialer_fab").assertCountEquals(0)
            composeRule.onNodeWithTag("nav_contacts").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_recents").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_keypad").assertIsDisplayed()
            composeRule.onAllNodesWithTag("nav_favorites").assertCountEquals(0)
        }
    }

    @Test
    fun configuredOpeningTabContactsShowsContactsAndGlobalDialerFabNavigatesToKeypad() {
        GeneralPrefs.setDefaultOpeningTab(context, GeneralPrefs.TAB_CONTACTS)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("nav_contacts").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_recents").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_keypad").assertIsDisplayed()
            composeRule.onAllNodesWithTag("nav_favorites").assertCountEquals(0)
            composeRule.onNodeWithTag("open_dialer_fab").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("keypad_number_display").assertIsDisplayed()
            composeRule.onAllNodesWithTag("open_dialer_fab").assertCountEquals(0)
        }
    }
}
