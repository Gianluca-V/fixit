package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.FeedItem
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestRepairRequestRepository
import com.vespelabs.fixit.core.domain.testing.TestUserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFeedUseCaseTest {
    private val auth = TestAuthRepository()
    private val users = TestUserRepository()
    private val requests = TestRepairRequestRepository()
    private val observeFeed = ObserveFeedUseCase(auth, users, requests)

    private val base = TestData.florencioVarela
    private val electrician = TestData.professional(
        id = "p1",
        categories = setOf(ServiceCategory.ELECTRICITY),
        base = base,
        radiusKm = 10,
    )

    private fun placeKmAway(km: Double) = Place(TestData.northOf(base.point, km), "Somewhere")

    private fun openRequest(
        id: String,
        km: Double,
        category: ServiceCategory = ServiceCategory.ELECTRICITY,
        createdAt: Long = 1_000L,
        status: RequestStatus = RequestStatus.OPEN,
    ): RepairRequest = TestData.request(
        id = id,
        category = category,
        place = placeKmAway(km),
        createdAt = createdAt,
        status = status,
        professionalId = if (status == RequestStatus.OPEN) null else "p2",
    )

    private fun signInAs(user: User) {
        users.upsert(user)
        auth.signedInAs(user)
    }

    /** Starts collecting the feed; the returned list grows with every emission. */
    private fun TestScope.collectFeed(): List<AppResult<List<FeedItem>>> {
        val emissions = mutableListOf<AppResult<List<FeedItem>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeFeed().toList(emissions) }
        runCurrent()
        return emissions
    }

    private fun List<AppResult<List<FeedItem>>>.latestIds(): List<String> =
        ((last() as AppResult.Success).data).map { it.request.id }

    @Test
    fun `given a compatible OPEN request when the feed is observed then it is included with its rounded distance`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("r1", km = 3.0)))

        val feed = collectFeed()

        val item = (feed.last() as AppResult.Success).data.single()
        assertEquals("r1", item.request.id)
        assertEquals(3.0, item.distanceKm, 0.0)
    }

    @Test
    fun `given the feed is open when a client publishes a compatible request 2 km away then it appears with distance 2_0`() = runTest {
        signInAs(electrician)
        val feed = collectFeed()
        assertEquals(emptyList<String>(), feed.latestIds())

        requests.upsert(openRequest("r1", km = 2.0))
        runCurrent()

        assertEquals(listOf("r1"), feed.latestIds())
        assertEquals(2.0, (feed.last() as AppResult.Success).data.single().distanceKm, 0.0)
    }

    @Test
    fun `given a request of another category when the feed is observed then it is not included`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("r1", km = 3.0, category = ServiceCategory.GAS)))

        assertEquals(emptyList<String>(), collectFeed().latestIds())
    }

    @Test
    fun `given a request 12 km away and one 9_99 km away when the feed is observed then only the nearer one is included`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("far", km = 12.0), openRequest("edge", km = 9.99)))

        assertEquals(listOf("edge"), collectFeed().latestIds())
    }

    @Test
    fun `given ACCEPTED COMPLETED and CANCELLED requests nearby when the feed is observed then none is included`() = runTest {
        signInAs(electrician)
        requests.setRequests(
            listOf(
                openRequest("a", km = 1.0, status = RequestStatus.ACCEPTED),
                openRequest("b", km = 1.0, status = RequestStatus.COMPLETED),
                openRequest("c", km = 1.0, status = RequestStatus.CANCELLED),
            ),
        )

        assertEquals(emptyList<String>(), collectFeed().latestIds())
    }

    @Test
    fun `given a request visible to two professionals when one accepts it then it disappears from the other feed`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("r1", km = 3.0)))
        val feed = collectFeed()
        assertEquals(listOf("r1"), feed.latestIds())

        requests.accept("r1", "p2")
        runCurrent()

        assertEquals(emptyList<String>(), feed.latestIds())
    }

    @Test
    fun `given a visible request when the client cancels it then it disappears from the feed`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("r1", km = 3.0)))
        val feed = collectFeed()

        requests.cancel("r1")
        runCurrent()

        assertEquals(emptyList<String>(), feed.latestIds())
    }

    @Test
    fun `given several matching requests when the feed is observed then they are ordered from newest to oldest`() = runTest {
        signInAs(electrician)
        requests.setRequests(
            listOf(
                openRequest("t2", km = 1.0, createdAt = 2_000L),
                openRequest("t3", km = 1.0, createdAt = 3_000L),
                openRequest("t1", km = 1.0, createdAt = 1_000L),
            ),
        )

        assertEquals(listOf("t3", "t2", "t1"), collectFeed().latestIds())
    }

    @Test
    fun `given an OPEN request created 30 days ago when the feed is observed then it is still included`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("old", km = 2.0, createdAt = 1_000L - 30L * 24 * 60 * 60 * 1000)))

        assertEquals(listOf("old"), collectFeed().latestIds())
    }

    @Test
    fun `given a failure while loading when the feed is observed then the feed emits that error`() = runTest {
        signInAs(electrician)
        requests.observeError = DomainError.Network

        val feed = collectFeed()

        assertEquals(AppResult.Error(DomainError.Network), feed.last())
    }

    @Test
    fun `given a failure while loading the profile when the feed is observed then the feed emits that error`() = runTest {
        signInAs(electrician)
        users.observeError = DomainError.Network

        assertEquals(AppResult.Error(DomainError.Network), collectFeed().last())
    }

    @Test
    fun `given a GAS request nearby when the professional adds GAS then it appears without re-subscribing manually`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("gas", km = 3.0, category = ServiceCategory.GAS)))
        val feed = collectFeed()
        assertEquals(emptyList<String>(), feed.latestIds())

        users.upsert(electrician.copy(professional = electrician.professional!!.copy(categories = setOf(ServiceCategory.ELECTRICITY, ServiceCategory.GAS))))
        runCurrent()

        assertEquals(listOf("gas"), feed.latestIds())
    }

    @Test
    fun `given a request 8 km away when the radius is reduced to 5 then it disappears`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("r1", km = 8.0)))
        val feed = collectFeed()
        assertEquals(listOf("r1"), feed.latestIds())

        users.upsert(electrician.copy(professional = electrician.professional!!.copy(radiusKm = 5)))
        runCurrent()

        assertEquals(emptyList<String>(), feed.latestIds())
    }

    @Test
    fun `given an ACCEPTED job when the professional changes categories and radius then the job stays ACCEPTED`() = runTest {
        val plumber = TestData.professional(id = "p1", categories = setOf(ServiceCategory.PLUMBING), base = base, radiusKm = 10)
        signInAs(plumber)
        requests.setRequests(listOf(openRequest("job", km = 8.0, category = ServiceCategory.PLUMBING, status = RequestStatus.ACCEPTED).copy(professionalId = "p1")))
        collectFeed()

        users.upsert(plumber.copy(professional = plumber.professional!!.copy(categories = setOf(ServiceCategory.GAS), radiusKm = 5)))
        runCurrent()

        val job = requests.request("job")!!
        assertEquals(RequestStatus.ACCEPTED, job.status)
        assertEquals("p1", job.professionalId)
    }

    @Test
    fun `given a name and reputation change when the feed is observed then the candidate observation is not restarted`() = runTest {
        signInAs(electrician)
        requests.setRequests(listOf(openRequest("r1", km = 3.0)))
        val feed = collectFeed()
        assertEquals(1, requests.openNearStarts)

        users.upsert(electrician.copy(name = "Carlos G."))
        users.upsert(electrician.copy(name = "Carlos G.", professional = electrician.professional!!.copy(reputation = Reputation(5, 1))))
        runCurrent()

        assertEquals(1, requests.openNearStarts)
        assertEquals(listOf("r1"), feed.latestIds())
    }

    @Test
    fun `given a radius change when the feed is observed then the candidate observation is restarted`() = runTest {
        signInAs(electrician)
        collectFeed()

        users.upsert(electrician.copy(professional = electrician.professional!!.copy(radiusKm = 20)))
        runCurrent()

        assertEquals(2, requests.openNearStarts)
    }

    @Test
    fun `given a client when observing the feed then the feed emits Unauthorized`() = runTest {
        signInAs(TestData.client())

        assertEquals(AppResult.Error(DomainError.Unauthorized), collectFeed().last())
    }

    @Test
    fun `given no session when observing the feed then the feed emits Unauthorized`() = runTest {
        assertEquals(AppResult.Error(DomainError.Unauthorized), collectFeed().last())
    }

    @Test
    fun `given a professional missing from the repository when observing the feed then the feed emits NotFound`() = runTest {
        auth.signedInAs(electrician)

        assertEquals(AppResult.Error(DomainError.NotFound), collectFeed().last())
    }
}
