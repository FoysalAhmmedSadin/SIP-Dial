package com.sipdial.app

import androidx.test.core.app.ActivityScenario
import com.sipdial.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityStartupTest {

    @Test
    fun screenRoutesInitializeWithoutCircularClinit() {
        assertEquals("recents", Screen.Recents.route)
        assertEquals("keypad", Screen.Keypad.route)
        assertEquals(
            setOf(Screen.Contacts.route, Screen.Recents.route, Screen.Keypad.route),
            Screen.primaryTabs
        )
        assertTrue(Screen.Favorites.route !in Screen.primaryTabs)
    }

    @Test
    fun mainActivityLaunchesAndComposesWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("com.sipdial.app", activity.packageName)
            }
        }
    }
}
