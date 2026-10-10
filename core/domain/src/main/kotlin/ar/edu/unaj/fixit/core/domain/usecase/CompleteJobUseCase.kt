package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.Review
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.rules.Validators
import javax.inject.Inject

class CompleteJobUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val requestRepository: RepairRequestRepository,
) {
    /**
     * Only the owner client completes. The comment is persisted trimmed (blank becomes null) and its
     * limit applies to the trimmed text; request, review and reputation then change atomically in the
     * repository (D9).
     */
    suspend operator fun invoke(requestId: String, rating: Int, comment: String?): AppResult<Review> {
        (readOwnedRequest(authRepository, requestRepository, requestId) as? AppResult.Error)?.let { return it }
        val trimmedComment = comment?.trim()?.takeIf { it.isNotEmpty() }
        Validators.review(rating, trimmedComment)?.let { return AppResult.Error(DomainError.Validation(it)) }
        return requestRepository.completeWithReview(requestId, rating, trimmedComment)
    }
}
