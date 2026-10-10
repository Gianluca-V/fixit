package ar.edu.unaj.fixit.core.domain.result

enum class FormField {
    EMAIL, PASSWORD, NAME, PHONE, ROLE, CATEGORIES, BASE, RADIUS, CATEGORY,
    DESCRIPTION, PHOTOS, LOCATION, RATING, COMMENT,
}

/** Same names as the specs (Validation.EmptyDescription -> EMPTY_DESCRIPTION); `field` lets forms mark the input. */
enum class ValidationError(val field: FormField) {
    INVALID_EMAIL(FormField.EMAIL),
    EMPTY_PASSWORD(FormField.PASSWORD),
    WEAK_PASSWORD(FormField.PASSWORD),
    INVALID_NAME(FormField.NAME),
    INVALID_PHONE(FormField.PHONE),
    ROLE_REQUIRED(FormField.ROLE),
    CATEGORIES_REQUIRED(FormField.CATEGORIES),
    BASE_LOCATION_REQUIRED(FormField.BASE),
    INVALID_RADIUS(FormField.RADIUS),
    INVALID_COORDINATES(FormField.LOCATION),
    CATEGORY_REQUIRED(FormField.CATEGORY),
    EMPTY_DESCRIPTION(FormField.DESCRIPTION),
    DESCRIPTION_TOO_SHORT(FormField.DESCRIPTION),
    DESCRIPTION_TOO_LONG(FormField.DESCRIPTION),
    PHOTOS_REQUIRED(FormField.PHOTOS),
    TOO_MANY_PHOTOS(FormField.PHOTOS),
    LOCATION_REQUIRED(FormField.LOCATION),
    INVALID_RATING(FormField.RATING),
    COMMENT_TOO_LONG(FormField.COMMENT),
}
