package com.sipdial.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.Core
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserAgentConfigTest {

    @Test
    fun sipUserAgentConstantsMatchAppVersion200() {
        assertEquals("2.0.0", BuildConfig.VERSION_NAME)
        assertEquals("SIP Dial", SipDialApplication.SIP_USER_AGENT_NAME)
        assertEquals("2.0.0", SipDialApplication.SIP_USER_AGENT_VERSION)
    }

    @Test
    fun applyUserAgentInvokesOfficialLinphoneCoreSetUserAgentApiWithoutDuplicateHeaders() {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        var currentUserAgent = ""

        val fakeCore = Proxy.newProxyInstance(
            Core::class.java.classLoader,
            arrayOf(Core::class.java)
        ) { _, method, args ->
            val argList = args?.toList().orEmpty()
            calls += method.name to argList
            when (method.name) {
                "setUserAgent" -> {
                    val name = argList.getOrNull(0) as? String ?: ""
                    val version = argList.getOrNull(1) as? String ?: ""
                    currentUserAgent = "$name/$version"
                    Unit
                }
                "getUserAgent" -> currentUserAgent
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    Double::class.javaPrimitiveType -> 0.0
                    String::class.java -> ""
                    else -> null
                }
            }
        } as Core

        SipDialApplication.applyUserAgent(fakeCore)
        SipDialApplication.applyUserAgent(fakeCore)

        val setCalls = calls.filter { it.first == "setUserAgent" }
        assertEquals(2, setCalls.size)
        assertEquals(listOf("SIP Dial", "2.0.0"), setCalls[0].second)
        assertEquals(listOf("SIP Dial", "2.0.0"), setCalls[1].second)
        assertEquals("SIP Dial/2.0.0", fakeCore.userAgent)
        assertFalse(calls.any { it.first.contains("Header", ignoreCase = true) })
    }
}
