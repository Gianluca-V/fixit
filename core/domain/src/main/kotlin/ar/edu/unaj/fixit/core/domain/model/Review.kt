package ar.edu.unaj.fixit.core.domain.model

data class Review(
    val requestId: String,
    val professionalId: String,
    val clientId: String,
    val rating: Int,
    val comment: String?,
    val createdAt: Long,
)
