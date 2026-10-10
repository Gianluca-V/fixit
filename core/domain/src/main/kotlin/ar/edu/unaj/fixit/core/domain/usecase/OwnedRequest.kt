package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError

/**
 * Fresh read of a request that the signed-in user owns as its client. No session or a request of
 * someone else is Unauthorized; a missing request is NotFound. Runs before any state transition so
 * that Unauthorized always precedes InvalidTransition (D9).
 */
internal suspend fun readOwnedRequest(
    auth: AuthRepository,
    requests: RepairRequestRepository,
    requestId: String,
): AppResult<RepairRequest> {
    val userId = auth.currentUserId() ?: return AppResult.Error(DomainError.Unauthorized)
    val found = requests.get(requestId)
    if (found is AppResult.Success && found.data.clientId != userId) return AppResult.Error(DomainError.Unauthorized)
    return found
}
