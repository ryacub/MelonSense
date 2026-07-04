package com.ryacub.melonsense.domain.inference

import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVisualMelonInferenceEngineTest {
    @Test
    fun modelInfo_reportsTrainedLocalVisualModel() {
        val engine = localEngine(FakeVisualModelRunner())

        assertEquals("melonsense-visual-runtime-v0", engine.modelInfo.id)
        assertEquals("runtime-v0", engine.modelInfo.version)
        assertEquals(LocalModelImplementation.TrainedModel, engine.modelInfo.implementation)
    }

    @Test
    fun scoreVisual_usesLocalScorerWhenPhotoIsAvailable() =
        runTest {
            val engine =
                localEngine(
                    FakeVisualModelRunner(
                        predictions =
                            mapOf(
                                "ripeness" to VisualModelPrediction(label = "ripe", confidencePercent = 80),
                                "sweetness" to VisualModelPrediction(label = "sweet", confidencePercent = 60),
                            ),
                    ),
                )

            val result = engine.scoreVisual(VisualInferenceInput(photoArtifact = sampleEnginePhotoArtifact()))

            assertEquals(90, result.score)
            assertEquals(74, result.confidencePercent)
            assertTrue(result.evidence.contains("ripeness: ripe (80%)"))
        }

    @Test
    fun scoreVisual_fallsBackWhenPhotoIsMissing() =
        runTest {
            val engine = localEngine(FakeVisualModelRunner())

            val result = engine.scoreVisual(VisualInferenceInput(photoArtifact = null))

            assertEquals(72, result.score)
            assertEquals(listOf("Photo artifact unavailable", "Using visual fallback"), result.evidence)
        }

    @Test
    fun scoreVisual_fallsBackWhenLocalModelsFail() =
        runTest {
            val engine =
                localEngine(
                    FakeVisualModelRunner(
                        failedTrackIds = setOf("ripeness", "sweetness"),
                    ),
                )

            val result = engine.scoreVisual(VisualInferenceInput(photoArtifact = sampleEnginePhotoArtifact()))

            assertEquals(72, result.score)
            assertEquals(listOf("Local visual model unavailable", "Using visual fallback"), result.evidence)
        }

    @Test(expected = CancellationException::class)
    fun scoreVisual_preservesCancellation() =
        runTest {
            val engine =
                localEngine(
                    FakeVisualModelRunner(
                        cancelledTrackIds = setOf("ripeness"),
                    ),
                )

            engine.scoreVisual(VisualInferenceInput(photoArtifact = sampleEnginePhotoArtifact()))
        }

    private fun localEngine(runner: VisualModelRunner): LocalVisualMelonInferenceEngine =
        LocalVisualMelonInferenceEngine(
            visualModelScorer =
                LocalVisualModelScorer(
                    runner = runner,
                    nowMillis = { 1234L },
                ),
            fallbackEngine = PlaceholderMelonInferenceEngine(nowMillis = { 1234L }),
        )

    private class FakeVisualModelRunner(
        private val predictions: Map<String, VisualModelPrediction> = emptyMap(),
        private val failedTrackIds: Set<String> = emptySet(),
        private val cancelledTrackIds: Set<String> = emptySet(),
    ) : VisualModelRunner {
        override suspend fun predict(
            track: LocalVisualModelTrack,
            photoArtifact: TrainingMediaArtifact,
        ): VisualModelPrediction {
            if (track.id in cancelledTrackIds) {
                throw CancellationException("cancelled ${track.id}")
            }
            if (track.id in failedTrackIds) {
                error("failed ${track.id}")
            }
            return predictions[track.id] ?: VisualModelPrediction(label = "ripe", confidencePercent = 80)
        }
    }
}

private fun sampleEnginePhotoArtifact(): TrainingMediaArtifact =
    TrainingMediaArtifact(
        kind = TrainingMediaKind.Photo,
        path = "/tmp/melon.jpg",
        mimeType = "image/jpeg",
        byteSize = 100,
        capturedAtMillis = 1,
        lastModifiedAtMillis = 1,
        width = 96,
        height = 96,
        sampleRateHz = null,
        durationMillis = null,
    )
