package com.vespelabs.fixit.core.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseCallsTest {

    private suspend fun failingWith(error: Throwable): AppResult<String> = firebaseCall { throw error }

    @Test
    fun `given a block that succeeds when firebaseCall runs then it returns Success with the value`() = runTest {
        assertEquals(AppResult.Success("ok"), firebaseCall { "ok" })
    }

    @Test
    fun `given Firestore UNAVAILABLE or DEADLINE_EXCEEDED when firebaseCall runs then the error is Network`() = runTest {
        assertEquals(AppResult.Error(DomainError.Network), failingWith(firestore(Code.UNAVAILABLE)))
        assertEquals(AppResult.Error(DomainError.Network), failingWith(firestore(Code.DEADLINE_EXCEEDED)))
    }

    @Test
    fun `given FirebaseNetworkException when firebaseCall runs then the error is Network`() = runTest {
        assertEquals(AppResult.Error(DomainError.Network), failingWith(FirebaseNetworkException("offline")))
    }

    @Test
    fun `given Firestore PERMISSION_DENIED when firebaseCall runs then the error is Unauthorized`() = runTest {
        assertEquals(AppResult.Error(DomainError.Unauthorized), failingWith(firestore(Code.PERMISSION_DENIED)))
    }

    @Test
    fun `given Firestore NOT_FOUND when firebaseCall runs then the error is NotFound`() = runTest {
        assertEquals(AppResult.Error(DomainError.NotFound), failingWith(firestore(Code.NOT_FOUND)))
    }

    @Test
    fun `given any other Firestore code when firebaseCall runs then the error is Unknown`() = runTest {
        assertEquals(AppResult.Error(DomainError.Unknown), failingWith(firestore(Code.ABORTED)))
    }

    @Test
    fun `given invalid credentials or an invalid user when firebaseCall runs then the error is InvalidCredentials`() = runTest {
        assertEquals(
            AppResult.Error(DomainError.InvalidCredentials),
            failingWith(FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "wrong password")),
        )
        assertEquals(
            AppResult.Error(DomainError.InvalidCredentials),
            failingWith(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user")),
        )
    }

    @Test
    fun `given a user collision when firebaseCall runs then the error is EmailAlreadyInUse`() = runTest {
        assertEquals(
            AppResult.Error(DomainError.EmailAlreadyInUse),
            failingWith(FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "taken")),
        )
    }

    @Test
    fun `given an unrelated exception when firebaseCall runs then the error is Unknown`() = runTest {
        assertEquals(AppResult.Error(DomainError.Unknown), failingWith(IllegalStateException("Cloudinary config missing")))
    }

    @Test
    fun `given a DomainException when firebaseCall runs then its own error is returned`() = runTest {
        assertEquals(AppResult.Error(DomainError.AlreadyTaken), failingWith(DomainException(DomainError.AlreadyTaken)))
    }

    @Test
    fun `given a DomainException wrapped as a cause when firebaseCall runs then its own error is returned`() = runTest {
        val wrapped = firestore(Code.ABORTED, cause = RuntimeException(DomainException(DomainError.NotFound)))
        assertEquals(AppResult.Error(DomainError.NotFound), failingWith(wrapped))
    }

    @Test
    fun `given a block that never ends when 15 virtual seconds pass then the default timeout returns Network`() = runTest {
        val call = async { firebaseCall { awaitCancellation() } }

        advanceTimeBy(WRITE_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(call.isCompleted)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(AppResult.Error(DomainError.Network), call.await())
    }

    @Test
    fun `given an upload that never ends when 30 virtual seconds pass then the upload timeout returns Network`() = runTest {
        val call = async { firebaseCall(UPLOAD_TIMEOUT_MS) { awaitCancellation() } }

        advanceTimeBy(WRITE_TIMEOUT_MS + 1)
        runCurrent()
        assertFalse(call.isCompleted)

        advanceTimeBy(UPLOAD_TIMEOUT_MS - WRITE_TIMEOUT_MS)
        runCurrent()
        assertEquals(AppResult.Error(DomainError.Network), call.await())
    }

    @Test
    fun `given a pending call when the caller is cancelled then the cancellation propagates instead of returning a result`() = runTest {
        var resumedAfterCall = false
        val job = launch {
            firebaseCall { awaitCancellation() }
            resumedAfterCall = true
        }
        runCurrent()

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertFalse(resumedAfterCall)
    }

    @Test
    fun `given a flow that emits when collected asResult then each element is a Success`() = runTest {
        assertEquals(listOf(AppResult.Success(1), AppResult.Success(2)), flowOf(1, 2).asResult().toList())
    }

    @Test
    fun `given a flow that fails when collected asResult then it ends with the mapped Error`() = runTest {
        val failing = flow {
            emit(1)
            throw firestore(Code.PERMISSION_DENIED)
        }

        assertEquals(
            listOf(AppResult.Success(1), AppResult.Error(DomainError.Unauthorized)),
            failing.asResult().toList(),
        )
    }

    private fun firestore(code: Code, cause: Throwable? = null) =
        if (cause == null) FirebaseFirestoreException("firestore $code", code)
        else FirebaseFirestoreException("firestore $code", code, cause)
}
