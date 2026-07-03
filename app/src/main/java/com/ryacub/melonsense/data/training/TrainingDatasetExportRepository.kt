package com.ryacub.melonsense.data.training

import androidx.room.withTransaction
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.data.local.MelonSenseDatabase
import com.ryacub.melonsense.data.local.toHistoryItem
import java.io.File

class TrainingDatasetExportRepository(
    private val database: MelonSenseDatabase,
    private val outputDirectory: File,
) {
    private val pickHistoryDao = database.pickHistoryDao()
    private val trainingCaptureDao = database.trainingCaptureDao()

    suspend fun getQueue(nowMillis: Long): List<TrainingQueueItem> =
        TrainingDatasetExporter.buildQueue(
            historyItems = pickHistoryDao.getAll().map { entity -> entity.toHistoryItem() },
            captures = trainingCaptureDao.getAll(),
            nowMillis = nowMillis,
        )

    suspend fun exportEligible(
        nowMillis: Long,
        createdAtMillis: Long,
    ): TrainingDatasetBundle {
        val eligibleItems = getQueue(nowMillis).filter { item -> item.isEligible }
        val bundle =
            TrainingDatasetExporter.writeBundle(
                eligibleItems = eligibleItems,
                outputDirectory = outputDirectory,
                createdAtMillis = createdAtMillis,
            )
        database.withTransaction {
            eligibleItems.forEach { item ->
                val pickHistoryId = item.historyItem.id
                trainingCaptureDao.updateExportStatus(
                    pickHistoryId = pickHistoryId,
                    exportStatus = TrainingExportStatus.Exported.name,
                    exportedAtMillis = createdAtMillis,
                )
                pickHistoryDao.updateTrainingExportStatus(
                    pickId = pickHistoryId,
                    trainingExportStatus = TrainingExportStatus.Exported.name,
                    trainingExportedAtMillis = createdAtMillis,
                )
            }
        }
        return bundle
    }
}
