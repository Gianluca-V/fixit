package com.vespelabs.fixit.core.data.push

import android.util.Log
import com.vespelabs.fixit.core.domain.model.NewRequest
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The Worker request is dropped after this long, without retries (D24). */
const val PUSH_TIMEOUT_MS = 5_000L

/** Bounds the idempotence pre-read of accept(): the user's operation never waits longer for it. */
const val PRE_READ_TIMEOUT_MS = 2_000L

/**
 * Decorator that asks the Worker for a push after a successful create, a non-idempotent accept or the cancel of an
 * ACCEPTED request (design 14b.3, D24). get, observe* and completeWithReview pass through unchanged.
 *
 * The returned result is ALWAYS the real repository's: the push is best effort and never changes success or error.
 * The caller never waits for the Worker; the only added wait is the accept pre-read, bounded by [PRE_READ_TIMEOUT_MS].
 */
class NotifyingRepairRequestRepository(
    private val delegate: RepairRequestRepository,
    private val worker: WorkerClient,
    private val tokens: IdTokenProvider,
    private val scope: CoroutineScope, // @ApplicationScope: the request survives the ViewModel
) : RepairRequestRepository by delegate {
    override suspend fun create(clientId: String, input: NewRequest): AppResult<RepairRequest> =
        delegate.create(clientId, input).also { if (it is AppResult.Success) fire(PushEventType.NEW_REQUEST, it.data.id) }

    override suspend fun accept(requestId: String, professionalId: String): AppResult<RepairRequest> {
        // Idempotent retry by the same professional -> no second request. On timeout (or Error) it counts as "not mine"
        // and may yield a duplicate request, which the device replaces by tag.
        val before = withTimeoutOrNull(PRE_READ_TIMEOUT_MS) { delegate.get(requestId) }
        val alreadyMine = before is AppResult.Success &&
            before.data.status == RequestStatus.ACCEPTED && before.data.professionalId == professionalId
        return delegate.accept(requestId, professionalId).also {
            if (it is AppResult.Success && !alreadyMine) fire(PushEventType.REQUEST_ACCEPTED, it.data.id)
        }
    }

    override suspend fun cancel(requestId: String): AppResult<RepairRequest> =
        delegate.cancel(requestId).also {
            // Only an ACCEPTED request had a professional assigned.
            if (it is AppResult.Success && it.data.professionalId != null) fire(PushEventType.REQUEST_CANCELLED, it.data.id)
        }

    private fun fire(type: PushEventType, id: String) {
        scope.launch { // fire-and-forget: the caller never waits
            try {
                withTimeoutOrNull(PUSH_TIMEOUT_MS) { // dropped after 5 s, no retries
                    val token = tokens.idToken() ?: return@withTimeoutOrNull
                    worker.send(token, PushEvent(type, id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("NotifyingRepository", "push request for $type failed", e) // never reaches presentation
            }
        }
    }
}

/** Pure factory: no Worker configured (Phases 1-3 or rollback) -> the same undecorated instance. */
fun withPush(
    real: RepairRequestRepository,
    workerUrl: String,
    worker: WorkerClient,
    tokens: IdTokenProvider,
    scope: CoroutineScope,
): RepairRequestRepository = if (workerUrl.isBlank()) real else NotifyingRepairRequestRepository(real, worker, tokens, scope)
