package com.vespelabs.fixit.core.data.photon

import com.vespelabs.fixit.core.data.firebase.firebaseCall
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** `[addr]` "Servicio no disponible": what PhotonAddressSearch returns for each failure of the request. */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotonSearchErrorsTest {
    private suspend fun searchWith(behavior: suspend () -> List<PhotonFeature>) = firebaseCall(SEARCH_TIMEOUT_MS) { behavior() }

    @Test
    fun `given no network when searching then the error is Network`() = runTest {
        assertEquals(AppResult.Error(DomainError.Network), searchWith { throw UnknownHostException("photon.komoot.io") })
    }

    @Test
    fun `given an HTTP 503 or a body that is not JSON when searching then the error is Unknown`() = runTest {
        assertEquals(AppResult.Error(DomainError.Unknown), searchWith { parsePhotonResponse(503, "") })
        assertEquals(AppResult.Error(DomainError.Unknown), searchWith { parsePhotonResponse(200, "<html>busy</html>") })
    }

    @Test
    fun `given a search that never ends when 10 virtual seconds pass then the error is Network`() = runTest {
        val call = async { searchWith { awaitCancellation() } }

        advanceTimeBy(SEARCH_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(call.isCompleted)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(AppResult.Error(DomainError.Network), call.await())
    }
}
