package com.vespelabs.fixit.core.data.firebase

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.vespelabs.fixit.core.data.cloudinary.CloudinaryUploadException
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

/** Upper bound for every Firestore write: offline writes are queued and their Task never completes (design 13.4). */
const val WRITE_TIMEOUT_MS = 15_000L

/** Upper bound for one photo upload; call site: `firebaseCall(UPLOAD_TIMEOUT_MS) { uploader.upload(bytes) }`. */
const val UPLOAD_TIMEOUT_MS = 30_000L

private const val LOG_TAG = "FirebaseCalls"

/** Carries a domain error out of a Firestore transaction. Never leaves the data layer. */
class DomainException(val error: DomainError) : Exception(error.toString())

/**
 * Runs [block] with a timeout and turns every failure into an [AppResult.Error], so Firebase repositories never
 * throw into the domain. The timeout maps to [DomainError.Network]; a cancelled caller is propagated, not mapped.
 */
suspend fun <T> firebaseCall(timeoutMs: Long = WRITE_TIMEOUT_MS, block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(withTimeout(timeoutMs) { block() })
} catch (e: TimeoutCancellationException) { // subclass of CancellationException: must be caught before it
    AppResult.Error(DomainError.Network)
} catch (e: CancellationException) {
    throw e // the caller was cancelled: propagate
} catch (e: Exception) {
    AppResult.Error(e.toDomainError())
}

/** Wraps every element of an observation in [AppResult.Success]; a failure ends the flow with the mapped error. */
fun <T> Flow<T>.asResult(): Flow<AppResult<T>> =
    map<T, AppResult<T>> { AppResult.Success(it) }.catch { emit(AppResult.Error(it.toDomainError())) }

/** Exception mapping table of design 13.4. A [DomainException] anywhere in the cause chain is evaluated first. */
fun Throwable.toDomainError(): DomainError {
    generateSequence(this) { it.cause }
        .filterIsInstance<DomainException>()
        .firstOrNull()
        ?.let { return it.error }

    return when (this) {
        is TimeoutCancellationException, is FirebaseNetworkException -> DomainError.Network
        is FirebaseFirestoreException -> when (code) {
            Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> DomainError.Network
            Code.PERMISSION_DENIED -> DomainError.Unauthorized
            Code.NOT_FOUND -> DomainError.NotFound
            else -> DomainError.Unknown
        }
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> DomainError.InvalidCredentials
        is FirebaseAuthUserCollisionException -> DomainError.EmailAlreadyInUse
        // OkHttp during a Cloudinary upload: no network or connection dropped (UnknownHost, Connect, SocketTimeout...).
        // No Firebase exception above extends IOException, so this row never shadows them.
        is IOException -> DomainError.Network
        is CloudinaryUploadException -> {
            Log.w(LOG_TAG, "Cloudinary upload rejected with HTTP $httpCode")
            DomainError.Unknown // specs/media: only network and timeout are Network
        }
        else -> DomainError.Unknown
    }
}
