package com.ryacub.melonsense.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ryacub.melonsense.data.local.MelonSenseDatabase
import com.ryacub.melonsense.data.training.TrainingDatasetExportRepository
import com.ryacub.melonsense.data.training.TrainingExportRepository
import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.PendingTrainingMedia
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class RoomHistoryRepositoryInstrumentedTest {
    private val databaseName = "history-persistence-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        File(context.cacheDir, "dataset-exports").deleteRecursively()
        File(context.cacheDir, "blocked-dataset-exports").deleteRecursively()
        File(context.cacheDir, "transaction-failure-exports").deleteRecursively()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
        File(context.cacheDir, "dataset-exports").deleteRecursively()
        File(context.cacheDir, "blocked-dataset-exports").deleteRecursively()
        File(context.cacheDir, "transaction-failure-exports").deleteRecursively()
    }

    @Test
    fun savedPickSurvivesDatabaseReopen() =
        runBlocking {
            val firstDatabase = openDatabase()
            val savedId =
                RoomHistoryRepository(firstDatabase)
                    .savePickedAssessment(sampleAssessment())
            firstDatabase.close()

            val reopenedDatabase = openDatabase()
            val savedItem =
                RoomHistoryRepository(reopenedDatabase)
                    .getHistoryItem(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(PickHistoryStatus.PendingOutcome, savedItem.status)
            assertEquals(ResultLabel.GoodCandidate, savedItem.resultLabel)
            assertEquals(72, savedItem.visualScore)
            assertEquals(81, savedItem.audioScore)
            assertEquals(TrainingExportStatus.NotCaptured, savedItem.trainingExportStatus)
            reopenedDatabase.close()
        }

    @Test
    fun savedOutcomeSurvivesDatabaseReopen() =
        runBlocking {
            val firstDatabase = openDatabase()
            val firstRepository = RoomHistoryRepository(firstDatabase)
            val savedId = firstRepository.savePickedAssessment(sampleAssessment())

            firstRepository.saveOutcome(
                pickId = savedId,
                resultLabel = ResultLabel.StrongPick,
                sweetness = SweetnessRating.VerySweet,
                texture = TextureRating.Crisp,
            )
            firstDatabase.close()

            val reopenedDatabase = openDatabase()
            val savedItem =
                RoomHistoryRepository(reopenedDatabase)
                    .getHistoryItem(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(PickHistoryStatus.Rated, savedItem.status)
            assertEquals(ResultLabel.StrongPick, savedItem.resultLabel)
            assertEquals(SweetnessRating.VerySweet, savedItem.sweetness)
            assertEquals(TextureRating.Crisp, savedItem.texture)
            reopenedDatabase.close()
        }

    @Test
    fun savedTrainingMediaSurvivesDatabaseReopen() =
        runBlocking {
            val firstDatabase = openDatabase()
            val savedId =
                RoomHistoryRepository(firstDatabase).savePickedAssessment(
                    sampleAssessment(trainingMedia = sampleTrainingMedia()),
                )
            firstDatabase.close()

            val reopenedDatabase = openDatabase()
            val savedItem =
                RoomHistoryRepository(reopenedDatabase)
                    .getHistoryItem(savedId)
            val trainingCapture = reopenedDatabase.trainingCaptureDao().getByPickHistoryId(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(TrainingExportStatus.Pending, savedItem.trainingExportStatus)
            assertNotNull(trainingCapture)
            requireNotNull(trainingCapture)
            assertEquals(savedId, trainingCapture.pickHistoryId)
            assertEquals(TrainingExportStatus.Pending, trainingCapture.exportStatus)
            assertEquals("/tmp/photo.jpg", trainingCapture.photoPath)
            assertEquals("/tmp/audio.pcm16.gz", trainingCapture.audioPath)
            assertEquals(1_789_209_602_000, trainingCapture.expiresAtMillis)
            reopenedDatabase.close()
        }

    @Test
    fun trainingExportStateSurvivesDatabaseReopen() =
        runBlocking {
            val firstDatabase = openDatabase()
            val savedId =
                RoomHistoryRepository(firstDatabase).savePickedAssessment(
                    sampleAssessment(trainingMedia = sampleTrainingMedia()),
                )

            TrainingExportRepository(firstDatabase).markExported(
                pickHistoryId = savedId,
                exportedAtMillis = 1_788_100_000_000,
            )
            firstDatabase.close()

            val reopenedDatabase = openDatabase()
            val savedItem = RoomHistoryRepository(reopenedDatabase).getHistoryItem(savedId)
            val trainingCapture = reopenedDatabase.trainingCaptureDao().getByPickHistoryId(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(TrainingExportStatus.Exported, savedItem.trainingExportStatus)
            assertEquals(1_788_100_000_000, savedItem.trainingExportedAtMillis)
            assertNotNull(trainingCapture)
            requireNotNull(trainingCapture)
            assertEquals(TrainingExportStatus.Exported, trainingCapture.exportStatus)
            assertEquals(1_788_100_000_000, trainingCapture.exportedAtMillis)
            reopenedDatabase.close()
        }

    @Test
    fun datasetExportWritesBundleAndMarksRowsExported() =
        runBlocking {
            val photoFile = File(context.cacheDir, "dataset-photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val audioFile = File(context.cacheDir, "dataset-audio.pcm16.gz").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val database = openDatabase()
            val historyRepository = RoomHistoryRepository(database)
            val savedId =
                historyRepository.savePickedAssessment(
                    sampleAssessment(
                        trainingMedia =
                            sampleTrainingMedia(
                                photoPath = photoFile.absolutePath,
                                audioPath = audioFile.absolutePath,
                            ),
                    ),
                )
            historyRepository.saveOutcome(
                pickId = savedId,
                resultLabel = ResultLabel.StrongPick,
                sweetness = SweetnessRating.VerySweet,
                texture = TextureRating.Crisp,
            )

            val bundle =
                TrainingDatasetExportRepository(
                    database = database,
                    outputDirectory = File(context.cacheDir, "dataset-exports"),
                ).exportEligible(
                    nowMillis = 1_788_100_000_000,
                    createdAtMillis = 1_788_100_001_000,
                )

            assertEquals(1, bundle.entryCount)
            assertEquals(true, bundle.archiveFile.exists())
            ZipFile(bundle.archiveFile).use { archive ->
                assertNotNull(archive.getEntry("manifest.jsonl"))
            }
            database.close()

            val reopenedDatabase = openDatabase()
            val savedItem = RoomHistoryRepository(reopenedDatabase).getHistoryItem(savedId)
            val trainingCapture = reopenedDatabase.trainingCaptureDao().getByPickHistoryId(savedId)
            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(TrainingExportStatus.Exported, savedItem.trainingExportStatus)
            assertEquals(1_788_100_001_000, savedItem.trainingExportedAtMillis)
            assertNotNull(trainingCapture)
            requireNotNull(trainingCapture)
            assertEquals(TrainingExportStatus.Exported, trainingCapture.exportStatus)
            assertEquals(1_788_100_001_000, trainingCapture.exportedAtMillis)
            reopenedDatabase.close()
        }

    @Test
    fun datasetExportOutputFailureLeavesRowsPending() =
        runBlocking {
            val photoFile = File(context.cacheDir, "failed-dataset-photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val audioFile = File(context.cacheDir, "failed-dataset-audio.pcm16.gz").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val blockedOutput = File(context.cacheDir, "blocked-dataset-exports").apply { writeText("not a directory") }
            val database = openDatabase()
            val historyRepository = RoomHistoryRepository(database)
            val savedId =
                historyRepository.savePickedAssessment(
                    sampleAssessment(
                        trainingMedia =
                            sampleTrainingMedia(
                                photoPath = photoFile.absolutePath,
                                audioPath = audioFile.absolutePath,
                            ),
                    ),
                )
            historyRepository.saveOutcome(
                pickId = savedId,
                resultLabel = ResultLabel.GoodCandidate,
                sweetness = SweetnessRating.Sweet,
                texture = TextureRating.Crisp,
            )

            val failure =
                runCatching {
                    TrainingDatasetExportRepository(
                        database = database,
                        outputDirectory = blockedOutput,
                    ).exportEligible(
                        nowMillis = 1_788_100_000_000,
                        createdAtMillis = 1_788_100_001_000,
                    )
                }.exceptionOrNull()

            assertNotNull(failure)
            val savedItem = historyRepository.getHistoryItem(savedId)
            val trainingCapture = database.trainingCaptureDao().getByPickHistoryId(savedId)
            requireNotNull(savedItem)
            requireNotNull(trainingCapture)
            assertEquals(TrainingExportStatus.Pending, savedItem.trainingExportStatus)
            assertEquals(TrainingExportStatus.Pending, trainingCapture.exportStatus)
            database.close()
        }

    @Test
    fun datasetExportTransactionFailureRemovesPublishedOutputAndLeavesRowsPending() =
        runBlocking {
            val photoFile = File(context.cacheDir, "transaction-failure-photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val audioFile = File(context.cacheDir, "transaction-failure-audio.pcm16.gz").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val outputDirectory = File(context.cacheDir, "transaction-failure-exports")
            val database = openDatabase()
            val historyRepository = RoomHistoryRepository(database)
            val savedId =
                historyRepository.savePickedAssessment(
                    sampleAssessment(
                        trainingMedia =
                            sampleTrainingMedia(
                                photoPath = photoFile.absolutePath,
                                audioPath = audioFile.absolutePath,
                            ),
                    ),
                )
            historyRepository.saveOutcome(
                pickId = savedId,
                resultLabel = ResultLabel.GoodCandidate,
                sweetness = SweetnessRating.Sweet,
                texture = TextureRating.Crisp,
            )

            val failure =
                runCatching {
                    TrainingDatasetExportRepository(
                        database = database,
                        outputDirectory = outputDirectory,
                        runInTransaction = { throw java.io.IOException("forced transaction failure") },
                    ).exportEligible(
                        nowMillis = 1_788_100_000_000,
                        createdAtMillis = 1_788_100_001_000,
                    )
                }.exceptionOrNull()

            assertNotNull(failure)
            assertEquals(true, outputDirectory.listFiles().orEmpty().isEmpty())
            val savedItem = historyRepository.getHistoryItem(savedId)
            val trainingCapture = database.trainingCaptureDao().getByPickHistoryId(savedId)
            requireNotNull(savedItem)
            requireNotNull(trainingCapture)
            assertEquals(TrainingExportStatus.Pending, savedItem.trainingExportStatus)
            assertEquals(TrainingExportStatus.Pending, trainingCapture.exportStatus)
            database.close()
        }

    private fun openDatabase(): MelonSenseDatabase =
        Room
            .databaseBuilder(
                context,
                MelonSenseDatabase::class.java,
                databaseName,
            )
            .build()

    private fun sampleAssessment(trainingMedia: PendingTrainingMedia? = null): MelonAssessmentResult =
        MelonAssessmentResult(
            visualScanResult =
                VisualScanResult(
                    score = 72,
                    confidencePercent = 64,
                    capturedAtMillis = 1_788_000_000_000,
                    evidence = listOf("visual placeholder"),
                ),
            audioScanResult =
                AudioScanResult(
                    score = 81,
                    confidencePercent = 88,
                    validKnocks = 3,
                    estimatedFrequencyHz = 142,
                    capturedAtMillis = 1_788_000_001_000,
                    evidence = listOf("audio placeholder"),
                ),
            recommendation = "Good Candidate",
            resultLabel = ResultLabel.GoodCandidate,
            confidencePercent = 77,
            trainingMedia = trainingMedia,
        )

    private fun sampleTrainingMedia(
        photoPath: String = "/tmp/photo.jpg",
        audioPath: String = "/tmp/audio.pcm16.gz",
    ): PendingTrainingMedia =
        PendingTrainingMedia(
            photoArtifact =
                TrainingMediaArtifact(
                    kind = TrainingMediaKind.Photo,
                    path = photoPath,
                    mimeType = "image/jpeg",
                    byteSize = 100,
                    capturedAtMillis = 1_788_000_000_000,
                    lastModifiedAtMillis = 1_788_000_000_100,
                    width = 640,
                    height = 480,
                    sampleRateHz = null,
                    durationMillis = null,
                ),
            audioArtifact =
                TrainingMediaArtifact(
                    kind = TrainingMediaKind.Audio,
                    path = audioPath,
                    mimeType = "audio/pcm16+gzip",
                    byteSize = 80,
                    capturedAtMillis = 1_788_000_001_000,
                    lastModifiedAtMillis = 1_788_000_001_100,
                    width = null,
                    height = null,
                    sampleRateHz = 16_000,
                    durationMillis = 750,
                ),
            createdAtMillis = 1_788_000_002_000,
            expiresAtMillis = 1_789_209_602_000,
        )
}
