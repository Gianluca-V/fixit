package ar.edu.unaj.fixit.feature.client.complete

import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.testing.MainDispatcherRule
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestRepairRequestRepository
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import ar.edu.unaj.fixit.core.domain.usecase.CompleteJobUseCase
import ar.edu.unaj.fixit.core.domain.usecase.ObserveRequestDetailUseCase
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
class CompleteJobViewModelTest {
    // StandardTestDispatcher queues the coroutines, so "in progress" states are observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val client = TestData.client(id = "c1")
    private val professional = TestData.professional(id = "p1", name = "Carlos Gómez")
    private val auth = TestAuthRepository(SessionState.SignedIn(client))
    private val users = TestUserRepository(listOf(client, professional))
    private val requests = TestRepairRequestRepository(
        listOf(TestData.request(id = "r1", clientId = "c1", status = RequestStatus.ACCEPTED, professionalId = "p1")),
    )
    private val viewModel = CompleteJobViewModel(
        ObserveRequestDetailUseCase(auth, users, requests),
        CompleteJobUseCase(auth, requests),
    )

    private val state get() = viewModel.uiState.value

    /** Waits for the first matching state; the virtual-time timeout makes a missing state fail fast. */
    private suspend fun awaitState(predicate: (CompleteJobUiState) -> Boolean): CompleteJobUiState =
        withTimeout(5_000L) { viewModel.uiState.first(predicate) }

    private suspend fun loadForm() {
        viewModel.load("r1")
        awaitState { it.professionalName != null }
    }

    // [jc] Complete and rate screen

    @Test
    fun `given an accepted request assigned to Carlos Gomez when the client opens the screen then the state contains his name`() =
        runTest {
            viewModel.load("r1")

            val loaded = awaitState { !it.isLoading }

            assertEquals("Carlos Gómez", loaded.professionalName)
            assertNull(loaded.loadError)
        }

    @Test
    fun `given the screen just opened when it is shown then confirm is disabled and no star is selected`() = runTest {
        loadForm()

        assertEquals(0, state.rating)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `given the screen just opened when the user taps the fourth star then the rating is 4 and confirm is enabled`() =
        runTest {
            loadForm()

            viewModel.onRatingChange(4)

            assertEquals(4, state.rating)
            assertTrue(state.canSubmit)
        }

    @Test
    fun `given the submission fails with network when the user confirms then a network message is shown and stars and comment are kept`() =
        runTest {
            loadForm()
            requests.completeError = DomainError.Network
            viewModel.onRatingChange(4)
            viewModel.onCommentChange("Muy prolijo")

            viewModel.onSubmit()
            assertTrue(state.isSubmitting)
            advanceUntilIdle()

            assertEquals(DomainError.Network, state.message)
            assertEquals(4, state.rating)
            assertEquals("Muy prolijo", state.comment)
            assertFalse(state.isSubmitting)
            assertFalse(state.completed)
            assertTrue(state.canSubmit)

            viewModel.onMessageShown()
            assertNull(state.message)
        }

    @Test
    fun `given a rating of five stars when the user confirms and it succeeds then the screen completes and the request is completed with the review`() =
        runTest {
            loadForm()
            viewModel.onRatingChange(5)
            viewModel.onCommentChange("  Muy prolijo  ")

            viewModel.onSubmit()
            advanceUntilIdle()

            assertTrue(state.completed)
            // The use case trims the comment before it reaches the repository.
            assertEquals(listOf(Triple("r1", 5, "Muy prolijo")), requests.completeCalls)
            assertEquals(RequestStatus.COMPLETED, requests.request("r1")?.status)
            assertEquals(5, requests.reviewsSnapshot().single().rating)

            viewModel.onNavigated()
            assertFalse(state.completed)
        }

    @Test
    fun `given the request cannot be loaded when the client opens the screen then the load error is shown and retry loads it`() =
        runTest {
            requests.observeError = DomainError.Network
            viewModel.load("r1")

            val failed = awaitState { it.loadError != null }
            assertEquals(DomainError.Network, failed.loadError)
            assertNull(failed.professionalName)

            viewModel.onRetry()
            assertTrue(state.isLoading)
            assertNull(state.loadError)

            assertEquals("Carlos Gómez", awaitState { it.professionalName != null }.professionalName)
        }

    @Test
    fun `given a request id that does not exist when the client opens the screen then the load error is not found`() =
        runTest {
            viewModel.load("missing")

            assertEquals(DomainError.NotFound, awaitState { it.loadError != null }.loadError)
        }
}
