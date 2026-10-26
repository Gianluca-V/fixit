package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestRepairRequestRepository
import com.vespelabs.fixit.core.domain.testing.TestUserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Design 14b.5: PushMessageHandler.handle(data), only domain interfaces, JVM doubles from testFixtures. */
class PushMessageHandlerTest {
    private val gasPro = TestData.professional(id = "p1", categories = setOf(ServiceCategory.GAS), radiusKm = 10)
    private val nearGas = TestData.request(
        id = "r1",
        category = ServiceCategory.GAS,
        place = TestData.florencioVarela.copy(point = TestData.northOf(TestData.florencioVarela.point, 2.0)),
    )

    /** Counts `get` calls and can fail the next one; everything else goes to the testFixtures double. */
    private class CountingUsers(private val delegate: TestUserRepository) : UserRepository by delegate {
        var getCalls = 0
        var getError: DomainError? = null

        override suspend fun get(userId: String): AppResult<User> {
            getCalls++
            getError?.let { return AppResult.Error(it) }
            return delegate.get(userId)
        }
    }

    private class CountingRequests(private val delegate: TestRepairRequestRepository) : RepairRequestRepository by delegate {
        var getCalls = 0
        var getError: DomainError? = null

        override suspend fun get(requestId: String): AppResult<RepairRequest> {
            getCalls++
            getError?.let { return AppResult.Error(it) }
            return delegate.get(requestId)
        }
    }

    private val auth = TestAuthRepository(SessionState.SignedIn(gasPro))
    private val users = CountingUsers(TestUserRepository(listOf(gasPro)))
    private val requests = CountingRequests(TestRepairRequestRepository(listOf(nearGas)))
    private val handler = PushMessageHandler(auth, users, requests)

    private fun newRequest(category: String? = "GAS", requestId: String? = "r1") = buildMap {
        put("type", "NEW_REQUEST")
        requestId?.let { put("requestId", it) }
        category?.let { put("category", it) }
    }

    @Test
    fun `given a matching NEW_REQUEST when handling then the event is returned after reading the user and the request`() = runTest {
        assertEquals(PushEvent(PushEventType.NEW_REQUEST, "r1"), handler.handle(newRequest()))
        assertEquals(1, users.getCalls)
        assertEquals(1, requests.getCalls)
    }

    @Test
    fun `given a REQUEST_ACCEPTED for the owner client when handling then the event is returned`() = runTest {
        val client = TestData.client(id = "c1")
        auth.signedInAs(client)
        TestUserRepository(listOf(client)).let { realUsers ->
            val handler = PushMessageHandler(
                auth,
                realUsers,
                TestRepairRequestRepository(listOf(nearGas.copy(status = RequestStatus.ACCEPTED, professionalId = "p1"))),
            )

            assertEquals(
                PushEvent(PushEventType.REQUEST_ACCEPTED, "r1"),
                handler.handle(mapOf("type" to "REQUEST_ACCEPTED", "requestId" to "r1")),
            )
        }
    }

    @Test
    fun `given no session when handling then it is null without reads`() = runTest {
        auth.setSession(SessionState.SignedOut)

        assertNull(handler.handle(newRequest()))
        assertEquals(0, users.getCalls + requests.getCalls)
    }

    @Test
    fun `given an unknown type or a missing requestId when handling then it is null without reads`() = runTest {
        assertNull(handler.handle(mapOf("type" to "SOMETHING_ELSE", "requestId" to "r1")))
        assertNull(handler.handle(newRequest(requestId = null)))
        assertNull(handler.handle(mapOf("requestId" to "r1")))
        assertEquals(0, users.getCalls + requests.getCalls)
    }

    @Test
    fun `Categoria ya no vigente (prefiltro) - a NEW_REQUEST of a category not in the session profile is null with no get at all`() = runTest {
        assertNull(handler.handle(newRequest(category = "PLUMBING")))
        assertEquals(0, users.getCalls)
        assertEquals(0, requests.getCalls)
    }

    @Test
    fun `given a session still Loading when a NEW_REQUEST arrives then the pre-filter waits for the profile`() = runTest {
        // Like FirebaseAuth after a cold start: the uid is known while the session profile is still Loading.
        val restoring = object : AuthRepository by auth {
            override fun currentUserId() = "p1"
        }
        auth.setSession(SessionState.Loading)
        val pending = async { PushMessageHandler(restoring, users, requests).handle(newRequest()) }
        testScheduler.runCurrent()
        assertFalse(pending.isCompleted)

        auth.signedInAs(gasPro)

        assertEquals(PushEvent(PushEventType.NEW_REQUEST, "r1"), pending.await())
    }

    @Test
    fun `Falla la lectura de la solicitud - a request get with Error is null without throwing`() = runTest {
        requests.getError = DomainError.Network

        assertNull(handler.handle(newRequest()))
    }

    @Test
    fun `given the user get fails when handling then it is null`() = runTest {
        users.getError = DomainError.NotFound

        assertNull(handler.handle(newRequest()))
    }

    @Test
    fun `given fresh data that no longer matches when handling then it is null`() = runTest {
        val far = nearGas.copy(place = nearGas.place.copy(point = TestData.northOf(TestData.florencioVarela.point, 15.0)))
        val handler = PushMessageHandler(auth, users, TestRepairRequestRepository(listOf(far)))

        assertNull(handler.handle(newRequest()))
    }
}
