package com.ryacub.melonsense.domain.inference

import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.KnockCapture
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.PendingTrainingMedia
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.VisualScanResult

interface MelonInferenceEngine {
    val modelInfo: LocalModelInfo

    suspend fun scoreVisual(input: VisualInferenceInput): VisualScanResult

    suspend fun scoreAudio(input: AudioInferenceInput): AudioScanResult

    suspend fun assess(input: AssessmentInferenceInput): MelonAssessmentResult
}

data class LocalModelInfo(
    val id: String,
    val version: String,
    val implementation: LocalModelImplementation,
)

enum class LocalModelImplementation {
    PlaceholderHeuristic,
    TrainedModel,
}

data class VisualInferenceInput(
    val photoArtifact: TrainingMediaArtifact?,
)

data class AudioInferenceInput(
    val validKnocks: List<KnockCapture>,
    val audioArtifact: TrainingMediaArtifact?,
)

data class AssessmentInferenceInput(
    val visualScanResult: VisualScanResult?,
    val audioScanResult: AudioScanResult,
    val trainingMedia: PendingTrainingMedia?,
)
