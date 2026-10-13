package com.vespelabs.fixit.core.data.cloudinary

import com.vespelabs.fixit.core.data.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.coroutines.executeAsync

/**
 * Unsigned Cloudinary upload over OkHttp (D21). `executeAsync()` suspends without blocking and cancels the `Call` when
 * the coroutine is cancelled, so the `firebaseCall(UPLOAD_TIMEOUT_MS)` timeout really stops it (D13). It only resumes
 * once the headers arrive: the body is still read with blocking I/O, so it runs on [dispatcher], never on Main.
 * No JVM test: request and response are covered by pure-function tests; the real upload is checked by hand (2.9.4).
 */
class CloudinaryUploader @Inject constructor(
    private val client: OkHttpClient,
    private val config: CloudinaryConfig,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : PhotoUploader {
    override suspend fun upload(bytes: ByteArray): String {
        check(config.cloudName.isNotBlank() && config.uploadPreset.isNotBlank()) { "Cloudinary config missing" }
        return client.newCall(buildUploadRequest(config, bytes)).executeAsync().use { response ->
            val body = withContext(dispatcher) { response.body.string() }
            parseUploadResponse(response.code, body)
        }
    }
}
