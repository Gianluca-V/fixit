package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.ProfileForm
import com.vespelabs.fixit.core.domain.model.UserRole

/**
 * The form as it is persisted: name and phone trimmed (the phone keeps its format); for a client,
 * without professional fields and with the area address trimmed; for a professional, without area.
 */
internal fun ProfileForm.forPersistence(role: UserRole): ProfileForm {
    val trimmedName = name.trim()
    val trimmedPhone = phone.trim()
    return if (role == UserRole.PROFESSIONAL) {
        copy(name = trimmedName, phone = trimmedPhone, area = null)
    } else {
        ProfileForm(trimmedName, trimmedPhone, area = area?.let { it.copy(address = it.address.trim()) })
    }
}
