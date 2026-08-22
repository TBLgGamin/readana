package dev.readana.core.model

import java.time.Instant

/** Progress of a download-and-import pipeline run. */
data class DownloadJob(
    val id: String,
    val sourceId: String,
    val recordId: String,
    val title: String,
    val authors: List<String> = emptyList(),
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
    /** Cover art the source knew about, so the queue can show the book rather than an icon. */
    val coverUrl: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    /** When the row last moved. For a finished job that is when it finished. */
    val updatedAt: Instant = Instant.EPOCH
) {
    val fraction: Float?
        get() = totalBytes?.takeIf {
            it > 0
        }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }

    /** Moving under its own steam right now, so the queue draws it a live progress bar. */
    val isActive: Boolean
        get() = status == DownloadStatus.Queued ||
            status == DownloadStatus.Resolving ||
            status == DownloadStatus.Downloading ||
            status == DownloadStatus.Converting

    /** In the queue at all: running, or held part-way with its bytes kept. */
    val isPending: Boolean
        get() = isActive || status == DownloadStatus.Paused

    /** Failed or cancelled: the user has to decide what happens next. */
    val isStalled: Boolean
        get() = status == DownloadStatus.Failed || status == DownloadStatus.Cancelled

    /** How much is left to fetch, when the size is known. */
    val remainingBytes: Long?
        get() = totalBytes?.let { (it - bytesDownloaded).coerceAtLeast(0) }

    /**
     * Whether the browser is the only way this one finishes.
     *
     * An exhausted quota counts: the membership page is where a wait-list slot is claimed, so both
     * reasons lead to the same door.
     */
    val needsBrowser: Boolean
        get() = errorKind == DownloadErrorKind.RequiresBrowser ||
            errorKind == DownloadErrorKind.QuotaExhausted
}

enum class DownloadStatus {
    Queued,

    /** Asking the source where the file actually lives, which can mean a wait-list or a check. */
    Resolving,
    Downloading,
    Converting,

    /** Held by the user. The bytes already fetched are kept, so resuming picks up where it stopped. */
    Paused,
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
