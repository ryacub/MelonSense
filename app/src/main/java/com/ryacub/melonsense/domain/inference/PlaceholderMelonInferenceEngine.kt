package com.ryacub.melonsense.domain.inference

import com.ryacub.melonsense.domain.audio.KnockAudioAnalyzer
import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaceholderMelonInferenceEngine(
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : MelonInferenceEngine {
    override val modelInfo: LocalModelInfo =
        LocalModelInfo(
            id = "melonsense-placeholder",
            version = "1",
            implementation = LocalModelImplementation.PlaceholderHeuristic,
        )

    override suspend fun scoreVisual(input: VisualInferenceInput): VisualScanResult =
        withContext(inferenceDispatcher) {
            VisualScanResult(
                score = 72,
                confidencePercent = 64,
                capturedAtMillis = nowMillis(),
                evidence =
                    listOf(
                        "Centered frame captured",
                        "Shape and surface scoring placeholder",
                        "Ready for knock-test refinement",
                    ),
                photoArtifact = input.photoArtifact,
            )
        }

    override suspend fun scoreAudio(input: AudioInferenceInput): AudioScanResult =
        withContext(inferenceDispatcher) {
            KnockAudioAnalyzer
                .buildAudioScanResult(input.validKnocks)
                .copy(
                    capturedAtMillis = nowMillis(),
                    audioArtifact = input.audioArtifact,
                )
        }

    override suspend fun assess(input: AssessmentInferenceInput): MelonAssessmentResult =
        withContext(inferenceDispatcher) {
            val combinedScore =
                weightedScore(
                    visualValue = input.visualScanResult?.score,
                    audioValue = input.audioScanResult.score,
                )
            val finalConfidence =
                weightedScore(
                    visualValue = input.visualScanResult?.confidencePercent,
                    audioValue = input.audioScanResult.confidencePercent,
                )

            MelonAssessmentResult(
                visualScanResult = input.visualScanResult,
                audioScanResult = input.audioScanResult,
                recommendation = input.recommendation,
                resultLabel = combinedScore.toResultLabel(),
                confidencePercent = finalConfidence,
                trainingMedia = input.trainingMedia,
            )
        }

    private fun weightedScore(
        visualValue: Int?,
        audioValue: Int,
    ): Int =
        if (visualValue == null) {
            audioValue
        } else {
            ((visualValue * 0.45f) + (audioValue * 0.55f)).toInt()
        }.coerceIn(0, 100)

    private fun Int.toResultLabel(): ResultLabel =
        when {
            this >= 85 -> ResultLabel.StrongPick
            this >= 70 -> ResultLabel.GoodCandidate
            this >= 55 -> ResultLabel.Maybe
            else -> ResultLabel.Skip
        }
}
