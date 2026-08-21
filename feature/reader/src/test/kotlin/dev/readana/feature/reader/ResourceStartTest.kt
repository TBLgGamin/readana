package dev.readana.feature.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Reading to the end of a chapter in scrolling mode should open the next one at its first line,
 * not the same distance down the page.
 */
@RunWith(AndroidJUnit4::class)
class ResourceStartTest {
    @Test
    fun `crossing into the next chapter while scrolling snaps to its start`() {
        val previous = locator("chapter1.xhtml", progression = 1.0)
        val current = locator("chapter2.xhtml", progression = 0.42)

        assertTrue(shouldSnapToResourceStart(previous, current, scrolling = true))
    }

    @Test
    fun `paginated reading is left alone`() {
        val previous = locator("chapter1.xhtml", progression = 1.0)
        val current = locator("chapter2.xhtml", progression = 0.42)

        assertFalse(shouldSnapToResourceStart(previous, current, scrolling = false))
    }

    @Test
    fun `a chapter that already opens at its start needs no correction`() {
        val previous = locator("chapter1.xhtml", progression = 1.0)
        val current = locator("chapter2.xhtml", progression = 0.0)

        assertFalse(shouldSnapToResourceStart(previous, current, scrolling = true))
    }

    @Test
    fun `scrolling within one chapter is not a boundary`() {
        val previous = locator("chapter1.xhtml", progression = 0.2)
        val current = locator("chapter1.xhtml", progression = 0.4)

        assertFalse(shouldSnapToResourceStart(previous, current, scrolling = true))
    }

    @Test
    fun `jumping from the contents list lands where it was asked to`() {
        // Half way through chapter 1, the reader picks chapter 7 from the drawer.
        val previous = locator("chapter1.xhtml", progression = 0.5)
        val current = locator("chapter7.xhtml", progression = 0.0)

        assertFalse(shouldSnapToResourceStart(previous, current, scrolling = true))
    }

    @Test
    fun `a search hit deep in another chapter is not dragged to the top`() {
        val previous = locator("chapter1.xhtml", progression = 0.3)
        val current = locator("chapter9.xhtml", progression = 0.77)

        assertFalse(shouldSnapToResourceStart(previous, current, scrolling = true))
    }

    @Test
    fun `the first locator of a session is not a boundary`() {
        assertFalse(
            shouldSnapToResourceStart(previous = null, current = locator("chapter1.xhtml", 0.5), scrolling = true)
        )
    }

    @Test
    fun `snapping keeps the resource but drops the position within it`() {
        val current = locator("chapter2.xhtml", progression = 0.42)

        val snapped = current.atResourceStart()

        assertEquals(current.href, snapped.href)
        assertEquals(0.0, snapped.locations.progression)
    }

    private fun locator(href: String, progression: Double) = Locator(
        href = Url(href)!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(progression = progression)
    )
}
