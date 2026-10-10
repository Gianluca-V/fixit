package ar.edu.unaj.fixit.feature.client.detail

import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.rules.average
import ar.edu.unaj.fixit.core.domain.testing.MainDispatcherRule
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestRepairRequestRepository
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import ar.edu.unaj.fixit.core.domain.usecase.CancelRequestUseCase
import ar.edu.unaj.fixit.core.domain.usecase.ObserveRequestDetailUseCase
import ar.edu.unaj.fixit.core.ui.components.SamplePhotos
import ar.edu.unaj.fixit.feature.client.detail.ClientRequestDetailUiState.Success
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientRequestDetailViewModelTest {
    // StandardTestDispatcher queues the observation, so Loading is observable before it runs.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val client = TestData.client(id = "c1")
    private val professional = TestData.professional(
        id = "p1",
        name = "Carlos Gómez",
        reputation = Reputation(ratingSum = 45, reviewCount = 10),
    )
    private val auth = TestAuthRepository(SessionState.SignedIn(client))
    private val users = TestUserRepository(listOf(client, professional, TestData.client(id = "c2", name = "Bruno")))
    private val requests = TestRepairRequestRepository()
    private val viewModel = ClientRequestDetailViewModel(
        ObserveRequestDetailUseCase(auth, users, requests),
        CancelRequestUseCase(auth, requests),
    )

    private val openRequest = TestData.request(id = "r1", clientId = "c1", status = RequestStatus.OPEN)
    private val acceptedRequest = openRequest.copy(status = RequestStatus.ACCEPTED, professionalId = "p1", acceptedAt = 2_000L)

    /**
     * Waits for the first state matching [predicate]: combining flows may emit transient states. The
     * virtual-time timeout makes a state that never arrives fail fast instead of hanging the test.
     */
    private suspend fun ClientRequestDetailViewModel.awaitState(
        predicate: (ClientRequestDetailUiState) -> Boolean,
    ): ClientRequestDetailUiState = withTimeout(5_000L) { uiState.first(predicate) }

    private suspend fun ClientRequestDetailViewModel.successWhere(predicate: (Success) -> Boolean): Success =
        awaitState { it is Success && predicate(it) } as Success

    // [rr] Client request detail

    @Test
    fun `given a request id that does not exist when the client opens the detail then the state is a not found error`() =
        runTest {
            viewModel.load("missing")

            val state = viewModel.awaitState { it is ClientRequestDetailUiState.Error }

            assertEquals(ClientRequestDetailUiState.Error(DomainError.NotFound), state)
        }

    @Test
    fun `given a request of another client when the client opens its detail then the state is an unauthorized error`() =
        runTest {
            requests.setRequests(listOf(openRequest.copy(clientId = "c2")))

            viewModel.load("r1")
            val state = viewModel.awaitState { it is ClientRequestDetailUiState.Error }

            assertEquals(ClientRequestDetailUiState.Error(DomainError.Unauthorized), state)
        }

    @Test
    fun `given an open request when the detail loads then it waits for a professional and only cancel is available`() =
        runTest {
            requests.setRequests(listOf(openRequest))

            viewModel.load("r1")
            val state = viewModel.successWhere { true }

            assertEquals(RequestStatus.OPEN, state.status)
            assertNull(state.detail.contact)
            assertTrue(state.canCancel)
            assertFalse(state.canComplete)
        }

    @Test
    fun `given the detail of an open request is shown when a professional accepts it then the professional and contact appear live`() =
        runTest {
            requests.setRequests(listOf(openRequest))
            viewModel.load("r1")
            viewModel.successWhere { it.status == RequestStatus.OPEN }

            requests.upsert(acceptedRequest)
            val state = viewModel.successWhere { it.status == RequestStatus.ACCEPTED && it.detail.contact != null }

            assertEquals("Carlos Gómez", state.detail.contact?.name)
            assertEquals(professional.phone, state.detail.contact?.phone)
            assertTrue(state.canComplete)
            assertTrue(state.canCancel)
        }

    @Test
    fun `given a request cancelled after being accepted when the client opens the detail then no professional data or actions are shown`() =
        runTest {
            requests.setRequests(listOf(acceptedRequest.copy(status = RequestStatus.CANCELLED, cancelledAt = 3_000L)))

            viewModel.load("r1")
            val state = viewModel.successWhere { true }

            assertEquals(RequestStatus.CANCELLED, state.status)
            assertNull(state.detail.contact)
            assertFalse(state.canCancel)
            assertFalse(state.canComplete)
        }

    @Test
    fun `given an open own request when the client confirms the cancellation then it becomes cancelled with its date`() =
        runTest {
            requests.now = 7_000L
            requests.setRequests(listOf(openRequest))
            viewModel.load("r1")
            viewModel.successWhere { true }

            viewModel.onCancelConfirm()
            val state = viewModel.successWhere { it.status == RequestStatus.CANCELLED && !it.isWorking }

            assertEquals(listOf("r1"), requests.cancelCalls)
            assertEquals(7_000L, state.detail.request.cancelledAt)
            assertFalse(state.canCancel)
        }

    @Test
    fun `given an accepted own request when the client confirms the cancellation then it is cancelled and the contact disappears`() =
        runTest {
            requests.setRequests(listOf(acceptedRequest))
            viewModel.load("r1")
            viewModel.successWhere { it.detail.contact != null }

            viewModel.onCancelConfirm()
            val state = viewModel.successWhere { it.status == RequestStatus.CANCELLED && !it.isWorking }

            assertNull(state.detail.contact)
            assertEquals("p1", state.detail.request.professionalId)
        }

    @Test
    fun `given the cancellation fails with network when the client confirms it then a message is shown and the request stays open`() =
        runTest {
            requests.setRequests(listOf(openRequest))
            requests.cancelError = DomainError.Network
            viewModel.load("r1")
            viewModel.successWhere { true }

            viewModel.onCancelConfirm()
            val state = viewModel.successWhere { it.message != null }

            assertEquals(DomainError.Network, state.message)
            assertEquals(RequestStatus.OPEN, state.status)
            assertFalse(state.isWorking)

            viewModel.onMessageShown()
            assertNull((viewModel.uiState.value as Success).message)
        }

    // [prof] Professional reputation in the detail

    @Test
    fun `given a request accepted by a professional with 45 points in 10 reviews when the client opens the detail then the name and the 4_5 average appear`() =
        runTest {
            requests.setRequests(listOf(acceptedRequest))

            viewModel.load("r1")
            val contact = viewModel.successWhere { it.detail.contact != null }.detail.contact

            assertNotNull(contact)
            assertEquals("Carlos Gómez", contact?.name)
            assertEquals(Reputation(ratingSum = 45, reviewCount = 10), contact?.reputation)
            assertEquals(BigDecimal("4.5"), contact?.reputation?.average())
        }

    // [contact] Live contact visibility

    @Test
    fun `given the detail of an accepted request with the contact visible when the request is cancelled elsewhere then the contact disappears without reloading`() =
        runTest {
            requests.setRequests(listOf(acceptedRequest))
            viewModel.load("r1")
            viewModel.successWhere { it.detail.contact != null }

            requests.upsert(acceptedRequest.copy(status = RequestStatus.CANCELLED, cancelledAt = 3_000L))
            val state = viewModel.successWhere { it.status == RequestStatus.CANCELLED }

            assertNull(state.detail.contact)
        }

    // [media] Photos in the detail

    @Test
    fun `given a request published with two sample photos when the client opens the detail then two photos are shown`() =
        runTest {
            requests.setRequests(listOf(openRequest.copy(photoUrls = SamplePhotos.take(2))))

            viewModel.load("r1")
            val state = viewModel.successWhere { true }

            assertEquals(SamplePhotos.take(2), state.detail.request.photoUrls)
        }

    // Loading, retry and idempotent load

    @Test
    fun `given the detail failed with network when the user retries then the state is loading and then the detail`() =
        runTest {
            requests.setRequests(listOf(openRequest))
            requests.observeError = DomainError.Network
            viewModel.load("r1")
            assertEquals(ClientRequestDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()
            assertEquals(ClientRequestDetailUiState.Error(DomainError.Network), viewModel.uiState.value)

            viewModel.onRetry()
            assertEquals(ClientRequestDetailUiState.Loading, viewModel.uiState.value)

            val state = viewModel.successWhere { true }
            assertEquals("r1", state.detail.request.id)
        }

    @Test
    fun `given the detail is loaded when load is called again with the same id then the observation is not restarted`() =
        runTest {
            requests.setRequests(listOf(openRequest))
            viewModel.load("r1")
            viewModel.successWhere { true }

            // A restarted observation would consume this one-shot hook and turn the state into an error.
            requests.observeError = DomainError.Network
            viewModel.load("r1")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is Success)
        }
}
