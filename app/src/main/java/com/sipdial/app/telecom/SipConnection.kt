package com.sipdial.app.telecom

import android.content.Context
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import com.sipdial.app.sip.SipCallState
import com.sipdial.app.sip.SipEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SipConnection(private val context: Context) : Connection() {

    private val sipEngine: SipEngine by lazy { SipEngine.getInstance(context) }
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hasSeenActiveOrRinging = false

    init {
        connectionScope.launch {
            sipEngine.callState.collectLatest { state ->
                when (state) {
                    is SipCallState.Incoming -> {
                        hasSeenActiveOrRinging = true
                        if (this@SipConnection.state != STATE_RINGING) setRinging()
                    }
                    is SipCallState.Outgoing -> {
                        hasSeenActiveOrRinging = true
                        if (this@SipConnection.state != STATE_DIALING) setDialing()
                    }
                    is SipCallState.Connected -> {
                        hasSeenActiveOrRinging = true
                        if (this@SipConnection.state != STATE_ACTIVE) setActive()
                    }
                    is SipCallState.OnHold -> {
                        hasSeenActiveOrRinging = true
                        if (this@SipConnection.state != STATE_HOLDING) setOnHold()
                    }
                    is SipCallState.Ended -> {
                        if (hasSeenActiveOrRinging && this@SipConnection.state != STATE_DISCONNECTED) {
                            setDisconnected(DisconnectCause(DisconnectCause.REMOTE, state.reason))
                            destroy()
                            connectionScope.cancel()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onCallAudioStateChanged(state: CallAudioState?) {
        super.onCallAudioStateChanged(state)
        state ?: return
        sipEngine.setMute(state.isMuted)
        sipEngine.setSpeaker(state.route == CallAudioState.ROUTE_SPEAKER)
    }

    override fun onAnswer() {
        sipEngine.answerCall()
        setActive()
    }

    override fun onAnswer(videoState: Int) {
        onAnswer()
    }

    override fun onReject() {
        connectionScope.cancel()
        sipEngine.declineCall()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        connectionScope.cancel()
        sipEngine.endCall()
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onHold() {
        sipEngine.toggleHold()
        setOnHold()
    }

    override fun onUnhold() {
        sipEngine.toggleHold()
        setActive()
    }

    override fun onAbort() {
        connectionScope.cancel()
        sipEngine.endCall()
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    override fun onPlayDtmfTone(c: Char) {
        sipEngine.sendDtmf(c)
    }

    override fun onStopDtmfTone() {}
}
