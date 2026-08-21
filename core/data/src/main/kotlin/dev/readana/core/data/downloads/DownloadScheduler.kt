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
        // REPLACE, not KEEP: the queue already refuses a record it is downloading, so anything
        // that reaches here is meant to run. KEEP would silently drop it whenever older work for
        // the same record was still sitting in a retry backoff, leaving the new row waiting for a
        // worker that never came.
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(uniqueName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
        const val TAG = "readana-download"
    }
}
