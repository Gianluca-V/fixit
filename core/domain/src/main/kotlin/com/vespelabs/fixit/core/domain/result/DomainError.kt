package com.vespelabs.fixit.core.domain.result

import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus

sealed interface DomainError {
    data class Validation(val error: ValidationError) : DomainError

    data class InvalidTransition(val from: RequestStatus, val action: RequestAction) : DomainError

    data object InvalidCredentials : DomainError

    data object EmailAlreadyInUse : DomainError

    data object Unauthorized : DomainError // wrong role, not the owner, category not offered

    data object NotFound : DomainError

    data object AlreadyTaken : DomainError // another professional accepted first

    data object LocationUnavailable : DomainError

    data object Network : DomainError

    data object ProfileMissing : DomainError // signed in, but users/{uid} does not exist
    data object Unknown : DomainError
}
