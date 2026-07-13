package com.ryacub.melonsense.data.training

import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class TrainingMediaDeletionException(
    val failedPaths: List<String>,
) : IllegalStateException("Failed to delete ${failedPaths.size} training media artifact(s)")

class TrainingMediaRetentionController(
    private val mediaStore: FileTrainingMediaStore,
) {
    suspend fun applyToVisualResult(
        result: VisualScanResult,
        retainTrainingMedia: Boolean,
    ): VisualScanResult {
        if (retainTrainingMedia) return result
        discardArtifacts(listOfNotNull(result.photoArtifact), retainTrainingMedia = false)
        return result.copy(photoArtifact = null)
    }

    suspend fun applyToAssessment(
        result: MelonAssessmentResult,
        retainTrainingMedia: Boolean,
    ): MelonAssessmentResult {
        if (retainTrainingMedia) return result
        val artifacts =
            listOfNotNull(
                result.visualScanResult?.photoArtifact,
                result.audioScanResult.audioArtifact,
                result.trainingMedia?.photoArtifact,
                result.trainingMedia?.audioArtifact,
            ).distinctBy { artifact -> artifact.path }
        discardArtifacts(artifacts, retainTrainingMedia = false)
        return result.copy(
            visualScanResult = result.visualScanResult?.copy(photoArtifact = null),
            audioScanResult = result.audioScanResult.copy(audioArtifact = null),
            trainingMedia = null,
        )
    }

    suspend fun discardArtifacts(
        artifacts: List<TrainingMediaArtifact>,
        retainTrainingMedia: Boolean,
    ) {
        if (retainTrainingMedia) return
        val requested = artifacts.distinctBy { artifact -> artifact.path }
        val deletedPaths = mediaStore.deleteArtifacts(requested) { true }.mapTo(mutableSetOf()) { it.path }
        val failedPaths = requested.map { it.path }.filterNot(deletedPaths::contains)
        if (failedPaths.isNotEmpty()) throw TrainingMediaDeletionException(failedPaths)
    }

    suspend fun <T> cleanupOnFailure(
        artifacts: List<TrainingMediaArtifact>,
        retainTrainingMedia: Boolean,
        operation: suspend () -> T,
    ): T =
        try {
            operation()
        } catch (failure: Throwable) {
            try {
                withContext(NonCancellable) {
                    withTimeout(CLEANUP_TIMEOUT_MILLIS) {
                        discardArtifacts(artifacts, retainTrainingMedia)
                    }
                }
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }

    private companion object {
        const val CLEANUP_TIMEOUT_MILLIS = 5_000L
    }
}
