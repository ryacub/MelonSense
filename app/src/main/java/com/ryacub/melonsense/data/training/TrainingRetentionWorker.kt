package com.ryacub.melonsense.data.training

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ryacub.melonsense.data.local.MelonSenseDatabase
import java.util.concurrent.TimeUnit

private const val TRAINING_RETENTION_WORK_NAME = "training-retention-cleanup"

class TrainingRetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        runCatching {
            val database = MelonSenseDatabase.getInstance(applicationContext)
            TrainingRetentionRepository(
                trainingCaptureDao = database.trainingCaptureDao(),
                pickHistoryDao = database.pickHistoryDao(),
                mediaStore = FileTrainingMediaStore(applicationContext),
                runInTransaction = { block -> database.withTransaction { block() } },
            ).purgeExpired(System.currentTimeMillis())
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
}

fun scheduleTrainingRetentionWork(context: Context) {
    val request =
        PeriodicWorkRequestBuilder<TrainingRetentionWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        ).build()
    WorkManager
        .getInstance(context.applicationContext)
        .enqueueUniquePeriodicWork(
            TRAINING_RETENTION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
}
