package com.vespelabs.fixit.push

import com.vespelabs.fixit.core.data.push.PushEvent
import com.vespelabs.fixit.core.data.push.PushEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Design 14b.6: what MainActivity keeps from the notice Intent until FixItApp consumes it. */
class PendingPushEventTest {
    private val pending = PendingPushEvent()
    private val accepted = PushEvent(PushEventType.REQUEST_ACCEPTED, "r1")

    @Test
    fun `given a fresh start from a notice when the activity is created then the event is pending`() {
        pending.onCreate(restored = false, type = "REQUEST_ACCEPTED", requestId = "r1")

        assertEquals(accepted, pending.event.value)
    }

    @Test
    fun `given a recreation after rotation when the activity is created again then the event is not reapplied`() {
        pending.onCreate(restored = true, type = "REQUEST_ACCEPTED", requestId = "r1")

        assertNull(pending.event.value)
    }

    @Test
    fun `given the activity on top when a notice is tapped then onNewIntent makes it pending`() {
        pending.onCreate(restored = false, type = null, requestId = null) // ordinary launcher start

        pending.onNewIntent(type = "NEW_REQUEST", requestId = "r2")

        assertEquals(PushEvent(PushEventType.NEW_REQUEST, "r2"), pending.event.value)
    }

    @Test
    fun `given extras that are not a notice when the activity starts then nothing is pending`() {
        pending.onCreate(restored = false, type = "SOMETHING", requestId = "r1")
        pending.onNewIntent(type = "REQUEST_CANCELLED", requestId = null)

        assertNull(pending.event.value)
    }

    @Test
    fun `given a pending event when it is consumed then nothing is pending, but a newer event is kept`() {
        pending.onCreate(restored = false, type = "REQUEST_ACCEPTED", requestId = "r1")
        pending.consume(accepted)
        assertNull(pending.event.value)

        pending.onNewIntent(type = "REQUEST_ACCEPTED", requestId = "r1")
        pending.onNewIntent(type = "NEW_REQUEST", requestId = "r2")
        pending.consume(accepted) // the older one, applied late

        assertEquals(PushEvent(PushEventType.NEW_REQUEST, "r2"), pending.event.value)
    }
}
