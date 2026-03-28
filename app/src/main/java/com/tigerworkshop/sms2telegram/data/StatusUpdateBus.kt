package com.tigerworkshop.sms2telegram.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object StatusUpdateBus {
    private val _updates = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val updates: SharedFlow<Unit> = _updates.asSharedFlow()

    fun notifyUpdated() {
        _updates.tryEmit(Unit)
    }
}
