package com.vespelabs.fixit.core.data.device

import android.content.Context
import android.util.Log
import com.vespelabs.fixit.core.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val LOG_TAG = "CapturedPhotos"

/** Must match `PhotoPickerButtons` and the `<cache-path name="photos" path="photos/"/>` of `:app` file_paths.xml. */
private const val PHOTOS_DIR = "photos"

/**
 * The file name behind [uri] when it is a camera capture served by our FileProvider ([authority]) from the
 * `photos` path (`content://<authority>/photos/<name>`); null for anything else, including system Photo Picker
 * URIs (never ours to delete) and names that could escape the directory (`/`, `\`, `..`).
 */
fun capturedPhotoName(uri: String, authority: String): String? {
    val parsed = try {
        URI(uri)
    } catch (e: URISyntaxException) {
        return null
    }
    if (parsed.scheme != "content" || parsed.rawAuthority != authority) return null
    val segments = parsed.rawPath?.split('/') ?: return null
    if (segments.size != 3 || segments[0].isNotEmpty() || segments[1] != PHOTOS_DIR) return null
    val name = try {
        URLDecoder.decode(segments[2].replace("+", "%2B"), Charsets.UTF_8.name())
    } catch (e: IllegalArgumentException) {
        return null
    }
    val unsafe = name.isEmpty() || name.contains('/') || name.contains('\\') || name.contains("..")
    return name.takeUnless { unsafe }
}

/** Deletes the camera captures of a published request from `cacheDir/photos/`; gallery URIs are left alone. */
class CapturedPhotos @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val authority get() = context.packageName + ".fileprovider"

    /** Best effort: a failure is only logged, so it never turns a publish into an error. */
    suspend fun delete(uris: List<String>) = withContext(ioDispatcher + NonCancellable) {
        val dir = File(context.cacheDir, PHOTOS_DIR)
        for (uri in uris) {
            val name = capturedPhotoName(uri, authority) ?: continue
            try {
                val file = File(dir, name)
                if (file.exists() && !file.delete()) Log.w(LOG_TAG, "Could not delete a captured photo")
            } catch (e: SecurityException) {
                Log.w(LOG_TAG, "Could not delete a captured photo", e)
            }
        }
    }
}
