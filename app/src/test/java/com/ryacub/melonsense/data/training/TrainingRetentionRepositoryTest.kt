package com.ryacub.melonsense.data.training

import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.data.local.PickHistoryDao
import com.ryacub.melonsense.data.local.PickHistoryEntity
import com.ryacub.melonsense.data.local.TrainingCaptureDao
import com.ryacub.melonsense.data.local.TrainingCaptureEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrainingRetentionRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun purgeExpired_deletesArtifactsAndMarksTrainingStateExpired() =
        runTest {
            val audioFile = temporaryFolder.newFile("expired-audio.pcm16.gz")
            audioFile.writeBytes(byteArrayOf(1, 2, 3))
            val trainingDao =
                FakeTrainingCaptureDao(
                    TrainingCaptureEntity(
                        pickHistoryId = 7,
                        exportStatus = TrainingExportStatus.Pending,
                        exportedAtMillis = null,
                        createdAtMillis = 1_000,
                        expiresAtMillis = 2_000,
                        photoPath = null,
                        photoMimeType = null,
                        photoByteSize = null,
                        photoCapturedAtMillis = null,
                        photoLastModifiedAtMillis = null,
                        photoWidth = null,
                        photoHeight = null,
                        audioPath = audioFile.absolutePath,
                        audioMimeType = "audio/pcm16+gzip",
                        audioByteSize = audioFile.length(),
                        audioCapturedAtMillis = 1_000,
                        audioLastModifiedAtMillis = 1_000,
                        audioSampleRateHz = 16_000,
                        audioDurationMillis = 250,
                    ),
                )
            val pickHistoryDao = FakePickHistoryDao()
            val repository =
                TrainingRetentionRepository(
                    trainingCaptureDao = trainingDao,
                    pickHistoryDao = pickHistoryDao,
                    mediaStore = FileTrainingMediaStore(temporaryFolder.root),
                )

            val purgedCount = repository.purgeExpired(nowMillis = 3_000)

            assertEquals(1, purgedCount)
            assertFalse(audioFile.exists())
            assertEquals(TrainingExportStatus.Expired.name, trainingDao.updatedStatus)
            assertEquals(TrainingExportStatus.Expired.name, pickHistoryDao.updatedStatus)
        }

    private class FakeTrainingCaptureDao(
        private val expiredCapture: TrainingCaptureEntity,
    ) : TrainingCaptureDao {
        var updatedStatus: String? = null

        override suspend fun getByPickHistoryId(pickHistoryId: Long): TrainingCaptureEntity? = expiredCapture

        override suspend fun getAll(): List<TrainingCaptureEntity> = listOf(expiredCapture)

        override suspend fun getExpired(
            nowMillis: Long,
            expiredStatus: String,
        ): List<TrainingCaptureEntity> = listOf(expiredCapture)

        override suspend fun insert(entity: TrainingCaptureEntity) = Unit

        override suspend fun updateExportStatus(
            pickHistoryId: Long,
            exportStatus: String,
            exportedAtMillis: Long?,
        ) {
            updatedStatus = exportStatus
        }
    }

    private class FakePickHistoryDao : PickHistoryDao {
        var updatedStatus: String? = null

        override fun observeHistory(): Flow<List<PickHistoryEntity>> = emptyFlow()

        override suspend fun getById(pickId: Long): PickHistoryEntity? = null

        override suspend fun insert(entity: PickHistoryEntity): Long = 0

        override suspend fun updateOutcome(
            pickId: Long,
            status: String,
            resultLabel: String,
            sweetness: String,
            texture: String,
        ) = Unit

        override suspend fun updateTrainingExportStatus(
            pickId: Long,
            trainingExportStatus: String,
            trainingExportedAtMillis: Long?,
        ) {
            updatedStatus = trainingExportStatus
        }
    }
}
