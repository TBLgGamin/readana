package dev.readana.core.data.downloads

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.readana.core.data.repository.DownloadsRepository
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadStatus
import timber.log.Timber

/**
 * Runs one queued download. Deliberately thin: everything worth testing lives in [BookDownloader].
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloader: BookDownloader,
    private val downloadsRepository: DownloadsRepository,
    private val notifications: DownloadNotifications
) : CoroutineWorker(context, params) {
    private val jobId: String? get() = inputData.getString(KEY_JOB_ID)

    override suspend fun doWork(): Result {
        val id = jobId ?: return Result.failure()
        // Nothing below may leave the row sitting at "queued" with no worker behind it: that is
        // indistinguishable from a download that has not started yet, and waits forever.
        return try {
            download(id)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.e(e, "Download %s fell over", id)
            downloadsRepository.setStatus(
                id,
                DownloadStatus.Failed,
                DownloadErrorKind.Unexpected,
                e.message ?: e::class.simpleName.orEmpty()
            )
            Result.failure()
        }
    }

    private suspend fun download(id: String): Result {
        val title = downloadsRepository.getJob(id)?.title.orEmpty()
        runForeground(title, 0, null)

        return when (
            downloader.run(id, isRetryAttempt = runAttemptCount > 0) { bytes, total ->
                downloadsRepository.updateProgress(id, bytes, total)
                runForeground(title, bytes, total)
            }
        ) {
            BookDownloader.Outcome.Success -> Result.success()

            BookDownloader.Outcome.Failure -> Result.failure()

            BookDownloader.Outcome.Retry ->
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                    downloadsRepository.setStatus(
                        id,
                        DownloadStatus.Failed,
                        DownloadErrorKind.Network,
                        "Gave up after $MAX_ATTEMPTS attempts"
                    )
                    Result.failure()
                } else {
                    // Back to queued, but with the reason kept: waiting out a backoff looks exactly
                    // like never having started unless the queue can say which one it is.
                    downloadsRepository.setStatus(
                        id,
                        DownloadStatus.Queued,
                        DownloadErrorKind.Network,
                        "Attempt ${runAttemptCount + 1} of $MAX_ATTEMPTS did not get through"
                    )
                    Result.retry()
                }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(
        downloadsRepository.getJob(jobId.orEmpty())?.title.orEmpty(),
        0,
        null
    )

    private fun foregroundInfo(title: String, bytes: Long, total: Long?): ForegroundInfo = ForegroundInfo(
        NOTIFICATION_ID,
        notifications.build(id, title, bytes, total),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    )

    /**
     * A retried worker can start while the app is in the background, where Android 12+ refuses to
     * promote it. The transfer still runs; HyperOS may then cut it short, which is what the
     * battery-restriction guidance in Settings is for.
     */
    private suspend fun runForeground(title: String, bytes: Long, total: Long?) {
        try {
            setForeground(foregroundInfo(title, bytes, total))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Anything here is survivable: the transfer runs either way, and taking the download
            // down with it would strand the row.
            Timber.d(e, "Continuing without a foreground service")
        }
    }

    companion object {
        const val KEY_JOB_ID = "jobId"
        private const val MAX_ATTEMPTS = 5
        private const val NOTIFICATION_ID = 4001
    }
}
