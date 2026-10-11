package com.vespelabs.fixit.core.domain.testing

import com.vespelabs.fixit.core.domain.repository.Clock

/** Controllable Clock double: time only moves when the test says so. */
class TestClock(@Volatile var time: Long = 0L) : Clock {
    override fun now(): Long = time

    fun advanceBy(millis: Long) {
        time += millis
    }
}
