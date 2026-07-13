package com.ryacub.melonsense.data.training

import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.PendingTrainingMedia
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrainingMediaRetentionControllerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun disabledRetention_deletesAndDetachesVisualArtifactAfterScoring() =
        runTest {
            val photo = artifact(temporaryFolder.newFile("visual.jpg"), TrainingMediaKind.Photo)
            val result = VisualScanResult(70, 80, 10L, emptyList(), photo)
            val controller = TrainingMediaRetentionController(FileTrainingMediaStore(temporaryFolder.root))

            val retained = controller.applyToVisualResult(result, retainTrainingMedia = false)

            assertFalse(temporaryFolder.root.resolve("visual.jpg").exists())
            assertNull(retained.photoArtifact)
            assertEquals(result.score, retained.score)
        }

    @Test
    fun disabledRetention_deletesAndDetachesAllTrainingArtifacts() =
        runTest {
            val photo = artifact(temporaryFolder.newFile("photo.jpg"), TrainingMediaKind.Photo)
            val audio = artifact(temporaryFolder.newFile("audio.pcm16.gz"), TrainingMediaKind.Audio)
            val result = assessment(photo, audio)
            val controller = TrainingMediaRetentionController(FileTrainingMediaStore(temporaryFolder.root))

            val retained = controller.applyToAssessment(result, retainTrainingMedia = false)

            assertFalse(temporaryFolder.root.resolve("photo.jpg").exists())
            assertFalse(temporaryFolder.root.resolve("audio.pcm16.gz").exists())
            assertNull(retained.visualScanResult?.photoArtifact)
            assertNull(retained.audioScanResult.audioArtifact)
            assertNull(retained.trainingMedia)
            assertEquals(result.resultLabel, retained.resultLabel)
        }

    @Test
    fun enabledRetention_leavesAssessmentAndFilesUnchanged() =
        runTest {
            val photo = artifact(temporaryFolder.newFile("photo.jpg"), TrainingMediaKind.Photo)
            val audio = artifact(temporaryFolder.newFile("audio.pcm16.gz"), TrainingMediaKind.Audio)
            val result = assessment(photo, audio)
            val controller = TrainingMediaRetentionController(FileTrainingMediaStore(temporaryFolder.root))

            val retained = controller.applyToAssessment(result, retainTrainingMedia = true)

            assertSame(result, retained)
            assertEquals(true, temporaryFolder.root.resolve("photo.jpg").exists())
            assertEquals(true, temporaryFolder.root.resolve("audio.pcm16.gz").exists())
        }

    @Test
    fun inferenceFailure_deletesCapturedArtifactsWhenRetentionIsDisabled() =
        runTest {
            val photo = artifact(temporaryFolder.newFile("failed-visual.jpg"), TrainingMediaKind.Photo)
            val controller = TrainingMediaRetentionController(FileTrainingMediaStore(temporaryFolder.root))

            var thrown: Throwable? = null
            try {
                controller.cleanupOnFailure(
                    artifacts = listOf(photo),
                    retainTrainingMedia = false,
                ) {
                    error("inference failed")
                }
            } catch (exception: Throwable) {
                thrown = exception
            }

            assertEquals("inference failed", thrown?.message)
            assertFalse(temporaryFolder.root.resolve("failed-visual.jpg").exists())
        }

    @Test
    fun cancellation_deletesCapturedArtifactsBeforeRethrowing() =
        runBlocking {
            val photo = artifact(temporaryFolder.newFile("cancelled-visual.jpg"), TrainingMediaKind.Photo)
            val controller = TrainingMediaRetentionController(FileTrainingMediaStore(temporaryFolder.root))
            val inferenceStarted = CompletableDeferred<Unit>()
            val job =
                launch {
                    controller.cleanupOnFailure(
                        artifacts = listOf(photo),
                        retainTrainingMedia = false,
                    ) {
                        inferenceStarted.complete(Unit)
                        awaitCancellation()
                    }
                }
            var completionCause: Throwable? = null
            job.invokeOnCompletion { cause -> completionCause = cause }
            inferenceStarted.await()

            job.cancelAndJoin()

            assertFalse(
                completionCause?.suppressed?.joinToString { it.toString() }.orEmpty(),
                temporaryFolder.root.resolve("cancelled-visual.jpg").exists(),
            )
        }

    @Test
    fun deleteFailure_throwsInsteadOfReturningDetachedResult() =
        runTest {
            val nonEmptyDirectory = temporaryFolder.newFolder("undeletable")
            nonEmptyDirectory.resolve("child").writeText("retained")
            val photo = artifact(nonEmptyDirectory, TrainingMediaKind.Photo)
            val result = VisualScanResult(70, 80, 10L, emptyList(), photo)
            val controller = TrainingMediaRetentionController(FileTrainingMediaStore(temporaryFolder.root))

            var thrown: Throwable? = null
            try {
                controller.applyToVisualResult(result, retainTrainingMedia = false)
            } catch (exception: Throwable) {
                thrown = exception
            }

            assertTrue(thrown is TrainingMediaDeletionException)
            assertTrue(nonEmptyDirectory.exists())
        }

    private fun artifact(
        file: java.io.File,
        kind: TrainingMediaKind,
    ): TrainingMediaArtifact =
        TrainingMediaArtifact(
            kind = kind,
            path = file.absolutePath,
            mimeType = if (kind == TrainingMediaKind.Photo) "image/jpeg" else "audio/pcm16+gzip",
            byteSize = file.length(),
            capturedAtMillis = 10L,
            lastModifiedAtMillis = file.lastModified(),
            width = null,
            height = null,
            sampleRateHz = if (kind == TrainingMediaKind.Audio) 16_000 else null,
            durationMillis = if (kind == TrainingMediaKind.Audio) 250L else null,
        )

    private fun assessment(
        photo: TrainingMediaArtifact,
        audio: TrainingMediaArtifact,
    ): MelonAssessmentResult {
        val visual = VisualScanResult(70, 80, 10L, emptyList(), photo)
        val audioResult = AudioScanResult(65, 75, 3, 220, 20L, emptyList(), audio)
        return MelonAssessmentResult(
            visualScanResult = visual,
            audioScanResult = audioResult,
            recommendation = "Good candidate",
            resultLabel = ResultLabel.GoodCandidate,
            confidencePercent = 78,
            trainingMedia = PendingTrainingMedia(photo, audio, 20L, 30L),
        )
    }
}
