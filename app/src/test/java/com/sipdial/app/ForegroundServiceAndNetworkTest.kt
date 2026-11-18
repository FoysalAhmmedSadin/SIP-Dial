package com.sipdial.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.service.SipForegroundService
import com.sipdial.app.sip.NetworkTransportType
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.sip.SipRegistrationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForegroundServiceAndNetworkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun buildStatusContentReflectsWifiMobileDataAndOfflineStates() {
        val (wifiTitle, wifiText) = SipForegroundService.buildStatusContent(
            regState = SipRegistrationState.REGISTERED,
            transport = NetworkTransportType.WIFI,
            fallbackTitle = "SIP Dial",
            fallbackText = "Ready"
        )
        assertEquals("SIP Active (Wi-Fi)", wifiTitle)
        assertTrue(wifiText.contains("Wi-Fi"))

        val (cellTitle, cellText) = SipForegroundService.buildStatusContent(
            regState = SipRegistrationState.REGISTERING,
            transport = NetworkTransportType.MOBILE_DATA,
            fallbackTitle = "SIP Dial",
            fallbackText = "Ready"
        )
        assertEquals("Connecting SIP (Mobile Data)...", cellTitle)
        assertTrue(cellText.contains("Mobile Data"))

        val (offlineTitle, offlineText) = SipForegroundService.buildStatusContent(
            regState = SipRegistrationState.NETWORK_UNAVAILABLE,
            transport = NetworkTransportType.NONE,
            fallbackTitle = "SIP Dial",
            fallbackText = "Ready"
        )
        assertEquals("Waiting for Network", offlineTitle)
        assertTrue(offlineText.contains("Wi-Fi") && offlineText.contains("Mobile Data"))
    }

    @Test
    fun sipForegroundServiceStartsAndStopsCleanlyInBackgroundMode() {
        val controller = Robolectric.buildService(
            SipForegroundService::class.java,
            Intent(context, SipForegroundService::class.java).apply {
                action = SipForegroundService.ACTION_START
            }
        ).create().startCommand(0, 1)

        assertTrue(SipForegroundService.isActive())

        controller.get().onStartCommand(
            Intent(context, SipForegroundService::class.java).apply {
                action = SipForegroundService.ACTION_STOP
            },
            0,
            2
        )
        controller.destroy()

        assertFalse(SipForegroundService.isActive())
    }

    @Test
    fun sipEngineUpdatesRegistrationStatusOnOfflineAndWifiToMobileDataHandover() {
        val engine = SipEngine.getInstance(context)
        val sampleAccount = SipAccountData(
            id = "test_acc_1",
            name = "Office",
            username = "1001",
            password = "secret",
            domain = "pbx.example.com",
            enabled = true
        )

        // Start offline -> marks enabled account NETWORK_UNAVAILABLE and NONE transport
        engine.register(sampleAccount, select = true)
        engine.handleNetworkConnectivityChange(
            available = false,
            transport = NetworkTransportType.NONE,
            networkSwitched = false
        )
        assertEquals(NetworkTransportType.NONE, engine.networkType.value)
        assertEquals(SipRegistrationState.NETWORK_UNAVAILABLE, engine.registrationState.value)

        // Come online over Wi-Fi
        engine.handleNetworkConnectivityChange(
            available = true,
            transport = NetworkTransportType.WIFI,
            networkSwitched = false
        )
        assertEquals(NetworkTransportType.WIFI, engine.networkType.value)

        // Switch Wi-Fi -> Mobile Data while already online: must recognize transport handover
        engine.handleNetworkConnectivityChange(
            available = true,
            transport = NetworkTransportType.MOBILE_DATA,
            networkSwitched = true
        )
        assertEquals(NetworkTransportType.MOBILE_DATA, engine.networkType.value)
        assertEquals(SipRegistrationState.REGISTERING, engine.accountStates.value[sampleAccount.id])
    }
}
