package dev.readana.core.data.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a job to the background runner. A seam, so the queue logic can be tested without
 * WorkManager.
 */
interface DownloadScheduler {
    fun schedule(jobId: String, uniqueName: String)

    fun cancel(uniqueName: String)
}

@Singleton
class WorkManagerDownloadScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    DownloadScheduler {
    override fun schedule(jobId: String, uniqueName: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_JOB_ID to jobId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        // KEEP only blocks while the same record is still enqueued or running, which is exactly the
        // duplicate rule we want; finished work can be queued again by a retry.
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(uniqueName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
        const val TAG = "readana-download"
    }
}
