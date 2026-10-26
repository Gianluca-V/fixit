package com.vespelabs.fixit.core.domain.model

/** The average is computed as a number by rules/Reputation (BigDecimal). */
data class Reputation(val ratingSum: Int = 0, val reviewCount: Int = 0)

data class ProfessionalInfo(
    val categories: Set<ServiceCategory>,
    val base: Place,
    val radiusKm: Int,
    val reputation: Reputation = Reputation(),
)

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val professional: ProfessionalInfo? = null, // non-null only when role == PROFESSIONAL
    val area: Place? = null, // optional "where the client is from"; always null for a professional
)
