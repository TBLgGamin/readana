package dev.readana.core.data.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

class ReadingSpeedTest {
    @Test
    fun `reads a rate off one honest session`() {
        // Ten positions in ten minutes is one a minute.
        val rate = ReadingSpeed.rate(listOf(stretch(minutes = 10, positions = 10)))

        assertEquals(1.0, rate)
    }

    @Test
    fun `knows nothing until something has been read`() {
        assertNull(ReadingSpeed.rate(emptyList()))
    }

    @Test
    fun `ignores a glance that was over before it started`() {
        assertNull(ReadingSpeed.rate(listOf(ReadingStretch(0, 5_000, positions = 2))))
    }

    @Test
    fun `ignores a book left open on the table`() {
        assertNull(ReadingSpeed.rate(listOf(stretch(minutes = 10 * 60, positions = 3))))
    }

    @Test
    fun `ignores a jump through the book`() {
        // Three hundred positions in a minute is a drag of the slider, not reading.
        assertNull(ReadingSpeed.rate(listOf(stretch(minutes = 1, positions = 300))))
    }

    @Test
    fun `ignores going backwards`() {
        assertNull(ReadingSpeed.rate(listOf(stretch(minutes = 10, positions = -10))))
    }

    @Test
    fun `weights recent reading over old reading`() {
        val slowThenFast = ReadingSpeed.rate(
            listOf(
                stretch(minutes = 10, positions = 20),
                stretch(minutes = 10, positions = 10)
            )
        )
        assertNotNull(slowThenFast)

        // The newest session says 2/min and the older one 1/min; the answer leans on the newest.
        assertTrue(slowThenFast > 1.5, "expected the newest session to dominate, got $slowThenFast")
        assertTrue(slowThenFast < 2.0)
    }

    @Test
    fun `uses the book's own pace once it has one`() {
        val slowBook = List(ReadingSpeed.SESSIONS_FOR_OWN_RATE) { stretch(minutes = 10, positions = 5) }
        val briskEverywhere = List(5) { stretch(minutes = 10, positions = 30) }

        assertEquals(0.5, ReadingSpeed.rateForBook(slowBook, briskEverywhere))
    }

    @Test
    fun `falls back to the reader's general pace for a book barely started`() {
        val onceOpened = listOf(stretch(minutes = 10, positions = 5))
        val briskEverywhere = List(5) { stretch(minutes = 10, positions = 30) }

        assertEquals(3.0, ReadingSpeed.rateForBook(onceOpened, briskEverywhere))
    }

    @Test
    fun `turns a rate into a time`() {
        assertEquals(10 * MINUTE, ReadingSpeed.timeLeftMillis(positions = 20, rate = 2.0))
    }

    @Test
    fun `promises nothing it cannot know`() {
        assertNull(ReadingSpeed.timeLeftMillis(positions = 20, rate = null))
        assertNull(ReadingSpeed.timeLeftMillis(positions = 20, rate = 0.0))
        assertNull(ReadingSpeed.timeLeftMillis(positions = 0, rate = 2.0))
    }

    private fun stretch(minutes: Long, positions: Int) =
        ReadingStretch(startedAt = 0, endedAt = minutes * MINUTE, positions = positions)
}
