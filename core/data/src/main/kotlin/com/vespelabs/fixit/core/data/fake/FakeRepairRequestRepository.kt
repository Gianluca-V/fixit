package com.vespelabs.fixit.core.data.fake

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.NewRequest
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.domain.rules.RequestStateMachine
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

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
        db.observe { it.findRequest(requestId) }

    override fun observeByClient(clientId: String): Flow<AppResult<List<RepairRequest>>> =
        db.observe { state ->
            AppResult.Success(state.requests.filter { it.clientId == clientId }.sortedByDescending { it.createdAt })
        }

    /** Everything assigned to the professional, including jobs cancelled after being accepted. */
    override fun observeByProfessional(professionalId: String): Flow<AppResult<List<RepairRequest>>> =
        db.observe { state ->
            AppResult.Success(
                state.requests.filter { it.professionalId == professionalId }.sortedByDescending { it.acceptedAt ?: 0L },
            )
        }

    /** Every OPEN request is a candidate: the exact category and distance filter belongs to the domain. */
    override fun observeOpenNear(center: GeoPoint, radiusKm: Int): Flow<AppResult<List<RepairRequest>>> =
        db.observe { state -> AppResult.Success(state.requests.filter { it.status == RequestStatus.OPEN }) }

    override fun observeReview(requestId: String): Flow<AppResult<Review?>> =
        db.observe { state -> AppResult.Success(state.reviews.firstOrNull { it.requestId == requestId }) }

    /** Idempotent for the same professional: checked BEFORE the state machine. */
    override suspend fun accept(requestId: String, professionalId: String): AppResult<RepairRequest> =
        db.write { old, now ->
            old.withRequest(requestId) { request ->
                if (request.status == RequestStatus.ACCEPTED && request.professionalId == professionalId) {
                    return@withRequest WriteDecision.Commit(old, request)
                }
                transition(request, RequestAction.ACCEPT) { next ->
                    val accepted = request.copy(status = next, professionalId = professionalId, acceptedAt = now)
                    WriteDecision.Commit(old.replacing(accepted), accepted)
                }
            }
        }

    /** The professional assigned by a previous accept is kept, so the job still shows in "My jobs". */
    override suspend fun cancel(requestId: String): AppResult<RepairRequest> =
        db.write { old, now ->
            old.withRequest(requestId) { request ->
                transition(request, RequestAction.CANCEL) { next ->
                    val cancelled = request.copy(status = next, cancelledAt = now)
                    WriteDecision.Commit(old.replacing(cancelled), cancelled)
                }
            }
        }

    /** One update of the whole state: request, review and the professional's reputation change together. */
    override suspend fun completeWithReview(requestId: String, rating: Int, comment: String?): AppResult<Review> =
        db.write { old, now ->
            old.withRequest(requestId) { request ->
                transition(request, RequestAction.COMPLETE) { next ->
                    val professionalId = request.professionalId
                        ?: return@transition WriteDecision.Reject(DomainError.Unknown)
                    val info = old.users.firstOrNull { it.id == professionalId }?.professional
                        ?: return@transition WriteDecision.Reject(DomainError.NotFound)

                    val review = Review(requestId, professionalId, request.clientId, rating, comment, createdAt = now)
                    val reputation = Reputation(info.reputation.ratingSum + rating, info.reputation.reviewCount + 1)
                    WriteDecision.Commit(
                        old.copy(
                            users = old.users.map {
                                if (it.id == professionalId) it.copy(professional = info.copy(reputation = reputation)) else it
                            },
                            requests = old.requests.map {
                                if (it.id == requestId) request.copy(status = next, completedAt = now) else it
                            },
                            reviews = old.reviews + review,
                        ),
                        review,
                    )
                }
            }
        }

    private fun validationError(error: ValidationError) = AppResult.Error(DomainError.Validation(error))
}

/** Runs [block] with the request, or rejects with NotFound when it does not exist. */
private inline fun <T> DbState.withRequest(
    requestId: String,
    block: (RepairRequest) -> WriteDecision<T>,
): WriteDecision<T> = requests.firstOrNull { it.id == requestId }?.let(block) ?: WriteDecision.Reject(DomainError.NotFound)

/** Applies [action] through the state machine; an invalid transition becomes a rejection. */
private inline fun <T> transition(
    request: RepairRequest,
    action: RequestAction,
    onValid: (RequestStatus) -> WriteDecision<T>,
): WriteDecision<T> = when (val next = RequestStateMachine.transition(request.status, action)) {
    is AppResult.Error -> WriteDecision.Reject(next.error)
    is AppResult.Success -> onValid(next.data)
}

private fun DbState.replacing(request: RepairRequest): DbState =
    copy(requests = requests.map { if (it.id == request.id) request else it })

internal fun DbState.findRequest(requestId: String): AppResult<RepairRequest> =
    requests.firstOrNull { it.id == requestId }?.let { AppResult.Success(it) } ?: AppResult.Error(DomainError.NotFound)
