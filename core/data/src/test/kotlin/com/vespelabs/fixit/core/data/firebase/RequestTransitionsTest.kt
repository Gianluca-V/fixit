package com.vespelabs.fixit.core.data.firebase

import com.google.firebase.firestore.FieldValue
import com.vespelabs.fixit.core.domain.model.RequestAction
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.result.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestTransitionsTest {

    private fun Transition.fields(): Map<String, Any> = (this as Transition.Write).fields

    // ---------- accept ----------

    @Test
    fun `accepting an open request writes only status, professional and acceptedAt`() {
        val fields = acceptTransition(RequestStatus.OPEN, assignedTo = null, professionalId = "p1").fields()

        assertEquals(setOf(RequestFields.STATUS, RequestFields.PROFESSIONAL_ID, RequestFields.ACCEPTED_AT), fields.keys)
        assertEquals(RequestStatus.ACCEPTED.name, fields[RequestFields.STATUS])
        assertEquals("p1", fields[RequestFields.PROFESSIONAL_ID])
        assertTrue(fields[RequestFields.ACCEPTED_AT] is FieldValue)
    }

    @Test
    fun `accepting again as the same professional is unchanged, before the state machine`() {
        assertEquals(Transition.Unchanged, acceptTransition(RequestStatus.ACCEPTED, assignedTo = "p1", professionalId = "p1"))
    }

    @Test
    fun `accepting a request taken by another professional is AlreadyTaken`() {
        assertEquals(
            Transition.Reject(DomainError.AlreadyTaken),
            acceptTransition(RequestStatus.ACCEPTED, assignedTo = "p2", professionalId = "p1"),
        )
    }

    @Test
    fun `accepting a finished request is an invalid transition, even for its professional`() {
        assertEquals(
            Transition.Reject(DomainError.InvalidTransition(RequestStatus.COMPLETED, RequestAction.ACCEPT)),
            acceptTransition(RequestStatus.COMPLETED, assignedTo = "p1", professionalId = "p1"),
        )
        assertEquals(
            Transition.Reject(DomainError.InvalidTransition(RequestStatus.CANCELLED, RequestAction.ACCEPT)),
            acceptTransition(RequestStatus.CANCELLED, assignedTo = null, professionalId = "p1"),
        )
    }

    // ---------- cancel ----------

    @Test
    fun `cancelling an open or accepted request writes only status and cancelledAt`() {
        for (from in listOf(RequestStatus.OPEN, RequestStatus.ACCEPTED)) {
            val fields = cancelTransition(from).fields()

            assertEquals(setOf(RequestFields.STATUS, RequestFields.CANCELLED_AT), fields.keys)
            assertEquals(RequestStatus.CANCELLED.name, fields[RequestFields.STATUS])
            assertTrue(fields[RequestFields.CANCELLED_AT] is FieldValue)
        }
    }

    @Test
    fun `cancelling a finished request is an invalid transition`() {
        assertEquals(
            Transition.Reject(DomainError.InvalidTransition(RequestStatus.COMPLETED, RequestAction.CANCEL)),
            cancelTransition(RequestStatus.COMPLETED),
        )
    }

    // ---------- complete ----------

    @Test
    fun `completing an accepted request writes only status and completedAt`() {
        val fields = completeTransition(RequestStatus.ACCEPTED).fields()

        assertEquals(setOf(RequestFields.STATUS, RequestFields.COMPLETED_AT), fields.keys)
        assertEquals(RequestStatus.COMPLETED.name, fields[RequestFields.STATUS])
        assertTrue(fields[RequestFields.COMPLETED_AT] is FieldValue)
    }

    @Test
    fun `completing an open request is an invalid transition`() {
        assertEquals(
            Transition.Reject(DomainError.InvalidTransition(RequestStatus.OPEN, RequestAction.COMPLETE)),
            completeTransition(RequestStatus.OPEN),
        )
    }

    @Test
    fun `the review adds its rating and exactly one review to the professional aggregate`() {
        assertEquals(
            mapOf(UserFields.RATING_SUM to 12L, UserFields.REVIEW_COUNT to 3L),
            reputationFields(ratingSum = 8, reviewCount = 2, rating = 4),
        )
    }
}
