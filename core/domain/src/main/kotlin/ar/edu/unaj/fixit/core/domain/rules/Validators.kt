package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Limits
import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.ValidationError

object Validators {
    // Applied after normalizeEmail(): trim().lowercase() (Kotlin lowercase() is locale-independent).
    private val EMAIL = Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$")

    // Applied after trim() and removing ' ', '-', '(' and ')' (the same characters PhoneFormatter ignores).
    private val PHONE = Regex("^\\+?[0-9]{${Limits.PHONE_DIGITS_MIN},${Limits.PHONE_DIGITS_MAX}}$")

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun signIn(email: String, password: String): ValidationError? = when {
        !EMAIL.matches(normalizeEmail(email)) -> ValidationError.INVALID_EMAIL
        password.isEmpty() -> ValidationError.EMPTY_PASSWORD
        else -> null
    }

    fun signUp(form: SignUpForm): ValidationError? {
        val profile = form.profile
        if (!EMAIL.matches(normalizeEmail(form.email))) return ValidationError.INVALID_EMAIL
        if (form.password.length < Limits.PASSWORD_MIN) return ValidationError.WEAK_PASSWORD
        if (profile.name.trim().length !in Limits.NAME_MIN..Limits.NAME_MAX) return ValidationError.INVALID_NAME
        if (!PHONE.matches(stripPhoneSeparators(profile.phone))) return ValidationError.INVALID_PHONE
        val role = form.role ?: return ValidationError.ROLE_REQUIRED
        if (role == UserRole.PROFESSIONAL) {
            if (profile.categories.isEmpty()) return ValidationError.CATEGORIES_REQUIRED
            val base = profile.base ?: return ValidationError.BASE_LOCATION_REQUIRED
            coordinates(base.point)?.let { return it }
            if (profile.radiusKm == null || profile.radiusKm !in Limits.RADIUS_KM_MIN..Limits.RADIUS_KM_MAX) {
                return ValidationError.INVALID_RADIUS
            }
        }
        return null
    }

    fun profile(role: UserRole, form: ProfileForm): ValidationError? {
        if (form.name.trim().length !in Limits.NAME_MIN..Limits.NAME_MAX) return ValidationError.INVALID_NAME
        if (!PHONE.matches(stripPhoneSeparators(form.phone))) return ValidationError.INVALID_PHONE
        if (role == UserRole.PROFESSIONAL) {
            if (form.categories.isEmpty()) return ValidationError.CATEGORIES_REQUIRED
            val base = form.base ?: return ValidationError.BASE_LOCATION_REQUIRED
            coordinates(base.point)?.let { return it }
            if (form.radiusKm == null || form.radiusKm !in Limits.RADIUS_KM_MIN..Limits.RADIUS_KM_MAX) {
                return ValidationError.INVALID_RADIUS
            }
        }
        return null
    }

    fun coordinates(point: GeoPoint): ValidationError? =
        if (point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0) null // NaN is not "in" the range
        else ValidationError.INVALID_COORDINATES

    private fun stripPhoneSeparators(raw: String): String =
        raw.trim().filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }
}
