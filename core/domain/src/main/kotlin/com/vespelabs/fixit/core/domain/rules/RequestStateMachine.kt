package com.vespelabs.fixit.core.domain.rules

import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError

/**
 * Pure transition table of a repair request. Authorization (who may act) is decided
 * by the callers before this table is consulted.
 */
object RequestStateMachine {
    fun transition(from: RequestStatus, action: RequestAction): AppResult<RequestStatus> =
        when (from to action) {
            RequestStatus.OPEN to RequestAction.ACCEPT -> AppResult.Success(RequestStatus.ACCEPTED)
            RequestStatus.OPEN to RequestAction.CANCEL -> AppResult.Success(RequestStatus.CANCELLED)
            RequestStatus.ACCEPTED to RequestAction.CANCEL -> AppResult.Success(RequestStatus.CANCELLED)
            RequestStatus.ACCEPTED to RequestAction.COMPLETE -> AppResult.Success(RequestStatus.COMPLETED)
            RequestStatus.ACCEPTED to RequestAction.ACCEPT -> AppResult.Error(DomainError.AlreadyTaken)
            else -> AppResult.Error(DomainError.InvalidTransition(from, action))
        }
}
