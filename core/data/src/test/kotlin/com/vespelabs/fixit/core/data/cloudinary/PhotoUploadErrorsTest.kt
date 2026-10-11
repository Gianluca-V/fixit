package com.vespelabs.fixit.core.data.cloudinary

import com.vespelabs.fixit.core.data.firebase.UPLOAD_TIMEOUT_MS
import com.vespelabs.fixit.core.data.firebase.firebaseCall
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** `[media]` "Mapeo de fallas de la subida": what a repository sees from `firebaseCall { uploader.upload(bytes) }`. */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoUploadErrorsTest {

    private val bytes = byteArrayOf(1, 2, 3)

    /** Test double: runs [behavior] as the upload. */
    private class TestPhotoUploader(private val behavior: suspend () -> String) : PhotoUploader {
        override suspend fun upload(bytes: ByteArray): String = behavior()
    }

    private suspend fun uploadWith(behavior: suspend () -> String): AppResult<String> {
        val uploader = TestPhotoUploader(behavior)
        return firebaseCall(UPLOAD_TIMEOUT_MS) { uploader.upload(bytes) }
    }

    @Test
    fun `given an upload that succeeds when it runs inside firebaseCall then it returns Success with the url`() = runTest {
        assertEquals(AppResult.Success("https://res.cloudinary.com/x.jpg"), uploadWith { "https://res.cloudinary.com/x.jpg" })
    }

    @Test
    fun `given an IOException from the upload when it runs inside firebaseCall then the error is Network`() = runTest {
        assertEquals(AppResult.Error(DomainError.Network), uploadWith { throw IOException("connection reset") })
        assertEquals(AppResult.Error(DomainError.Network), uploadWith { throw UnknownHostException("api.cloudinary.com") })
        assertEquals(AppResult.Error(DomainError.Network), uploadWith { throw SocketTimeoutException("read timed out") })
    }

    @Test
    fun `given an upload that never ends when 30 virtual seconds pass then the error is Network`() = runTest {
        val call = async { uploadWith { awaitCancellation() } }

        advanceTimeBy(UPLOAD_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(call.isCompleted)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(AppResult.Error(DomainError.Network), call.await())
    }

    @Test
    fun `given a CloudinaryUploadException with any code when it runs inside firebaseCall then the error is Unknown`() = runTest {
        listOf(400, 401, 500).forEach { code ->
            assertEquals(AppResult.Error(DomainError.Unknown), uploadWith { throw CloudinaryUploadException(code) })
        }
    }

    @Test
    fun `given an empty config IllegalStateException when it runs inside firebaseCall then the error is Unknown`() = runTest {
        assertEquals(
            AppResult.Error(DomainError.Unknown),
            uploadWith { check(false) { "Cloudinary config missing" }; "unreachable" },
        )
    }
}
