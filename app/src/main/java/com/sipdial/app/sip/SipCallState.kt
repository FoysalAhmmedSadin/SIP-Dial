package com.sipdial.app.sip

import org.linphone.core.Call

enum class SipRegistrationState(val label: String) {
    UNREGISTERED("Not Registered"),
    REGISTERING("Registering"),
    REGISTERED("Registered"),
    ERROR("Registration Failed"),
    DISABLED("Disabled"),
    NETWORK_UNAVAILABLE("Network Unavailable")
}

enum class NetworkTransportType(val label: String) {
    NONE("Offline"),
    WIFI("Wi-Fi"),
    MOBILE_DATA("Mobile Data"),
    ETHERNET("Ethernet"),
    OTHER("Online")
}

enum class AudioRouteType {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET
}

data class AudioRouteOption(
    val id: String,
    val name: String,
    val type: AudioRouteType
)

data class CallQualityStats(
    val codec: String = "",
    val rttMs: Int = 0,
    val jitterMs: Int = 0,
    val lossPercent: Float = 0f,
    val downKbps: Int = 0,
    val upKbps: Int = 0,
    val rating: Float = 5f,
    val micDb: Float = 0f,
    val inputDevice: String = "",
    val micEnabled: Boolean = true
)

sealed class SipCallState {
    object Idle : SipCallState()
    data class Incoming(val remoteAddress: String, val call: Call? = null) : SipCallState()
    data class Outgoing(val remoteAddress: String, val call: Call) : SipCallState()
    data class Connected(val remoteAddress: String, val call: Call) : SipCallState()
    data class OnHold(val remoteAddress: String, val call: Call) : SipCallState()
    data class Ended(val remoteAddress: String, val reason: String = "") : SipCallState()
}
