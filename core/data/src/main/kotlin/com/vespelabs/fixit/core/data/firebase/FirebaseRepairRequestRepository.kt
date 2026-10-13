package com.vespelabs.fixit.core.data.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.vespelabs.fixit.core.data.cloudinary.ImageCompressor
import com.vespelabs.fixit.core.data.cloudinary.PhotoUploader
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.NewRequest
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.repository.Clock
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.ValidationError
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val LOG_TAG = "FirebaseRepairRequestRepo"

/**
 * `requests/{id}` and `reviews/{requestId}` over Firestore, photos over Cloudinary (design 13.1, 13.3, 13.7).
 * Never throws into the domain: commands go through [firebaseCall], observations through [asResult] (13.4).
 */
class FirebaseRepairRequestRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val imageCompressor: ImageCompressor,
    private val photoUploader: PhotoUploader,
    private val clock: Clock,
) : RepairRequestRepository {

    private val requests get() = firestore.collection(REQUESTS)

    private fun requestDoc(requestId: String): DocumentReference = requests.document(requestId)

    private fun reviewDoc(requestId: String): DocumentReference = firestore.collection(REVIEWS).document(requestId)

    /**
     * For each photo, in order: compress (local CPU, outside any timeout) and upload in its own 30 s [firebaseCall];
     * then write the document in a 15 s [firebaseCall]. The first failure ends the creation: no request is written
     * and the photos already uploaded are NOT deleted (deleting needs the signed API, D18).
     */
    override suspend fun create(clientId: String, input: NewRequest): AppResult<RepairRequest> {
        val category = input.category ?: return validationError(ValidationError.CATEGORY_REQUIRED)
        val place = input.place ?: return validationError(ValidationError.LOCATION_REQUIRED)
        val doc = requests.document()

        val photoUrls = mutableListOf<String>()
        for (uri in input.photoUris) {
            val bytes = when (val compressed = compress(uri)) {
                is AppResult.Error -> return compressed
                is AppResult.Success -> compressed.data
            }
            when (val uploaded = firebaseCall(UPLOAD_TIMEOUT_MS) { photoUploader.upload(bytes) }) {
                is AppResult.Error -> return uploaded
                is AppResult.Success -> photoUrls += uploaded.data
            }
        }

        return firebaseCall {
            doc.set(newRequestFields(clientId, category, input.description, photoUrls, place)).await()
            // The acknowledged write is in the local cache; ESTIMATE resolves createdAt even before the server echo.
            doc.get(Source.CACHE).await().toRepairRequest()
        }
    }

    override suspend fun get(requestId: String): AppResult<RepairRequest> = firebaseCall {
        val snapshot = requestDoc(requestId).get().await()
        if (!snapshot.exists()) throw DomainException(DomainError.NotFound)
        snapshot.toRepairRequest()
    }

    /** A missing document emits NotFound without ending the flow; a listener failure ends it with the mapped error. */
    override fun observe(requestId: String): Flow<AppResult<RepairRequest>> = requestDoc(requestId).snapshotFlow()
        .map<DocumentSnapshot, AppResult<RepairRequest>> { snapshot ->
            if (snapshot.exists()) AppResult.Success(snapshot.toRepairRequest()) else AppResult.Error(DomainError.NotFound)
        }
        .catch { emit(AppResult.Error(it.toDomainError())) }

    /** Index `clientId ASC, createdAt DESC` (13.2); re-sorted locally so pending server timestamps keep the order. */
    override fun observeByClient(clientId: String): Flow<AppResult<List<RepairRequest>>> =
        requests.whereEqualTo(RequestFields.CLIENT_ID, clientId)
            .orderBy(RequestFields.CREATED_AT, Query.Direction.DESCENDING)
            .snapshotFlow()
            .map { snapshot -> snapshot.toRepairRequests().sortedByDescending { it.createdAt } }
            .asResult()

    /**
     * Index `professionalId ASC, acceptedAt DESC` (13.2): everything assigned to the professional, including jobs
     * cancelled after being accepted (professionalId is kept), like the Fake.
     */
    override fun observeByProfessional(professionalId: String): Flow<AppResult<List<RepairRequest>>> =
        requests.whereEqualTo(RequestFields.PROFESSIONAL_ID, professionalId)
            .orderBy(RequestFields.ACCEPTED_AT, Query.Direction.DESCENDING)
            .snapshotFlow()
            .map { snapshot -> snapshot.toRepairRequests().sortedByDescending { it.acceptedAt ?: 0L } }
            .asResult()

    /**
     * One `status == OPEN` listener per geohash range (index `status ASC, geohash ASC`, 13.2), each wrapped with
     * [asResult], combined and de-duplicated. Only candidates: `Matching` in the domain drops the false positives.
     */
    override fun observeOpenNear(center: GeoPoint, radiusKm: Int): Flow<AppResult<List<RepairRequest>>> {
        val listeners = feedRanges(center, radiusKm).map { range ->
            requests.whereEqualTo(RequestFields.STATUS, RequestStatus.OPEN.name)
                .orderBy(RequestFields.GEOHASH)
                .startAt(range.start)
                .endAt(range.end)
                .snapshotFlow()
                .map { it.toRepairRequests() }
                .asResult()
        }
        return combine(listeners) { results -> mergeCandidates(results.toList()) }
    }

    /** Success(null) while there is no review. */
    override fun observeReview(requestId: String): Flow<AppResult<Review?>> = reviewDoc(requestId).snapshotFlow()
        .map { snapshot -> if (snapshot.exists()) snapshot.toReview() else null }
        .asResult()

    /**
     * Idempotent: the professional already assigned gets the request unchanged (no write); another one gets
     * AlreadyTaken from the state machine. Writes only status, professionalId and acceptedAt (13.3).
     */
    override suspend fun accept(requestId: String, professionalId: String): AppResult<RepairRequest> =
        transitionRequest(requestId) { request -> acceptTransition(request.status, request.professionalId, professionalId) }

    /** Writes only status and cancelledAt (13.3); professionalId is kept, like the Fake. */
    override suspend fun cancel(requestId: String): AppResult<RepairRequest> =
        transitionRequest(requestId) { request -> cancelTransition(request.status) }

    /**
     * One transaction: request (status, completedAt), the review and the professional's aggregate change together or
     * not at all (13.3 `existsAfter`). The aggregate is computed from the values read in the same transaction.
     */
    override suspend fun completeWithReview(requestId: String, rating: Int, comment: String?): AppResult<Review> =
        firebaseCall {
            val requestRef = requestDoc(requestId)
            val reviewRef = reviewDoc(requestId)
            val written = firestore.runTransaction { tx ->
                val request = tx.readRequest(requestRef)
                val fields = when (val decision = completeTransition(request.status)) {
                    is Transition.Write -> decision.fields
                    is Transition.Reject -> throw DomainException(decision.error)
                    Transition.Unchanged -> error("Completing never leaves a request unchanged")
                }
                val professionalId = request.professionalId ?: throw DomainException(DomainError.Unknown)
                val professionalRef = firestore.collection(USERS).document(professionalId)
                val professional = tx.get(professionalRef) // every read happens before the first write
                if (!professional.exists()) throw DomainException(DomainError.NotFound)

                tx.update(requestRef, fields)
                tx.set(reviewRef, newReviewFields(professionalId, request.clientId, rating, comment))
                tx.update(
                    professionalRef,
                    reputationFields(
                        ratingSum = professional.getLong(UserFields.RATING_SUM) ?: 0,
                        reviewCount = professional.getLong(UserFields.REVIEW_COUNT) ?: 0,
                        rating = rating,
                    ),
                )
                Review(requestId, professionalId, request.clientId, rating, comment, createdAt = clock.now())
            }.await()
            // The commit already happened, so a failed read-back must not turn it into an error: it only swaps the
            // local createdAt for the server one. SERVER for the same reason as in transitionRequest (observeReview).
            val committed = try {
                reviewRef.get(Source.SERVER).await().takeIf { it.exists() }?.toReview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Review read-back failed; keeping the local createdAt", e)
                null
            }
            committed ?: written
        }

    /**
     * Reads the request in a transaction, applies [decide] and writes its fields; a rejection is thrown as
     * [DomainException] (13.4). A written request is read back so the server timestamps are real dates.
     */
    private suspend fun transitionRequest(
        requestId: String,
        decide: (RepairRequest) -> Transition,
    ): AppResult<RepairRequest> = firebaseCall {
        val doc = requestDoc(requestId)
        val unchanged: RepairRequest? = firestore.runTransaction { tx ->
            val request = tx.readRequest(doc)
            when (val decision = decide(request)) {
                Transition.Unchanged -> request
                is Transition.Write -> {
                    tx.update(doc, decision.fields)
                    null
                }
                is Transition.Reject -> throw DomainException(decision.error)
            }
        }.await()
        // SERVER, not DEFAULT: with a live listener on this document, DEFAULT may answer from the listener's view,
        // which does not have the transaction's write yet (transactions are not applied to the local cache).
        unchanged ?: doc.get(Source.SERVER).await().toRepairRequest()
    }

    /** Compression is local CPU work: no timeout (13.4), but its failure still becomes an error, never a throw. */
    private suspend fun compress(uri: String): AppResult<ByteArray> = try {
        AppResult.Success(imageCompressor.compress(uri))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Error(e.toDomainError())
    }

    private fun validationError(error: ValidationError) = AppResult.Error(DomainError.Validation(error))
}

/** NotFound (as [DomainException]) when the request does not exist. */
private fun Transaction.readRequest(doc: DocumentReference): RepairRequest {
    val snapshot = get(doc)
    if (!snapshot.exists()) throw DomainException(DomainError.NotFound)
    return snapshot.toRepairRequest()
}

private fun QuerySnapshot.toRepairRequests(): List<RepairRequest> = documents.map { it.toRepairRequest() }

/** Query listener as a cold flow; the registration is removed when the collector goes away. */
internal fun Query.snapshotFlow(
    metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE,
): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(metadataChanges) { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}
