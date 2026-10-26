package com.vespelabs.fixit.push

import com.vespelabs.fixit.core.data.push.PushEvent
import com.vespelabs.fixit.core.data.push.parsePushEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The tapped notice MainActivity keeps until FixItApp applies it (design 14b.6). Only the (type, requestId) pair
 * travels in the Intent; the destination is resolved later with the current role.
 */
class PendingPushEvent {
    private val pending = MutableStateFlow<PushEvent?>(null)

    val event: StateFlow<PushEvent?> = pending.asStateFlow()

    /** A recreation (rotation, restored state) never reapplies the Intent that started the activity. */
    fun onCreate(restored: Boolean, type: String?, requestId: String?) {
        if (!restored) offer(type, requestId)
    }

    /** singleTop: a notice tapped while the activity is on top arrives here. */
    fun onNewIntent(type: String?, requestId: String?) = offer(type, requestId)

    /** Clears [event] only if it is still the pending one, so a newer tap is never lost. */
    fun consume(event: PushEvent) {
        pending.compareAndSet(event, null)
    }

    private fun offer(type: String?, requestId: String?) {
        parsePushEvent(type, requestId)?.let { pending.value = it }
    }
}
