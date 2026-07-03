package com.ryacub.melonsense.data.training

import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.data.local.PickHistoryDao
import com.ryacub.melonsense.data.local.TrainingCaptureDao
import com.ryacub.melonsense.data.local.artifacts

private const val TRAINING_MEDIA_RETENTION_DAYS = 14L
const val TRAINING_MEDIA_RETENTION_MILLIS: Long = TRAINING_MEDIA_RETENTION_DAYS * 24L * 60L * 60L * 1_000L

class TrainingRetentionRepository(
    private val trainingCaptureDao: TrainingCaptureDao,
    private val pickHistoryDao: PickHistoryDao,
    private val mediaStore: FileTrainingMediaStore,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) {
    suspend fun purgeExpired(nowMillis: Long): Int {
        val expiredCaptures = trainingCaptureDao.getExpired(nowMillis)
        val purgedCaptureCount =
            expiredCaptures.count { capture ->
                val artifacts = capture.artifacts()
                val deletedArtifacts = mediaStore.deleteArtifacts(artifacts) { true }
                if (deletedArtifacts.size != artifacts.size) {
                    return@count false
                }
                runInTransaction {
                    if (capture.exportStatus == TrainingExportStatus.Exported) {
                        trainingCaptureDao.clearArtifacts(capture.pickHistoryId)
                    } else {
                        trainingCaptureDao.updateExportStatus(
                            pickHistoryId = capture.pickHistoryId,
                            exportStatus = TrainingExportStatus.Expired.name,
                            exportedAtMillis = null,
                        )
                        pickHistoryDao.updateTrainingExportStatus(
                            pickId = capture.pickHistoryId,
                            trainingExportStatus = TrainingExportStatus.Expired.name,
                            trainingExportedAtMillis = null,
                        )
                    }
                }
                true
            }
        val retainedPaths =
            trainingCaptureDao
                .getAll()
                .flatMap { capture -> capture.artifacts() }
                .map { artifact -> artifact.path }
                .toSet()
        val cutoffMillis = nowMillis - TRAINING_MEDIA_RETENTION_MILLIS
        val purgedOrphanCount =
            mediaStore.deleteUntrackedArtifactsOlderThan(
                retainedPaths = retainedPaths,
                cutoffMillis = cutoffMillis,
            )
        return purgedCaptureCount + purgedOrphanCount
    }
}
