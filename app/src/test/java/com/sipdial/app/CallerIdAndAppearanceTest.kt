package com.sipdial.app

import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.ui.screens.AppearanceSettingsScreen
import com.sipdial.app.ui.theme.AccentColor
import com.sipdial.app.ui.theme.AppTheme
import com.sipdial.app.ui.theme.ThemePreferences
import com.sipdial.app.util.CallerIdFormatter
import com.sipdial.app.util.ContactsHelper
import com.sipdial.app.util.DeviceContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallerIdAndAppearanceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ThemePreferences.setAccentColor(context, AccentColor.GREEN)
    }

    @Test
    fun extractNumberCleansSipTelEncodedPlusAndParametersWithoutBogusPrefix() {
        assertEquals("1001", CallerIdFormatter.extractNumber("sip:1001@pbx.example.com"))
        assertEquals(
            "+8801712345678",
            CallerIdFormatter.extractNumber("sips:+8801712345678@pbx.example.com:5061;transport=tls")
        )
        assertEquals(
            "+8801712345678",
            CallerIdFormatter.extractNumber("tel:+8801712345678;phone-context=+880")
        )
        assertEquals(
            "+8801712345678",
            CallerIdFormatter.extractNumber("sip:%2B8801712345678@pbx.example.com;user=phone")
        )
        assertEquals(
            "+8801712345678",
            CallerIdFormatter.extractNumber("\"Alice\" <sip:+8801712345678@pbx.example.com:5060;user=phone>")
        )
        assertEquals(
            "01712345678",
            CallerIdFormatter.extractNumber("sip:01712345678:5060@pbx.example.com")
        )
    }

    @Test
    fun contactMatchingRejectsShortCodeFalsePositivesAndMatchesEncodedCaller() {
        val contacts = listOf(
            DeviceContact(
                id = "1",
                name = "Carrier Helpdesk",
                number = "121",
                normalizedDigits = "121"
            ),
            DeviceContact(
                id = "2",
                name = "Nadia Karim",
                number = "01712345678",
                normalizedDigits = "01712345678"
            )
        )

        val matched = ContactsHelper.contactFor("sip:%2B8801712345678@pbx.example.com;user=phone", contacts)
        assertEquals("Nadia Karim", matched?.name)

        val falsePositive = ContactsHelper.contactFor("sip:+8801812340121@pbx.example.com", contacts)
        assertNull(falsePositive)

        val exactShort = ContactsHelper.contactFor("sip:121@pbx.example.com", contacts)
        assertEquals("Carrier Helpdesk", exactShort?.name)

        assertTrue(CallerIdFormatter.numbersMatch("+8801712345678", "01712345678"))
        assertFalse(CallerIdFormatter.numbersMatch("+8801812340121", "121"))
    }

    @Test
    fun appearanceSettingsDisplaysAllAccentSwatchesIncludingOrangeOnNarrowWidth() {
        composeRule.setContent {
            AppTheme {
                AppearanceSettingsScreen(
                    onBack = {},
                    modifier = Modifier.width(320.dp)
                )
            }
        }
        composeRule.waitForIdle()

        AccentColor.entries.forEach { accent ->
            composeRule.onNodeWithTag("accent_swatch_${accent.key}").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("accent_swatch_orange").performClick()
        composeRule.waitForIdle()
        assertEquals(AccentColor.ORANGE, ThemePreferences.state.value.accentColor)
        composeRule.onNodeWithText("Orange").assertIsDisplayed()
    }
}
