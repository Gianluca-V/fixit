package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import javax.inject.Inject

class AcceptRequestUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val requestRepository: RepairRequestRepository,
) {
    /**
     * Checks WHO may accept (a professional offering the request's category) with fresh reads, then
     * delegates the atomic, idempotent transition to the repository (D9). The distance is not
     * checked: the radius only filters the feed.
     */
    suspend operator fun invoke(requestId: String): AppResult<RepairRequest> {
        val professional = when (val current = readCurrentUser(authRepository, userRepository)) {
            is AppResult.Error -> return current
            is AppResult.Success -> current.data
        }
        val offeredCategories = professional.professional?.categories
            ?: return AppResult.Error(DomainError.Unauthorized)
        val request = when (val found = requestRepository.get(requestId)) {
            is AppResult.Error -> return found
            is AppResult.Success -> found.data
        }
        if (request.category !in offeredCategories) return AppResult.Error(DomainError.Unauthorized)
        return requestRepository.accept(requestId, professional.id)
    }
}
