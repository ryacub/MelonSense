package com.ryacub.melonsense.data.history

import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.PendingTrainingMedia
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HistoryRepositoryTest {
    @Test
    fun savePickedAssessment_createsPendingOutcome() =
        runTest {
            val repository = InMemoryHistoryRepository()

            val savedId = repository.savePickedAssessment(sampleAssessment())
            val savedItem = repository.getHistoryItem(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(PickHistoryStatus.PendingOutcome, savedItem.status)
            assertEquals(72, savedItem.visualScore)
            assertEquals(81, savedItem.audioScore)
            assertEquals(ResultLabel.GoodCandidate, savedItem.resultLabel)
            assertEquals(TrainingExportStatus.NotCaptured, savedItem.trainingExportStatus)
        }

    @Test
    fun savePickedAssessment_marksTrainingExportPendingWhenMediaIsAttached() =
        runTest {
            val repository = InMemoryHistoryRepository()

            val savedId = repository.savePickedAssessment(sampleAssessment(trainingMedia = sampleTrainingMedia()))
            val savedItem = repository.getHistoryItem(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(TrainingExportStatus.Pending, savedItem.trainingExportStatus)
        }

    @Test
    fun savePickedAssessment_usesTypedResultLabelInsteadOfDisplayText() =
        runTest {
            val repository = InMemoryHistoryRepository()

            val savedId =
                repository.savePickedAssessment(
                    sampleAssessment().copy(
                        recommendation = "Copy can change",
                        resultLabel = ResultLabel.Skip,
                    ),
                )
            val savedItem = repository.getHistoryItem(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(ResultLabel.Skip, savedItem.resultLabel)
        }

    @Test
    fun saveOutcome_marksItemRatedAndStoresSelectors() =
        runTest {
            val repository = InMemoryHistoryRepository()
            val savedId = repository.savePickedAssessment(sampleAssessment())

            repository.saveOutcome(
                pickId = savedId,
                resultLabel = ResultLabel.StrongPick,
                sweetness = SweetnessRating.VerySweet,
                texture = TextureRating.Crisp,
            )

            val savedItem = repository.getHistoryItem(savedId)
            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(PickHistoryStatus.Rated, savedItem.status)
            assertEquals(ResultLabel.StrongPick, savedItem.resultLabel)
            assertEquals(SweetnessRating.VerySweet, savedItem.sweetness)
            assertEquals(TextureRating.Crisp, savedItem.texture)
        }

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

    private fun sampleTrainingMedia(): PendingTrainingMedia =
        PendingTrainingMedia(
            photoArtifact =
                TrainingMediaArtifact(
                    kind = TrainingMediaKind.Photo,
                    path = "/tmp/photo.jpg",
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
                    path = "/tmp/audio.pcm16.gz",
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
