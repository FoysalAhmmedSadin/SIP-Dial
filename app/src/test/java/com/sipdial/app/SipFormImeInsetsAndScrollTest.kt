package com.sipdial.app

import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.data.models.SavedContact
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.ui.screens.AccountSetupScreen
import com.sipdial.app.ui.screens.SavedContactFormDialog
import com.sipdial.app.ui.theme.AppTheme
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
@Config(sdk = [36], qualifiers = "w412dp-h780dp")
class SipFormImeInsetsAndScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun dispatchImeInsets(hostView: View, imeHeightPx: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 48, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeHeightPx))
            .build()
        ViewCompat.dispatchApplyWindowInsets(hostView.rootView, insets)
    }

    @Test
    fun sipAccountFormResizesForImeAutoScrollsFocusedFieldsAndKeepsSaveAccessible() {
        var savedAccount: SipAccountData? = null
        var hostViewRef: View? = null

        val existing = listOf(
            SipAccountData(id = "a1", name = "Work Line", username = "1001", domain = "pbx1.example.com"),
            SipAccountData(id = "a2", name = "Home Line", username = "1002", domain = "pbx2.example.com"),
            SipAccountData(id = "a3", name = "Branch Line", username = "1003", domain = "pbx3.example.com")
        )

        composeRule.setContent {
            hostViewRef = LocalView.current
            AppTheme {
                AccountSetupScreen(
                    currentAccount = existing.first(),
                    accounts = existing,
                    registrationState = SipRegistrationState.REGISTERED,
                    registrationError = null,
                    accountStates = mapOf("a1" to SipRegistrationState.REGISTERED),
                    accountErrors = emptyMap(),
                    onBack = {},
                    onSelectAccount = {},
                    onToggleEnabled = { _, _ -> },
                    onDeleteAccount = {},
                    onSaveAndRegister = { savedAccount = it },
                    onUnregister = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            dispatchImeInsets(hostViewRef!!, 0)
        }
        composeRule.waitForIdle()

        val listBoundsBefore = composeRule.onNodeWithTag("sip_accounts_list").fetchSemanticsNode().boundsInRoot

        // Open Add SIP Account form when accounts exist above it
        composeRule.onNodeWithTag("add_sip_account_button").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()

        // Focus Account Label and open IME (simulate 320dp keyboard height)
        composeRule.onNodeWithTag("sip_form_name").performClick()
        composeRule.waitForIdle()
        val imePx = (320f * context.resources.displayMetrics.density).toInt()
        composeRule.runOnUiThread {
            dispatchImeInsets(hostViewRef!!, imePx)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()

        val listBoundsWithIme = composeRule.onNodeWithTag("sip_accounts_list").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "LazyColumn bottom (${listBoundsWithIme.bottom}) should shrink well above closed bottom (${listBoundsBefore.bottom})",
            listBoundsWithIme.bottom < listBoundsBefore.bottom - 150f
        )
        assertEquals(780f * context.resources.displayMetrics.density - imePx, listBoundsWithIme.bottom, 2f)

        // Type through Account Label -> Next -> Username -> Next -> Password -> Next -> Domain -> Next -> Port
        composeRule.onNodeWithTag("sip_form_name").performTextInput("Office SIP")
        composeRule.onNodeWithTag("sip_form_name").performImeAction()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sip_form_username").assertIsFocused()
        composeRule.onNodeWithTag("sip_form_username").performTextInput("2005")

        composeRule.onNodeWithTag("sip_form_username").performImeAction()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sip_form_password").assertIsFocused()
        composeRule.onNodeWithTag("sip_form_password").performTextInput("s3cr3tPass")

        composeRule.onNodeWithTag("sip_form_password").performImeAction()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sip_form_domain").assertIsFocused()
        composeRule.onNodeWithTag("sip_form_domain").performTextInput("sip.office.example")

        // Domain field must remain cleanly inside visible list viewport above IME
        val domainBounds = composeRule.onNodeWithTag("sip_form_domain").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Focused domain field (${domainBounds.top}..${domainBounds.bottom}) must stay inside list viewport (${listBoundsWithIme.top}..${listBoundsWithIme.bottom})",
            domainBounds.top >= listBoundsWithIme.top - 2f && domainBounds.bottom <= listBoundsWithIme.bottom + 2f
        )

        // Move Next to Port and verify Port is visible above IME
        composeRule.onNodeWithTag("sip_form_domain").performImeAction()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sip_form_port").assertIsFocused()
        composeRule.onNodeWithTag("sip_form_port").performTextReplacement("5080")
        val portBounds = composeRule.onNodeWithTag("sip_form_port").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Focused port field (${portBounds.bottom}) must stay visible above IME (${listBoundsWithIme.bottom})",
            portBounds.bottom <= listBoundsWithIme.bottom + 2f
        )

        // Expand Advanced Options while IME is still open and navigate into Outbound Proxy
        composeRule.onNodeWithTag("sip_form_advanced_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sip_form_proxy").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sip_form_proxy").performTextInput("proxy.office.example")

        // Verify previously entered values were NOT lost when keyboard opened or form scrolled
        assertEquals(
            "Office SIP",
            composeRule.onNodeWithTag("sip_form_name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        assertEquals(
            "2005",
            composeRule.onNodeWithTag("sip_form_username").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )

        // Access and click Save & Register WHILE IME is still open
        composeRule.onNodeWithTag("sip_form_save_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedAccount)
        assertEquals("Office SIP", savedAccount!!.name)
        assertEquals("2005", savedAccount!!.username)
        assertEquals("s3cr3tPass", savedAccount!!.password)
        assertEquals("sip.office.example", savedAccount!!.domain)
        assertEquals(5080, savedAccount!!.port)
        assertEquals("proxy.office.example", savedAccount!!.proxy)

        // Dismiss IME and verify list viewport restores to full height
        composeRule.runOnUiThread {
            dispatchImeInsets(hostViewRef!!, 0)
        }
        composeRule.waitForIdle()
        val listBoundsRestored = composeRule.onNodeWithTag("sip_accounts_list").fetchSemanticsNode().boundsInRoot
        assertEquals(listBoundsBefore.bottom, listBoundsRestored.bottom, 2f)
    }

    @Test
    fun editExistingSipAccountPopulatesFormScrollsIntoViewAndSavesUpdatesUnderIme() {
        var savedAccount: SipAccountData? = null
        var hostViewRef: View? = null

        val existing = listOf(
            SipAccountData(
                id = "a1",
                name = "Primary PBX",
                username = "1001",
                password = "old",
                domain = "pbx.example.com",
                port = 5060
            )
        )

        composeRule.setContent {
            hostViewRef = LocalView.current
            AppTheme {
                AccountSetupScreen(
                    currentAccount = existing.first(),
                    accounts = existing,
                    registrationState = SipRegistrationState.REGISTERED,
                    registrationError = null,
                    accountStates = mapOf("a1" to SipRegistrationState.REGISTERED),
                    accountErrors = emptyMap(),
                    onBack = {},
                    onSelectAccount = {},
                    onToggleEnabled = { _, _ -> },
                    onDeleteAccount = {},
                    onSaveAndRegister = { savedAccount = it },
                    onUnregister = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_sip_account_a1").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()

        val imePx = (300f * context.resources.displayMetrics.density).toInt()
        composeRule.runOnUiThread {
            dispatchImeInsets(hostViewRef!!, imePx)
        }
        composeRule.waitForIdle()

        assertEquals(
            "Primary PBX",
            composeRule.onNodeWithTag("sip_form_name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        composeRule.onNodeWithTag("sip_form_domain").performScrollTo().performTextReplacement("new.pbx.example.com")
        composeRule.onNodeWithTag("sip_form_save_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedAccount)
        assertEquals("a1", savedAccount!!.id)
        assertEquals("new.pbx.example.com", savedAccount!!.domain)
    }

    @Test
    fun savedContactFormDialogMovesNextBetweenFieldsAndSavesAboveIme() {
        var savedContact: SavedContact? = null

        composeRule.setContent {
            AppTheme {
                SavedContactFormDialog(
                    draft = SavedContact(category = SavedContact.CATEGORY_CLIENT, name = "", number = ""),
                    detectMessengers = { false to false },
                    onSave = { savedContact = it },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("contact_form_name").performClick()
        composeRule.onNodeWithTag("contact_form_name").performTextInput("Client Alpha")
        composeRule.onNodeWithTag("contact_form_name").performImeAction()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("contact_form_number").assertIsFocused()
        composeRule.onNodeWithTag("contact_form_number").performTextInput("01711223344")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("contact_form_save_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedContact)
        assertEquals("Client Alpha", savedContact!!.name)
        assertEquals("01711223344", savedContact!!.number)
    }
}
