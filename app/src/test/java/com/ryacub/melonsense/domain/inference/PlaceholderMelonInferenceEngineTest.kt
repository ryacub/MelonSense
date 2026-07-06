package com.ryacub.melonsense.domain.inference

import com.ryacub.melonsense.domain.audio.KnockAudioAnalyzer
import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.PendingTrainingMedia
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderMelonInferenceEngineTest {
    private val engine = PlaceholderMelonInferenceEngine(nowMillis = { 1_788_200_000_000 })

    @Test
    fun scoreVisual_returnsPlaceholderResultWithCapturedArtifact() =
        runTest {
            val scoreVisual: suspend (VisualInferenceInput) -> VisualScanResult = engine::scoreVisual
            val photoArtifact = samplePhotoArtifact()

            val result =
                scoreVisual(
                    VisualInferenceInput(
                        photoArtifact = photoArtifact,
                    ),
                )

            assertEquals(72, result.score)
            assertEquals(64, result.confidencePercent)
            assertEquals(1_788_200_000_000, result.capturedAtMillis)
            assertSame(photoArtifact, result.photoArtifact)
            assertTrue(result.evidence.any { evidence -> evidence.contains("placeholder", ignoreCase = true) })
        }

    @Test
    fun scoreAudio_usesKnockHeuristicsBehindInferenceBoundary() =
        runTest {
            val scoreAudio: suspend (AudioInferenceInput) -> AudioScanResult = engine::scoreAudio
            val validKnocks =
                List(KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT) {
                    KnockAudioAnalyzer.analyzeSamples(
                        ShortArray(128) { index ->
                            if (index % 2 == 0) 2_200 else (-2_200).toShort()
                        },
                    )
                }
            val audioArtifact = sampleAudioArtifact()

            val result =
                scoreAudio(
                    AudioInferenceInput(
                        validKnocks = validKnocks,
                        audioArtifact = audioArtifact,
                    ),
                )

            assertEquals(KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT, result.validKnocks)
            assertEquals(1_788_200_000_000, result.capturedAtMillis)
            assertSame(audioArtifact, result.audioArtifact)
            assertTrue(result.score > 0)
            assertTrue(result.confidencePercent > 0)
        }

    @Test
    fun assess_combinesVisionAndAudioIntoStableResultLabel() =
        runTest {
            val assess: suspend (AssessmentInferenceInput) -> MelonAssessmentResult = engine::assess
            val trainingMedia =
                PendingTrainingMedia(
                    photoArtifact = samplePhotoArtifact(),
                    audioArtifact = sampleAudioArtifact(),
                    createdAtMillis = 2_000,
                    expiresAtMillis = 3_000,
                )

            val assessment =
                assess(
                    AssessmentInferenceInput(
                        visualScanResult =
                            VisualScanResult(
                                score = 86,
                                confidencePercent = 80,
                                capturedAtMillis = 1_000,
                                evidence = emptyList(),
                            ),
                        audioScanResult =
                            AudioScanResult(
                                score = 90,
                                confidencePercent = 88,
                                validKnocks = 3,
                                estimatedFrequencyHz = 145,
                                capturedAtMillis = 1_100,
                                evidence = emptyList(),
                            ),
                        trainingMedia = trainingMedia,
                    ),
                )

            assertEquals(ResultLabel.StrongPick, assessment.resultLabel)
            assertEquals(84, assessment.confidencePercent)
            assertEquals("Strong Pick", assessment.recommendation)
            assertSame(trainingMedia, assessment.trainingMedia)
        }

    @Test
    fun assess_audioOnlyDoesNotPenalizeMissingVisualScore() =
        runTest {
            val assessment =
                engine.assess(
                    AssessmentInferenceInput(
                        visualScanResult = null,
                        audioScanResult =
                            AudioScanResult(
                                score = 90,
                                confidencePercent = 88,
                                validKnocks = 3,
                                estimatedFrequencyHz = 145,
                                capturedAtMillis = 1_100,
                                evidence = emptyList(),
                            ),
                        trainingMedia = null,
                    ),
                )

            assertEquals(ResultLabel.StrongPick, assessment.resultLabel)
            assertEquals(88, assessment.confidencePercent)
        }

    @Test
    fun assess_derivesRecommendationFromCombinedScore() =
        runTest {
            val assessment =
                engine.assess(
                    AssessmentInferenceInput(
                        visualScanResult =
                            VisualScanResult(
                                score = 30,
                                confidencePercent = 50,
                                capturedAtMillis = 1_000,
                                evidence = emptyList(),
                            ),
                        audioScanResult =
                            AudioScanResult(
                                score = 42,
                                confidencePercent = 60,
                                validKnocks = 3,
                                estimatedFrequencyHz = 110,
                                capturedAtMillis = 1_100,
                                evidence = emptyList(),
                            ),
                        trainingMedia = null,
                    ),
                )

            assertEquals(ResultLabel.Skip, assessment.resultLabel)
            assertEquals("Skip", assessment.recommendation)
        }

    @Test
    fun assess_mapsCombinedScoreThresholdsToRecommendationCopy() =
        runTest {
            assertEquals("Strong Pick", assessmentFor(score = 85).recommendation)
            assertEquals("Good Candidate", assessmentFor(score = 70).recommendation)
            assertEquals("Maybe", assessmentFor(score = 55).recommendation)
            assertEquals("Skip", assessmentFor(score = 54).recommendation)
        }

    private fun samplePhotoArtifact(): TrainingMediaArtifact =
        TrainingMediaArtifact(
            kind = TrainingMediaKind.Photo,
            path = "/tmp/photo.jpg",
            mimeType = "image/jpeg",
            byteSize = 128,
            capturedAtMillis = 1_000,
            lastModifiedAtMillis = 1_100,
            width = 640,
            height = 480,
            sampleRateHz = null,
            durationMillis = null,
        )

    private fun sampleAudioArtifact(): TrainingMediaArtifact =
        TrainingMediaArtifact(
            kind = TrainingMediaKind.Audio,
            path = "/tmp/audio.pcm16.gz",
            mimeType = "audio/pcm16+gzip",
            byteSize = 96,
            capturedAtMillis = 1_200,
            lastModifiedAtMillis = 1_300,
            width = null,
            height = null,
            sampleRateHz = 44_100,
            durationMillis = 750,
        )

    private suspend fun assessmentFor(score: Int): MelonAssessmentResult =
        engine.assess(
            AssessmentInferenceInput(
                visualScanResult = null,
                audioScanResult =
                    AudioScanResult(
                        score = score,
                        confidencePercent = 80,
                        validKnocks = 3,
                        estimatedFrequencyHz = 140,
                        capturedAtMillis = 1_100,
                        evidence = emptyList(),
                    ),
                trainingMedia = null,
            ),
        )
}
