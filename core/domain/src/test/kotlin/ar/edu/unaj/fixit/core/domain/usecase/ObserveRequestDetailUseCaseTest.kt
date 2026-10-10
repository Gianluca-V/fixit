package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.model.RequestDetail
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.Review
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.rules.average
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestRepairRequestRepository
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveRequestDetailUseCaseTest {
    private val auth = TestAuthRepository()
    private val users = TestUserRepository()
    private val requests = TestRepairRequestRepository()
    private val observeDetail = ObserveRequestDetailUseCase(auth, users, requests)

    private val client = TestData.client(id = "c1", name = "Ana López", phone = "11 5555-0001")
    private val carlos = TestData.professional(
        id = "p1",
        name = "Carlos Gómez",
        phone = "11 5555-0101",
        categories = setOf(ServiceCategory.PLUMBING),
        reputation = Reputation(ratingSum = 45, reviewCount = 10),
    )
    private val diana = TestData.professional(id = "p2", name = "Diana Ruiz", phone = "11 5555-0102")

    private fun signInAs(user: User) {
        auth.signedInAs(user)
    }

    private fun givenUsers(vararg all: User) = all.forEach { users.upsert(it) }

    private fun givenRequest(
        status: RequestStatus,
        clientId: String = client.id,
        professionalId: String? = null,
        place: Place = TestData.florencioVarela,
    ): RepairRequest = TestData.request(
        id = "r1",
        clientId = clientId,
        status = status,
        professionalId = professionalId,
        place = place,
    ).also { requests.upsert(it) }

    /** Starts collecting the detail; the returned list grows with every emission. */
    private fun TestScope.collectDetail(requestId: String = "r1"): List<AppResult<RequestDetail>> {
        val emissions = mutableListOf<AppResult<RequestDetail>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeDetail(requestId).toList(emissions) }
        runCurrent()
        return emissions
    }

    private fun List<AppResult<RequestDetail>>.latest(): RequestDetail = (last() as AppResult.Success).data

    // region visibility and errors

    @Test
    fun `given a request that does not exist when observing the detail then the result is NotFound`() = runTest {
        givenUsers(client)
        signInAs(client)

        assertEquals(AppResult.Error(DomainError.NotFound), collectDetail("missing").last())
    }

    @Test
    fun `given a request of another client when a client observes it then the result is Unauthorized`() = runTest {
        givenUsers(client, TestData.client(id = "c2"))
        signInAs(client)
        givenRequest(RequestStatus.OPEN, clientId = "c2")

        assertEquals(AppResult.Error(DomainError.Unauthorized), collectDetail().last())
    }

    @Test
    fun `given no session when observing the detail then the result is Unauthorized`() = runTest {
        givenRequest(RequestStatus.OPEN)

        assertEquals(AppResult.Error(DomainError.Unauthorized), collectDetail().last())
    }

    @Test
    fun `given a failure while observing the request when observing the detail then that error is emitted`() = runTest {
        givenUsers(client)
        signInAs(client)
        givenRequest(RequestStatus.OPEN)
        requests.observeError = DomainError.Network

        assertEquals(AppResult.Error(DomainError.Network), collectDetail().last())
    }

    @Test
    fun `given an ACCEPTED request whose professional profile is missing when observing then the result is NotFound`() = runTest {
        givenUsers(client)
        signInAs(client)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "ghost")

        assertEquals(AppResult.Error(DomainError.NotFound), collectDetail().last())
    }

    // endregion

    // region client viewer

    @Test
    fun `given an OPEN request when the client observes it then there is no contact review or distance`() = runTest {
        givenUsers(client)
        signInAs(client)
        givenRequest(RequestStatus.OPEN)

        val detail = collectDetail().latest()

        assertEquals(RequestStatus.OPEN, detail.request.status)
        assertNull(detail.contact)
        assertNull(detail.review)
        assertNull(detail.distanceKm)
        assertFalse(detail.viewerIsAssignee)
    }

    @Test
    fun `given an open detail when a professional accepts then it shows the professional and contact without reloading`() = runTest {
        givenUsers(client, carlos)
        signInAs(client)
        givenRequest(RequestStatus.OPEN)
        val detail = collectDetail()
        assertNull(detail.latest().contact)

        requests.accept("r1", "p1")
        runCurrent()

        val contact = detail.latest().contact!!
        assertEquals(RequestStatus.ACCEPTED, detail.latest().request.status)
        assertEquals("Carlos Gómez", contact.name)
        assertEquals("11 5555-0101", contact.phone)
        assertEquals("https://wa.me/5491155550101", contact.whatsAppUrl)
    }

    @Test
    fun `given an ACCEPTED request by a professional with sum 45 and 10 reviews when the client observes it then average and count are exposed`() = runTest {
        givenUsers(client, carlos)
        signInAs(client)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")

        val reputation = collectDetail().latest().contact!!.reputation!!

        assertEquals(Reputation(45, 10), reputation)
        assertEquals(BigDecimal("4.5"), reputation.average())
        assertEquals(10, reputation.reviewCount)
    }

    @Test
    fun `given a request accepted and then cancelled when the client observes it then there is no professional contact`() = runTest {
        givenUsers(client, carlos)
        signInAs(client)
        givenRequest(RequestStatus.CANCELLED, professionalId = "p1")

        val detail = collectDetail().latest()

        assertEquals(RequestStatus.CANCELLED, detail.request.status)
        assertNull(detail.contact)
    }

    @Test
    fun `given an ACCEPTED detail when the client cancels then the contact disappears live`() = runTest {
        givenUsers(client, carlos)
        signInAs(client)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")
        val detail = collectDetail()
        assertNotNull(detail.latest().contact)

        requests.cancel("r1")
        runCurrent()

        assertEquals(RequestStatus.CANCELLED, detail.latest().request.status)
        assertNull(detail.latest().contact)
    }

    @Test
    fun `given a COMPLETED request with a review when the client observes it then the review and contact are included`() = runTest {
        givenUsers(client, carlos)
        signInAs(client)
        givenRequest(RequestStatus.COMPLETED, professionalId = "p1")
        val review = Review("r1", "p1", "c1", rating = 5, comment = "Muy prolijo", createdAt = 2_000L)
        requests.setReviews(listOf(review))

        val detail = collectDetail().latest()

        assertEquals(review, detail.review)
        assertNotNull(detail.contact)
    }

    @Test
    fun `given a COMPLETED request when the review arrives later then the final emission includes it`() = runTest {
        givenUsers(client, carlos)
        signInAs(client)
        givenRequest(RequestStatus.COMPLETED, professionalId = "p1")
        val detail = collectDetail()
        assertNull(detail.latest().review)

        val review = Review("r1", "p1", "c1", rating = 4, comment = null, createdAt = 2_000L)
        requests.setReviews(listOf(review))
        runCurrent()

        assertEquals(review, detail.latest().review)
    }

    // endregion

    // region professional viewer

    @Test
    fun `given an OPEN request when a professional observes it then there is no client contact and the distance is rounded`() = runTest {
        givenUsers(client, carlos)
        signInAs(carlos)
        givenRequest(RequestStatus.OPEN, place = Place(TestData.northOf(carlos.professional!!.base.point, 3.04), "Somewhere"))

        val detail = collectDetail().latest()

        assertNull(detail.contact)
        assertEquals(3.0, detail.distanceKm!!, 0.0)
        assertFalse(detail.viewerIsAssignee)
    }

    @Test
    fun `given a request ACCEPTED by another professional when a professional observes it then there is no contact and they are not the assignee`() = runTest {
        givenUsers(client, carlos, diana)
        signInAs(diana)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")

        val detail = collectDetail().latest()

        assertNull(detail.contact)
        assertFalse(detail.viewerIsAssignee)
    }

    @Test
    fun `given a request cancelled while OPEN when a professional observes it then there is no contact and they are not the assignee`() = runTest {
        givenUsers(client, diana)
        signInAs(diana)
        givenRequest(RequestStatus.CANCELLED)

        val detail = collectDetail().latest()

        assertNull(detail.contact)
        assertFalse(detail.viewerIsAssignee)
    }

    @Test
    fun `given a request ACCEPTED by the professional when they observe it then they get the client contact and are the assignee`() = runTest {
        givenUsers(client, carlos)
        signInAs(carlos)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")

        val detail = collectDetail().latest()

        assertEquals("Ana López", detail.contact!!.name)
        assertEquals("11 5555-0001", detail.contact!!.phone)
        assertTrue(detail.viewerIsAssignee)
    }

    @Test
    fun `given a detail opened on an OPEN request when another professional accepts then it becomes unavailable for this one`() = runTest {
        givenUsers(client, carlos, diana)
        signInAs(diana)
        givenRequest(RequestStatus.OPEN)
        val detail = collectDetail()
        assertEquals(RequestStatus.OPEN, detail.latest().request.status)

        requests.accept("r1", "p1")
        runCurrent()

        assertEquals(RequestStatus.ACCEPTED, detail.latest().request.status)
        assertFalse(detail.latest().viewerIsAssignee)
        assertNull(detail.latest().contact)
    }

    @Test
    fun `given the assigned professional's detail when the client cancels then the contact is hidden live`() = runTest {
        givenUsers(client, carlos)
        signInAs(carlos)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")
        val detail = collectDetail()
        assertNotNull(detail.latest().contact)

        requests.cancel("r1")
        runCurrent()

        assertNull(detail.latest().contact)
        assertEquals(RequestStatus.CANCELLED, detail.latest().request.status)
        assertTrue(detail.latest().viewerIsAssignee)
    }

    @Test
    fun `given the assigned professional's detail when the client changes the phone then the new phone is shown`() = runTest {
        givenUsers(client, carlos)
        signInAs(carlos)
        givenRequest(RequestStatus.ACCEPTED, professionalId = "p1")
        val detail = collectDetail()

        users.upsert(client.copy(phone = "11 5555-9999"))
        runCurrent()

        assertEquals("11 5555-9999", detail.latest().contact!!.phone)
    }

    @Test
    fun `given a COMPLETED job with a review when the assigned professional observes it then the review is included`() = runTest {
        givenUsers(client, carlos)
        signInAs(carlos)
        givenRequest(RequestStatus.COMPLETED, professionalId = "p1")
        val review = Review("r1", "p1", "c1", rating = 5, comment = "Muy prolijo", createdAt = 2_000L)
        requests.setReviews(listOf(review))

        assertEquals(review, collectDetail().latest().review)
    }

    // endregion
}
