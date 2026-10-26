package com.vespelabs.fixit.core.domain.model

// Inputs (professional fields are ignored for CLIENT; area is ignored for PROFESSIONAL and on sign-up)
data class ProfileForm(
    val name: String,
    val phone: String,
    val categories: Set<ServiceCategory> = emptySet(),
    val base: Place? = null,
    val radiusKm: Int? = null,
    val area: Place? = null, // the client sends it in full on every edit: null means "no area"
)

data class SignUpForm(
    val email: String,
    val password: String,
    val role: UserRole?,
    val profile: ProfileForm,
)

data class NewRequest(
    val category: ServiceCategory?,
    val description: String,
    val photoUris: List<String>,
    val place: Place?,
)
