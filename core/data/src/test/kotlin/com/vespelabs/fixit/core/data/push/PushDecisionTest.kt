package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.rules.Haversine
import com.vespelabs.fixit.core.domain.testing.TestData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design 14b.5: the on-device decision (spec "Filtro de distancia en el dispositivo" and the two user events). */
class PushDecisionTest {
    private val base = TestData.florencioVarela
    private val plumber = TestData.professional(id = "p1", categories = setOf(ServiceCategory.PLUMBING), radiusKm = 10)

    private fun openPlumbingAt(point: GeoPoint, status: RequestStatus = RequestStatus.OPEN) =
        TestData.request(id = "r1", category = ServiceCategory.PLUMBING, place = Place(point, "Somewhere"), status = status)

    /**
     * A point whose unrounded Haversine distance from the base is exactly [km]. Doubles are discrete, so for each of
     * several small longitude offsets the latitude is bisected and the first exact hit is kept.
     */
    private fun pointAtExactly(km: Double): GeoPoint {
        for (step in 0 until 20_000) {
            val longitude = base.point.longitude + step * 1e-7
            var low = base.point.latitude
            var high = base.point.latitude + 2 * km / 111.0
            repeat(80) {
                val mid = (low + high) / 2
                if (Haversine.km(base.point, GeoPoint(mid, longitude)) < km) low = mid else high = mid
            }
            listOf(low, high).map { GeoPoint(it, longitude) }.firstOrNull { Haversine.km(base.point, it) == km }
                ?.let { return it }
        }
        error("no point at exactly $km km was found")
    }

    // region NEW_REQUEST (Matching.distanceIfMatches)

    @Test
    fun `Solicitud dentro del radio - an OPEN plumbing request 4 km away from a 10 km plumber is shown`() {
        val request = openPlumbingAt(TestData.northOf(base.point, 4.0))

        assertTrue(shouldNotify(PushEventType.NEW_REQUEST, request, plumber))
    }

    @Test
    fun `Solicitud fuera del radio - an OPEN request of the category 15 km away is not shown`() {
        val request = openPlumbingAt(TestData.northOf(base.point, 15.0))

        assertFalse(shouldNotify(PushEventType.NEW_REQUEST, request, plumber))
    }

    @Test
    fun `Solicitud exactamente en el limite del radio - an unrounded distance equal to the radius is shown`() {
        val request = openPlumbingAt(pointAtExactly(10.0))

        assertTrue(shouldNotify(PushEventType.NEW_REQUEST, request, plumber))
    }

    @Test
    fun `Categoria ya no vigente - a request of a category the professional no longer offers is not shown`() {
        val gasRequest = openPlumbingAt(TestData.northOf(base.point, 1.0)).copy(category = ServiceCategory.GAS)

        assertFalse(shouldNotify(PushEventType.NEW_REQUEST, gasRequest, plumber))
    }

    @Test
    fun `Solicitud que ya no esta OPEN - an ACCEPTED or CANCELLED request is not shown`() {
        val near = TestData.northOf(base.point, 1.0)

        assertFalse(shouldNotify(PushEventType.NEW_REQUEST, openPlumbingAt(near, RequestStatus.ACCEPTED), plumber))
        assertFalse(shouldNotify(PushEventType.NEW_REQUEST, openPlumbingAt(near, RequestStatus.CANCELLED), plumber))
    }

    @Test
    fun `Sin sesion de profesional - a CLIENT viewer or no viewer is never shown a new request`() {
        val request = openPlumbingAt(TestData.northOf(base.point, 1.0))

        assertFalse(shouldNotify(PushEventType.NEW_REQUEST, request, TestData.client(id = "c9")))
        assertFalse(shouldNotify(PushEventType.NEW_REQUEST, request, null))
    }

    // endregion

    // region REQUEST_ACCEPTED / REQUEST_CANCELLED

    @Test
    fun `given an ACCEPTED request when its owner client receives REQUEST_ACCEPTED then it is shown`() {
        val accepted = TestData.request(clientId = "c1", status = RequestStatus.ACCEPTED, professionalId = "p1")

        assertTrue(shouldNotify(PushEventType.REQUEST_ACCEPTED, accepted, TestData.client(id = "c1")))
    }

    @Test
    fun `given REQUEST_ACCEPTED when the viewer is another user or the request is not ACCEPTED then it is not shown`() {
        val accepted = TestData.request(clientId = "c1", status = RequestStatus.ACCEPTED, professionalId = "p1")

        assertFalse(shouldNotify(PushEventType.REQUEST_ACCEPTED, accepted, TestData.client(id = "c2")))
        assertFalse(shouldNotify(PushEventType.REQUEST_ACCEPTED, accepted.copy(status = RequestStatus.CANCELLED), TestData.client(id = "c1")))
        assertFalse(shouldNotify(PushEventType.REQUEST_ACCEPTED, accepted, null))
    }

    @Test
    fun `given a CANCELLED request when its assigned professional receives REQUEST_CANCELLED then it is shown`() {
        val cancelled = TestData.request(status = RequestStatus.CANCELLED, professionalId = "p1")

        assertTrue(shouldNotify(PushEventType.REQUEST_CANCELLED, cancelled, plumber))
    }

    @Test
    fun `given REQUEST_CANCELLED when the viewer is another professional or the request is not CANCELLED then it is not shown`() {
        val cancelled = TestData.request(status = RequestStatus.CANCELLED, professionalId = "p1")

        assertFalse(shouldNotify(PushEventType.REQUEST_CANCELLED, cancelled, TestData.professional(id = "p2")))
        assertFalse(shouldNotify(PushEventType.REQUEST_CANCELLED, cancelled.copy(status = RequestStatus.ACCEPTED), plumber))
        assertFalse(shouldNotify(PushEventType.REQUEST_CANCELLED, cancelled, null))
    }

    // endregion

    // region mayMatchCategory (pre-filter, no reads)

    @Test
    fun `given a category the professional offers when pre-filtering then it may match`() {
        assertTrue(mayMatchCategory("PLUMBING", plumber))
    }

    @Test
    fun `given a category the professional no longer offers when pre-filtering then it cannot match`() {
        assertFalse(mayMatchCategory("GAS", plumber))
    }

    @Test
    fun `given a CLIENT or no viewer with a category when pre-filtering then it cannot match`() {
        assertFalse(mayMatchCategory("PLUMBING", TestData.client()))
        assertFalse(mayMatchCategory("PLUMBING", null))
    }

    @Test
    fun `given a payload without category when pre-filtering then it is not filtered`() {
        assertTrue(mayMatchCategory(null, TestData.client()))
        assertTrue(mayMatchCategory(null, null))
    }

    // endregion
}
