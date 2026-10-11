package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.result.AppResult
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
