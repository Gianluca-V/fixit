package com.vespelabs.fixit.feature.professional.feed

import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.MainDispatcherRule
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestRepairRequestRepository
import com.vespelabs.fixit.core.domain.testing.TestUserRepository
import com.vespelabs.fixit.core.domain.usecase.ObserveFeedUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    // StandardTestDispatcher queues the observation started in init, so Loading is observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val auth = TestAuthRepository()
    private val users = TestUserRepository()
    private val requests = TestRepairRequestRepository()

    private val base = TestData.florencioVarela

    private fun createViewModel() = FeedViewModel(ObserveFeedUseCase(auth, users, requests))

    private fun signInAs(user: User) {
        users.upsert(user)
        auth.signedInAs(user)
    }

    private fun professional(
        categories: Set<ServiceCategory> = setOf(ServiceCategory.ELECTRICITY),
        radiusKm: Int = 10,
    ): User = TestData.professional(id = "p1", categories = categories, base = base, radiusKm = radiusKm)

    private fun openRequest(id: String, km: Double, category: ServiceCategory = ServiceCategory.ELECTRICITY): RepairRequest =
        TestData.request(id = id, category = category, place = Place(TestData.northOf(base.point, km), "Somewhere"))

    private fun FeedUiState.ids(): List<String> = (this as FeedUiState.Success).items.map { it.request.id }

    @Test
    fun `given the screen opens when the feed has not arrived yet then the state is loading until it arrives`() = runTest {
        signInAs(professional())
        requests.setRequests(listOf(openRequest("r1", km = 2.0)))

        val viewModel = createViewModel()
        assertEquals(FeedUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()
        assertEquals(listOf("r1"), viewModel.uiState.value.ids())
    }

    @Test
    fun `given the feed is open when a client publishes a compatible request at 2 km then it appears without reloading with its distance`() =
        runTest {
            signInAs(professional())
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(emptyList<String>(), viewModel.uiState.value.ids())

            requests.upsert(openRequest("r1", km = 2.0))
            advanceUntilIdle()

            val items = (viewModel.uiState.value as FeedUiState.Success).items
            assertEquals(listOf("r1"), items.map { it.request.id })
            assertEquals(2.0, items.single().distanceKm, 0.0)
        }

    @Test
    fun `given the load fails with network when the feed opens then the state is a network error`() = runTest {
        signInAs(professional())
        users.observeError = DomainError.Network

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedUiState.Error(DomainError.Network), viewModel.uiState.value)
    }

    @Test
    fun `given a network error when retry is tapped then the state goes to loading and then shows the feed`() = runTest {
        signInAs(professional())
        requests.setRequests(listOf(openRequest("r1", km = 2.0)))
        requests.observeError = DomainError.Network
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(FeedUiState.Error(DomainError.Network), viewModel.uiState.value)

        viewModel.onRetry()
        assertEquals(FeedUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()
        assertEquals(listOf("r1"), viewModel.uiState.value.ids())
    }

    @Test
    fun `given a GAS request not visible when the professional adds GAS to the categories then it appears in the feed`() = runTest {
        val plumber = professional(categories = setOf(ServiceCategory.PLUMBING))
        signInAs(plumber)
        requests.setRequests(listOf(openRequest("gas", km = 3.0, category = ServiceCategory.GAS)))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), viewModel.uiState.value.ids())

        users.upsert(plumber.copy(professional = plumber.professional!!.copy(categories = setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS))))
        advanceUntilIdle()

        assertEquals(listOf("gas"), viewModel.uiState.value.ids())
    }

    @Test
    fun `given a compatible request at 8 km visible with radius 10 when the radius changes to 5 then it disappears from the feed`() =
        runTest {
            val electrician = professional(radiusKm = 10)
            signInAs(electrician)
            requests.setRequests(listOf(openRequest("r8", km = 8.0)))
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(listOf("r8"), viewModel.uiState.value.ids())

            users.upsert(electrician.copy(professional = electrician.professional!!.copy(radiusKm = 5)))
            advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.uiState.value.ids())
        }
}
