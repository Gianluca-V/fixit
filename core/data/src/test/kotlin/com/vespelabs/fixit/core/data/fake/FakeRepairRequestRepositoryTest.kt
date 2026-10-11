package com.vespelabs.fixit.core.data.fake

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.rules.average
import com.vespelabs.fixit.core.domain.testing.TestClock
import com.vespelabs.fixit.core.domain.testing.TestData
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

    // region (b) atomic writes

    private fun request(id: String) = db.state.value.requests.first { it.id == id }

    private fun reputationOf(userId: String) = db.state.value.users.first { it.id == userId }.professional!!.reputation

    @Test
    fun `given an OPEN request when a professional accepts then it is ACCEPTED assigned to them and stamped with the clock`() = runTest {
        clock.time = 1_800_000_900_000L

        val result = requests.accept("r1", "p1")

        val accepted = (result as AppResult.Success).data
        assertEquals(RequestStatus.ACCEPTED, accepted.status)
        assertEquals("p1", accepted.professionalId)
        assertEquals(1_800_000_900_000L, accepted.acceptedAt)
        assertEquals(accepted, request("r1"))
    }

    @Test
    fun `given a request accepted by A when B accepts then the result is AlreadyTaken and A stays assigned`() = runTest {
        requests.accept("r1", "p1")

        val result = requests.accept("r1", "p2")

        assertEquals(AppResult.Error(DomainError.AlreadyTaken), result)
        assertEquals("p1", request("r1").professionalId)
    }

    @Test
    fun `given a request accepted by A when A accepts again then it succeeds unchanged and the acceptance date stays`() = runTest {
        clock.time = 1_800_000_900_000L
        val first = (requests.accept("r1", "p1") as AppResult.Success).data
        clock.advanceBy(60_000)

        val second = requests.accept("r1", "p1")

        assertEquals(AppResult.Success(first), second)
        assertEquals(1_800_000_900_000L, request("r1").acceptedAt)
    }

    @Test
    fun `given 10 professionals when they accept the same request in parallel then exactly one succeeds and nine get AlreadyTaken`() = runTest {
        val results = withContext(Dispatchers.Default) {
            List(10) { i -> async { requests.accept("r3", "pro$i") } }.awaitAll()
        }

        assertEquals(1, results.count { it is AppResult.Success })
        assertEquals(9, results.count { it == AppResult.Error(DomainError.AlreadyTaken) })
        val winner = (results.first { it is AppResult.Success } as AppResult.Success).data
        assertEquals(winner, request("r3"))
    }

    @Test
    fun `given a cancelled request when a professional accepts then the result is InvalidTransition and it stays CANCELLED`() = runTest {
        requests.cancel("r1")

        val result = requests.accept("r1", "p1")

        assertEquals(AppResult.Error(DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.ACCEPT)), result)
        assertEquals(RequestStatus.CANCELLED, request("r1").status)
    }

    @Test
    fun `given an OPEN request when the client cancels it then it is CANCELLED with the clock date and leaves the candidates`() = runTest {
        clock.time = 1_800_000_700_000L
        val candidates = collect(requests.observeOpenNear(GeoPoint(-34.8065, -58.2788), 10))
        assertTrue(ids(candidates.last()).contains("r1"))

        val result = requests.cancel("r1")

        val cancelled = (result as AppResult.Success).data
        assertEquals(RequestStatus.CANCELLED, cancelled.status)
        assertEquals(1_800_000_700_000L, cancelled.cancelledAt)
        assertTrue(!ids(candidates.last()).contains("r1"))
    }

    @Test
    fun `given a request accepted by P when the client cancels it then it stays linked to P and shows as CANCELLED in P's jobs`() = runTest {
        requests.accept("r1", "p1")
        clock.advanceBy(1_000)

        val result = requests.cancel("r1")

        val cancelled = (result as AppResult.Success).data
        assertEquals(RequestStatus.CANCELLED, cancelled.status)
        assertEquals("p1", cancelled.professionalId)
        assertEquals(clock.now(), cancelled.cancelledAt)
        assertEquals(
            RequestStatus.CANCELLED,
            (requests.observeByProfessional("p1").first() as AppResult.Success).data.first { it.id == "r1" }.status,
        )
    }

    @Test
    fun `given a request accepted by P when the client cancels it then no review is created and P's reputation does not change`() = runTest {
        requests.accept("r1", "p1")
        val reviewsBefore = db.state.value.reviews
        val reputationBefore = reputationOf("p1")

        requests.cancel("r1")

        assertEquals(reviewsBefore, db.state.value.reviews)
        assertEquals(reputationBefore, reputationOf("p1"))
    }

    @Test
    fun `given a COMPLETED or a CANCELLED request when cancelling then the result is InvalidTransition`() = runTest {
        requests.cancel("r1")

        assertEquals(
            AppResult.Error(DomainError.InvalidTransition(RequestStatus.COMPLETED, RequestAction.CANCEL)),
            requests.cancel("r5"),
        )
        assertEquals(
            AppResult.Error(DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.CANCEL)),
            requests.cancel("r1"),
        )
    }

    @Test
    fun `given an OPEN request when P accepts and then the client cancels then the final state is CANCELLED and assigned to P`() = runTest {
        requests.accept("r1", "p1")

        val result = requests.cancel("r1")

        assertTrue(result is AppResult.Success)
        assertEquals(RequestStatus.CANCELLED, request("r1").status)
        assertEquals("p1", request("r1").professionalId)
    }

    @Test
    fun `given missing requests when accepting cancelling or completing then the result is NotFound`() = runTest {
        assertEquals(AppResult.Error(DomainError.NotFound), requests.accept("missing", "p1"))
        assertEquals(AppResult.Error(DomainError.NotFound), requests.cancel("missing"))
        assertEquals(AppResult.Error(DomainError.NotFound), requests.completeWithReview("missing", 5, null))
    }

    @Test
    fun `given an accepted job with a professional at 4 of 1 when completing with rating 5 then request review and reputation change together`() = runTest {
        clock.time = 1_800_000_800_000L
        val reputationBefore = reputationOf("p3")

        val result = requests.completeWithReview("r4", 5, "Excelente")

        val review = (result as AppResult.Success).data
        assertEquals(Review("r4", "p3", "c2", 5, "Excelente", 1_800_000_800_000L), review)
        assertEquals(RequestStatus.COMPLETED, request("r4").status)
        assertEquals(1_800_000_800_000L, request("r4").completedAt)
        assertEquals(
            Reputation(reputationBefore.ratingSum + 5, reputationBefore.reviewCount + 1),
            reputationOf("p3"),
        )
        assertEquals(AppResult.Success(review), requests.observeReview("r4").first())
    }

    @Test
    fun `given a professional with sum 8 and 2 reviews when a job is completed with 5 then the sum is 13 and the count 3`() = runTest {
        db.write { old, _ ->
            val reputation = Reputation(ratingSum = 8, reviewCount = 2)
            WriteDecision.Commit(
                old.copy(
                    users = old.users.map { u ->
                        if (u.id == "p2") u.copy(professional = u.professional!!.copy(reputation = reputation)) else u
                    },
                    requests = old.requests +
                        TestData.request(id = "job", clientId = "c1", status = RequestStatus.ACCEPTED, professionalId = "p2", acceptedAt = 5),
                ),
                Unit,
            )
        }

        requests.completeWithReview("job", 5, null)

        assertEquals(Reputation(13, 3), reputationOf("p2"))
        assertEquals(BigDecimal("4.3"), reputationOf("p2").average())
    }

    @Test
    fun `given completing without comment when saving then the review has no comment`() = runTest {
        val review = (requests.completeWithReview("r4", 4, null) as AppResult.Success).data

        assertNull(review.comment)
    }

    @Test
    fun `given a job when failNextWrite is armed and the client completes then nothing changes and the retry succeeds`() = runTest {
        val before = db.state.value
        db.failNextWrite(DomainError.Network)

        val failed = requests.completeWithReview("r4", 5, "Excelente")

        assertEquals(AppResult.Error(DomainError.Network), failed)
        assertEquals(RequestStatus.ACCEPTED, request("r4").status)
        assertNull(request("r4").completedAt)
        assertEquals(before.reviews, db.state.value.reviews)
        assertEquals(Reputation(4, 1), reputationOf("p3"))

        val retried = requests.completeWithReview("r4", 5, "Excelente")

        assertTrue(retried is AppResult.Success)
        assertEquals(Reputation(9, 2), reputationOf("p3"))
    }

    @Test
    fun `given a COMPLETED request with its review when completing again then the result is InvalidTransition and nothing changes`() = runTest {
        requests.completeWithReview("r4", 5, null)
        val reputationAfterFirst = reputationOf("p3")
        val reviewsAfterFirst = db.state.value.reviews

        val second = requests.completeWithReview("r4", 5, null)

        assertEquals(AppResult.Error(DomainError.InvalidTransition(RequestStatus.COMPLETED, RequestAction.COMPLETE)), second)
        assertEquals(1, db.state.value.reviews.count { it.requestId == "r4" })
        assertEquals(reviewsAfterFirst, db.state.value.reviews)
        assertEquals(reputationAfterFirst, reputationOf("p3"))
    }

    @Test
    fun `given two parallel completions when they run then exactly one succeeds and a single review exists`() = runTest {
        val results = withContext(Dispatchers.Default) {
            List(2) { async { requests.completeWithReview("r4", 5, null) } }.awaitAll()
        }

        assertEquals(1, results.count { it is AppResult.Success })
        assertEquals(1, db.state.value.reviews.count { it.requestId == "r4" })
        assertEquals(Reputation(9, 2), reputationOf("p3"))
    }

    @Test
    fun `given OPEN and CANCELLED requests when completing then the result is InvalidTransition and no review or reputation change`() = runTest {
        requests.cancel("r2")
        val before = db.state.value

        assertEquals(
            AppResult.Error(DomainError.InvalidTransition(RequestStatus.OPEN, RequestAction.COMPLETE)),
            requests.completeWithReview("r1", 5, null),
        )
        assertEquals(
            AppResult.Error(DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.COMPLETE)),
            requests.completeWithReview("r2", 5, null),
        )
        assertEquals(before, db.state.value)
    }

    @Test
    fun `given observers when a job is completed then no state with only part of the change is ever observed`() = runTest {
        val states = collect(db.state)

        requests.completeWithReview("r4", 5, "Excelente")

        states.forEach { state ->
            val completed = state.requests.first { it.id == "r4" }.status == RequestStatus.COMPLETED
            val hasReview = state.reviews.any { it.requestId == "r4" }
            val reputation = state.users.first { it.id == "p3" }.professional!!.reputation
            assertEquals(completed, hasReview)
            assertEquals(completed, reputation == Reputation(9, 2))
        }
        assertEquals(2, states.size)
    }

    @Test
    fun `given failNextWrite when accepting or cancelling then the result is that error and the request is untouched`() = runTest {
        db.failNextWrite(DomainError.Network)
        assertEquals(AppResult.Error(DomainError.Network), requests.accept("r1", "p1"))
        assertEquals(RequestStatus.OPEN, request("r1").status)

        db.failNextWrite(DomainError.Network)
        assertEquals(AppResult.Error(DomainError.Network), requests.cancel("r1"))
        assertEquals(RequestStatus.OPEN, request("r1").status)
    }

    @Test
    fun `given a professional's job list when the job is accepted and then cancelled then each change is emitted live`() = runTest {
        val jobs = collect(requests.observeByProfessional("p2"))
        assertEquals(emptyList<String>(), ids(jobs.last()))

        requests.accept("r2", "p2")
        assertEquals(RequestStatus.ACCEPTED, (jobs.last() as AppResult.Success).data.single().status)

        requests.cancel("r2")
        assertEquals(RequestStatus.CANCELLED, (jobs.last() as AppResult.Success).data.single().status)
    }

    @Test
    fun `given a candidate list when another professional accepts a request then it leaves the candidates`() = runTest {
        val candidates = collect(requests.observeOpenNear(GeoPoint(-34.7206, -58.2546), 8))
        assertTrue(ids(candidates.last()).contains("r3"))

        requests.accept("r3", "p1")

        assertTrue(!ids(candidates.last()).contains("r3"))
    }

    @Test
    fun `given a review observation when the job is completed then Success null becomes the review`() = runTest {
        val reviews = collect(requests.observeReview("r4"))
        assertEquals(AppResult.Success(null), reviews.last())

        requests.completeWithReview("r4", 5, "Excelente")

        assertEquals(5, (reviews.last() as AppResult.Success).data!!.rating)
    }

    // endregion
}
