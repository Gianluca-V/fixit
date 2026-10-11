package com.vespelabs.fixit.core.data.fake

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfessionalInfo
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole

/**
 * Phase 1 sample data (design section 12). Every account uses [PASSWORD]. Dates are relative to the
 * given `now`, so `createdAt < acceptedAt < completedAt` and nothing lies in the future. The seeded
 * professional reputations equal the sum and count of the seeded reviews.
 */
object SeedData {
    const val PASSWORD = "test1234"

    private const val HOUR = 60L * 60 * 1000
    private const val DAY = 24 * HOUR
    private const val PHOTO_BASE = "android.resource://com.vespelabs.fixit/drawable/sample_repair_"

    fun initialState(now: Long): DbState {
        val requests = requests(now)
        val reviews = reviews(requests)
        val users = users(reviews)
        return DbState(
            users = users,
            requests = requests,
            reviews = reviews,
            passwords = users.associate { it.id to PASSWORD },
            currentUserId = null,
        )
    }

    private fun users(reviews: List<Review>): List<User> {
        fun reputationOf(professionalId: String): Reputation {
            val own = reviews.filter { it.professionalId == professionalId }
            return Reputation(ratingSum = own.sumOf { it.rating }, reviewCount = own.size)
        }
        return listOf(
            User("c1", "Ana López", "ana@fixit.test", "11 5555-0001", UserRole.CLIENT),
            User("c2", "Bruno Díaz", "bruno@fixit.test", "11 5555-0002", UserRole.CLIENT),
            User(
                "p1", "Carlos Gómez", "carlos@fixit.test", "11 5555-0101", UserRole.PROFESSIONAL,
                ProfessionalInfo(
                    categories = setOf(ServiceCategory.ELECTRICITY, ServiceCategory.GAS),
                    base = Place(GeoPoint(-34.8065, -58.2788), "Florencio Varela"),
                    radiusKm = 10,
                    reputation = reputationOf("p1"),
                ),
            ),
            User(
                "p2", "Diana Ruiz", "diana@fixit.test", "11 5555-0102", UserRole.PROFESSIONAL,
                ProfessionalInfo(
                    categories = setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS),
                    base = Place(GeoPoint(-34.7206, -58.2546), "Quilmes"),
                    radiusKm = 8,
                    reputation = reputationOf("p2"),
                ),
            ),
            User(
                "p3", "Esteban Paz", "esteban@fixit.test", "11 5555-0103", UserRole.PROFESSIONAL,
                ProfessionalInfo(
                    categories = setOf(ServiceCategory.CARPENTRY, ServiceCategory.PAINTING, ServiceCategory.OTHER),
                    base = Place(GeoPoint(-34.7600, -58.2120), "Berazategui"),
                    radiusKm = 15,
                    reputation = reputationOf("p3"),
                ),
            ),
        )
    }

    private fun requests(now: Long): List<RepairRequest> = listOf(
        open("r1", "c1", ServiceCategory.ELECTRICITY, "Se cortó la luz en la cocina y los tomacorrientes no funcionan.",
            -34.7950, -58.2700, "Calle 14 1520, Florencio Varela", now - 2 * HOUR, photos = 2),
        open("r2", "c1", ServiceCategory.PLUMBING, "Pérdida de agua debajo de la bacha de la cocina.",
            -34.7300, -58.2600, "Av. Mitre 850, Quilmes", now - 5 * HOUR, photos = 1),
        open("r3", "c2", ServiceCategory.GAS, "Siento olor a gas cerca del calefón y necesito una revisión.",
            -34.7700, -58.2650, "Calle 120 3300, Berazategui", now - 1 * HOUR, photos = 1),
        job("r4", "c2", ServiceCategory.PAINTING, "Pintar el living y un dormitorio, las paredes están descascaradas.",
            -34.7650, -58.2200, "Calle 21 790, Berazategui", professionalId = "p3",
            createdAt = now - 3 * DAY, acceptedAt = now - 2 * DAY, completedAt = null, photos = 3),
        job("r5", "c1", ServiceCategory.ELECTRICITY, "Cambiar el tablero eléctrico y revisar la instalación.",
            -34.8100, -58.2800, "Calle 9 410, Florencio Varela", professionalId = "p1",
            createdAt = now - 20 * DAY, acceptedAt = now - 19 * DAY, completedAt = now - 18 * DAY, photos = 2),
        open("r6", "c2", ServiceCategory.MASONRY, "Hay que levantar una pared de ladrillo en el patio.",
            -34.8000, -58.2750, "Calle 7 2200, Florencio Varela", now - 3 * HOUR, photos = 1),
        job("r7", "c2", ServiceCategory.ELECTRICITY, "La térmica salta cada vez que prendo el aire acondicionado.",
            -34.8040, -58.2790, "Calle 5 1180, Florencio Varela", professionalId = "p1",
            createdAt = now - 12 * DAY, acceptedAt = now - 11 * DAY, completedAt = now - 10 * DAY, photos = 1),
        job("r8", "c1", ServiceCategory.PAINTING, "Pintar la fachada de la casa y las rejas del frente.",
            -34.7580, -58.2150, "Calle 18 640, Berazategui", professionalId = "p3",
            createdAt = now - 8 * DAY, acceptedAt = now - 7 * DAY, completedAt = now - 6 * DAY, photos = 2),
    )

    private fun reviews(requests: List<RepairRequest>): List<Review> {
        fun review(requestId: String, rating: Int, comment: String): Review {
            val request = requests.first { it.id == requestId }
            return Review(
                requestId = requestId,
                professionalId = checkNotNull(request.professionalId),
                clientId = request.clientId,
                rating = rating,
                comment = comment,
                createdAt = checkNotNull(request.completedAt),
            )
        }
        return listOf(
            review("r5", rating = 5, comment = "Muy prolijo"),
            review("r7", rating = 4, comment = "Buen trabajo, llegó puntual"),
            review("r8", rating = 4, comment = "Quedó muy bien la pintura"),
        )
    }

    private fun open(
        id: String,
        clientId: String,
        category: ServiceCategory,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        createdAt: Long,
        photos: Int,
    ) = RepairRequest(
        id = id,
        clientId = clientId,
        category = category,
        description = description,
        photoUrls = photoUrls(photos),
        place = Place(GeoPoint(latitude, longitude), address),
        status = RequestStatus.OPEN,
        createdAt = createdAt,
    )

    /** An assigned request: ACCEPTED while [completedAt] is null, COMPLETED otherwise. */
    private fun job(
        id: String,
        clientId: String,
        category: ServiceCategory,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        professionalId: String,
        createdAt: Long,
        acceptedAt: Long,
        completedAt: Long?,
        photos: Int,
    ) = open(id, clientId, category, description, latitude, longitude, address, createdAt, photos).copy(
        status = if (completedAt == null) RequestStatus.ACCEPTED else RequestStatus.COMPLETED,
        professionalId = professionalId,
        acceptedAt = acceptedAt,
        completedAt = completedAt,
    )

    private fun photoUrls(count: Int) = (1..count).map { "$PHOTO_BASE$it" }
}
