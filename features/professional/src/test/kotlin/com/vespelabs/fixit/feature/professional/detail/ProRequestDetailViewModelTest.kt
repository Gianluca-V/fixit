package com.vespelabs.fixit.feature.professional.detail

import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.MainDispatcherRule
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestRepairRequestRepository
import com.vespelabs.fixit.core.domain.testing.TestUserRepository
import com.vespelabs.fixit.core.domain.usecase.AcceptRequestUseCase
import com.vespelabs.fixit.core.domain.usecase.ObserveRequestDetailUseCase
import com.vespelabs.fixit.feature.professional.detail.ProRequestDetailUiState.Success
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProRequestDetailViewModelTest {
    // StandardTestDispatcher queues the observation, so Loading is observable before it runs.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val client = TestData.client(id = "c1", name = "Ana López", phone = "11 5555-0001")
    private val plumberA = TestData.professional(id = "p1", name = "Carlos Gómez")
    private val plumberB = TestData.professional(id = "p2", name = "Diana Ruiz", email = "diana@fixit.test")
    private val auth = TestAuthRepository()
    private val users = TestUserRepository(listOf(client, plumberA, plumberB))
    private val requests = TestRepairRequestRepository()
    private val viewModel = ProRequestDetailViewModel(
        ObserveRequestDetailUseCase(auth, users, requests),
        AcceptRequestUseCase(auth, users, requests),
    )

    private val openRequest = TestData.request(
        id = "r1",
        clientId = "c1",
        place = Place(TestData.northOf(TestData.florencioVarela.point, 3.0), "Calle 1 123"),
    )
    private fun acceptedBy(professionalId: String) =
        openRequest.copy(status = RequestStatus.ACCEPTED, professionalId = professionalId, acceptedAt = 2_000L)

    /**
     * Waits for the first state matching [predicate]: combining flows may emit transient states. The
     * virtual-time timeout makes a state that never arrives fail fast instead of hanging the test.
     */
    private suspend fun ProRequestDetailViewModel.awaitState(
        predicate: (ProRequestDetailUiState) -> Boolean,
    ): ProRequestDetailUiState = withTimeout(5_000L) { uiState.first(predicate) }

    private suspend fun ProRequestDetailViewModel.successWhere(predicate: (Success) -> Boolean): Success =
        awaitState { it is Success && predicate(it) } as Success

    // [feed] Professional request detail

    @Test
    fun `given a compatible open request when the professional opens it from the feed then the detail and accept are shown without client data`() =
        runTest {
            auth.signedInAs(plumberA)
            requests.setRequests(listOf(openRequest))

            viewModel.load("r1")
            val state = viewModel.successWhere { true }

            assertEquals(openRequest, state.detail.request)
            assertEquals(3.0, state.detail.distanceKm!!, 0.0)
            assertTrue(state.canAccept)
            assertFalse(state.isUnavailable)
            assertNull(state.detail.contact) // [prof] no client name or phone while OPEN
            assertFalse(state.showsDestinationMap) // not until it is accepted
        }

    @Test
    fun `given a request accepted by A when B opens its detail then it is unavailable without accept or contact`() = runTest {
        auth.signedInAs(plumberB)
        requests.setRequests(listOf(acceptedBy("p1")))

        viewModel.load("r1")
        val state = viewModel.successWhere { true }

        assertTrue(state.isUnavailable)
        assertFalse(state.canAccept)
        assertNull(state.detail.contact)
        assertFalse(state.showsDestinationMap) // only the assignee is told where to go
    }

    @Test
    fun `given a request cancelled while open when a professional opens its detail then it is unavailable without accept`() =
        runTest {
            auth.signedInAs(plumberA)
            requests.setRequests(listOf(openRequest.copy(status = RequestStatus.CANCELLED, cancelledAt = 3_000L)))

            viewModel.load("r1")
            val state = viewModel.successWhere { true }

            assertTrue(state.isUnavailable)
            assertFalse(state.canAccept)
            assertFalse(state.isCancelledByClient)
        }

    @Test
    fun `given an accepted request assigned to A when A opens it from my jobs then the client contact is shown without accept or unavailable notice`() =
        runTest {
            auth.signedInAs(plumberA)
            requests.setRequests(listOf(acceptedBy("p1")))

            viewModel.load("r1")
            val state = viewModel.successWhere { it.detail.contact != null }

            // [contact] the professional gets the client's contact
            assertEquals("Ana López", state.detail.contact?.name)
            assertEquals("11 5555-0001", state.detail.contact?.phone)
            assertFalse(state.canAccept)
            assertFalse(state.isUnavailable)
        }

    @Test
    fun `given an accepted request assigned to A when A opens it then the map of where to go is shown`() = runTest {
        auth.signedInAs(plumberA)
        requests.setRequests(listOf(acceptedBy("p1")))

        viewModel.load("r1")
        val state = viewModel.successWhere { true }

        assertTrue(state.showsDestinationMap)
        assertEquals(openRequest.place, state.detail.request.place)
    }

    @Test
    fun `given a job of A cancelled by the client when A opens it then it shows cancelled by the client without contact`() =
        runTest {
            auth.signedInAs(plumberA)
            requests.setRequests(listOf(acceptedBy("p1").copy(status = RequestStatus.CANCELLED, cancelledAt = 3_000L)))

            viewModel.load("r1")
            val state = viewModel.successWhere { true }

            assertTrue(state.isCancelledByClient)
            assertFalse(state.isUnavailable)
            assertNull(state.detail.contact)
            assertFalse(state.showsDestinationMap)
        }

    @Test
    fun `given B is looking at an open request when A accepts it then B sees the unavailable notice without reloading`() =
        runTest {
            auth.signedInAs(plumberB)
            requests.setRequests(listOf(openRequest))
            viewModel.load("r1")
            viewModel.successWhere { it.canAccept }

            requests.upsert(acceptedBy("p1"))
            val state = viewModel.successWhere { it.detail.request.status == RequestStatus.ACCEPTED }

            assertTrue(state.isUnavailable)
            assertFalse(state.canAccept)
        }

    // [feed] Result of the acceptance on the screen

    @Test
    fun `given another professional just accepted when the professional confirms accept and gets AlreadyTaken then the race lost message is shown`() =
        runTest {
            auth.signedInAs(plumberB)
            requests.setRequests(listOf(openRequest))
            requests.acceptError = DomainError.AlreadyTaken
            viewModel.load("r1")
            viewModel.successWhere { it.canAccept }

            viewModel.onAcceptConfirm()
            val state = viewModel.successWhere { !it.isAccepting && it.message != null }

            assertEquals(DomainError.AlreadyTaken, state.message)
            assertTrue(state.isRaceLost)
            assertNull(state.acceptedId)
        }

    @Test
    fun `given the client just cancelled when the professional confirms accept then the no longer available message is shown`() =
        runTest {
            auth.signedInAs(plumberA)
            requests.setRequests(listOf(openRequest))
            requests.acceptError = DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.ACCEPT)
            viewModel.load("r1")
            viewModel.successWhere { it.canAccept }

            viewModel.onAcceptConfirm()
            val state = viewModel.successWhere { !it.isAccepting && it.message != null }

            assertEquals(DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.ACCEPT), state.message)
            assertTrue(state.isRaceLost)
        }

    @Test
    fun `given a compatible open request when the professional confirms and wins then it navigates to the job with the client contact`() =
        runTest {
            auth.signedInAs(plumberA)
            requests.setRequests(listOf(openRequest))
            viewModel.load("r1")
            viewModel.successWhere { it.canAccept }

            viewModel.onAcceptConfirm()
            val state = viewModel.successWhere { it.acceptedId != null && it.detail.contact != null }

            assertEquals("r1", state.acceptedId)
            assertEquals(RequestStatus.ACCEPTED, state.detail.request.status)
            assertEquals("p1", requests.request("r1")?.professionalId)
            assertEquals("Ana López", state.detail.contact?.name)

            viewModel.onNavigated()
            assertNull((viewModel.uiState.value as Success).acceptedId)
        }

    @Test
    fun `given accept is in progress when it is confirmed again then a single accept call is made`() = runTest {
        auth.signedInAs(plumberA)
        requests.setRequests(listOf(openRequest))
        viewModel.load("r1")
        viewModel.successWhere { it.canAccept }

        viewModel.onAcceptConfirm()
        assertTrue((viewModel.uiState.value as Success).isAccepting)
        viewModel.onAcceptConfirm()
        advanceUntilIdle()

        assertEquals(1, requests.acceptCalls.size)
    }

    @Test
    fun `given a network error on accept when the message is shown then it is cleared`() = runTest {
        auth.signedInAs(plumberA)
        requests.setRequests(listOf(openRequest))
        requests.acceptError = DomainError.Network
        viewModel.load("r1")
        viewModel.successWhere { it.canAccept }

        viewModel.onAcceptConfirm()
        val state = viewModel.successWhere { it.message != null }
        assertEquals(DomainError.Network, state.message)
        assertFalse(state.isRaceLost)

        viewModel.onMessageShown()
        assertNull((viewModel.uiState.value as Success).message)
    }

    // [jc] Review shown to the professional; one-way rating

    @Test
    fun `given a completed job with a 5 star review when the professional opens it then the rating and comment are shown and nothing can be done`() =
        runTest {
            auth.signedInAs(plumberA)
            requests.setRequests(listOf(acceptedBy("p1").copy(status = RequestStatus.COMPLETED, completedAt = 4_000L)))
            requests.setReviews(listOf(Review("r1", "p1", "c1", rating = 5, comment = "Muy prolijo", createdAt = 4_000L)))

            viewModel.load("r1")
            val state = viewModel.successWhere { it.detail.review != null }

            assertEquals(5, state.detail.review?.rating)
            assertEquals("Muy prolijo", state.detail.review?.comment)
            assertFalse(state.canAccept) // the only action of this screen; there is no rating action
            assertFalse(state.isUnavailable)
            assertFalse(state.showsDestinationMap) // the job is done, there is nowhere to go
        }

    // Loading, errors and idempotent load

    @Test
    fun `given a request id that does not exist when the detail opens then the state is a not found error`() = runTest {
        auth.signedInAs(plumberA)

        viewModel.load("missing")
        val state = viewModel.awaitState { it is ProRequestDetailUiState.Error }

        assertEquals(ProRequestDetailUiState.Error(DomainError.NotFound), state)
    }

    @Test
    fun `given a network error when retry is tapped then the state goes to loading and then shows the detail`() = runTest {
        auth.signedInAs(plumberA)
        requests.setRequests(listOf(openRequest))
        requests.observeError = DomainError.Network
        viewModel.load("r1")
        assertEquals(ProRequestDetailUiState.Error(DomainError.Network), viewModel.awaitState { it is ProRequestDetailUiState.Error })

        viewModel.onRetry()
        assertEquals(ProRequestDetailUiState.Loading, viewModel.uiState.value)

        assertTrue(viewModel.successWhere { true }.canAccept)
    }

    @Test
    fun `given the detail is loaded when load is called again with the same id then the state is kept`() = runTest {
        auth.signedInAs(plumberA)
        requests.setRequests(listOf(openRequest))
        viewModel.load("r1")
        val loaded = viewModel.successWhere { true }

        viewModel.load("r1")

        assertEquals(loaded, viewModel.uiState.value)
    }
}
