package dev.readana.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.WavyProgress
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.BookFormat
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus

/** The download queue, shown above the grid while anything is in flight or unacknowledged. */
@Composable
internal fun DownloadsSection(
    downloads: List<DownloadJob>,
    onOpenBook: (bookId: String) -> Unit,
    onRetry: (jobId: String) -> Unit,
    onCancel: (jobId: String) -> Unit,
    onDismiss: (jobId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (downloads.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.feature_library_downloads_title, downloads.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) ReadanaIcons.ExpandLess else ReadanaIcons.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) {
                        R.string.feature_library_downloads_collapse
                    } else {
                        R.string.feature_library_downloads_expand
                    }
                )
            )
        }
        if (expanded) {
            downloads.forEach { job ->
                DownloadRow(
                    job = job,
                    onOpenBook = onOpenBook,
                    onRetry = onRetry,
                    onCancel = onCancel,
                    onDismiss = onDismiss
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun DownloadRow(
    job: DownloadJob,
    onOpenBook: (bookId: String) -> Unit,
    onRetry: (jobId: String) -> Unit,
    onCancel: (jobId: String) -> Unit,
    onDismiss: (jobId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = job.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = job.statusLine(),
                style = MaterialTheme.typography.bodySmall,
                color = if (job.status == DownloadStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (job.isActive) {
                val fraction = job.fraction
                if (fraction != null) {
                    WavyProgress(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                } else {
                    WavyProgress(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        DownloadActions(
            job = job,
            onOpenBook = onOpenBook,
            onRetry = onRetry,
            onCancel = onCancel,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun DownloadActions(
    job: DownloadJob,
    onOpenBook: (bookId: String) -> Unit,
    onRetry: (jobId: String) -> Unit,
    onCancel: (jobId: String) -> Unit,
    onDismiss: (jobId: String) -> Unit
) {
    when (job.status) {
        DownloadStatus.Queued, DownloadStatus.Downloading, DownloadStatus.Converting ->
            IconButton(onClick = { onCancel(job.id) }) {
                Icon(
                    ReadanaIcons.Close,
                    contentDescription = stringResource(R.string.feature_library_download_cancel)
                )
            }

        DownloadStatus.Completed -> {
            job.bookId?.let { bookId ->
                IconButton(onClick = { onOpenBook(bookId) }) {
                    Icon(
                        ReadanaIcons.Library,
                        contentDescription = stringResource(R.string.feature_library_download_open)
                    )
                }
            }
            DismissButton(job.id, onDismiss)
        }

        DownloadStatus.Failed, DownloadStatus.Cancelled -> {
            IconButton(onClick = { onRetry(job.id) }) {
                Icon(
                    imageVector = if (job.needsBrowser) ReadanaIcons.Discover else ReadanaIcons.Refresh,
                    contentDescription = stringResource(
                        if (job.needsBrowser) {
                            R.string.feature_library_download_open_browser
                        } else {
                            R.string.feature_library_download_retry
                        }
                    )
                )
            }
            DismissButton(job.id, onDismiss)
        }
    }
}

@Composable
private fun DismissButton(jobId: String, onDismiss: (String) -> Unit) {
    IconButton(onClick = { onDismiss(jobId) }) {
        Icon(
            ReadanaIcons.Delete,
            contentDescription = stringResource(R.string.feature_library_download_dismiss)
        )
    }
}

private val DownloadJob.needsBrowser: Boolean
    get() = errorKind == DownloadErrorKind.RequiresBrowser || errorKind == DownloadErrorKind.QuotaExhausted

/** A queued job that already carries a reason is waiting out a backoff, not waiting to begin. */
@Composable
private fun DownloadJob.queuedLine(): String = errorMessage
    ?.let { stringResource(R.string.feature_library_download_retrying, it) }
    ?: stringResource(R.string.feature_library_download_queued)

@Composable
private fun DownloadJob.statusLine(): String = when (status) {
    DownloadStatus.Queued -> queuedLine()

    DownloadStatus.Downloading -> totalBytes?.let { total ->
        stringResource(
            R.string.feature_library_download_progress,
            megabytes(bytesDownloaded),
            megabytes(total)
        )
    } ?: stringResource(R.string.feature_library_download_progress_unknown, megabytes(bytesDownloaded))

    DownloadStatus.Converting -> stringResource(R.string.feature_library_download_converting)

    DownloadStatus.Completed -> stringResource(R.string.feature_library_download_completed)

    DownloadStatus.Cancelled -> stringResource(R.string.feature_library_download_cancelled)

    DownloadStatus.Failed -> when (errorKind) {
        DownloadErrorKind.QuotaExhausted -> stringResource(R.string.feature_library_download_quota)
        DownloadErrorKind.RequiresBrowser -> stringResource(R.string.feature_library_download_needs_browser)
        DownloadErrorKind.Unauthorized -> stringResource(R.string.feature_library_download_unauthorized)
        else -> errorMessage ?: stringResource(R.string.feature_library_download_failed)
    }
}

private const val BYTES_PER_MB = 1024.0 * 1024.0

private fun megabytes(bytes: Long): String = "%.1f".format(bytes / BYTES_PER_MB)

@Preview(showBackground = true, widthDp = 500)
@Composable
private fun DownloadsSectionPreview() {
    ReadanaTheme {
        DownloadsSection(
            downloads = listOf(
                previewJob(
                    "1",
                    "Pride and Prejudice",
                    DownloadStatus.Downloading,
                    bytes = 3_000_000,
                    total = 8_000_000
                ),
                previewJob("2", "Moby-Dick", DownloadStatus.Completed).copy(bookId = "book-2"),
                previewJob("3", "Frankenstein", DownloadStatus.Failed)
                    .copy(errorKind = DownloadErrorKind.QuotaExhausted)
            ),
            onOpenBook = {},
            onRetry = {},
            onCancel = {},
            onDismiss = {}
        )
    }
}

private fun previewJob(id: String, title: String, status: DownloadStatus, bytes: Long = 0, total: Long? = null) =
    DownloadJob(
        id = id,
        sourceId = "annas-archive",
        recordId = id,
        title = title,
        format = BookFormat.EPUB,
        status = status,
        bytesDownloaded = bytes,
        totalBytes = total
    )
