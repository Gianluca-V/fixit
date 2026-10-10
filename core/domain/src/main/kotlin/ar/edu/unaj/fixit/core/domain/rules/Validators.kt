package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Limits
import ar.edu.unaj.fixit.core.domain.model.NewRequest
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.ValidationError

/**
 * Pure validations. Each public function returns the FIRST error in the documented order
 * (design section 5.3), or null when the input is valid.
 */
object Validators {
    // Applied after normalizeEmail(): trim().lowercase() (Kotlin lowercase() is locale-independent).
    private val EMAIL = Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$")

    // Applied after trim() and removing ' ', '-', '(' and ')' (the same characters PhoneFormatter ignores).
    private val PHONE = Regex("^\\+?[0-9]{${Limits.PHONE_DIGITS_MIN},${Limits.PHONE_DIGITS_MAX}}$")

    private val LATITUDE_RANGE = -90.0..90.0
    private val LONGITUDE_RANGE = -180.0..180.0

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun signIn(email: String, password: String): ValidationError? =
        emailError(email) ?: if (password.isEmpty()) ValidationError.EMPTY_PASSWORD else null

    fun signUp(form: SignUpForm): ValidationError? {
        emailError(form.email)?.let { return it }
        if (form.password.length < Limits.PASSWORD_MIN) return ValidationError.WEAK_PASSWORD
        nameError(form.profile.name)?.let { return it }
        phoneError(form.profile.phone)?.let { return it }
        val role = form.role ?: return ValidationError.ROLE_REQUIRED
        return professionalError(role, form.profile)
    }

    fun profile(role: UserRole, form: ProfileForm): ValidationError? =
        nameError(form.name) ?: phoneError(form.phone) ?: professionalError(role, form)

    fun request(input: NewRequest): ValidationError? {
        if (input.category == null) return ValidationError.CATEGORY_REQUIRED
        descriptionError(input.description)?.let { return it }
        photosError(input.photoUris.size)?.let { return it }
        val place = input.place
        if (place == null || place.address.isBlank()) return ValidationError.LOCATION_REQUIRED
        return coordinates(place.point)
    }

    fun review(rating: Int, comment: String?): ValidationError? = when {
        rating !in Limits.RATING_MIN..Limits.RATING_MAX -> ValidationError.INVALID_RATING
        comment != null && comment.trim().length > Limits.COMMENT_MAX -> ValidationError.COMMENT_TOO_LONG
        else -> null
    }

    fun coordinates(point: GeoPoint): ValidationError? =
        if (point.latitude in LATITUDE_RANGE && point.longitude in LONGITUDE_RANGE) null // NaN is not "in" the range
        else ValidationError.INVALID_COORDINATES

    private fun emailError(raw: String): ValidationError? =
        if (EMAIL.matches(normalizeEmail(raw))) null else ValidationError.INVALID_EMAIL

    private fun nameError(raw: String): ValidationError? =
        if (raw.trim().length in Limits.NAME_MIN..Limits.NAME_MAX) null else ValidationError.INVALID_NAME

    private fun phoneError(raw: String): ValidationError? =
        if (PHONE.matches(stripPhoneSeparators(raw))) null else ValidationError.INVALID_PHONE

    private fun stripPhoneSeparators(raw: String): String =
        raw.trim().filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }

    /** Categories, base point, its coordinates and radius; only applies to professionals. */
    private fun professionalError(role: UserRole, form: ProfileForm): ValidationError? {
        if (role != UserRole.PROFESSIONAL) return null
        return categoriesError(form.categories) ?: baseError(form.base) ?: radiusError(form.radiusKm)
    }

    private fun categoriesError(categories: Set<ServiceCategory>): ValidationError? =
        if (categories.isEmpty()) ValidationError.CATEGORIES_REQUIRED else null

    private fun baseError(base: Place?): ValidationError? =
        if (base == null) ValidationError.BASE_LOCATION_REQUIRED else coordinates(base.point)

    private fun radiusError(radiusKm: Int?): ValidationError? =
        if (radiusKm != null && radiusKm in Limits.RADIUS_KM_MIN..Limits.RADIUS_KM_MAX) null
        else ValidationError.INVALID_RADIUS

    private fun descriptionError(raw: String): ValidationError? {
        val length = raw.trim().length
        return when {
            length == 0 -> ValidationError.EMPTY_DESCRIPTION
            length < Limits.DESCRIPTION_MIN -> ValidationError.DESCRIPTION_TOO_SHORT
            length > Limits.DESCRIPTION_MAX -> ValidationError.DESCRIPTION_TOO_LONG
            else -> null
        }
    }

    private fun photosError(count: Int): ValidationError? = when {
        count < Limits.PHOTOS_MIN -> ValidationError.PHOTOS_REQUIRED
        count > Limits.PHOTOS_MAX -> ValidationError.TOO_MANY_PHOTOS
        else -> null
    }
}
