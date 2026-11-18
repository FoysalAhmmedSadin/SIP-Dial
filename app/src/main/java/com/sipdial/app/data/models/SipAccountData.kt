package com.sipdial.app.data.models

data class SipAccountData(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val authUsername: String = "",
    val password: String = "",
    val domain: String = "",
    val port: Int = 5060,
    val transport: String = "UDP",
    val proxy: String = "",
    val displayName: String = "",
    val registrationExpiry: Int = 60,
    val autoStart: Boolean = false,
    val enabled: Boolean = true
)
