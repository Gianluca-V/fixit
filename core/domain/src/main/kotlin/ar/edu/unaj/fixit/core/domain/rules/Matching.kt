package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.ProfessionalInfo
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.RequestStatus

object Matching {
    /** Inclusive boundary check on the unrounded distance. */
    fun isWithin(distanceKm: Double, radiusKm: Int): Boolean = distanceKm <= radiusKm

    /** Rounds to 0.1 km for display purposes. */
    fun roundKm(km: Double): Double = Math.round(km * 10) / 10.0

    /**
     * Distance (rounded to 0.1 km) from the professional's base to the request when the request
     * is OPEN, in one of the professional's categories and within the radius; otherwise null.
     */
    fun distanceIfMatches(pro: ProfessionalInfo, r: RepairRequest): Double? {
        if (r.status != RequestStatus.OPEN || r.category !in pro.categories) return null
        val distance = Haversine.km(pro.base.point, r.place.point)
        return if (isWithin(distance, pro.radiusKm)) roundKm(distance) else null
    }
}
