package com.ryacub.melonsense.domain.inference

import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

interface VisualModelRunner {
    suspend fun predict(
        track: LocalVisualModelTrack,
        photoArtifact: TrainingMediaArtifact,
    ): VisualModelPrediction

    suspend fun predictAll(
        tracks: List<LocalVisualModelTrack>,
        photoArtifact: TrainingMediaArtifact,
    ): List<VisualModelTrackPrediction> =
        tracks.map { track ->
            try {
                VisualModelTrackPrediction(
                    track = track,
                    prediction = predict(track, photoArtifact),
                    unavailable = false,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                VisualModelTrackPrediction(
                    track = track,
                    prediction = null,
                    unavailable = true,
                )
            }
        }
}

data class VisualModelPrediction(
    val label: String,
    val confidencePercent: Int,
)

data class VisualModelTrackPrediction(
    val track: LocalVisualModelTrack,
    val prediction: VisualModelPrediction?,
    val unavailable: Boolean,
)

class LocalVisualModelScorer(
    private val runner: VisualModelRunner,
    private val catalog: LocalVisualModelCatalog = LocalVisualModelCatalog.fallback,
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun score(photoArtifact: TrainingMediaArtifact): VisualScanResult =
        withContext(inferenceDispatcher) {
            val outcomes = runner.predictAll(catalog.tracks, photoArtifact)
            val successfulPredictions =
                outcomes.mapNotNull { outcome ->
                    outcome.prediction?.let { prediction -> outcome.track to prediction }
                }
            require(successfulPredictions.isNotEmpty()) {
                "No local visual model predictions succeeded"
            }

            val availableWeight = successfulPredictions.sumOf { (track, _) -> track.weight.toDouble() }.toFloat()
            val score =
                successfulPredictions
                    .sumOf { (track, prediction) ->
                        prediction.toPickScore(track).toDouble() * (track.weight / availableWeight)
                    }.roundToInt()
                    .coerceIn(0, 100)
            val confidence =
                successfulPredictions
                    .sumOf { (track, prediction) ->
                        prediction.confidencePercent.toDouble() * (track.weight / availableWeight)
                    }.roundToInt()
                    .coerceIn(0, 100)

            VisualScanResult(
                score = score,
                confidencePercent = confidence,
                capturedAtMillis = nowMillis(),
                evidence =
                    successfulPredictions.map { (track, prediction) ->
                        "${track.id}: ${prediction.label} (${prediction.confidencePercent}%)"
                    } +
                        outcomes.mapNotNull { outcome ->
                            if (outcome.unavailable) {
                                "${outcome.track.id}: unavailable"
                            } else {
                                null
                            }
                        },
                photoArtifact = photoArtifact,
            )
        }

    private fun VisualModelPrediction.toPickScore(track: LocalVisualModelTrack): Int =
        when (track.id) {
            "ripeness" ->
                when (label) {
                    "ripe" -> 92
                    "unripe" -> 48
                    "overripe" -> 34
                    else -> 50
                }
            "sweetness" ->
                when (label) {
                    "sweet" -> 86
                    "not_sweet" -> 58
                    else -> 50
                }
            else -> 50
        }
}
