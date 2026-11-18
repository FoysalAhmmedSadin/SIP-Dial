package com.sipdial.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.telecom.sim.DialCodes
import com.sipdial.app.ui.screens.callNumberKey
import com.sipdial.app.ui.screens.normalizeDialInput
import com.sipdial.app.util.BlockList
import com.sipdial.app.util.ContactsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DialCodesAndBlockListTest {

    @Test
    fun dialCodesRecognizeUssdAndSecretCodes() {
        assertTrue(DialCodes.isCode("*#06#"))
        assertTrue(DialCodes.isAutoCode("*#06#"))
        assertTrue(DialCodes.isCode("*#*#4636#*#*"))
        assertTrue(DialCodes.isAutoCode("*#*#4636#*#*"))
        assertTrue(DialCodes.isCode("*121#"))
        assertFalse(DialCodes.isAutoCode("*121#"))
        assertFalse(DialCodes.isCode("+15551234567"))
    }

    @Test
    fun blockListNormalizesTenDigitTailAndPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BlockList.init(context)
        BlockList.add(context, "+1 (555) 987-6543")
        assertTrue(BlockList.isBlocked(context, "sip:5559876543@example.com"))
        assertTrue(BlockList.isBlocked(context, "555-987-6543"))
        assertFalse(BlockList.isBlocked(context, "555-000-1111"))
        BlockList.remove(context, "5559876543")
        assertFalse(BlockList.isBlocked(context, "5559876543"))
    }

    @Test
    fun dialInputAndChatDigitsNormalizeCorrectly() {
        assertEquals("+15551234567", normalizeDialInput(" +1 (555) 123-4567 "))
        assertEquals("*#06#", normalizeDialInput("*#06#"))
        assertEquals("5551234567", callNumberKey("sip:+15551234567@sip.example.com"))
        assertEquals("8801712345678", ContactsHelper.chatDigits("01712345678"))
        assertEquals("15551234567", ContactsHelper.chatDigits("+1 (555) 123-4567"))
    }
}
