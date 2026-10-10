package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import javax.inject.Inject

class CancelRequestUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val requestRepository: RepairRequestRepository,
) {
    /** Only the owner client may cancel; the OPEN/ACCEPTED -> CANCELLED transition is atomic in the repository (D9). */
    suspend operator fun invoke(requestId: String): AppResult<RepairRequest> {
        val userId = authRepository.currentUserId() ?: return AppResult.Error(DomainError.Unauthorized)
        val request = when (val found = requestRepository.get(requestId)) {
            is AppResult.Error -> return found
            is AppResult.Success -> found.data
        }
        if (request.clientId != userId) return AppResult.Error(DomainError.Unauthorized)
        return requestRepository.cancel(requestId)
    }
}
