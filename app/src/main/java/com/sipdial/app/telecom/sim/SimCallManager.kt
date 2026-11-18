package com.sipdial.app.telecom.sim

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow

object SimCallManager {
    val calls = MutableStateFlow<List<Call>>(emptyList())
    val tick = MutableStateFlow(0)
    val audioState = MutableStateFlow<CallAudioState?>(null)
    val activityVisible = MutableStateFlow(false)
    val recording = MutableStateFlow(false)

    @Volatile
    var service: InCallService? = null

    private val callbacks = HashMap<Call, Call.Callback>()

    fun add(call: Call) {
        if (calls.value.contains(call)) return
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                tick.value = tick.value + 1
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                tick.value = tick.value + 1
            }
        }
        callbacks[call] = cb
        call.registerCallback(cb)
        calls.value = calls.value + call
    }

    fun remove(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        calls.value = calls.value - call
        tick.value = tick.value + 1
    }

    fun ringingCall(): Call? = calls.value.firstOrNull { it.compatState() == Call.STATE_RINGING }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(call: Call) = call.reject(false, null)
    fun disconnect(call: Call) = call.disconnect()
    fun hold(call: Call) = call.hold()
    fun unhold(call: Call) = call.unhold()

    fun playDtmf(call: Call, c: Char) {
        call.playDtmfTone(c)
        call.stopDtmfTone()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun setSpeaker(on: Boolean) {
        val targetRoute = if (on) {
            CallAudioState.ROUTE_SPEAKER
        } else {
            val mask = audioState.value?.supportedRouteMask ?: 0
            when {
                (mask and CallAudioState.ROUTE_BLUETOOTH) != 0 -> CallAudioState.ROUTE_BLUETOOTH
                (mask and CallAudioState.ROUTE_WIRED_HEADSET) != 0 -> CallAudioState.ROUTE_WIRED_HEADSET
                else -> CallAudioState.ROUTE_EARPIECE
            }
        }
        setAudioRouteCompat(targetRoute)
    }

    @Suppress("DEPRECATION")
    private fun setAudioRouteCompat(route: Int) {
        service?.setAudioRoute(route)
    }
}

internal fun Call.compatState(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        details.state
    } else {
        @Suppress("DEPRECATION")
        state
    }
