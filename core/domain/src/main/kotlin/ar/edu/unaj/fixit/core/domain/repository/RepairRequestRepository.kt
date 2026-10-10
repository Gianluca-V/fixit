package ar.edu.unaj.fixit.core.domain.repository

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.NewRequest
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.Review
import ar.edu.unaj.fixit.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

interface RepairRequestRepository {
    suspend fun create(clientId: String, input: NewRequest): AppResult<RepairRequest>

    /** Fresh read for pre-checks; NotFound if missing. */
    suspend fun get(requestId: String): AppResult<RepairRequest>

    /** NotFound if missing. */
    fun observe(requestId: String): Flow<AppResult<RepairRequest>>

    /** Ordered by createdAt descending. */
    fun observeByClient(clientId: String): Flow<AppResult<List<RepairRequest>>>

    /** Ordered by acceptedAt descending. */
    fun observeByProfessional(professionalId: String): Flow<AppResult<List<RepairRequest>>>

    /** Candidates only; the exact filter is applied in the domain. */
    fun observeOpenNear(center: GeoPoint, radiusKm: Int): Flow<AppResult<List<RepairRequest>>>

    /** Success(null) while there is no review. */
    fun observeReview(requestId: String): Flow<AppResult<Review?>>

    // Atomic operations (Mutex in Fake, transaction in Firestore); the status is checked with
    // RequestStateMachine inside the atomic block.

    /**
     * Idempotent: already ACCEPTED by the same professionalId -> Success(unchanged request),
     * checked before the state machine.
     */
    suspend fun accept(requestId: String, professionalId: String): AppResult<RepairRequest>

    suspend fun cancel(requestId: String): AppResult<RepairRequest>

    suspend fun completeWithReview(requestId: String, rating: Int, comment: String?): AppResult<Review>
}
