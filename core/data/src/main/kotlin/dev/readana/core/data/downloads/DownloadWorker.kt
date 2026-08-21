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
                    downloadsRepository.setStatus(id, DownloadStatus.Queued)
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
        } catch (e: IllegalStateException) {
            Timber.d(e, "Continuing without a foreground service")
        }
    }

    companion object {
        const val KEY_JOB_ID = "jobId"
        private const val MAX_ATTEMPTS = 5
        private const val NOTIFICATION_ID = 4001
    }
}
