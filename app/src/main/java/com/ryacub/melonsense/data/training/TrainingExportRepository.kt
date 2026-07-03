package com.ryacub.melonsense.data.training

import androidx.room.withTransaction
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.data.local.MelonSenseDatabase

class TrainingExportRepository(
    private val database: MelonSenseDatabase,
) {
    private val trainingCaptureDao = database.trainingCaptureDao()
    private val pickHistoryDao = database.pickHistoryDao()

    suspend fun markExported(
        pickHistoryId: Long,
        exportedAtMillis: Long,
    ) = database.withTransaction {
        trainingCaptureDao.updateExportStatus(
            pickHistoryId = pickHistoryId,
            exportStatus = TrainingExportStatus.Exported.name,
            exportedAtMillis = exportedAtMillis,
        )
        pickHistoryDao.updateTrainingExportStatus(
            pickId = pickHistoryId,
            trainingExportStatus = TrainingExportStatus.Exported.name,
            trainingExportedAtMillis = exportedAtMillis,
        )
    }
}
