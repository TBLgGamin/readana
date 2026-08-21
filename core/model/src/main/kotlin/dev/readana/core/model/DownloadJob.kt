package dev.readana.core.model

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
    val errorMessage: String? = null
) {
    val fraction: Float?
        get() = totalBytes?.takeIf {
            it > 0
        }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
}

enum class DownloadStatus {
    Queued,
    Downloading,
    Converting,
    Completed,
    Failed,
    Cancelled
}
