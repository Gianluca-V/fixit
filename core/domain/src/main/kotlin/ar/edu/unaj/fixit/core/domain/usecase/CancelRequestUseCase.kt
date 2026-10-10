package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import javax.inject.Inject

class CancelRequestUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val requestRepository: RepairRequestRepository,
) {
    /** Only the owner client may cancel; the OPEN/ACCEPTED -> CANCELLED transition is atomic in the repository (D9). */
    suspend operator fun invoke(requestId: String): AppResult<RepairRequest> {
        (readOwnedRequest(authRepository, requestRepository, requestId) as? AppResult.Error)?.let { return it }
        return requestRepository.cancel(requestId)
    }
}
