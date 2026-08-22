package dev.readana.feature.downloads

import androidx.annotation.StringRes
import dev.readana.core.model.BookFormat
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import dev.readana.engine.pdf2epub.ConversionPhase

/** Where one step of the pipeline stands. */
enum class StageState { Done, Running, Waiting }

/** One step, as the card draws it. [fraction] is null while the step cannot say how far it is. */
data class QueueStage(@param:StringRes val labelRes: Int, val state: StageState, val fraction: Float? = null)

/**
 * The steps a piece of queued work goes through, and which one it is on.
 *
 * A single bar creeping along says a download is happening but nothing about what it is doing, and
 * "downloading" covered asking the source where the file lives, fetching it, and turning a PDF into
 * an EPUB - three waits of very different lengths, one of which is not a download at all. Naming
 * them makes a long pause legible instead of worrying.
 */
internal object QueueStages {
    /**
     * A download's steps: wait, find the file, fetch it, and convert it if it arrives as a PDF.
     *
     * The converting step is listed from the start for a PDF, so the reader can see there is
     * another wait coming rather than meeting it as a surprise once the bytes are down.
     */
    fun of(job: DownloadJob, conversion: ActiveConversion?): List<QueueStage> {
        val converts = job.format == BookFormat.PDF ||
            job.status == DownloadStatus.Converting ||
            conversion != null
        val kinds = buildList {
            add(Step.Queued)
            add(Step.Finding)
            add(Step.Downloading)
            if (converts) add(Step.Converting)
        }
        val reached = kinds.indexOf(job.reachedStep(converts)).coerceAtLeast(0)
        return kinds.mapIndexed { index, step ->
            QueueStage(
                labelRes = step.labelRes,
                state = stateAt(index, reached, job.isActive),
                fraction = when (step) {
                    Step.Downloading -> job.fraction
                    Step.Converting -> conversion?.fraction
                    else -> null
                }
            )
        }
    }

    /** A conversion's steps are the engine's own phases, which it reports as it goes. */
    fun of(conversion: ActiveConversion): List<QueueStage> {
        val phases = ConversionPhase.entries
        val reached = phases.indexOf(conversion.phase).coerceAtLeast(0)
        return phases.mapIndexed { index, phase ->
            QueueStage(
                labelRes = phase.labelRes(),
                state = stateAt(index, reached, running = true),
                fraction = conversion.fraction.takeIf { phase == ConversionPhase.Extracting }
            )
        }
    }

    /**
     * Steps before the one in hand are done; the one in hand is running only while the work is.
     *
     * A paused or failed job keeps its place in the list without a spinner on it, which is what
     * makes "stopped at converting" different from "converting".
     */
    private fun stateAt(index: Int, reached: Int, running: Boolean): StageState = when {
        index < reached -> StageState.Done
        index > reached -> StageState.Waiting
        running -> StageState.Running
        else -> StageState.Waiting
    }

    /**
     * Which step a job stands on.
     *
     * A job that stopped does not record where it got to, so it is placed by what it has to show
     * for itself: bytes on disk mean the fetch had started, and nothing means it never found the
     * file to begin with.
     */
    private fun DownloadJob.reachedStep(converts: Boolean): Step = when (status) {
        DownloadStatus.Queued -> Step.Queued

        DownloadStatus.Resolving -> Step.Finding

        DownloadStatus.Downloading, DownloadStatus.Paused -> Step.Downloading

        DownloadStatus.Converting -> Step.Converting

        DownloadStatus.Completed -> if (converts) Step.Converting else Step.Downloading

        DownloadStatus.Failed, DownloadStatus.Cancelled ->
            if (bytesDownloaded > 0) Step.Downloading else Step.Finding
    }

    private enum class Step(@param:StringRes val labelRes: Int) {
        Queued(R.string.feature_downloads_stage_queued),
        Finding(R.string.feature_downloads_stage_finding),
        Downloading(R.string.feature_downloads_stage_downloading),
        Converting(R.string.feature_downloads_stage_converting)
    }

    private fun ConversionPhase.labelRes(): Int = when (this) {
        ConversionPhase.Extracting -> R.string.feature_downloads_phase_extracting
        ConversionPhase.Reflowing -> R.string.feature_downloads_phase_reflowing
        ConversionPhase.Assembling -> R.string.feature_downloads_phase_assembling
        ConversionPhase.Writing -> R.string.feature_downloads_phase_writing
    }
}
