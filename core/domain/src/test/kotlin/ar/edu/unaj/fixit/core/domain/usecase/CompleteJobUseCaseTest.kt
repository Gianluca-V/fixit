package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.RequestAction
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.Review
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestRepairRequestRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteJobUseCaseTest {
    private val auth = TestAuthRepository()
    private val requests = TestRepairRequestRepository()
    private val completeJob = CompleteJobUseCase(auth, requests)

    private val client = TestData.client(id = "c1")

    private fun givenRequest(
        status: RequestStatus,
        clientId: String = client.id,
        professionalId: String? = "p1",
    ): RepairRequest = TestData.request(id = "r1", clientId = clientId, status = status, professionalId = professionalId)
        .also { requests.upsert(it) }

    private fun assertRejected(expected: ValidationError, result: AppResult<Review>) {
        assertEquals(AppResult.Error(DomainError.Validation(expected)), result)
        assertTrue("nothing may be completed", requests.completeCalls.isEmpty())
        assertEquals(RequestStatus.ACCEPTED, requests.request("r1")!!.status)
    }

    @Test
    fun `given an ACCEPTED request of the client when completing with rating 5 and a comment then the request is COMPLETED with that review`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)
        requests.now = 8_000L

        val result = completeJob("r1", 5, "Excelente")

        val review = (result as AppResult.Success).data
        assertEquals(Review("r1", "p1", "c1", 5, "Excelente", 8_000L), review)
        val completed = requests.request("r1")!!
        assertEquals(RequestStatus.COMPLETED, completed.status)
        assertEquals(8_000L, completed.completedAt)
    }

    @Test
    fun `given a blank comment when completing then the review is saved without comment`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)

        val result = completeJob("r1", 4, "   ")

        assertNull((result as AppResult.Success).data.comment)
        assertNull(requests.completeCalls.single().third)
    }

    @Test
    fun `given a padded comment when completing then the trimmed comment is persisted`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)

        completeJob("r1", 4, "  Muy prolijo \n")

        assertEquals("Muy prolijo", requests.completeCalls.single().third)
    }

    @Test
    fun `given no comment when completing then the review is saved without comment`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)

        val result = completeJob("r1", 3, null)

        assertNull((result as AppResult.Success).data.comment)
    }

    @Test
    fun `given ratings of 0 and 6 when completing then the result is InvalidRating and the request stays ACCEPTED`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)

        assertRejected(ValidationError.INVALID_RATING, completeJob("r1", 0, null))
        assertRejected(ValidationError.INVALID_RATING, completeJob("r1", 6, null))
    }

    @Test
    fun `given ratings of 1 and 5 and a comment of exactly 300 characters when completing then the review is accepted`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)

        assertTrue(completeJob("r1", 1, "c".repeat(300)) is AppResult.Success)
    }

    @Test
    fun `given a comment of 300 characters plus surrounding spaces when completing then the limit applies to the trimmed text`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)

        assertTrue(completeJob("r1", 5, "  " + "c".repeat(300) + "  ") is AppResult.Success)
    }

    @Test
    fun `given a comment of 301 characters when completing then the result is CommentTooLong and the request stays ACCEPTED`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)

        assertRejected(ValidationError.COMMENT_TOO_LONG, completeJob("r1", 5, "c".repeat(301)))
    }

    @Test
    fun `given the assigned professional when completing then the result is Unauthorized`() = runTest {
        auth.signedInAs(TestData.professional(id = "p1"))
        givenRequest(RequestStatus.ACCEPTED)

        assertEquals(AppResult.Error(DomainError.Unauthorized), completeJob("r1", 5, null))
        assertTrue(requests.completeCalls.isEmpty())
    }

    @Test
    fun `given a client who does not own the request when completing then the result is Unauthorized`() = runTest {
        auth.signedInAs(TestData.client(id = "c2"))
        givenRequest(RequestStatus.ACCEPTED)

        assertEquals(AppResult.Error(DomainError.Unauthorized), completeJob("r1", 5, null))
    }

    @Test
    fun `given another user and an invalid rating when completing then authorization precedes validation`() = runTest {
        auth.signedInAs(TestData.client(id = "c2"))
        givenRequest(RequestStatus.ACCEPTED)

        assertEquals(AppResult.Error(DomainError.Unauthorized), completeJob("r1", 0, null))
    }

    @Test
    fun `given an OPEN request when the owner completes it then the result is InvalidTransition`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.OPEN, professionalId = null)

        val result = completeJob("r1", 5, null)

        assertEquals(AppResult.Error(DomainError.InvalidTransition(RequestStatus.OPEN, RequestAction.COMPLETE)), result)
    }

    @Test
    fun `given a COMPLETED request when the owner completes it again then the result is InvalidTransition and no second review exists`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)
        completeJob("r1", 5, null)

        val second = completeJob("r1", 5, null)

        assertEquals(AppResult.Error(DomainError.InvalidTransition(RequestStatus.COMPLETED, RequestAction.COMPLETE)), second)
        assertEquals(1, requests.reviewsSnapshot().size)
    }

    @Test
    fun `given a request accepted and then cancelled when the owner completes it then the result is InvalidTransition and no review is created`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.CANCELLED)

        val result = completeJob("r1", 5, null)

        assertEquals(AppResult.Error(DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.COMPLETE)), result)
        assertTrue(requests.reviewsSnapshot().isEmpty())
    }

    @Test
    fun `given a failure saving the review when completing then the error is returned and the request stays ACCEPTED`() = runTest {
        auth.signedInAs(client)
        givenRequest(RequestStatus.ACCEPTED)
        requests.completeError = DomainError.Network

        val result = completeJob("r1", 5, null)

        assertEquals(AppResult.Error(DomainError.Network), result)
        assertEquals(RequestStatus.ACCEPTED, requests.request("r1")!!.status)
    }

    @Test
    fun `given an unknown request when completing then the result is NotFound`() = runTest {
        auth.signedInAs(client)

        assertEquals(AppResult.Error(DomainError.NotFound), completeJob("missing", 5, null))
    }

    @Test
    fun `given no session when completing then the result is Unauthorized`() = runTest {
        givenRequest(RequestStatus.ACCEPTED)

        assertEquals(AppResult.Error(DomainError.Unauthorized), completeJob("r1", 5, null))
        assertTrue(requests.completeCalls.isEmpty())
    }
}
