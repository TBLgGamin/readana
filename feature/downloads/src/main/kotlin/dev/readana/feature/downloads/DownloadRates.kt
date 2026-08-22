package dev.readana.feature.downloads

import dev.readana.core.model.DownloadJob

/** How fast one job is going, and what that implies for the time left. */
data class DownloadRate(val bytesPerSecond: Long, val etaMillis: Long?)

/**
 * Works out transfer speed from the queue rows themselves.
 *
 * The download worker records byte counts, not rates, so the speed has to come from watching the
 * counts move. Successive readings are smoothed, because the worker writes progress in bursts and
 * the raw difference between two of them swings between "nothing at all" and "the whole burst at
 * once" — a figure that flickers is worse than none.
 *
 * Readings are dropped when a job stops moving, so a paused download does not go on claiming the
 * speed it had when it was running.
 */
class DownloadRates(private val clock: () -> Long = System::currentTimeMillis) {
    private val samples = mutableMapOf<String, Sample>()

    /** Folds the newest queue into the readings and returns the rate for each moving job. */
    fun update(jobs: List<DownloadJob>): Map<String, DownloadRate> {
        val now = clock()
        samples.keys.retainAll(jobs.mapTo(mutableSetOf()) { it.id })
        return buildMap {
            jobs.forEach { job -> rateFor(job, now)?.let { put(job.id, it) } }
        }
    }

    private fun rateFor(job: DownloadJob, now: Long): DownloadRate? {
        if (!job.isActive) {
            samples.remove(job.id)
            return null
        }
        val speed = sample(job, now).bytesPerSecond ?: return null
        return DownloadRate(
            bytesPerSecond = speed,
            etaMillis = job.remainingBytes
                ?.takeIf { speed > 0 }
                ?.let { it * MILLIS_PER_SECOND / speed }
        )
    }

    /**
     * The reading to work from, taking a new one only once enough time has passed.
     *
     * Sampling on every emission would keep resetting the clock to a few milliseconds ago, and the
     * interval that the speed is divided by would never grow large enough to mean anything.
     */
    private fun sample(job: DownloadJob, now: Long): Sample {
        val previous = samples[job.id]
            ?: return Sample(job.bytesDownloaded, now, null).also { samples[job.id] = it }
        val elapsed = now - previous.atMillis
        if (elapsed < MIN_INTERVAL_MILLIS) return previous
        val instant = ((job.bytesDownloaded - previous.bytes).coerceAtLeast(0) * MILLIS_PER_SECOND) / elapsed
        val speed = previous.bytesPerSecond
            ?.let { (SMOOTHING * instant + (1 - SMOOTHING) * it).toLong() }
            ?: instant
        return Sample(job.bytesDownloaded, now, speed).also { samples[job.id] = it }
    }

    private data class Sample(val bytes: Long, val atMillis: Long, val bytesPerSecond: Long?)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L

        /** Below this the arithmetic is dominated by rounding rather than by the transfer. */
        const val MIN_INTERVAL_MILLIS = 250L

        /** Weight on the newest reading. Low enough to ride out a stalled second. */
        const val SMOOTHING = 0.35
    }
}
