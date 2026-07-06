package com.ryacub.melonsense.domain.inference

import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.CancellationException

class LocalVisualMelonInferenceEngine(
    private val catalog: LocalVisualModelCatalog = LocalVisualModelCatalog.fallback,
    private val visualModelScorer: LocalVisualModelScorer,
    private val fallbackEngine: MelonInferenceEngine = PlaceholderMelonInferenceEngine(),
) : MelonInferenceEngine {
    override val modelInfo: LocalModelInfo =
        LocalModelInfo(
            id = catalog.id,
            version = catalog.version,
            implementation = LocalModelImplementation.TrainedModel,
        )

    override suspend fun scoreVisual(input: VisualInferenceInput): VisualScanResult {
        val photoArtifact = input.photoArtifact
        if (photoArtifact == null) {
            return fallbackEngine.scoreVisual(input).copy(
                evidence = listOf("Photo artifact unavailable", "Using visual fallback"),
            )
        }

        return try {
            visualModelScorer.score(photoArtifact)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            fallbackEngine.scoreVisual(input).copy(
                evidence = listOf("Local visual model unavailable", "Using visual fallback"),
            )
        }
    }

    override suspend fun scoreAudio(input: AudioInferenceInput): AudioScanResult = fallbackEngine.scoreAudio(input)

    override suspend fun assess(input: AssessmentInferenceInput): MelonAssessmentResult = fallbackEngine.assess(input)
}
