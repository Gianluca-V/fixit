package ar.edu.unaj.fixit.core.domain.model

data class RepairRequest(
    val id: String,
    val clientId: String,
    val category: ServiceCategory,
    val description: String,
    val photoUrls: List<String>,
    val place: Place,
    val status: RequestStatus,
    val professionalId: String? = null, // kept after ACCEPTED -> CANCELLED (shows in "My jobs")
    val createdAt: Long,
    val acceptedAt: Long? = null,
    val cancelledAt: Long? = null,
    val completedAt: Long? = null, // all dates come from the injected Clock (Fake) / serverTimestamp (Firebase)
)
