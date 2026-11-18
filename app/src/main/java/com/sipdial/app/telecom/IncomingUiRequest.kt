package com.sipdial.app.telecom

import kotlinx.coroutines.flow.MutableStateFlow

object IncomingUiRequest {
    val tick = MutableStateFlow(0)

    @Volatile
    var pending = false

    fun request() {
        pending = true
        tick.value = tick.value + 1
    }

    fun clear() {
        pending = false
    }
}
