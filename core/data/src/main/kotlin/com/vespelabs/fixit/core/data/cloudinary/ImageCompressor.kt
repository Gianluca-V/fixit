package com.vespelabs.fixit.core.data.cloudinary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.vespelabs.fixit.core.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Longest side, in pixels, of every uploaded photo (design 13.6). */
const val MAX_PHOTO_SIDE = 1600

private const val JPEG_QUALITY = 80

/** Size that fits [maxSide] on the longest side keeping the aspect ratio. Never upscales and never returns 0 px. */
fun targetSize(width: Int, height: Int, maxSide: Int = MAX_PHOTO_SIDE): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val scale = maxSide.toDouble() / longest
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

/**
 * Largest power of two that keeps the decoded longest side >= [maxSide], so [targetSize] can still downscale and
 * never has to upscale. Invalid bounds (width or height <= 0) -> 1.
 */
fun sampleSizeFor(width: Int, height: Int, maxSide: Int = MAX_PHOTO_SIDE): Int {
    if (width <= 0 || height <= 0) return 1
    val longest = maxOf(width, height)
    var sample = 1
    while (longest / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/**
 * Turns a picked photo into a JPEG ready for Cloudinary: bounded decoding (never at full size), EXIF orientation
 * applied, longest side <= [MAX_PHOTO_SIDE] and quality 80. Blocking work, so it runs on the injected IO
 * dispatcher (D13). No JVM test: the pure size functions above are tested; the output is checked by hand (design 15).
 */
class ImageCompressor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend fun compress(uri: String): ByteArray = withContext(dispatcher) {
        val source = Uri.parse(uri)

        // (1) bounds only, (2) decode already subsampled.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read(source) { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        val decoded = read(source) { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("Cannot decode image $uri")

        // (3) EXIF orientation, (4) one single transformation, (5) JPEG 80 and recycle both bitmaps.
        val rotation = read(source) { ExifInterface(it).rotationDegrees }
        val transformed = transform(decoded, rotation)
        try {
            ByteArrayOutputStream().use { out ->
                transformed.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            if (transformed !== decoded) transformed.recycle()
            decoded.recycle()
        }
    }

    private fun <T> read(source: Uri, block: (InputStream) -> T): T {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw IllegalArgumentException("Cannot open image $source")
        return stream.use(block)
    }

    /** Rotation and scale in a single [Matrix] and a single copy; returns [src] itself when nothing changes. */
    private fun transform(src: Bitmap, rotation: Int): Bitmap {
        val quarterTurn = rotation % 180 != 0
        val rotatedWidth = if (quarterTurn) src.height else src.width
        val rotatedHeight = if (quarterTurn) src.width else src.height
        val (width, height) = targetSize(rotatedWidth, rotatedHeight)
        if (rotation == 0 && width == rotatedWidth && height == rotatedHeight) return src

        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
            postScale(width.toFloat() / rotatedWidth, height.toFloat() / rotatedHeight)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
}
