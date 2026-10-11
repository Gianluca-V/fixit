package com.vespelabs.fixit.feature.client.myrequests

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
class MyRequestsViewModelTest {
    // StandardTestDispatcher queues the observation started in init, so Loading is observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val auth = TestAuthRepository(SessionState.SignedIn(TestData.client(id = "c1")))
    private val requests = TestRepairRequestRepository()

    private fun createViewModel() = MyRequestsViewModel(auth, requests)

    private fun MyRequestsUiState.requestIds(): List<String> =
        (this as MyRequestsUiState.Success).requests.map { it.id }

    @Test
    fun `given three own requests created at t1 t2 t3 when the list loads then the order is t3 t2 t1`() = runTest {
        requests.setRequests(
            listOf(
                TestData.request(id = "t2", createdAt = 2_000L),
                TestData.request(id = "t1", createdAt = 1_000L),
                TestData.request(id = "t3", createdAt = 3_000L),
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("t3", "t2", "t1"), viewModel.uiState.value.requestIds())
    }

    @Test
    fun `given requests of other clients when the list loads then only the own requests appear`() = runTest {
        requests.setRequests(
            listOf(
                TestData.request(id = "mine", clientId = "c1"),
                TestData.request(id = "bruno", clientId = "c2"),
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("mine"), viewModel.uiState.value.requestIds())
    }

    @Test
    fun `given the screen opens when the requests have not arrived yet then the state is loading until they arrive`() =
        runTest {
            requests.setRequests(listOf(TestData.request(id = "r1")))

            val viewModel = createViewModel()
            assertEquals(MyRequestsUiState.Loading, viewModel.uiState.value)

            advanceUntilIdle()
            assertEquals(listOf("r1"), viewModel.uiState.value.requestIds())
        }

    @Test
    fun `given the observation fails with network when the list loads then the state is a network error`() = runTest {
        requests.observeError = DomainError.Network

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(MyRequestsUiState.Error(DomainError.Network), viewModel.uiState.value)
    }

    @Test
    fun `given the list is shown when a new own request is created then it appears first`() = runTest {
        requests.setRequests(listOf(TestData.request(id = "old", createdAt = 1_000L)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        requests.upsert(TestData.request(id = "new", createdAt = 5_000L))
        advanceUntilIdle()

        assertEquals(listOf("new", "old"), viewModel.uiState.value.requestIds())
    }

    @Test
    fun `given no session when the list loads then the state is an unauthorized error`() = runTest {
        auth.setSession(SessionState.SignedOut)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(MyRequestsUiState.Error(DomainError.Unauthorized), viewModel.uiState.value)
    }
}
