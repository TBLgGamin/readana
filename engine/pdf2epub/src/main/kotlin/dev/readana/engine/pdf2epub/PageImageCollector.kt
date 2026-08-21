package dev.readana.engine.pdf2epub

import android.graphics.Bitmap
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dev.readana.engine.reflow.model.PageImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Pulls raster figures off one page with the position and size they are drawn at.
 *
 * PDFBox reports images through the content stream rather than the page model, so this walks the
 * stream and reads the current transformation matrix at each `Do` operator. Codecs Android cannot
 * decode (JPEG 2000, JBIG2) are skipped with a warning rather than failing the conversion.
 */
internal class PageImageCollector(
    private val pageHeight: Float,
    private val options: PdfExtractionOptions,
    private val budget: ImageBudget
) : PDFStreamEngine() {
    private val collected = mutableListOf<PageImage>()

    /** Only used to say which page a warning came from. */
    private var pageIndex = 0

    init {
        addOperator(Concatenate())
        addOperator(DrawObject())
        addOperator(SetGraphicsStateParameters())
        addOperator(Save())
        addOperator(Restore())
        addOperator(SetMatrix())
    }

    fun collect(page: PDPage, index: Int): List<PageImage> {
        collected.clear()
        pageIndex = index
        try {
            processPage(page)
        } catch (e: IOException) {
            budget.warnings += "Could not read figures on page ${index + 1}: ${e.message ?: "unknown error"}"
        }
        return collected.toList()
    }

    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        if (operator.name != DRAW_OBJECT) {
            super.processOperator(operator, operands)
            return
        }
        val name = operands.getOrNull(0) as? COSName
        val xObject = name?.let { resolve(it) }
        when (xObject) {
            // Forms nest their own content stream, which may draw images of its own.
            is PDFormXObject -> showForm(xObject)

            is PDImageXObject -> takeSafely(xObject)

            else -> Unit
        }
    }

    private fun resolve(name: COSName): PDXObject? = try {
        resources?.getXObject(name)
    } catch (_: IOException) {
        null
    }

    /**
     * A figure is a bonus, never a reason to lose the book: anything the decoders throw at us is
     * recorded as a warning and the text carries on.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun takeSafely(image: PDImageXObject) {
        try {
            take(image)
        } catch (e: RuntimeException) {
            budget.warnings += "Skipped a figure on page ${pageIndex + 1}: ${e.message ?: e::class.simpleName}"
        } catch (e: OutOfMemoryError) {
            budget.warnings += "Skipped a figure on page ${pageIndex + 1}: ${e.message ?: "out of memory"}"
        }
    }

    private fun take(image: PDImageXObject) {
        if (!budget.canTakeMore()) {
            budget.noteExhausted(pageIndex)
            return
        }
        val matrix = graphicsState.currentTransformationMatrix
        val width = matrix.scalingFactorX
        val height = matrix.scalingFactorY
        if (width <= 0f || height <= 0f) return
        if (image.width < options.minImageDimensionPx || image.height < options.minImageDimensionPx) return

        val bitmap = decode(image) ?: return
        // PDFBox hands back a bitmap it caches and reuses, so recycling it breaks every later draw
        // of the same XObject — a logo repeated across pages would fail the whole conversion with
        // "Can't compress a recycled bitmap". Only the scaled copy below is ours to free.
        val scaled = downscale(bitmap)
        val encoded = try {
            encode(scaled)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        } ?: return

        budget.record(encoded.bytes.size)
        collected += PageImage(
            x = matrix.translateX,
            // PDF space has its origin bottom-left; the reflow model counts down from the top.
            y = pageHeight - matrix.translateY - height,
            width = width,
            height = height,
            bytes = encoded.bytes,
            mediaType = encoded.mediaType,
            pixelWidth = encoded.width,
            pixelHeight = encoded.height
        )
    }

    private fun decode(image: PDImageXObject): Bitmap? = try {
        image.image
    } catch (e: IOException) {
        // JPEG 2000 and JBIG2 have no Android decoder; the text around the figure is still fine.
        budget.warnings += "Skipped a figure with an unsupported codec: ${e.message ?: "unknown error"}"
        null
    } catch (e: UnsupportedOperationException) {
        budget.warnings += "Skipped a figure with an unsupported codec: ${e.message ?: "unknown error"}"
        null
    } catch (e: OutOfMemoryError) {
        budget.warnings += "Skipped a figure that was too large to decode: ${e.message ?: "out of memory"}"
        null
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= options.maxImageDimensionPx) return bitmap
        val factor = options.maxImageDimensionPx.toFloat() / longest
        val width = (bitmap.width * factor).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /** JPEG for photographs, PNG when transparency would otherwise be lost. */
    private fun encode(bitmap: Bitmap): Encoded? {
        val transparent = bitmap.hasAlpha()
        val format = if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (transparent) PNG_QUALITY else options.jpegQuality
        val stream = ByteArrayOutputStream()
        if (!bitmap.compress(format, quality, stream)) return null
        val bytes = stream.toByteArray()
        if (bytes.isEmpty()) return null
        return Encoded(
            bytes = bytes,
            mediaType = if (transparent) "image/png" else "image/jpeg",
            width = bitmap.width,
            height = bitmap.height
        )
    }

    private class Encoded(val bytes: ByteArray, val mediaType: String, val width: Int, val height: Int)

    private companion object {
        const val DRAW_OBJECT = "Do"
        const val PNG_QUALITY = 100
    }
}
