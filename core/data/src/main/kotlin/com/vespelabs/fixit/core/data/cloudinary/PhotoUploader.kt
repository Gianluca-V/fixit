package com.vespelabs.fixit.core.data.cloudinary

/**
 * Uploads one compressed JPEG and returns its public `secure_url` (design 13.7). Seam for the JVM tests of the error
 * mapping; callers wrap it in `firebaseCall(UPLOAD_TIMEOUT_MS) { uploader.upload(bytes) }`, so it may throw freely.
 */
interface PhotoUploader {
    suspend fun upload(bytes: ByteArray): String
}
