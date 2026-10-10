package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.RequestAction
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestRepairRequestRepository
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptRequestUseCaseTest {
    private val auth = TestAuthRepository()
    private val users = TestUserRepository()
    private val requests = TestRepairRequestRepository()
    private val acceptRequest = AcceptRequestUseCase(auth, users, requests)

    private val plumberA = TestData.professional(id = "pA", categories = setOf(ServiceCategory.PLUMBING))
    private val plumberB = TestData.professional(id = "pB", categories = setOf(ServiceCategory.PLUMBING))

    private fun signInAs(user: User) {
        users.upsert(user)
        auth.signedInAs(user)
    }

    @Test
    fun `given an OPEN compatible request when a professional accepts then it is ACCEPTED and assigned to them`() = runTest {
        signInAs(plumberA)
        requests.upsert(TestData.request(id = "r1", category = ServiceCategory.PLUMBING))
        requests.now = 7_000L

        val result = acceptRequest("r1")

        val accepted = (result as AppResult.Success).data
        assertEquals(RequestStatus.ACCEPTED, accepted.status)
        assertEquals("pA", accepted.professionalId)
        assertEquals(7_000L, accepted.acceptedAt)
    }

    @Test
    fun `given a request already accepted by A when B accepts then the result is AlreadyTaken and A stays assigned`() = runTest {
        signInAs(plumberB)
        users.upsert(plumberA)
        requests.upsert(TestData.request(id = "r1", status = RequestStatus.ACCEPTED, professionalId = "pA", acceptedAt = 5_000L))

        val result = acceptRequest("r1")

        assertEquals(AppResult.Error(DomainError.AlreadyTaken), result)
        assertEquals("pA", requests.request("r1")!!.professionalId)
    }

    @Test
    fun `given a request already accepted by A when A accepts again then it succeeds and the acceptance date does not change`() = runTest {
        signInAs(plumberA)
        val original = TestData.request(id = "r1", status = RequestStatus.ACCEPTED, professionalId = "pA", acceptedAt = 5_000L)
        requests.upsert(original)
        requests.now = 9_000L

        val result = acceptRequest("r1")

        assertEquals(AppResult.Success(original), result)
    }

    @Test
    fun `given a CANCELLED request when a compatible professional accepts then the result is InvalidTransition`() = runTest {
        signInAs(plumberA)
        requests.upsert(TestData.request(id = "r1", status = RequestStatus.CANCELLED))

        val result = acceptRequest("r1")

        assertEquals(AppResult.Error(DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.ACCEPT)), result)
    }

    @Test
    fun `given a professional without the category when accepting then the result is Unauthorized and the request stays OPEN`() = runTest {
        signInAs(TestData.professional(id = "pG", categories = setOf(ServiceCategory.GAS)))
        requests.upsert(TestData.request(id = "r1", category = ServiceCategory.PLUMBING))

        val result = acceptRequest("r1")

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertTrue(requests.acceptCalls.isEmpty())
        assertEquals(RequestStatus.OPEN, requests.request("r1")!!.status)
    }

    @Test
    fun `given a client when accepting then the result is Unauthorized`() = runTest {
        signInAs(TestData.client())
        requests.upsert(TestData.request(id = "r1"))

        val result = acceptRequest("r1")

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertTrue(requests.acceptCalls.isEmpty())
    }

    @Test
    fun `given a COMPLETED request and a professional without the category when accepting then authorization precedes the transition`() = runTest {
        signInAs(TestData.professional(id = "pG", categories = setOf(ServiceCategory.GAS)))
        requests.upsert(TestData.request(id = "r1", category = ServiceCategory.PLUMBING, status = RequestStatus.COMPLETED, professionalId = "pA"))

        val result = acceptRequest("r1")

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
    }

    @Test
    fun `given an OPEN request outside the professional radius when accepting then the acceptance succeeds`() = runTest {
        signInAs(plumberA)
        val farAway = Place(GeoPoint(-30.0, -58.0), "Far away")
        requests.upsert(TestData.request(id = "r1", category = ServiceCategory.PLUMBING, place = farAway))

        val result = acceptRequest("r1")

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `given a professional with three ACCEPTED jobs when accepting another then it succeeds`() = runTest {
        signInAs(plumberA)
        repeat(3) { requests.upsert(TestData.request(id = "job$it", status = RequestStatus.ACCEPTED, professionalId = "pA")) }
        requests.upsert(TestData.request(id = "r1"))

        val result = acceptRequest("r1")

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `given the race is lost inside the repository when accepting then AlreadyTaken is returned unchanged`() = runTest {
        signInAs(plumberA)
        requests.upsert(TestData.request(id = "r1"))
        requests.acceptError = DomainError.AlreadyTaken

        val result = acceptRequest("r1")

        assertEquals(AppResult.Error(DomainError.AlreadyTaken), result)
        assertNull(requests.request("r1")!!.professionalId)
    }

    @Test
    fun `given an unknown request when accepting then the result is NotFound`() = runTest {
        signInAs(plumberA)

        assertEquals(AppResult.Error(DomainError.NotFound), acceptRequest("missing"))
    }

    @Test
    fun `given no session when accepting then the result is Unauthorized`() = runTest {
        requests.upsert(TestData.request(id = "r1"))

        assertEquals(AppResult.Error(DomainError.Unauthorized), acceptRequest("r1"))
        assertTrue(requests.acceptCalls.isEmpty())
    }
}
