package com.vespelabs.fixit.core.data.firebase

import com.google.firebase.firestore.FieldValue
import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.rules.RequestStateMachine

// Pure decisions of the request transactions (design 13.1): RequestStateMachine plus the idempotent accept (10.2),
// and EXACTLY the fields each rule of 13.3 allows (`changed().hasOnly(...)`), every date as serverTimestamp().

/** What a transaction does with the request it just read. */
internal sealed interface Transition {
    /** Nothing to write: the same professional accepts again (idempotent). */
    data object Unchanged : Transition

    data class Write(val fields: Map<String, Any>) : Transition

    data class Reject(val error: DomainError) : Transition
}

/** Accept: idempotent for the professional already assigned, checked BEFORE the state machine (like the Fake). */
internal fun acceptTransition(status: RequestStatus, assignedTo: String?, professionalId: String): Transition {
    if (status == RequestStatus.ACCEPTED && assignedTo == professionalId) return Transition.Unchanged
    return transition(status, RequestAction.ACCEPT) { next ->
        mapOf(
            RequestFields.STATUS to next.name,
            RequestFields.PROFESSIONAL_ID to professionalId,
            RequestFields.ACCEPTED_AT to FieldValue.serverTimestamp(),
        )
    }
}

/** Cancel keeps professionalId, so an accepted job still shows in "My jobs". */
internal fun cancelTransition(status: RequestStatus): Transition = transition(status, RequestAction.CANCEL) { next ->
    mapOf(RequestFields.STATUS to next.name, RequestFields.CANCELLED_AT to FieldValue.serverTimestamp())
}

/** Complete; the review and the reputation are written in the same transaction (`existsAfter` in 13.3). */
internal fun completeTransition(status: RequestStatus): Transition = transition(status, RequestAction.COMPLETE) { next ->
    mapOf(RequestFields.STATUS to next.name, RequestFields.COMPLETED_AT to FieldValue.serverTimestamp())
}

/**
 * The professional's aggregate after one more review, computed from the values read in the same transaction; the
 * permissive rule only accepts these two fields with `reviewCount + 1` (13.3).
 */
internal fun reputationFields(ratingSum: Long, reviewCount: Long, rating: Int): Map<String, Any> =
    mapOf(UserFields.RATING_SUM to ratingSum + rating, UserFields.REVIEW_COUNT to reviewCount + 1)

private inline fun transition(
    status: RequestStatus,
    action: RequestAction,
    fields: (RequestStatus) -> Map<String, Any>,
): Transition = when (val next = RequestStateMachine.transition(status, action)) {
    is AppResult.Error -> Transition.Reject(next.error)
    is AppResult.Success -> Transition.Write(fields(next.data))
}
