package com.vespelabs.fixit.core.domain.testing

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.NewRequest
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.rules.RequestStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Hand-written RepairRequestRepository double over configurable, observable lists.
 *
 * Writes apply the real [RequestStateMachine] (and the idempotent accept) so use-case tests can
 * reach InvalidTransition / AlreadyTaken naturally. Error hooks are one-shot: each is consumed by the
 * NEXT call of its operation only (`observeError` by the next observe, whichever it is), so a test can
 * fail `accept` without failing `get`.
 */
class TestRepairRequestRepository(
    initialRequests: List<RepairRequest> = emptyList(),
    initialReviews: List<Review> = emptyList(),
) : RepairRequestRepository {
    private val requests = MutableStateFlow(initialRequests)
    private val reviews = MutableStateFlow(initialReviews)
    private var nextId = 1

    /** Value written as acceptedAt / cancelledAt / completedAt / createdAt by this double. */
    var now: Long = 1_000L

    var createError: DomainError? = null
    var acceptError: DomainError? = null
    var cancelError: DomainError? = null
    var completeError: DomainError? = null
    var observeError: DomainError? = null

    val createCalls = mutableListOf<Pair<String, NewRequest>>()
    val acceptCalls = mutableListOf<Pair<String, String>>()
    val cancelCalls = mutableListOf<String>()
    val completeCalls = mutableListOf<Triple<String, Int, String?>>()

    /** How many times an observeOpenNear flow has been started (a restart shows up here). */
    var openNearStarts = 0
        private set

    fun setRequests(list: List<RepairRequest>) {
        requests.value = list
    }

    /** Adds or replaces a request (by id); observers re-emit. */
    fun upsert(request: RepairRequest) {
        requests.value = requests.value.filterNot { it.id == request.id } + request
    }

    fun setReviews(list: List<Review>) {
        reviews.value = list
    }

    fun request(requestId: String): RepairRequest? = requests.value.firstOrNull { it.id == requestId }

    fun reviewsSnapshot(): List<Review> = reviews.value

    fun requestsSnapshot(): List<RepairRequest> = requests.value

    override suspend fun create(clientId: String, input: NewRequest): AppResult<RepairRequest> {
        createCalls += clientId to input
        ::createError.take()?.let { return AppResult.Error(it) }
        val created = RepairRequest(
            id = "created-${nextId++}",
            clientId = clientId,
            category = checkNotNull(input.category) { "TestRepairRequestRepository.create needs a validated input" },
            description = input.description,
            photoUrls = input.photoUris,
            place = checkNotNull(input.place),
            status = RequestStatus.OPEN,
            createdAt = now,
        )
        upsert(created)
        return AppResult.Success(created)
    }

    override suspend fun get(requestId: String): AppResult<RepairRequest> = find(requestId)

    override fun observe(requestId: String): Flow<AppResult<RepairRequest>> =
        observed { requests.map { find(requestId) } }

    override fun observeByClient(clientId: String): Flow<AppResult<List<RepairRequest>>> =
        observed {
            requests.map { all ->
                AppResult.Success(all.filter { it.clientId == clientId }.sortedByDescending { it.createdAt })
            }
        }

    override fun observeByProfessional(professionalId: String): Flow<AppResult<List<RepairRequest>>> =
        observed {
            requests.map { all ->
                AppResult.Success(
                    all.filter { it.professionalId == professionalId }.sortedByDescending { it.acceptedAt ?: 0L },
                )
            }
        }

    /** Returns every OPEN request as a candidate: the exact distance filter belongs to the domain. */
    override fun observeOpenNear(center: GeoPoint, radiusKm: Int): Flow<AppResult<List<RepairRequest>>> =
        observed {
            requests
                .map { all -> AppResult.Success(all.filter { it.status == RequestStatus.OPEN }) }
                .onStart { openNearStarts++ }
        }

    override fun observeReview(requestId: String): Flow<AppResult<Review?>> =
        observed { reviews.map { all -> AppResult.Success(all.firstOrNull { it.requestId == requestId }) } }

    override suspend fun accept(requestId: String, professionalId: String): AppResult<RepairRequest> {
        acceptCalls += requestId to professionalId
        ::acceptError.take()?.let { return AppResult.Error(it) }
        val current = request(requestId) ?: return AppResult.Error(DomainError.NotFound)
        if (current.status == RequestStatus.ACCEPTED && current.professionalId == professionalId) {
            return AppResult.Success(current)
        }
        return when (val next = RequestStateMachine.transition(current.status, RequestAction.ACCEPT)) {
            is AppResult.Error -> next
            is AppResult.Success -> {
                val accepted = current.copy(status = next.data, professionalId = professionalId, acceptedAt = now)
                upsert(accepted)
                AppResult.Success(accepted)
            }
        }
    }

    override suspend fun cancel(requestId: String): AppResult<RepairRequest> {
        cancelCalls += requestId
        ::cancelError.take()?.let { return AppResult.Error(it) }
        val current = request(requestId) ?: return AppResult.Error(DomainError.NotFound)
        return when (val next = RequestStateMachine.transition(current.status, RequestAction.CANCEL)) {
            is AppResult.Error -> next
            is AppResult.Success -> {
                val cancelled = current.copy(status = next.data, cancelledAt = now)
                upsert(cancelled)
                AppResult.Success(cancelled)
            }
        }
    }

    override suspend fun completeWithReview(requestId: String, rating: Int, comment: String?): AppResult<Review> {
        completeCalls += Triple(requestId, rating, comment)
        ::completeError.take()?.let { return AppResult.Error(it) }
        val current = request(requestId) ?: return AppResult.Error(DomainError.NotFound)
        return when (val next = RequestStateMachine.transition(current.status, RequestAction.COMPLETE)) {
            is AppResult.Error -> next
            is AppResult.Success -> {
                val review = Review(
                    requestId = requestId,
                    professionalId = checkNotNull(current.professionalId),
                    clientId = current.clientId,
                    rating = rating,
                    comment = comment,
                    createdAt = now,
                )
                upsert(current.copy(status = next.data, completedAt = now))
                reviews.value = reviews.value + review
                AppResult.Success(review)
            }
        }
    }

    private fun find(requestId: String): AppResult<RepairRequest> =
        request(requestId)?.let { AppResult.Success(it) } ?: AppResult.Error(DomainError.NotFound)

    /** Applies the observeError hook to whichever observe* runs next. */
    private fun <T> observed(source: () -> Flow<AppResult<T>>): Flow<AppResult<T>> {
        ::observeError.take()?.let { return flowOf(AppResult.Error(it)) }
        return source()
    }
}
