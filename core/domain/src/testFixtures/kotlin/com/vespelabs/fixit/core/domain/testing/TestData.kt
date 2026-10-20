package com.vespelabs.fixit.core.domain.testing

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.NewRequest
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfessionalInfo
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole

/** Small builders shared by the use-case and ViewModel tests. */
object TestData {
    val florencioVarela = Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela")

    fun client(
        id: String = "c1",
        name: String = "Ana López",
        email: String = "ana@fixit.test",
        phone: String = "11 5555-0001",
        area: Place? = null,
    ) = User(id = id, name = name, email = email, phone = phone, role = UserRole.CLIENT, area = area)

    fun professional(
        id: String = "p1",
        name: String = "Carlos Gómez",
        email: String = "carlos@fixit.test",
        phone: String = "11 5555-0101",
        categories: Set<ServiceCategory> = setOf(ServiceCategory.PLUMBING),
        base: Place = florencioVarela,
        radiusKm: Int = 10,
        reputation: Reputation = Reputation(),
    ) = User(
        id = id,
        name = name,
        email = email,
        phone = phone,
        role = UserRole.PROFESSIONAL,
        professional = ProfessionalInfo(categories, base, radiusKm, reputation),
    )

    fun request(
        id: String = "r1",
        clientId: String = "c1",
        category: ServiceCategory = ServiceCategory.PLUMBING,
        description: String = "The kitchen tap is leaking",
        place: Place = florencioVarela,
        status: RequestStatus = RequestStatus.OPEN,
        professionalId: String? = null,
        createdAt: Long = 1_000L,
        acceptedAt: Long? = null,
        cancelledAt: Long? = null,
        completedAt: Long? = null,
    ) = RepairRequest(
        id = id,
        clientId = clientId,
        category = category,
        description = description,
        photoUrls = listOf("photo-1"),
        place = place,
        status = status,
        professionalId = professionalId,
        createdAt = createdAt,
        acceptedAt = acceptedAt,
        cancelledAt = cancelledAt,
        completedAt = completedAt,
    )

    fun newRequest(
        category: ServiceCategory? = ServiceCategory.PLUMBING,
        description: String = "The kitchen tap is leaking",
        photoUris: List<String> = listOf("photo-1", "photo-2"),
        place: Place? = florencioVarela,
    ) = NewRequest(category, description, photoUris, place)

    /** A point [km] kilometers north of [from] (one degree of latitude is about 111.195 km). */
    fun northOf(from: GeoPoint, km: Double) = GeoPoint(from.latitude + km / 111.195, from.longitude)
}
