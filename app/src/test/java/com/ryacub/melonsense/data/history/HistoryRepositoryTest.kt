package com.ryacub.melonsense.data.history

import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
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

    private fun sampleAssessment(): MelonAssessmentResult =
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
        )
}
