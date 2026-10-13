package com.vespelabs.fixit.core.data.device

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocateWithinTest {

    private val point = GeoPoint(-34.8065, -58.2788)

    @Test
    fun `given the system delivers a fix when locating then it returns Success with the point`() = runTest {
        assertEquals(AppResult.Success(point), locateWithin { point })
    }

    @Test
    fun `given the device location is off (null fix) when locating then the error is LocationUnavailable`() = runTest {
        assertEquals(AppResult.Error(DomainError.LocationUnavailable), locateWithin { null })
    }

    @Test
    fun `given no fix arrives when 10 virtual seconds pass then the error is LocationUnavailable`() = runTest {
        val call = async { locateWithin { awaitCancellation() } }

        advanceTimeBy(LOCATION_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(call.isCompleted)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(AppResult.Error(DomainError.LocationUnavailable), call.await())
    }

    @Test
    fun `given the permission is missing (SecurityException) when locating then the error is LocationUnavailable`() = runTest {
        assertEquals(AppResult.Error(DomainError.LocationUnavailable), locateWithin { throw SecurityException("denied") })
    }

    @Test
    fun `given Play services fail when locating then the error is LocationUnavailable`() = runTest {
        assertEquals(AppResult.Error(DomainError.LocationUnavailable), locateWithin { throw IllegalStateException("no api") })
    }

    @Test
    fun `given a pending fix when the caller is cancelled then the cancellation propagates instead of returning a result`() = runTest {
        var resumedAfterCall = false
        val job = launch {
            locateWithin { awaitCancellation() }
            resumedAfterCall = true
        }
        runCurrent()

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertFalse(resumedAfterCall)
    }
}
