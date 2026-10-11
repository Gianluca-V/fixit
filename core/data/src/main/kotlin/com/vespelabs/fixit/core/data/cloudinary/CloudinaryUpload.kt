package com.vespelabs.fixit.core.data.cloudinary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Unsigned upload settings read from `BuildConfig` (D22). Empty values make the first upload fail (design 13.7). */
data class CloudinaryConfig(val cloudName: String, val uploadPreset: String)

/** Any non-2xx response, or a 2xx without `secure_url`; maps to `DomainError.Unknown` with the code logged (13.4). */
class CloudinaryUploadException(val httpCode: Int) : Exception("Cloudinary upload failed: HTTP $httpCode")

private val JPEG = "image/jpeg".toMediaType()

/**
 * Unsigned upload request (D21): `POST https://api.cloudinary.com/v1_1/{cloudName}/image/upload` with exactly the
 * multipart fields `file` (JPEG named `photo.jpg`) and `upload_preset`. No API key, secret or signature: the preset
 * decides folder, formats and transformations.
 */
fun buildUploadRequest(config: CloudinaryConfig, bytes: ByteArray): Request {
    val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", "photo.jpg", bytes.toRequestBody(JPEG))
        .addFormDataPart("upload_preset", config.uploadPreset)
        .build()
    return Request.Builder()
        .url("https://api.cloudinary.com/v1_1/${config.cloudName}/image/upload")
        .post(body)
        .build()
}

/** 2xx with a string `secure_url` -> that URL; anything else throws [CloudinaryUploadException] with [code]. */
fun parseUploadResponse(code: Int, body: String): String {
    if (code !in 200..299) throw CloudinaryUploadException(code)
    // kotlinx-serialization JsonElement API, not org.json (a throwing stub in Android JVM tests).
    val secureUrl = runCatching {
        (Json.parseToJsonElement(body).jsonObject["secure_url"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
    }.getOrNull()
    return secureUrl?.takeIf { it.isNotBlank() } ?: throw CloudinaryUploadException(code)
}
