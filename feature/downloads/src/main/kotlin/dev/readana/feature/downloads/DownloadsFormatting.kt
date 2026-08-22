package dev.readana.feature.downloads

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import java.time.Instant

private const val BYTES_PER_KIB = 1024.0
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L

/**
 * A byte count at a scale a person reads at a glance: kilobytes below a megabyte, then megabytes,
 * then gigabytes once a book somehow gets that large.
 */
internal fun humanSize(bytes: Long): String {
    val kib = bytes / BYTES_PER_KIB
    val mib = kib / BYTES_PER_KIB
    val gib = mib / BYTES_PER_KIB
    return when {
        gib >= 1 -> "%.2f GB".format(gib)
        mib >= 1 -> "%.1f MB".format(mib)
        else -> "%.0f KB".format(kib)
    }
}

/** A span of time, rounded to whatever unit makes it shortest. */
@Composable
internal fun humanDuration(millis: Long): String {
    val seconds = millis / MILLIS_PER_SECOND
    val minutes = seconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        hours > 0 -> stringResource(
            R.string.feature_downloads_duration_hours,
            hours,
            minutes % MINUTES_PER_HOUR
        )

        minutes > 0 -> stringResource(
            R.string.feature_downloads_duration_minutes,
            minutes,
            seconds % SECONDS_PER_MINUTE
        )

        else -> stringResource(R.string.feature_downloads_duration_seconds, seconds)
    }
}

/** "3 minutes ago", in the system's wording and the reader's language. */
internal fun relativeTime(instant: Instant, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(
        instant.toEpochMilli(),
        now,
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

/**
 * What the row says about itself under the title.
 *
 * A queued job that already carries a reason is waiting out a backoff rather than waiting to
 * begin, and saying so is the difference between "stuck" and "trying".
 */
@Composable
internal fun DownloadJob.statusLine(): String = when (status) {
    DownloadStatus.Queued ->
        errorMessage
            ?.let { stringResource(R.string.feature_downloads_status_retrying, it) }
            ?: stringResource(R.string.feature_downloads_status_queued)

    DownloadStatus.Resolving -> stringResource(R.string.feature_downloads_status_resolving)

    DownloadStatus.Downloading -> totalBytes?.let { total ->
        stringResource(
            R.string.feature_downloads_status_progress,
            humanSize(bytesDownloaded),
            humanSize(total)
        )
    } ?: stringResource(
        R.string.feature_downloads_status_progress_unknown,
        humanSize(bytesDownloaded)
    )

    DownloadStatus.Converting -> stringResource(R.string.feature_downloads_status_converting)

    DownloadStatus.Paused -> if (bytesDownloaded > 0) {
        stringResource(R.string.feature_downloads_status_paused, humanSize(bytesDownloaded))
    } else {
        stringResource(R.string.feature_downloads_status_paused_unknown)
    }

    DownloadStatus.Completed -> stringResource(R.string.feature_downloads_status_completed)

    DownloadStatus.Cancelled -> stringResource(R.string.feature_downloads_status_cancelled)

    DownloadStatus.Failed -> failureLine()
}

/**
 * Why it stopped, in words the reader can act on.
 *
 * The three reasons with a door out of them get their own wording; anything else falls back to
 * whatever the source said, which is more use than a generic apology.
 */
@Composable
private fun DownloadJob.failureLine(): String = when (errorKind) {
    DownloadErrorKind.QuotaExhausted -> stringResource(R.string.feature_downloads_status_quota)
    DownloadErrorKind.RequiresBrowser -> stringResource(R.string.feature_downloads_status_needs_browser)
    DownloadErrorKind.Unauthorized -> stringResource(R.string.feature_downloads_status_unauthorized)
    else -> errorMessage ?: stringResource(R.string.feature_downloads_status_failed)
}

/** Where the book came from and what it is, on one line under the status. */
@Composable
internal fun DownloadJob.metaLine(): String = listOfNotNull(
    authors.takeIf { it.isNotEmpty() }?.joinToString(", "),
    format.extension.uppercase(),
    totalBytes?.let(::humanSize),
    when {
        status == DownloadStatus.Completed -> stringResource(
            R.string.feature_downloads_took,
            humanDuration(updatedAt.toEpochMilli() - createdAt.toEpochMilli())
        )

        !isPending -> stringResource(R.string.feature_downloads_finished, relativeTime(updatedAt))

        else -> null
    }
).joinToString(" · ")
