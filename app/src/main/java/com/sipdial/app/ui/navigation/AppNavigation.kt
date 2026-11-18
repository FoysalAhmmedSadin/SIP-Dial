package com.sipdial.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Favorites : Screen("favorites")
    data object Recents : Screen("recents")
    data object Contacts : Screen("contacts")
    data object Keypad : Screen("keypad")
    data object Settings : Screen("settings")
    data object Appearance : Screen("appearance")
    data object SipAccounts : Screen("sip_accounts")
    data object CallingSettings : Screen("calling_settings")
    data object GeneralSettings : Screen("general_settings")
    data object AudioCodecSettings : Screen("audio_codec_settings")
    data object NotificationSettings : Screen("notification_settings")
    data object PowerNetworkSettings : Screen("power_network_settings")
    data object PrivacySecuritySettings : Screen("privacy_security_settings")
    data object BackupRestore : Screen("backup_restore")
    data object Recordings : Screen("recordings")
    data object InCall : Screen("in_call")
    data object About : Screen("about")

    data object CallDetails : Screen("call_details/{number}") {
        fun createRoute(number: String): String = "call_details/${Uri.encode(number)}"
    }

    companion object {
        const val ROUTE_PRIMARY_TABS = "primary_tabs"
        const val ROUTE_FAVORITES = "favorites"
        const val ROUTE_RECENTS = "recents"
        const val ROUTE_CONTACTS = "contacts"
        const val ROUTE_KEYPAD = "keypad"

        val primaryTabs: Set<String> = setOf(
            ROUTE_CONTACTS,
            ROUTE_RECENTS,
            ROUTE_KEYPAD
        )
    }
}
