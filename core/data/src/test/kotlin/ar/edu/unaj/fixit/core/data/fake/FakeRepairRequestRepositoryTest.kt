package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.Review
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.testing.TestClock
import ar.edu.unaj.fixit.core.domain.testing.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeRepairRequestRepositoryTest {
    private val clock = TestClock(1_800_000_000_000L)
    private val db = FakeDatabase(clock, SeedData.initialState(clock.now()))
    private val requests = FakeRepairRequestRepository(db)

    private fun ids(result: AppResult<List<RepairRequest>>) = (result as AppResult.Success).data.map { it.id }

    private suspend fun setRequests(transform: (List<RepairRequest>) -> List<RepairRequest>) {
        db.write { old, _ -> WriteDecision.Commit(old.copy(requests = transform(old.requests)), Unit) }
    }

    /** Collects an observation; the returned list grows with every emission. */
    private fun <T> TestScope.collect(flow: Flow<T>): List<T> {
        val emissions = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(emissions) }
        return emissions
    }

    // region (a) reads

    @Test
    fun `given a seeded request when getting it then it is returned and a missing id is NotFound`() = runTest {
        assertEquals("r1", (requests.get("r1") as AppResult.Success).data.id)
        assertEquals(AppResult.Error(DomainError.NotFound), requests.get("missing"))
        assertEquals(AppResult.Error(DomainError.NotFound), requests.observe("missing").first())
    }

    @Test
    fun `given a seeded request when observing it then it emits the request`() = runTest {
        assertEquals("r3", (requests.observe("r3").first() as AppResult.Success).data.id)
    }

    @Test
    fun `given a client with several requests when observing them then they are ordered from newest to oldest`() = runTest {
        assertEquals(listOf("r1", "r2", "r8", "r5"), ids(requests.observeByClient("c1").first()))
    }

    @Test
    fun `given requests of other clients when observing a client's list then only that client's requests appear`() = runTest {
        assertEquals(listOf("r3", "r6", "r4", "r7"), ids(requests.observeByClient("c2").first()))
    }

    @Test
    fun `given a client when creating a request then it is OPEN without professional and stamped with the clock`() = runTest {
        clock.time = 1_800_000_500_000L

        val result = requests.create("c1", TestData.newRequest(description = "Leaking tap in the kitchen"))

        val created = (result as AppResult.Success).data
        assertEquals(RequestStatus.OPEN, created.status)
        assertNull(created.professionalId)
        assertEquals("c1", created.clientId)
        assertEquals(1_800_000_500_000L, created.createdAt)
        assertEquals(listOf("photo-1", "photo-2"), created.photoUrls)
        assertEquals(created, (requests.get(created.id) as AppResult.Success).data)
    }

    @Test
    fun `given a created request when observing the client's list then it appears first`() = runTest {
        clock.time += 1_000L

        val created = (requests.create("c1", TestData.newRequest()) as AppResult.Success).data

        assertEquals(created.id, ids(requests.observeByClient("c1").first()).first())
    }

    @Test
    fun `given three requests created in order when observing the list then the order is t3 t2 t1`() = runTest {
        val first = (requests.create("c2", TestData.newRequest()) as AppResult.Success).data
        clock.advanceBy(1_000)
        val second = (requests.create("c2", TestData.newRequest()) as AppResult.Success).data
        clock.advanceBy(1_000)
        val third = (requests.create("c2", TestData.newRequest()) as AppResult.Success).data

        assertEquals(
            listOf(third.id, second.id, first.id),
            ids(requests.observeByClient("c2").first()).take(3),
        )
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `given a missing client when creating a request then the result is NotFound`() = runTest {
        assertEquals(AppResult.Error(DomainError.NotFound), requests.create("ghost", TestData.newRequest()))
    }

    @Test
    fun `given failNextWrite when creating a request then the result is that error and nothing is created`() = runTest {
        val before = db.state.value.requests
        db.failNextWrite(DomainError.Network)

        val result = requests.create("c1", TestData.newRequest())

        assertEquals(AppResult.Error(DomainError.Network), result)
        assertEquals(before, db.state.value.requests)
    }

    @Test
    fun `given the seed when observing a professional's jobs then ACCEPTED COMPLETED and CANCELLED-after-accept appear by acceptance date`() = runTest {
        setRequests { all -> all.map { if (it.id == "r8") it.copy(status = RequestStatus.CANCELLED, cancelledAt = clock.now()) else it } }

        assertEquals(listOf("r4", "r8"), ids(requests.observeByProfessional("p3").first()))
        assertEquals(listOf("r7", "r5"), ids(requests.observeByProfessional("p1").first()))
        assertEquals(emptyList<String>(), ids(requests.observeByProfessional("p2").first()))
    }

    @Test
    fun `given a COMPLETED job t1 and two ACCEPTED jobs t2 and t3 when observing the jobs then the order is t3 t2 t1`() = runTest {
        setRequests { all ->
            listOf(
                TestData.request(id = "j1", status = RequestStatus.COMPLETED, professionalId = "p2", createdAt = 10, acceptedAt = 100, completedAt = 150),
                TestData.request(id = "j2", status = RequestStatus.ACCEPTED, professionalId = "p2", createdAt = 20, acceptedAt = 200),
                TestData.request(id = "j3", status = RequestStatus.ACCEPTED, professionalId = "p2", createdAt = 30, acceptedAt = 300),
            ) + all
        }

        assertEquals(listOf("j3", "j2", "j1"), ids(requests.observeByProfessional("p2").first()))
    }

    @Test
    fun `given a professional with three ACCEPTED jobs when observing them then all of them are listed`() = runTest {
        setRequests { all ->
            all + List(3) { TestData.request(id = "a$it", status = RequestStatus.ACCEPTED, professionalId = "p2", acceptedAt = 100L + it) }
        }

        assertEquals(listOf("a2", "a1", "a0"), ids(requests.observeByProfessional("p2").first()))
    }

    @Test
    fun `given the seed when observing open candidates then only OPEN requests are returned`() = runTest {
        val candidates = ids(requests.observeOpenNear(GeoPoint(-34.8065, -58.2788), 10).first())

        assertEquals(setOf("r1", "r2", "r3", "r6"), candidates.toSet())
    }

    @Test
    fun `given a request with a review when observing its review then the review is emitted and otherwise Success null`() = runTest {
        val review = (requests.observeReview("r5").first() as AppResult.Success).data

        assertEquals(Review::class, review!!::class)
        assertEquals(5, review.rating)
        assertEquals(AppResult.Success(null), requests.observeReview("r4").first())
    }

    @Test
    fun `given an observed client list when another client's request is created then nothing is emitted again`() = runTest {
        val emissions = collect(requests.observeByClient("c1"))

        requests.create("c2", TestData.newRequest())
        db.signIn("c2")

        assertEquals(1, emissions.size)
    }

    @Test
    fun `given an observed request when it is unrelated to a change then the observation does not repeat`() = runTest {
        val emissions = collect(requests.observe("r1"))

        requests.create("c2", TestData.newRequest())

        assertEquals(1, emissions.size)
        assertTrue(emissions.single() is AppResult.Success)
    }

    @Test
    fun `given an observed candidates list when a compatible request is created then the list emits with it`() = runTest {
        val emissions = collect(requests.observeOpenNear(GeoPoint(-34.8065, -58.2788), 10))

        val created = (requests.create("c1", TestData.newRequest(category = ServiceCategory.PLUMBING)) as AppResult.Success).data

        assertEquals(2, emissions.size)
        assertTrue(ids(emissions.last()).contains(created.id))
    }

    // endregion
}
