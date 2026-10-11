package com.vespelabs.fixit.core.domain.rules

import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestAction.ACCEPT
import com.vespelabs.fixit.core.domain.model.RequestAction.CANCEL
import com.vespelabs.fixit.core.domain.model.RequestAction.COMPLETE
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.RequestStatus.ACCEPTED
import com.vespelabs.fixit.core.domain.model.RequestStatus.CANCELLED
import com.vespelabs.fixit.core.domain.model.RequestStatus.COMPLETED
import com.vespelabs.fixit.core.domain.model.RequestStatus.OPEN
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestStateMachineTest {
    private fun success(status: RequestStatus) = AppResult.Success(status)

    private fun invalid(from: RequestStatus, action: RequestAction) =
        AppResult.Error(DomainError.InvalidTransition(from, action))

    @Test
    fun `given an open request when accepted then it becomes accepted`() {
        assertEquals(success(ACCEPTED), RequestStateMachine.transition(OPEN, ACCEPT))
    }

    @Test
    fun `given an open request when cancelled then it becomes cancelled`() {
        assertEquals(success(CANCELLED), RequestStateMachine.transition(OPEN, CANCEL))
    }

    @Test
    fun `given an accepted request when cancelled then it becomes cancelled`() {
        assertEquals(success(CANCELLED), RequestStateMachine.transition(ACCEPTED, CANCEL))
    }

    @Test
    fun `given an accepted request when completed then it becomes completed`() {
        assertEquals(success(COMPLETED), RequestStateMachine.transition(ACCEPTED, COMPLETE))
    }

    @Test
    fun `given an open request when completed then the transition is invalid`() {
        assertEquals(invalid(OPEN, COMPLETE), RequestStateMachine.transition(OPEN, COMPLETE))
    }

    @Test
    fun `given an accepted request when accepted again then it is already taken`() {
        assertEquals(AppResult.Error(DomainError.AlreadyTaken), RequestStateMachine.transition(ACCEPTED, ACCEPT))
    }

    @Test
    fun `given a completed request when any action is applied then the transition is invalid`() {
        RequestAction.entries.forEach { action ->
            assertEquals(invalid(COMPLETED, action), RequestStateMachine.transition(COMPLETED, action))
        }
    }

    @Test
    fun `given a cancelled request when any action is applied then the transition is invalid`() {
        RequestAction.entries.forEach { action ->
            assertEquals(invalid(CANCELLED, action), RequestStateMachine.transition(CANCELLED, action))
        }
    }

    @Test
    fun `given every status and action when transitioned then all 12 combinations are covered`() {
        val valid = mapOf(
            (OPEN to ACCEPT) to ACCEPTED,
            (OPEN to CANCEL) to CANCELLED,
            (ACCEPTED to CANCEL) to CANCELLED,
            (ACCEPTED to COMPLETE) to COMPLETED,
        )
        var combinations = 0
        for (status in RequestStatus.entries) {
            for (action in RequestAction.entries) {
                combinations++
                val result = RequestStateMachine.transition(status, action)
                val target = valid[status to action]
                when {
                    target != null -> assertEquals(success(target), result)
                    status == ACCEPTED && action == ACCEPT ->
                        assertEquals(AppResult.Error(DomainError.AlreadyTaken), result)
                    else -> assertEquals(invalid(status, action), result)
                }
            }
        }
        assertEquals(12, combinations)
    }
}
