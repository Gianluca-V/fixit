package com.vespelabs.fixit.feature.professional.myjobs

import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.MainDispatcherRule
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestRepairRequestRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyJobsViewModelTest {
    // StandardTestDispatcher queues the observation started in init, so Loading is observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val auth = TestAuthRepository(SessionState.SignedIn(TestData.professional(id = "p1")))
    private val requests = TestRepairRequestRepository()

    private fun createViewModel() = MyJobsViewModel(auth, requests)

    private fun job(
        id: String,
        status: RequestStatus = RequestStatus.ACCEPTED,
        acceptedAt: Long = 2_000L,
        professionalId: String = "p1",
    ): RepairRequest = TestData.request(id = id, status = status, professionalId = professionalId, acceptedAt = acceptedAt)

    private fun MyJobsUiState.active(): List<String> = (this as MyJobsUiState.Success).active.map { it.id }

    private fun MyJobsUiState.finished(): List<String> = (this as MyJobsUiState.Success).finished.map { it.id }

    @Test
    fun `given the professional accepted a request when my jobs opens then the job appears as accepted`() = runTest {
        requests.setRequests(listOf(job("r1"), job("other", professionalId = "p2")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as MyJobsUiState.Success
        assertEquals(listOf("r1"), state.active.map { it.id })
        assertEquals(RequestStatus.ACCEPTED, state.active.single().status)
        assertEquals(emptyList<String>(), state.finished.map { it.id })
    }

    @Test
    fun `given an accepted job when the client completes it then the item shows completed without reloading`() = runTest {
        requests.setRequests(listOf(job("r1")))
        val viewModel = createViewModel()
        advanceUntilIdle()

        requests.upsert(job("r1", status = RequestStatus.COMPLETED))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MyJobsUiState.Success
        assertEquals(emptyList<String>(), state.active.map { it.id })
        assertEquals(RequestStatus.COMPLETED, state.finished.single().status)
    }

    @Test
    fun `given an accepted job when the client cancels it then the item shows cancelled without reloading`() = runTest {
        requests.setRequests(listOf(job("r1")))
        val viewModel = createViewModel()
        advanceUntilIdle()

        requests.upsert(job("r1", status = RequestStatus.CANCELLED))
        advanceUntilIdle()

        val state = viewModel.uiState.value as MyJobsUiState.Success
        assertEquals(emptyList<String>(), state.active.map { it.id })
        assertEquals(RequestStatus.CANCELLED, state.finished.single().status)
    }

    @Test
    fun `given a completed job accepted at t1 and accepted jobs at t2 and t3 when my jobs opens then the order is t3 t2 and the completed one`() =
        runTest {
            requests.setRequests(
                listOf(
                    job("t1", status = RequestStatus.COMPLETED, acceptedAt = 1_000L),
                    job("t2", acceptedAt = 2_000L),
                    job("t3", acceptedAt = 3_000L),
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(listOf("t3", "t2"), viewModel.uiState.value.active())
            assertEquals(listOf("t1"), viewModel.uiState.value.finished())
        }

    @Test
    fun `given a completed job accepted after an accepted one when my jobs opens then the accepted one still comes first`() =
        runTest {
            requests.setRequests(
                listOf(
                    job("accepted", acceptedAt = 1_000L),
                    job("completed", status = RequestStatus.COMPLETED, acceptedAt = 5_000L),
                    job("cancelled", status = RequestStatus.CANCELLED, acceptedAt = 3_000L),
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(listOf("accepted"), viewModel.uiState.value.active())
            assertEquals(listOf("completed", "cancelled"), viewModel.uiState.value.finished())
        }

    @Test
    fun `given three accepted jobs when the professional accepts another one then the four are active`() = runTest {
        requests.setRequests(listOf(job("a", acceptedAt = 1_000L), job("b", acceptedAt = 2_000L), job("c", acceptedAt = 3_000L)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        requests.upsert(job("d", acceptedAt = 4_000L))
        advanceUntilIdle()

        assertEquals(listOf("d", "c", "b", "a"), viewModel.uiState.value.active())
    }

    @Test
    fun `given the screen opens when the jobs have not arrived yet then the state is loading until they arrive`() = runTest {
        requests.setRequests(listOf(job("r1")))

        val viewModel = createViewModel()
        assertEquals(MyJobsUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()
        assertEquals(listOf("r1"), viewModel.uiState.value.active())
    }

    @Test
    fun `given the observation fails with network when my jobs opens then the state is a network error`() = runTest {
        requests.observeError = DomainError.Network

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(MyJobsUiState.Error(DomainError.Network), viewModel.uiState.value)
    }

    @Test
    fun `given a network error when retry is tapped then the jobs are shown`() = runTest {
        requests.setRequests(listOf(job("r1")))
        requests.observeError = DomainError.Network
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRetry()
        assertEquals(MyJobsUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(listOf("r1"), viewModel.uiState.value.active())
    }

    @Test
    fun `given no session when my jobs opens then the state is an unauthorized error`() = runTest {
        auth.setSession(SessionState.SignedOut)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(MyJobsUiState.Error(DomainError.Unauthorized), viewModel.uiState.value)
    }
}
