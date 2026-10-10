package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.result.ValidationError

object Validators {
    // Applied after normalizeEmail(): trim().lowercase() (Kotlin lowercase() is locale-independent).
    private val EMAIL = Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$")

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun signIn(email: String, password: String): ValidationError? = when {
        !EMAIL.matches(normalizeEmail(email)) -> ValidationError.INVALID_EMAIL
        password.isEmpty() -> ValidationError.EMPTY_PASSWORD
        else -> null
    }

    fun coordinates(point: GeoPoint): ValidationError? =
        if (point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0) null // NaN is not "in" the range
        else ValidationError.INVALID_COORDINATES
}
