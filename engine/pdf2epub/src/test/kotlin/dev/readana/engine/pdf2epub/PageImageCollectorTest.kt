package dev.readana.engine.pdf2epub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageImageCollectorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val warnings = mutableListOf<String>()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Regression for "Can't compress a recycled bitmap".
     *
     * `PDImageXObject.getImage()` caches the bitmap it decodes, so the collector must treat it as
     * borrowed. It used to recycle it, which meant the second page drawing the same logo got a
     * freed bitmap back and the whole conversion died — on the last page, long after the real
     * mistake.
     *
     * This asserts the invariant rather than the crash on purpose: Robolectric does not enforce
     * recycled bitmaps (`compress` on one quietly succeeds here but throws on a device), so a test
     * that only converted the document would pass either way. `isRecycled` is tracked, so ask that.
     */
    @Test
    fun `the bitmap PDFBox owns is left usable for the next page that draws it`() {
        PDDocument.load(CorpusPdfs.repeatedLogo(pages = 3).inputStream()).use { document ->
            val page = document.getPage(0)
            val borrowed = assertNotNull(page.firstImage(), "the fixture should embed an image").image

            collector(page.mediaBox.height).collect(page, index = 0)

            assertFalse(borrowed.isRecycled, "the collector freed a bitmap PDFBox still owns")
        }
    }

    @Test
    fun `the same image drawn on every page is extracted every time`() {
        PDDocument.load(CorpusPdfs.repeatedLogo(pages = 3).inputStream()).use { document ->
            val budget = ImageBudget(PdfExtractionOptions(), warnings)

            val perPage = (0 until document.numberOfPages).map { index ->
                val page = document.getPage(index)
                PageImageCollector(page.mediaBox.height, PdfExtractionOptions(), budget)
                    .collect(page, index)
            }

            assertEquals(listOf(1, 1, 1), perPage.map { it.size }, "warnings: $warnings")
            assertTrue(perPage.all { it.single().bytes.isNotEmpty() })
            assertTrue(warnings.isEmpty(), "unexpected warnings: $warnings")
        }
    }

    @Test
    fun `an image is positioned from the top of the page, not the bottom`() {
        PDDocument.load(CorpusPdfs.repeatedLogo(pages = 1).inputStream()).use { document ->
            val page = document.getPage(0)

            val image = collector(page.mediaBox.height).collect(page, index = 0).single()

            // Drawn at y=700 with height 40 in PDF space on a 792pt page → 792 - 700 - 40 = 52.
            assertEquals(52f, image.y, 1f)
            assertEquals(80f, image.width, 1f)
        }
    }

    @Test
    fun `images below the size floor are ignored`() {
        PDDocument.load(CorpusPdfs.repeatedLogo(pages = 1).inputStream()).use { document ->
            val page = document.getPage(0)
            val options = PdfExtractionOptions(minImageDimensionPx = 4096)

            val images = PageImageCollector(page.mediaBox.height, options, ImageBudget(options, warnings))
                .collect(page, index = 0)

            assertTrue(images.isEmpty(), "a tiny decoration should not become a figure")
        }
    }

    private fun collector(pageHeight: Float) =
        PageImageCollector(pageHeight, PdfExtractionOptions(), ImageBudget(PdfExtractionOptions(), warnings))

    private fun PDPage.firstImage(): PDImageXObject? = resources?.let { resources ->
        resources.xObjectNames.firstNotNullOfOrNull { resources.getXObject(it) as? PDImageXObject }
    }
}
