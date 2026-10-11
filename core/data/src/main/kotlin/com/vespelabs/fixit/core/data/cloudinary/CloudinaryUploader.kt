package com.vespelabs.fixit.core.data.cloudinary

import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.coroutines.executeAsync

/**
 * Unsigned Cloudinary upload over OkHttp (D21). No dispatcher: `executeAsync()` suspends without blocking and cancels
 * the `Call` when the coroutine is cancelled, so the `firebaseCall(UPLOAD_TIMEOUT_MS)` timeout really stops it (D13).
 * No JVM test: request and response are covered by pure-function tests; the real upload is checked by hand (2.9.4).
 */
class CloudinaryUploader @Inject constructor(
    private val client: OkHttpClient,
    private val config: CloudinaryConfig,
) : PhotoUploader {
    override suspend fun upload(bytes: ByteArray): String {
        check(config.cloudName.isNotBlank() && config.uploadPreset.isNotBlank()) { "Cloudinary config missing" }
        return client.newCall(buildUploadRequest(config, bytes)).executeAsync().use { response ->
            parseUploadResponse(response.code, response.body.string())
        }
    }
}
