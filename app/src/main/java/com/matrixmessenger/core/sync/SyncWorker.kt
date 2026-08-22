package com.matrixmessenger.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.matrixmessenger.data.matrix.MatrixClientManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Background worker for periodic Matrix incremental sync.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val matrixClientManager: MatrixClientManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker starting background sync")
        
        val session = matrixClientManager.getCurrentSession() ?: return Result.success()
        
        return try {
            session.syncService().startSync(fromForeground = false)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker failed")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "matrix_sync_work"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
        
        fun stopWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
