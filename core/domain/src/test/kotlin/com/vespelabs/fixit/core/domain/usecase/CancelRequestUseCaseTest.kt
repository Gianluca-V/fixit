package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestRepairRequestRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelRequestUseCaseTest {
    private val auth = TestAuthRepository()
    private val requests = TestRepairRequestRepository()
    private val cancelRequest = CancelRequestUseCase(auth, requests)

    private val client = TestData.client(id = "c1")

    private fun givenRequest(
        status: RequestStatus,
        clientId: String = client.id,
        professionalId: String? = null,
    ): RepairRequest = TestData.request(id = "r1", clientId = clientId, status = status, professionalId = professionalId)
        .also { requests.upsert(it) }

    @Test
    fun `given an OPEN request of the client when cancelling then it is CANCELLED with the cancellation date`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.OPEN)
        requests.now = 5_000L

        val result = cancelRequest("r1")

        val cancelled = (result as AppResult.Success).data
        assertEquals(RequestStatus.CANCELLED, cancelled.status)
        assertEquals(5_000L, cancelled.cancelledAt)
    }

    @Test
    fun `given an ACCEPTED request of the client when cancelling then it is CANCELLED and stays linked to the professional`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")

        val result = cancelRequest("r1")

        val cancelled = (result as AppResult.Success).data
        assertEquals(RequestStatus.CANCELLED, cancelled.status)
        assertEquals("p1", cancelled.professionalId)
    }

    @Test
    fun `given an ACCEPTED request when cancelling then no review is created`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")

        cancelRequest("r1")

        assertTrue(requests.reviewsSnapshot().isEmpty())
        assertTrue(requests.completeCalls.isEmpty())
    }

    @Test
    fun `given a request of another client when cancelling then the result is Unauthorized and the state does not change`() = runTest {
        auth.signedInAs(client)
        val other = givenRequest(RequestStatus.OPEN, clientId = "c2")

        val result = cancelRequest("r1")

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertTrue(requests.cancelCalls.isEmpty())
        assertEquals(other, requests.request("r1"))
    }

    @Test
    fun `given a professional when cancelling a request then the result is Unauthorized`() = runTest {
        auth.signedInAs(TestData.professional(id = "p1"))
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")

        val result = cancelRequest("r1")

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertEquals(RequestStatus.ACCEPTED, requests.request("r1")!!.status)
    }

    @Test
    fun `given a COMPLETED request of the client when cancelling then the result is InvalidTransition`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.COMPLETED, professionalId = "p1")

        val result = cancelRequest("r1")

        assertEquals(AppResult.Error(DomainError.InvalidTransition(RequestStatus.COMPLETED, RequestAction.CANCEL)), result)
    }

    @Test
    fun `given a CANCELLED request of the client when cancelling then the result is InvalidTransition`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.CANCELLED)

        val result = cancelRequest("r1")

        assertEquals(AppResult.Error(DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.CANCEL)), result)
    }

    @Test
    fun `given a COMPLETED request of another client when cancelling then authorization precedes the transition`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.COMPLETED, clientId = "c2", professionalId = "p1")

        val result = cancelRequest("r1")

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
    }

    @Test
    fun `given an unknown request when cancelling then the result is NotFound`() = runTest {
        auth.signedInAs(client)

        val result = cancelRequest("missing")

        assertEquals(AppResult.Error(DomainError.NotFound), result)
    }

    @Test
    fun `given no session when cancelling then the result is Unauthorized`() = runTest {
        givenRequest(RequestStatus.OPEN)

        val result = cancelRequest("r1")

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertTrue(requests.cancelCalls.isEmpty())
    }

    @Test
    fun `given a repository failure when cancelling then the error is returned unchanged`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.OPEN)
        requests.cancelError = DomainError.Network

        val result = cancelRequest("r1")

        assertEquals(AppResult.Error(DomainError.Network), result)
        assertEquals(RequestStatus.OPEN, requests.request("r1")!!.status)
    }
}
