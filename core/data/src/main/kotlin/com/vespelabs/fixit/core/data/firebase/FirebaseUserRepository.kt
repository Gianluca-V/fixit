package com.vespelabs.fixit.core.data.firebase

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.vespelabs.fixit.core.domain.model.ProfileForm
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** `users/{uid}` over Firestore (design 13.1, 13.3). Never throws into the domain (13.4). */
class FirebaseUserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserRepository {

    private fun userDoc(userId: String): DocumentReference = firestore.collection(USERS).document(userId)

    override suspend fun get(userId: String): AppResult<User> = firebaseCall {
        val snapshot = userDoc(userId).get().await()
        if (!snapshot.exists()) throw DomainException(DomainError.NotFound)
        snapshot.toUser()
    }

    /** A missing document emits NotFound without ending the flow; a listener failure ends it with the mapped error. */
    override fun observe(userId: String): Flow<AppResult<User>> = userDoc(userId).snapshotFlow()
        .map<DocumentSnapshot, AppResult<User>> { snapshot ->
            if (snapshot.exists()) AppResult.Success(snapshot.toUser()) else AppResult.Error(DomainError.NotFound)
        }
        .catch { emit(AppResult.Error(it.toDomainError())) }

    /**
     * Reads the role, then writes the editable fields only (never email, role or reputation, rules 13.3) and returns
     * the user with the same changes FakeUserRepository applies. Read and write share one [firebaseCall].
     */
    override suspend fun updateProfile(userId: String, form: ProfileForm): AppResult<User> = firebaseCall {
        val doc = userDoc(userId)
        val snapshot = doc.get().await()
        if (!snapshot.exists()) throw DomainException(DomainError.NotFound)
        val user = snapshot.toUser()
        doc.update(profileUpdateFields(user.role, form)).await()
        user.withProfile(form)
    }
}

/** Snapshot listener as a cold flow; the registration is removed when the collector goes away. */
internal fun DocumentReference.snapshotFlow(
    metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE,
): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener(metadataChanges) { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}
