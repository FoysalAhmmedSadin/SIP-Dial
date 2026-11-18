package com.sipdial.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.ui.screens.KeypadScreen
import com.sipdial.app.ui.screens.RecentsScreen
import com.sipdial.app.ui.screens.buildKeypadSuggestionIndex
import com.sipdial.app.ui.screens.filterKeypadSuggestions
import com.sipdial.app.ui.screens.filterKeypadSuggestionsIndexed
import com.sipdial.app.ui.theme.AppTheme
import com.sipdial.app.util.ContactsHelper
import com.sipdial.app.util.DeviceContact
import com.sipdial.app.util.GeneralPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class KeypadSelectionAndNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("general_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        GeneralPrefs.resetForTest()
        GeneralPrefs.init(context)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun singleDoubleAndRepeatedTapsNeverTriggerPlatformImeOrSoftwareKeyboardShow() {
        var imeStartCount = 0
        var keyboardShowCount = 0
        var keyboardHideCount = 0
        var dialValue by mutableStateOf(TextFieldValue("+8801940401566"))

        val spyKeyboard = object : SoftwareKeyboardController {
            override fun show() {
                keyboardShowCount++
            }

            override fun hide() {
                keyboardHideCount++
            }
        }
        val spyPlatformInterceptor = PlatformTextInputInterceptor { request, nextHandler ->
            imeStartCount++
            nextHandler.startInputMethod(request)
        }

        composeRule.setContent {
            AppTheme {
                InterceptPlatformTextInput(interceptor = spyPlatformInterceptor) {
                    CompositionLocalProvider(LocalSoftwareKeyboardController provides spyKeyboard) {
                        KeypadScreen(
                            registrationState = SipRegistrationState.UNREGISTERED,
                            currentAccount = null,
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
            }
        }
        composeRule.waitForIdle()

        val field = composeRule.onNodeWithTag("keypad_number_display")
        field.performTouchInput { click() }
        composeRule.waitForIdle()
        field.performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        field.performTouchInput {
            click()
            click()
            click()
        }
        composeRule.waitForIdle()

        assertEquals(0, imeStartCount)
        assertEquals(0, keyboardShowCount)
        assertTrue(keyboardHideCount >= 1)

        // Custom dialpad buttons still edit text even with IME suppressed
        composeRule.onNodeWithTag("dial_key_9").performClick()
        composeRule.waitForIdle()
        assertTrue(dialValue.text.contains("9"))
        assertEquals(0, imeStartCount)
        assertEquals(0, keyboardShowCount)
    }

    @Test
    fun textSelectionAndClipboardActionsSupportCopyCutPastePartialReplaceAndCursorInsert() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("phone", "+880 1711-223344"))

        var dialValue by mutableStateOf(TextFieldValue("01940401566", TextRange(11)))

        composeRule.setContent {
            AppTheme {
                KeypadScreen(
                    registrationState = SipRegistrationState.UNREGISTERED,
                    currentAccount = null,
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

        val field = composeRule.onNodeWithTag("keypad_number_display")
        field.performTouchInput { click() }
        composeRule.waitForIdle()

        // Select partial range "01940" and Copy via native semantics
        field.performSemanticsAction(SemanticsActions.SetSelection) { action ->
            action(0, 5, true)
        }
        composeRule.waitForIdle()
        field.performSemanticsAction(SemanticsActions.CopyText) { action -> action() }
        composeRule.waitForIdle()
        assertEquals("01940", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Re-select partial range "01940" and Cut via native semantics
        field.performSemanticsAction(SemanticsActions.SetSelection) { action ->
            action(0, 5, true)
        }
        composeRule.waitForIdle()
        field.performSemanticsAction(SemanticsActions.CutText) { action -> action() }
        composeRule.waitForIdle()
        assertEquals("401566", dialValue.text)

        // Dialpad digit inserts cleanly at current cursor position (index 0)
        composeRule.onNodeWithTag("dial_key_9").performClick()
        composeRule.waitForIdle()
        assertEquals("9401566", dialValue.text)
        assertEquals(TextRange(1), dialValue.selection)

        // Select All and Paste formatted number from clipboard -> normalized cleanly
        clipboard.setPrimaryClip(ClipData.newPlainText("num", "+880 1711-223344"))
        field.performSemanticsAction(SemanticsActions.SetSelection) { action ->
            action(0, dialValue.text.length, true)
        }
        composeRule.waitForIdle()
        field.performSemanticsAction(SemanticsActions.PasteText) { action -> action() }
        composeRule.waitForIdle()
        assertEquals("+8801711223344", dialValue.text)
        assertEquals(TextRange("+8801711223344".length), dialValue.selection)

        // Empty field Paste works directly
        dialValue = TextFieldValue("")
        composeRule.waitForIdle()
        field.performSemanticsAction(SemanticsActions.PasteText) { action -> action() }
        composeRule.waitForIdle()
        assertEquals("+8801711223344", dialValue.text)
    }

    @Config(sdk = [27])
    @Test
    fun longPressOnKeypadFieldShowsNativeSelectionMenuAndSupportsEmptyPaste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("phone", "01940-401566"))

        var dialValue by mutableStateOf(TextFieldValue(""))
        var menuShownCount = 0
        var lastPaste: (() -> Unit)? = null
        var lastCopy: (() -> Unit)? = null
        var lastCut: (() -> Unit)? = null
        var lastSelectAll: (() -> Unit)? = null

        val spyToolbar = object : TextToolbar {
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden
                private set

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                menuShownCount++
                status = TextToolbarStatus.Shown
                lastCopy = onCopyRequested
                lastPaste = onPasteRequested
                lastCut = onCutRequested
                lastSelectAll = onSelectAllRequested
            }

            override fun hide() {
                status = TextToolbarStatus.Hidden
            }
        }

        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalTextToolbar provides spyToolbar) {
                    KeypadScreen(
                        registrationState = SipRegistrationState.UNREGISTERED,
                        currentAccount = null,
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
        }
        composeRule.waitForIdle()

        val field = composeRule.onNodeWithTag("keypad_number_display")
        // Long-pressing an empty field shows Paste
        field.performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertTrue(menuShownCount >= 1)
        assertNotNull("Paste should be exposed on empty field long-press", lastPaste)
        lastPaste!!.invoke()
        composeRule.waitForIdle()
        assertEquals("01940401566", dialValue.text)

        // Long-pressing a single-token populated field selects it and exposes Copy/Cut/Paste
        field.performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertNotNull(lastCopy)
        assertNotNull(lastCut)
        assertNotNull(lastPaste)

        // Copy selected token via toolbar action
        lastCopy!!.invoke()
        composeRule.waitForIdle()
        assertEquals("01940401566", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // With a partial word selection (e.g. dialing "*123#456"), long-press menu exposes Select All as well
        dialValue = TextFieldValue("*123#456", TextRange(8))
        composeRule.waitForIdle()
        field.performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertNotNull("Copy should be exposed", lastCopy)
        assertNotNull("Cut should be exposed", lastCut)
        assertNotNull("Paste should be exposed", lastPaste)
        assertNotNull("Select All should be exposed on partial selection", lastSelectAll)

        // Invoke Select All then Cut via toolbar callbacks
        lastSelectAll!!.invoke()
        composeRule.waitForIdle()
        assertEquals(TextRange(0, "*123#456".length), dialValue.selection)
        lastCut!!.invoke()
        composeRule.waitForIdle()
        assertEquals("", dialValue.text)
    }

    @Test
    fun contactsPhoneIndexMatchesLinearContactForAcrossFormats() {
        val contacts = listOf(
            DeviceContact("1", "Alice", "+880 1940-401566", "+8801940401566"),
            DeviceContact("2", "Bob", "01711-223344", "01711223344"),
            DeviceContact("3", "Office", "5551234", "5551234"),
            DeviceContact("4", "Alice SIP", "alice.sip", "alice.sip")
        )
        val index = ContactsHelper.buildPhoneIndex(contacts)

        val probes = listOf(
            "+8801940401566",
            "008801940401566",
            "01940401566",
            "sip:+8801940401566@pbx.example.com",
            "+8801711223344",
            "01711-223344",
            "5551234",
            "+18005551234",
            "sip:alice.sip@pbx.example.com",
            "9990001112"
        )
        for (probe in probes) {
            assertEquals(
                "Mismatch for $probe",
                ContactsHelper.contactFor(probe, contacts),
                index.find(probe)
            )
        }
    }

    @Test
    fun indexedKeypadSuggestionsMatchLegacySuggestionFilter() {
        val contacts = listOf(
            DeviceContact("1", "Foysal Sadin", "01940-401566", "01940401566"),
            DeviceContact("2", "Rahim", "+880 1711-009988", "+8801711009988"),
            DeviceContact("3", "Karim", "1001", "1001")
        )
        val indexed = buildKeypadSuggestionIndex(contacts)
        for (q in listOf("", "0194", "369", "724", "1001", "+880")) {
            assertEquals(
                filterKeypadSuggestions(contacts, q),
                filterKeypadSuggestionsIndexed(indexed, q)
            )
        }
    }

    @Test
    fun recentsScreenPreservesLazyListStateAcrossRecomposition() {
        val records = (1..30).map { i ->
            CallRecord(
                id = i.toLong(),
                remoteAddress = "+88017000000${i.toString().padStart(2, '0')}",
                displayName = "Caller $i",
                isIncoming = i % 2 == 0,
                isAnswered = true,
                duration = 30,
                timestamp = 1_700_000_000_000L - i * 60_000L
            )
        }
        val listState = LazyListState(firstVisibleItemIndex = 12, firstVisibleItemScrollOffset = 24)
        var showRecents by mutableStateOf(true)

        composeRule.setContent {
            AppTheme {
                if (showRecents) {
                    RecentsScreen(
                        recentCalls = records,
                        contacts = emptyList(),
                        blockedNumbers = emptySet(),
                        onCall = {},
                        onOpenDetails = {},
                        onDeleteCalls = {},
                        onClearHistory = {},
                        onBlockNumber = {},
                        onUnblockNumber = {},
                        listState = listState
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(12, listState.firstVisibleItemIndex)

        showRecents = false
        composeRule.waitForIdle()
        showRecents = true
        composeRule.waitForIdle()
        assertEquals(12, listState.firstVisibleItemIndex)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class RetainedPrimaryTabsNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("general_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        GeneralPrefs.resetForTest()
        GeneralPrefs.init(context)
        GeneralPrefs.setDefaultOpeningTab(context, GeneralPrefs.TAB_RECENTS)
    }

    @Test
    fun switchingPrimaryTabsPreservesDialpadDigitsAndUpdatesActiveSemanticsCleanly() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()

            // Switch Recents -> Keypad and enter digits
            composeRule.onNodeWithTag("nav_keypad").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("keypad_number_display").assertIsDisplayed()
            composeRule.onNodeWithTag("dial_key_1").performClick()
            composeRule.onNodeWithTag("dial_key_2").performClick()
            composeRule.onNodeWithTag("dial_key_3").performClick()
            composeRule.waitForIdle()

            // Switch Keypad -> Contacts -> Recents -> Keypad
            composeRule.onNodeWithTag("nav_contacts").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("keypad_number_display").assertIsNotDisplayed()
            composeRule.onNodeWithTag("open_dialer_fab").assertIsDisplayed()

            composeRule.onNodeWithTag("nav_recents").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("keypad_number_display").assertIsNotDisplayed()

            composeRule.onNodeWithTag("nav_keypad").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("keypad_number_display").assertIsDisplayed()
            // Entered digits "123" remain preserved across repeated tab switches
            composeRule.onNodeWithTag("dial_key_4").performClick()
            composeRule.waitForIdle()
            val editable = composeRule.onNodeWithTag("keypad_number_display")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
            assertEquals("1234", editable.text)
        }
    }
}
