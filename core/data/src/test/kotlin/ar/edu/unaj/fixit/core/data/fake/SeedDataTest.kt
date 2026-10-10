package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.rules.Matching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {
    private val now = 1_800_000_000_000L
    private val seed = SeedData.initialState(now)

    private fun user(id: String) = seed.users.first { it.id == id }

    private fun request(id: String) = seed.requests.first { it.id == id }

    @Test
    fun `given the seed when listing users then there are two clients and three professionals`() {
        assertEquals(listOf("c1", "c2", "p1", "p2", "p3"), seed.users.map { it.id })
        assertEquals(2, seed.users.count { it.role == UserRole.CLIENT })
        assertEquals(3, seed.users.count { it.role == UserRole.PROFESSIONAL })
    }

    @Test
    fun `given the seed when checking contact data then every user has a phone`() {
        assertTrue(seed.users.all { it.phone.isNotBlank() })
    }

    @Test
    fun `given the seed when checking accounts then every user can sign in with the test password`() {
        assertEquals("test1234", SeedData.PASSWORD)
        assertEquals(seed.users.map { it.id }.toSet(), seed.passwords.keys)
        assertTrue(seed.passwords.values.all { it == "test1234" })
    }

    @Test
    fun `given the seed when starting then nobody is signed in`() {
        assertEquals(null, seed.currentUserId)
    }

    @Test
    fun `given the seed when checking emails then they follow the documented accounts`() {
        assertEquals(
            listOf("ana@fixit.test", "bruno@fixit.test", "carlos@fixit.test", "diana@fixit.test", "esteban@fixit.test"),
            seed.users.map { it.email },
        )
    }

    @Test
    fun `given the seed when summing each professional's reviews then reputation matches the stored reviews`() {
        seed.users.filter { it.role == UserRole.PROFESSIONAL }.forEach { professional ->
            val reviews = seed.reviews.filter { it.professionalId == professional.id }
            assertEquals(
                "reputation of ${professional.id}",
                Reputation(ratingSum = reviews.sumOf { it.rating }, reviewCount = reviews.size),
                professional.professional!!.reputation,
            )
        }
    }

    @Test
    fun `given the seed when reading reputations then they are 9 of 2 and 0 of 0 and 4 of 1`() {
        assertEquals(Reputation(9, 2), user("p1").professional!!.reputation)
        assertEquals(Reputation(0, 0), user("p2").professional!!.reputation)
        assertEquals(Reputation(4, 1), user("p3").professional!!.reputation)
    }

    @Test
    fun `given the seed when reading professionals then categories radius and base follow the design table`() {
        val p1 = user("p1").professional!!
        assertEquals(setOf(ServiceCategory.ELECTRICITY, ServiceCategory.GAS), p1.categories)
        assertEquals(10, p1.radiusKm)
        assertEquals(-34.8065, p1.base.point.latitude, 0.0)
        assertEquals(setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS), user("p2").professional!!.categories)
        assertEquals(8, user("p2").professional!!.radiusKm)
        assertEquals(
            setOf(ServiceCategory.CARPENTRY, ServiceCategory.PAINTING, ServiceCategory.OTHER),
            user("p3").professional!!.categories,
        )
        assertEquals(15, user("p3").professional!!.radiusKm)
    }

    @Test
    fun `given the seed when reading requests then ids statuses and owners follow the design table`() {
        val expected = mapOf(
            "r1" to Triple("c1", ServiceCategory.ELECTRICITY, RequestStatus.OPEN),
            "r2" to Triple("c1", ServiceCategory.PLUMBING, RequestStatus.OPEN),
            "r3" to Triple("c2", ServiceCategory.GAS, RequestStatus.OPEN),
            "r4" to Triple("c2", ServiceCategory.PAINTING, RequestStatus.ACCEPTED),
            "r5" to Triple("c1", ServiceCategory.ELECTRICITY, RequestStatus.COMPLETED),
            "r6" to Triple("c2", ServiceCategory.MASONRY, RequestStatus.OPEN),
            "r7" to Triple("c2", ServiceCategory.ELECTRICITY, RequestStatus.COMPLETED),
            "r8" to Triple("c1", ServiceCategory.PAINTING, RequestStatus.COMPLETED),
        )
        assertEquals(expected.keys.toList(), seed.requests.map { it.id })
        expected.forEach { (id, triple) ->
            val r = request(id)
            assertEquals("owner of $id", triple.first, r.clientId)
            assertEquals("category of $id", triple.second, r.category)
            assertEquals("status of $id", triple.third, r.status)
        }
    }

    @Test
    fun `given the seed when reading assigned jobs then r4 is p3's and r5 and r7 are p1's and r8 is p3's`() {
        assertEquals("p3", request("r4").professionalId)
        assertEquals("p1", request("r5").professionalId)
        assertEquals("p1", request("r7").professionalId)
        assertEquals("p3", request("r8").professionalId)
        assertTrue(listOf("r1", "r2", "r3", "r6").all { request(it).professionalId == null })
    }

    @Test
    fun `given the seed when checking dates then created is before accepted and accepted before completed`() {
        seed.requests.filter { it.acceptedAt != null }.forEach { r ->
            assertTrue("${r.id}: createdAt < acceptedAt", r.createdAt < r.acceptedAt!!)
            r.completedAt?.let { assertTrue("${r.id}: acceptedAt < completedAt", r.acceptedAt!! < it) }
        }
        assertTrue(seed.requests.filter { it.status == RequestStatus.COMPLETED }.all { it.completedAt != null })
    }

    @Test
    fun `given the seed when checking dates against the clock then none is in the future`() {
        val dates = seed.requests.flatMap { listOfNotNull(it.createdAt, it.acceptedAt, it.completedAt) } +
            seed.reviews.map { it.createdAt }
        assertTrue(dates.all { it <= now })
    }

    @Test
    fun `given the seed when reading reviews then each belongs to a completed request of the same pair`() {
        assertEquals(listOf("r5", "r7", "r8"), seed.reviews.map { it.requestId })
        seed.reviews.forEach { review ->
            val r = request(review.requestId)
            assertEquals(RequestStatus.COMPLETED, r.status)
            assertEquals(r.professionalId, review.professionalId)
            assertEquals(r.clientId, review.clientId)
            assertEquals(r.completedAt, review.createdAt)
        }
        assertEquals(listOf(5, 4, 4), seed.reviews.map { it.rating })
    }

    @Test
    fun `given the seed when matching open requests then p1 sees r1 and r3 and p2 sees r2 and r3 and p3 sees none`() {
        val open = seed.requests.filter { it.status == RequestStatus.OPEN }

        fun feedOf(professionalId: String) = open
            .filter { Matching.distanceIfMatches(user(professionalId).professional!!, it) != null }
            .map { it.id }

        assertEquals(listOf("r1", "r3"), feedOf("p1"))
        assertEquals(listOf("r2", "r3"), feedOf("p2"))
        assertEquals(emptyList<String>(), feedOf("p3"))
    }

    @Test
    fun `given the seed when reading photos then requests use the bundled sample drawables`() {
        assertTrue(seed.requests.all { it.photoUrls.isNotEmpty() && it.photoUrls.size <= 3 })
        assertTrue(
            seed.requests.flatMap { it.photoUrls }.all {
                it.startsWith("android.resource://ar.edu.unaj.fixit/drawable/sample_repair_")
            },
        )
    }
}
