package ar.edu.unaj.fixit.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.ui.R

/** The only place that turns a [DomainError] into text for the user. */
@Composable
fun DomainError.asMessage(): String = when (this) {
    is DomainError.Validation -> error.asMessage()
    is DomainError.InvalidTransition -> stringResource(R.string.error_invalid_transition)
    DomainError.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    DomainError.EmailAlreadyInUse -> stringResource(R.string.error_email_already_in_use)
    DomainError.Unauthorized -> stringResource(R.string.error_unauthorized)
    DomainError.NotFound -> stringResource(R.string.error_not_found)
    DomainError.AlreadyTaken -> stringResource(R.string.error_already_taken)
    DomainError.LocationUnavailable -> stringResource(R.string.error_location_unavailable)
    DomainError.Network -> stringResource(R.string.error_network)
    DomainError.Unknown -> stringResource(R.string.error_unknown) // also: account without profile
}

/** Text shown next to the form field that [ValidationError.field] points to. */
@Composable
fun ValidationError.asMessage(): String = stringResource(
    when (this) {
        ValidationError.INVALID_EMAIL -> R.string.validation_invalid_email
        ValidationError.EMPTY_PASSWORD -> R.string.validation_empty_password
        ValidationError.WEAK_PASSWORD -> R.string.validation_weak_password
        ValidationError.INVALID_NAME -> R.string.validation_invalid_name
        ValidationError.INVALID_PHONE -> R.string.validation_invalid_phone
        ValidationError.ROLE_REQUIRED -> R.string.validation_role_required
        ValidationError.CATEGORIES_REQUIRED -> R.string.validation_categories_required
        ValidationError.BASE_LOCATION_REQUIRED -> R.string.validation_base_location_required
        ValidationError.INVALID_RADIUS -> R.string.validation_invalid_radius
        ValidationError.INVALID_COORDINATES -> R.string.validation_invalid_coordinates
        ValidationError.CATEGORY_REQUIRED -> R.string.validation_category_required
        ValidationError.EMPTY_DESCRIPTION -> R.string.validation_empty_description
        ValidationError.DESCRIPTION_TOO_SHORT -> R.string.validation_description_too_short
        ValidationError.DESCRIPTION_TOO_LONG -> R.string.validation_description_too_long
        ValidationError.PHOTOS_REQUIRED -> R.string.validation_photos_required
        ValidationError.TOO_MANY_PHOTOS -> R.string.validation_too_many_photos
        ValidationError.LOCATION_REQUIRED -> R.string.validation_location_required
        ValidationError.INVALID_RATING -> R.string.validation_invalid_rating
        ValidationError.COMMENT_TOO_LONG -> R.string.validation_comment_too_long
    },
)
