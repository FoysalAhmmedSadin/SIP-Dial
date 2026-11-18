package com.sipdial.app.telecom

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.sipdial.app.sip.SipCallState
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.util.CallerIdFormatter

class SipConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "SipConnectionService"
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val rawUri = request?.address
        val targetNumber = Uri.decode(rawUri?.schemeSpecificPart ?: "").trim()
        val displayNumber = CallerIdFormatter.extractNumber(targetNumber)

        val connection = SipConnection(applicationContext).apply {
            setAddress(
                rawUri ?: Uri.fromParts("tel", displayNumber, null),
                TelecomManager.PRESENTATION_ALLOWED
            )
            setCallerDisplayName(
                displayNumber.ifEmpty { "Unknown" },
                TelecomManager.PRESENTATION_ALLOWED
            )
            connectionCapabilities = Connection.CAPABILITY_MUTE or
                Connection.CAPABILITY_HOLD or
                Connection.CAPABILITY_SUPPORT_HOLD
            audioModeIsVoip = true
            setDialing()
        }

        val sipEngine = SipEngine.getInstance(applicationContext)
        val alreadyCalling = sipEngine.callState.value is SipCallState.Outgoing ||
            sipEngine.callState.value is SipCallState.Connected

        if (!alreadyCalling && !sipEngine.makeCall(targetNumber)) {
            connection.setDisconnected(
                DisconnectCause(
                    DisconnectCause.ERROR,
                    "SIP call failed. Please make sure your SIP account is registered."
                )
            )
            connection.destroy()
            Log.e(TAG, "Failed to initiate SIP call: $targetNumber")
        }

        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val extras = request?.extras
        val callerAddress = extras?.getString("caller_address")
            ?: request?.address?.schemeSpecificPart
            ?: "Unknown"
        val cleanCaller = CallerIdFormatter.extractNumber(callerAddress).ifEmpty { "Unknown" }

        return SipConnection(applicationContext).apply {
            setAddress(
                Uri.fromParts("sip", cleanCaller, null),
                TelecomManager.PRESENTATION_ALLOWED
            )
            setCallerDisplayName(cleanCaller, TelecomManager.PRESENTATION_ALLOWED)
            connectionCapabilities = Connection.CAPABILITY_MUTE or
                Connection.CAPABILITY_HOLD or
                Connection.CAPABILITY_SUPPORT_HOLD
            audioModeIsVoip = true
            setRinging()
        }
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateOutgoingConnectionFailed: ${request?.address}")
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateIncomingConnectionFailed")
    }
}
