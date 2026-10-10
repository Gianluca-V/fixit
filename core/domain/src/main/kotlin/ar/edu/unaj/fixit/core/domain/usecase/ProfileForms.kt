package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.UserRole

/**
 * The form as it is persisted: name and phone trimmed (the phone keeps its format) and, for a
 * client, without professional fields.
 */
internal fun ProfileForm.forPersistence(role: UserRole): ProfileForm {
    val trimmedName = name.trim()
    val trimmedPhone = phone.trim()
    return if (role == UserRole.PROFESSIONAL) {
        copy(name = trimmedName, phone = trimmedPhone)
    } else {
        ProfileForm(trimmedName, trimmedPhone)
    }
}
