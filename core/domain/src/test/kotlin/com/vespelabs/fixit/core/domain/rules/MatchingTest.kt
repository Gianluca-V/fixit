package com.vespelabs.fixit.core.domain.rules

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfessionalInfo
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchingTest {
    private val base = GeoPoint(-34.80, -58.27)
    private val professional = ProfessionalInfo(
        categories = setOf(ServiceCategory.PLUMBING),
        base = Place(base, "Base"),
        radiusKm = 10,
    )

    // One degree of latitude is about 111.195 km, so these offsets are about 3, 1 and 12 km north of the base.
    private val about3Km = GeoPoint(base.latitude + 0.027, base.longitude)
    private val about1Km = GeoPoint(base.latitude + 0.009, base.longitude)
    private val about12Km = GeoPoint(base.latitude + 0.108, base.longitude)

    private fun request(
        category: ServiceCategory = ServiceCategory.PLUMBING,
        status: RequestStatus = RequestStatus.OPEN,
        at: GeoPoint = about3Km,
    ) = RepairRequest(
        id = "r1",
        clientId = "c1",
        category = category,
        description = "Pipe leaking under the sink",
        photoUrls = emptyList(),
        place = Place(at, "Somewhere"),
        status = status,
        createdAt = 0L,
    )

    @Test
    fun `given an open request of the same category about 3 km away when matching then it matches with the rounded distance`() {
        val distance = Matching.distanceIfMatches(professional, request())

        assertNotNull(distance)
        assertEquals(3.0, distance!!, 0.1)
        assertEquals(Matching.roundKm(Haversine.km(base, about3Km)), distance, 0.0)
    }

    @Test
    fun `given an open request of another category when matching then it does not match`() {
        assertNull(Matching.distanceIfMatches(professional, request(category = ServiceCategory.GAS)))
    }

    @Test
    fun `given an open request about 12 km away and a radius of 10 when matching then it does not match`() {
        assertNull(Matching.distanceIfMatches(professional, request(at = about12Km)))
    }

    @Test
    fun `given a request that is not open when matching then it does not match`() {
        listOf(RequestStatus.ACCEPTED, RequestStatus.COMPLETED, RequestStatus.CANCELLED).forEach { status ->
            assertNull(
                "status $status must not match",
                Matching.distanceIfMatches(professional, request(status = status, at = about1Km)),
            )
        }
    }

    @Test
    fun `given a distance of exactly the radius when checking isWithin then the boundary is inclusive`() {
        assertTrue(Matching.isWithin(10.0, 10))
    }

    @Test
    fun `given a distance just over the radius when checking isWithin then it is outside`() {
        assertFalse(Matching.isWithin(10.01, 10))
    }

    @Test
    fun `given distances of 2_04 and 2_06 km when rounding then they become 2_0 and 2_1`() {
        assertEquals(2.0, Matching.roundKm(2.04), 0.0)
        assertEquals(2.1, Matching.roundKm(2.06), 0.0)
    }
}
