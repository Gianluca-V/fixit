package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.RequestDetail
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.Review
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.rules.ContactRules
import ar.edu.unaj.fixit.core.domain.rules.Haversine
import ar.edu.unaj.fixit.core.domain.rules.Matching
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class ObserveRequestDetailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val requestRepository: RepairRequestRepository,
) {
    /**
     * Live detail = request + counterpart + review. A client only sees their own requests
     * (Unauthorized otherwise); a professional may open any request and ContactRules decides the
     * contact. ContactRules.Unauthorized is NOT a flow error: the detail is emitted with contact = null
     * (documented D6 exception). Combining the three flows may emit a transient state (e.g. COMPLETED
     * with review = null); consumers wait for the final state.
     */
    operator fun invoke(requestId: String): Flow<AppResult<RequestDetail>> = flow {
        val viewer = when (val current = readCurrentUser(authRepository, userRepository)) {
            is AppResult.Error -> {
                emit(current)
                return@flow
            }
            is AppResult.Success -> current.data
        }
        emitAll(detailFor(viewer, requestId))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun detailFor(viewer: User, requestId: String): Flow<AppResult<RequestDetail>> =
        requestRepository.observe(requestId).flatMapLatest { observed ->
            when (observed) {
                is AppResult.Error -> flowOf(observed)
                is AppResult.Success ->
                    if (viewer.role == UserRole.CLIENT && observed.data.clientId != viewer.id) {
                        flowOf(AppResult.Error(DomainError.Unauthorized))
                    } else {
                        detailOf(viewer, observed.data)
                    }
            }
        }

    private fun detailOf(viewer: User, request: RepairRequest): Flow<AppResult<RequestDetail>> =
        combine(counterpartOf(viewer, request), requestRepository.observeReview(request.id)) { counterpart, review ->
            when (counterpart) {
                is AppResult.Error -> counterpart
                is AppResult.Success -> when (review) {
                    is AppResult.Error -> review
                    is AppResult.Success -> AppResult.Success(buildDetail(viewer, request, counterpart.data, review.data))
                }
            }
        }

    /** Only a participant of an ACCEPTED/COMPLETED request has a counterpart to read; null otherwise. */
    private fun counterpartOf(viewer: User, request: RepairRequest): Flow<AppResult<User?>> {
        val contactable = request.status == RequestStatus.ACCEPTED || request.status == RequestStatus.COMPLETED
        val counterpartId = when (viewer.id) {
            request.clientId -> request.professionalId
            request.professionalId -> request.clientId
            else -> null
        }
        if (!contactable || counterpartId == null) return flowOf(AppResult.Success(null))
        return userRepository.observe(counterpartId)
    }

    private fun buildDetail(viewer: User, request: RepairRequest, counterpart: User?, review: Review?): RequestDetail {
        val contact = counterpart
            ?.let { ContactRules.contactFor(request, viewer.id, it) }
            ?.let { (it as? AppResult.Success)?.data }
        val distanceKm = viewer.professional?.let { Matching.roundKm(Haversine.km(it.base.point, request.place.point)) }
        return RequestDetail(
            request = request,
            contact = contact,
            review = review,
            distanceKm = distanceKm,
            viewerIsAssignee = viewer.role == UserRole.PROFESSIONAL && request.professionalId == viewer.id,
        )
    }
}
