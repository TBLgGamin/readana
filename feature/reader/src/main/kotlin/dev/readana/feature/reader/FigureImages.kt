package dev.readana.feature.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Prepares a book's illustration for sending.
 *
 * Plates in an EPUB are sized for print, and a request carrying three of them raw is megabytes of
 * base64 that providers variously reject, throttle, or take so long to accept that the answer
 * looks stuck. Shrinking to something a model can actually read costs nothing in usefulness.
 */
internal object FigureImages {
    /** Returns JPEG bytes no larger than [MAX_EDGE] on the long side, or null if unreadable. */
    fun prepare(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        if (longest <= MAX_EDGE && bytes.size <= MAX_BYTES) return bytes

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(longest) }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                out.toByteArray()
            }
        } catch (_: OutOfMemoryError) {
            // A plate too big to hold in memory is simply not sent.
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Powers of two only, which is all [BitmapFactory] honours. */
    private fun sampleSizeFor(longestEdge: Int): Int {
        var sample = 1
        while (longestEdge / sample > MAX_EDGE) sample *= 2
        return sample
    }

    /** Every provider reads detail well below this, and it keeps a request in the tens of kB. */
    const val MEDIA_TYPE = "image/jpeg"

    private const val MAX_EDGE = 1_024
    private const val MAX_BYTES = 400_000
    private const val QUALITY = 85
}
