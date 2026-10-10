package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.NewRequest
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.model.RequestAction
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.Review
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.rules.RequestStateMachine
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FakeRepairRequestRepository @Inject constructor(
    private val db: FakeDatabase,
) : RepairRequestRepository {
    override suspend fun create(clientId: String, input: NewRequest): AppResult<RepairRequest> {
        val category = input.category ?: return validationError(ValidationError.CATEGORY_REQUIRED)
        val place = input.place ?: return validationError(ValidationError.LOCATION_REQUIRED)
        val id = db.newId("r")
        return db.write { old, now ->
            if (old.users.none { it.id == clientId }) return@write WriteDecision.Reject(DomainError.NotFound)
            val request = RepairRequest(
                id = id,
                clientId = clientId,
                category = category,
                description = input.description,
                photoUrls = input.photoUris,
                place = place,
                status = RequestStatus.OPEN,
                createdAt = now,
            )
            WriteDecision.Commit(old.copy(requests = old.requests + request), request)
        }
    }

    override suspend fun get(requestId: String): AppResult<RepairRequest> = db.state.value.findRequest(requestId)

    override fun observe(requestId: String): Flow<AppResult<RepairRequest>> =
        db.state.map { it.findRequest(requestId) }.distinctUntilChanged()

    override fun observeByClient(clientId: String): Flow<AppResult<List<RepairRequest>>> =
        db.state
            .map { state ->
                AppResult.Success(state.requests.filter { it.clientId == clientId }.sortedByDescending { it.createdAt })
            }
            .distinctUntilChanged()

    /** Everything assigned to the professional, including jobs cancelled after being accepted. */
    override fun observeByProfessional(professionalId: String): Flow<AppResult<List<RepairRequest>>> =
        db.state
            .map { state ->
                AppResult.Success(
                    state.requests.filter { it.professionalId == professionalId }.sortedByDescending { it.acceptedAt ?: 0L },
                )
            }
            .distinctUntilChanged()

    /** Every OPEN request is a candidate: the exact category and distance filter belongs to the domain. */
    override fun observeOpenNear(center: GeoPoint, radiusKm: Int): Flow<AppResult<List<RepairRequest>>> =
        db.state
            .map { state -> AppResult.Success(state.requests.filter { it.status == RequestStatus.OPEN }) }
            .distinctUntilChanged()

    override fun observeReview(requestId: String): Flow<AppResult<Review?>> =
        db.state
            .map { state -> AppResult.Success(state.reviews.firstOrNull { it.requestId == requestId }) }
            .distinctUntilChanged()

    /** Idempotent for the same professional: checked BEFORE the state machine. */
    override suspend fun accept(requestId: String, professionalId: String): AppResult<RepairRequest> =
        db.write { old, now ->
            val request = old.requests.firstOrNull { it.id == requestId }
                ?: return@write WriteDecision.Reject(DomainError.NotFound)
            if (request.status == RequestStatus.ACCEPTED && request.professionalId == professionalId) {
                return@write WriteDecision.Commit(old, request)
            }
            when (val next = RequestStateMachine.transition(request.status, RequestAction.ACCEPT)) {
                is AppResult.Error -> WriteDecision.Reject(next.error)
                is AppResult.Success -> {
                    val accepted = request.copy(status = next.data, professionalId = professionalId, acceptedAt = now)
                    WriteDecision.Commit(old.replacing(accepted), accepted)
                }
            }
        }

    /** The professional assigned by a previous accept is kept, so the job still shows in "My jobs". */
    override suspend fun cancel(requestId: String): AppResult<RepairRequest> =
        db.write { old, now ->
            val request = old.requests.firstOrNull { it.id == requestId }
                ?: return@write WriteDecision.Reject(DomainError.NotFound)
            when (val next = RequestStateMachine.transition(request.status, RequestAction.CANCEL)) {
                is AppResult.Error -> WriteDecision.Reject(next.error)
                is AppResult.Success -> {
                    val cancelled = request.copy(status = next.data, cancelledAt = now)
                    WriteDecision.Commit(old.replacing(cancelled), cancelled)
                }
            }
        }

    /** One update of the whole state: request, review and the professional's reputation change together. */
    override suspend fun completeWithReview(requestId: String, rating: Int, comment: String?): AppResult<Review> =
        db.write { old, now ->
            val request = old.requests.firstOrNull { it.id == requestId }
                ?: return@write WriteDecision.Reject(DomainError.NotFound)
            val completedStatus = when (val next = RequestStateMachine.transition(request.status, RequestAction.COMPLETE)) {
                is AppResult.Error -> return@write WriteDecision.Reject(next.error)
                is AppResult.Success -> next.data
            }
            val professionalId = request.professionalId
                ?: return@write WriteDecision.Reject(DomainError.Unknown)
            val info = old.users.firstOrNull { it.id == professionalId }?.professional
                ?: return@write WriteDecision.Reject(DomainError.NotFound)

            val review = Review(requestId, professionalId, request.clientId, rating, comment, createdAt = now)
            val reputation = Reputation(info.reputation.ratingSum + rating, info.reputation.reviewCount + 1)
            WriteDecision.Commit(
                old.copy(
                    users = old.users.map {
                        if (it.id == professionalId) it.copy(professional = info.copy(reputation = reputation)) else it
                    },
                    requests = old.requests.map {
                        if (it.id == requestId) request.copy(status = completedStatus, completedAt = now) else it
                    },
                    reviews = old.reviews + review,
                ),
                review,
            )
        }

    private fun validationError(error: ValidationError) = AppResult.Error(DomainError.Validation(error))
}

private fun DbState.replacing(request: RepairRequest): DbState =
    copy(requests = requests.map { if (it.id == request.id) request else it })

internal fun DbState.findRequest(requestId: String): AppResult<RepairRequest> =
    requests.firstOrNull { it.id == requestId }?.let { AppResult.Success(it) } ?: AppResult.Error(DomainError.NotFound)
