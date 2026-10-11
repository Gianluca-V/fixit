package com.vespelabs.fixit.core.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.vespelabs.fixit.core.domain.model.ProfessionalInfo
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.model.SignUpForm
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.ValidationError
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val LOG_TAG = "FirebaseAuthRepository"

/**
 * Firebase Auth (email/password) plus the `users/{uid}` profile, following design 13.5. Commands never throw into
 * the domain ([firebaseCall], 13.4) and [session] never fails: any data-layer error ends as SignedOut.
 */
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    /** On while a signUp or signIn runs: the session stays SignedOut so the auth screen is not torn down mid-way. */
    private val operationInProgress = MutableStateFlow(false)

    private val authUserId: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) } // also fires on registration
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val session: Flow<SessionState> = combine(authUserId, operationInProgress) { uid, busy -> uid to busy }
        .flatMapLatest { (uid, busy) -> if (busy || uid == null) flowOf(SessionState.SignedOut) else profileSession(uid) }
        .catch { emit(SessionState.SignedOut) }
        .distinctUntilChanged()

    /**
     * Re-emits whenever `users/{uid}` changes. Metadata changes are included so a "missing" read from the cache can be
     * told apart from the server confirming it: only the latter is an orphan account (Auth user without profile, the
     * app died between the two sign-up steps), which is signed out instead of staying in Loading forever.
     */
    private fun profileSession(uid: String): Flow<SessionState> = userDoc(uid)
        .snapshotFlow(MetadataChanges.INCLUDE)
        .map { snapshot -> snapshot.toSessionState() }
        .catch { error ->
            Log.w(LOG_TAG, "Profile listener failed", error)
            emit(SessionState.SignedOut)
            auth.signOut()
        }

    private fun DocumentSnapshot.toSessionState(): SessionState = when {
        exists() -> SessionState.SignedIn(toUser())
        metadata.isFromCache -> SessionState.Loading
        else -> {
            Log.w(LOG_TAG, "Orphan account without profile document: signing out")
            auth.signOut()
            SessionState.SignedOut
        }
    }

    override fun currentUserId(): String? = auth.currentUser?.uid

    /** Fails with InvalidCredentials from Auth, or Unknown (after signing out) when the profile document is missing. */
    override suspend fun signIn(email: String, password: String): AppResult<User> = withOperationFlag {
        val signedIn = firebaseCall {
            checkNotNull(auth.signInWithEmailAndPassword(email, password).await().user) { "No user after sign-in" }.uid
        }
        val uid = when (signedIn) {
            is AppResult.Error -> return@withOperationFlag signedIn
            is AppResult.Success -> signedIn.data
        }

        val profile = firebaseCall {
            val snapshot = userDoc(uid).get().await()
            if (!snapshot.exists()) {
                Log.w(LOG_TAG, "Signed in without profile document")
                throw DomainException(DomainError.Unknown)
            }
            snapshot.toUser()
        }
        if (profile is AppResult.Error) auth.signOut() // the session must not open without a readable profile
        profile
    }

    /**
     * (1) creates the Auth user, (2) writes `users/{uid}`. If (2) fails or the caller is cancelled during it, the Auth
     * user is deleted on a best-effort basis under [NonCancellable] and the error is returned: no session is left open.
     */
    override suspend fun signUp(form: SignUpForm): AppResult<User> {
        val role = form.role ?: return validationError(ValidationError.ROLE_REQUIRED)
        val professional = if (role == UserRole.PROFESSIONAL) {
            ProfessionalInfo(
                categories = form.profile.categories,
                base = form.profile.base ?: return validationError(ValidationError.BASE_LOCATION_REQUIRED),
                radiusKm = form.profile.radiusKm ?: return validationError(ValidationError.INVALID_RADIUS),
            )
        } else {
            null
        }
        return withOperationFlag {
            val created = firebaseCall {
                checkNotNull(auth.createUserWithEmailAndPassword(form.email, form.password).await().user) {
                    "No user after sign-up"
                }
            }
            val firebaseUser = when (created) {
                is AppResult.Error -> return@withOperationFlag created
                is AppResult.Success -> created.data
            }
            val user = User(
                id = firebaseUser.uid,
                name = form.profile.name,
                email = form.email,
                phone = form.profile.phone,
                role = role,
                professional = professional,
            )
            val written = try {
                firebaseCall { userDoc(user.id).set(user.toCreateFields()).await() }
            } catch (e: CancellationException) {
                rollBack(firebaseUser)
                throw e
            }
            if (written is AppResult.Error) {
                rollBack(firebaseUser)
                written
            } else {
                AppResult.Success(user)
            }
        }
    }

    override fun signOut() = auth.signOut()

    /** Best effort: offline the delete may never finish, so it is bounded; the session is closed either way. */
    private suspend fun rollBack(firebaseUser: FirebaseUser) = withContext(NonCancellable) {
        try {
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { firebaseUser.delete().await() }
                ?: Log.w(LOG_TAG, "Rollback of the Auth user timed out")
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Rollback of the Auth user failed", e)
        }
        auth.signOut()
    }

    /** The flag is always cleared (finally); clearing it makes [session] recompute. */
    private suspend fun <T> withOperationFlag(block: suspend () -> T): T {
        operationInProgress.value = true
        try {
            return block()
        } finally {
            operationInProgress.value = false
        }
    }

    private fun userDoc(uid: String) = firestore.collection(USERS).document(uid)

    private fun validationError(error: ValidationError) = AppResult.Error(DomainError.Validation(error))
}
