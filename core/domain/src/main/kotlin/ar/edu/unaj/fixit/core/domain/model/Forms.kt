package ar.edu.unaj.fixit.core.domain.model

// Inputs (professional fields are ignored for CLIENT)
data class ProfileForm(
    val name: String,
    val phone: String,
    val categories: Set<ServiceCategory> = emptySet(),
    val base: Place? = null,
    val radiusKm: Int? = null,
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
