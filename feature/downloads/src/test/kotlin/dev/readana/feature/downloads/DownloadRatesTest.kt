package dev.readana.feature.downloads

import dev.readana.core.model.BookFormat
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadRatesTest {
    private var now = 0L
    private val rates = DownloadRates { now }

    @Test
    fun `the first reading cannot know a speed yet`() {
        assertTrue(rates.update(listOf(job(bytes = 1_000))).isEmpty())
    }

    @Test
    fun `two readings a second apart give the bytes between them`() {
        rates.update(listOf(job(bytes = 1_000)))
        now = 1_000

        val rate = rates.update(listOf(job(bytes = 3_000))).getValue("job-1")

        assertEquals(2_000, rate.bytesPerSecond)
    }

    @Test
    fun `the time left follows from the speed and what is left`() {
        rates.update(listOf(job(bytes = 1_000)))
        now = 1_000

        val rate = rates.update(listOf(job(bytes = 3_000, total = 9_000))).getValue("job-1")

        assertEquals(3_000, rate.etaMillis)
    }

    @Test
    fun `readings closer together than the sampling interval are ignored`() {
        rates.update(listOf(job(bytes = 1_000)))
        now = 1_000
        rates.update(listOf(job(bytes = 3_000)))

        // A burst arriving 50ms later must not be divided by 50ms.
        now = 1_050
        val rate = rates.update(listOf(job(bytes = 3_500))).getValue("job-1")

        assertEquals(2_000, rate.bytesPerSecond)
    }

    @Test
    fun `a stalled second drags the figure down rather than zeroing it`() {
        rates.update(listOf(job(bytes = 1_000)))
        now = 1_000
        rates.update(listOf(job(bytes = 3_000)))
        now = 2_000

        val rate = rates.update(listOf(job(bytes = 3_000))).getValue("job-1")

        assertTrue(rate.bytesPerSecond in 1..1_999)
    }

    @Test
    fun `a job that stops moving stops reporting a speed`() {
        rates.update(listOf(job(bytes = 1_000)))
        now = 1_000
        rates.update(listOf(job(bytes = 3_000)))

        assertNull(rates.update(listOf(job(bytes = 3_000, status = DownloadStatus.Paused)))["job-1"])
    }

    @Test
    fun `resuming starts the readings over rather than reusing stale ones`() {
        rates.update(listOf(job(bytes = 1_000)))
        now = 1_000
        rates.update(listOf(job(bytes = 3_000)))
        now = 2_000
        rates.update(listOf(job(bytes = 3_000, status = DownloadStatus.Paused)))
        now = 60_000

        // The first reading after the pause is a fresh baseline, so no speed is claimed from the
        // minute the job spent held.
        assertNull(rates.update(listOf(job(bytes = 3_000)))["job-1"])
    }

    private fun job(bytes: Long, total: Long? = null, status: DownloadStatus = DownloadStatus.Downloading) =
        DownloadJob(
            id = "job-1",
            sourceId = "annas-archive",
            recordId = "md5abc",
            title = "Pride and Prejudice",
            format = BookFormat.EPUB,
            status = status,
            bytesDownloaded = bytes,
            totalBytes = total
        )
}
