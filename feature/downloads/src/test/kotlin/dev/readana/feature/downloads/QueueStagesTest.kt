package dev.readana.feature.downloads

import dev.readana.core.model.BookFormat
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import dev.readana.engine.pdf2epub.ConversionPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueStagesTest {
    @Test
    fun `an EPUB never shows a converting step`() {
        val stages = QueueStages.of(job(DownloadStatus.Downloading), conversion = null)

        assertEquals(3, stages.size)
        assertTrue(stages.none { it.labelRes == R.string.feature_downloads_stage_converting })
    }

    @Test
    fun `a PDF shows the converting step from the start`() {
        val stages = QueueStages.of(job(DownloadStatus.Queued, format = BookFormat.PDF), conversion = null)

        // Listed while still waiting, so the second wait is not a surprise once the bytes are down.
        assertEquals(R.string.feature_downloads_stage_converting, stages.last().labelRes)
        assertEquals(StageState.Waiting, stages.last().state)
    }

    @Test
    fun `the step in hand runs and the ones before it are done`() {
        val stages = QueueStages.of(job(DownloadStatus.Downloading), conversion = null)

        assertEquals(
            listOf(StageState.Done, StageState.Done, StageState.Running),
            stages.map { it.state }
        )
    }

    @Test
    fun `finding the file is its own step rather than part of downloading`() {
        val stages = QueueStages.of(job(DownloadStatus.Resolving), conversion = null)

        assertEquals(
            listOf(StageState.Done, StageState.Running, StageState.Waiting),
            stages.map { it.state }
        )
    }

    @Test
    fun `a paused job keeps its place without a step running`() {
        val stages = QueueStages.of(job(DownloadStatus.Paused, bytes = 500, total = 1_000), conversion = null)

        assertTrue(stages.none { it.state == StageState.Running })
        assertEquals(listOf(StageState.Done, StageState.Done, StageState.Waiting), stages.map { it.state })
    }

    @Test
    fun `a job that failed before finding the file stops at that step`() {
        val stages = QueueStages.of(job(DownloadStatus.Failed), conversion = null)

        assertEquals(StageState.Done, stages[0].state)
        assertEquals(StageState.Waiting, stages[1].state)
    }

    @Test
    fun `a job that failed mid transfer stops at downloading`() {
        val stages = QueueStages.of(job(DownloadStatus.Failed, bytes = 500), conversion = null)

        assertEquals(listOf(StageState.Done, StageState.Done, StageState.Waiting), stages.map { it.state })
    }

    @Test
    fun `the converting step borrows the conversion's own progress`() {
        val stages = QueueStages.of(
            job(DownloadStatus.Converting, format = BookFormat.PDF),
            conversion = ActiveConversion("book-1", "A book", currentPage = 30, totalPages = 60)
        )

        val converting = stages.last()
        assertEquals(StageState.Running, converting.state)
        assertEquals(0.5f, converting.fraction)
    }

    @Test
    fun `a conversion of its own runs through the engine's phases`() {
        val stages = QueueStages.of(
            ActiveConversion("book-1", "A book", phase = ConversionPhase.Assembling)
        )

        assertEquals(ConversionPhase.entries.size, stages.size)
        assertEquals(
            listOf(StageState.Done, StageState.Done, StageState.Running, StageState.Waiting),
            stages.map { it.state }
        )
    }

    @Test
    fun `only the page-counting phase can say how far it is`() {
        val stages = QueueStages.of(
            ActiveConversion("book-1", "A book", phase = ConversionPhase.Writing, currentPage = 5, totalPages = 10)
        )

        assertEquals(0.5f, stages.first().fraction)
        assertNull(stages.last().fraction)
    }

    private fun job(
        status: DownloadStatus,
        format: BookFormat = BookFormat.EPUB,
        bytes: Long = 0,
        total: Long? = null
    ) = DownloadJob(
        id = "job-1",
        sourceId = "annas-archive",
        recordId = "md5abc",
        title = "Pride and Prejudice",
        format = format,
        status = status,
        bytesDownloaded = bytes,
        totalBytes = total
    )
}
