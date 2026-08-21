package dev.readana.core.model

import java.time.Instant

/** Progress of a download-and-import pipeline run. */
data class DownloadJob(
    val id: String,
    val sourceId: String,
    val recordId: String,
    val title: String,
    val format: BookFormat,
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
    val errorKind: DownloadErrorKind? = null,
    /** Set once the file has been imported; lets the queue offer "Open". */
    val bookId: String? = null,
    /** Record page to hand to the in-app browser when [errorKind] is [DownloadErrorKind.RequiresBrowser]. */
    val browserUrl: String? = null,
    val createdAt: Instant = Instant.EPOCH
) {
    val fraction: Float?
        get() = totalBytes?.takeIf {
            it > 0
        }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }

    val isActive: Boolean
        get() = status == DownloadStatus.Queued ||
            status == DownloadStatus.Downloading ||
            status == DownloadStatus.Converting
}

enum class DownloadStatus {
    Queued,
    Downloading,
    Converting,
    Completed,
    Failed,
    Cancelled
}

/**
 * Why a job failed. Quota exhaustion and "needs a browser" are reasons rather than states: nothing
 * resumes them on its own, but the queue renders a different action for each.
 */
enum class DownloadErrorKind {
    Network,
    Http,
    Unauthorized,
    QuotaExhausted,
    RequiresBrowser,
    Storage,
    Unexpected
}
