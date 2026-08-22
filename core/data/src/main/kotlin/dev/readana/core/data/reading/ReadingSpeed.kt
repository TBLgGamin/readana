package dev.readana.core.data.reading

import kotlin.math.abs

/** One stretch of reading, as the estimator sees it. Newest first when it arrives in a list. */
data class ReadingStretch(val startedAt: Long, val endedAt: Long, val positions: Int) {
    val millis: Long get() = endedAt - startedAt
}

/**
 * How fast this reader reads, in Readium positions per minute.
 *
 * Pure, and deliberately conservative: an estimate that is confidently wrong is worse than no
 * estimate, because "12 minutes left" is a promise. Where the evidence is thin the answer is null
 * and the reader is simply told nothing.
 */
object ReadingSpeed {
    /** Below this a "session" is a page turn on the way somewhere, not reading. */
    const val MIN_SESSION_MILLIS = 30_000L

    /** Above this the reader put the tablet down without closing the book. */
    const val MAX_SESSION_MILLIS = 4L * 60 * 60 * 1000

    /** Nobody reads this fast; the position delta came from a seek, not from reading. */
    const val MAX_POSITIONS_PER_MINUTE = 40.0

    /** Nor this slowly — the book was open on a table. */
    const val MIN_POSITIONS_PER_MINUTE = 0.2

    /** A book's own pace is only trusted once it has been read this many times. */
    const val SESSIONS_FOR_OWN_RATE = 3

    /** How much each older session counts against the one after it. */
    private const val DECAY = 0.8

    private const val MILLIS_PER_MINUTE = 60_000.0

    /**
     * Positions per minute from [stretches], newest first, or null when none of them say anything
     * usable. Recent reading counts for more: pace changes with the book and with the hour.
     */
    fun rate(stretches: List<ReadingStretch>): Double? {
        var weightedTotal = 0.0
        var weight = 1.0
        var weightSum = 0.0
        stretches.filter { it.isUsable() }.forEach { stretch ->
            weightedTotal += stretch.rate() * weight
            weightSum += weight
            weight *= DECAY
        }
        return if (weightSum == 0.0) null else weightedTotal / weightSum
    }

    /**
     * The rate to show for one book: its own once it has been read enough to have a pace, and the
     * reader's general pace until then. Null when neither is known.
     */
    fun rateForBook(bookStretches: List<ReadingStretch>, allStretches: List<ReadingStretch>): Double? {
        val usable = bookStretches.filter { it.isUsable() }
        return if (usable.size >= SESSIONS_FOR_OWN_RATE) rate(usable) else rate(allStretches)
    }

    /**
     * Milliseconds of reading left for [positions] at [rate], or null when either is unknown or
     * the reader is already there.
     */
    fun timeLeftMillis(positions: Int, rate: Double?): Long? {
        if (rate == null || rate <= 0.0 || positions <= 0) return null
        return ((positions / rate) * MILLIS_PER_MINUTE).toLong()
    }

    private fun ReadingStretch.isUsable(): Boolean = millis in MIN_SESSION_MILLIS..MAX_SESSION_MILLIS &&
        positions > 0 &&
        rate() in MIN_POSITIONS_PER_MINUTE..MAX_POSITIONS_PER_MINUTE

    private fun ReadingStretch.rate(): Double = abs(positions) / (millis / MILLIS_PER_MINUTE)
}
