package com.ryacub.melonsense.data.training

import com.ryacub.melonsense.data.history.PickHistoryItem
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.SweetnessRating
import com.ryacub.melonsense.data.history.TextureRating
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.data.local.TrainingCaptureEntity
import com.ryacub.melonsense.domain.model.ResultLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class TrainingDatasetExportTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun buildQueue_marksRatedPendingCaptureEligible() {
        val photoFile = temporaryFolder.newFile("photo.jpg")
        val audioFile = temporaryFolder.newFile("audio.pcm16.gz")

        val queue =
            TrainingDatasetExporter.buildQueue(
                historyItems = listOf(sampleHistoryItem()),
                captures = listOf(sampleCapture(photoPath = photoFile.absolutePath, audioPath = audioFile.absolutePath)),
                nowMillis = 2_000,
            )

        assertEquals(1, queue.size)
        assertTrue(queue.single().isEligible)
        assertEquals(TrainingQueueBlockReason.None, queue.single().blockReason)
    }

    @Test
    fun buildQueue_blocksExpiredMedia() {
        val photoFile = temporaryFolder.newFile("photo.jpg")

        val queue =
            TrainingDatasetExporter.buildQueue(
                historyItems = listOf(sampleHistoryItem()),
                captures = listOf(sampleCapture(photoPath = photoFile.absolutePath, expiresAtMillis = 1_000)),
                nowMillis = 2_000,
            )

        assertFalse(queue.single().isEligible)
        assertEquals(TrainingQueueBlockReason.ExpiredMedia, queue.single().blockReason)
    }

    @Test
    fun buildQueue_blocksPendingOutcome() {
        val audioFile = temporaryFolder.newFile("audio.pcm16.gz")

        val queue =
            TrainingDatasetExporter.buildQueue(
                historyItems =
                    listOf(
                        sampleHistoryItem(
                            status = PickHistoryStatus.PendingOutcome,
                            sweetness = null,
                            texture = null,
                        ),
                    ),
                captures = listOf(sampleCapture(audioPath = audioFile.absolutePath)),
                nowMillis = 2_000,
            )

        assertFalse(queue.single().isEligible)
        assertEquals(TrainingQueueBlockReason.NeedsOutcome, queue.single().blockReason)
    }

    @Test
    fun buildQueue_blocksMissingArtifactFile() {
        val queue =
            TrainingDatasetExporter.buildQueue(
                historyItems = listOf(sampleHistoryItem()),
                captures = listOf(sampleCapture(audioPath = temporaryFolder.root.resolve("missing.gz").absolutePath)),
                nowMillis = 2_000,
            )

        assertFalse(queue.single().isEligible)
        assertEquals(TrainingQueueBlockReason.MissingArtifact, queue.single().blockReason)
    }

    @Test
    fun writeBundle_includesMetadataAndArtifactReferences() {
        val photoFile = temporaryFolder.newFile("photo.jpg")
        val audioFile = temporaryFolder.newFile("audio.pcm16.gz")
        val outputDirectory = temporaryFolder.newFolder("exports")
        val queue =
            TrainingDatasetExporter.buildQueue(
                historyItems = listOf(sampleHistoryItem()),
                captures = listOf(sampleCapture(photoPath = photoFile.absolutePath, audioPath = audioFile.absolutePath)),
                nowMillis = 2_000,
            )

        val bundle =
            TrainingDatasetExporter.writeBundle(
                eligibleItems = queue.filter { item -> item.isEligible },
                outputDirectory = outputDirectory,
                createdAtMillis = 3_000,
            )

        val bundleText = bundle.manifestFile.readText()
        assertEquals(1, bundle.entryCount)
        assertTrue(bundleText.contains("\"schemaVersion\":1"))
        assertTrue(bundleText.contains("\"labelSource\":\"user_feedback\""))
        assertTrue(bundleText.contains("\"pickHistoryId\":42"))
        assertTrue(bundleText.contains("\"resultLabel\":\"GoodCandidate\""))
        assertTrue(bundleText.contains("\"sweetness\":\"Sweet\""))
        assertTrue(bundleText.contains("\"texture\":\"Crisp\""))
        assertTrue(bundleText.contains("\"visualScore\":74"))
        assertTrue(bundleText.contains("\"audioScore\":82"))
        assertTrue(bundleText.contains("\"trainingExportStatus\":\"Exported\""))
        assertTrue(bundleText.contains("\"trainingCaptureStatus\":\"Exported\""))
        assertTrue(bundleText.contains(photoFile.absolutePath))
        assertTrue(bundleText.contains(audioFile.absolutePath))
        assertTrue(bundle.manifestFile.parentFile?.resolve("media")?.listFiles().orEmpty().size == 2)
        assertTrue(bundle.archiveFile.isFile)
        ZipFile(bundle.archiveFile).use { archive ->
            assertTrue(archive.getEntry("manifest.jsonl") != null)
            assertEquals(2, archive.entries().asSequence().count { entry -> entry.name.startsWith("media/") })
        }
        assertTrue(bundleText.contains("\"path\":\"media/"))
        assertFalse(bundleText.contains("\"path\":\"${outputDirectory.absolutePath}"))
    }

    @Test
    fun writeBundle_rejectsCompletedTimestampCollisionWithoutOverwriting() {
        val photoFile = temporaryFolder.newFile("collision-photo.jpg")
        val outputDirectory = temporaryFolder.newFolder("collision-exports")
        val finalDirectory = File(outputDirectory, "dataset-3000").apply { mkdirs() }
        val marker = File(finalDirectory, "keep.txt").apply { writeText("existing") }
        val queue =
            TrainingDatasetExporter.buildQueue(
                historyItems = listOf(sampleHistoryItem()),
                captures = listOf(sampleCapture(photoPath = photoFile.absolutePath)),
                nowMillis = 2_000,
            )

        runCatching {
            TrainingDatasetExporter.writeBundle(queue.filter { it.isEligible }, outputDirectory, 3_000)
        }.onSuccess { throw AssertionError("Expected timestamp collision to fail") }

        assertEquals("existing", marker.readText())
        assertFalse(File(outputDirectory, "dataset-3000.zip").exists())
    }

    @Test
    fun writeBundle_copyFailureRemovesTemporaryAndFinalOutput() {
        val sourceFile = temporaryFolder.newFile("copy-failure.jpg")
        val outputDirectory = temporaryFolder.newFolder("failed-exports")
        val queue =
            TrainingDatasetExporter.buildQueue(
                historyItems = listOf(sampleHistoryItem()),
                captures = listOf(sampleCapture(photoPath = sourceFile.absolutePath)),
                nowMillis = 2_000,
            )

        runCatching {
            TrainingDatasetExporter.writeBundle(
                eligibleItems = queue.filter { it.isEligible },
                outputDirectory = outputDirectory,
                createdAtMillis = 3_000,
                copyArtifact = { _, _ -> throw java.io.IOException("forced copy failure") },
            )
        }.onSuccess { throw AssertionError("Expected artifact copy to fail") }

        assertTrue(outputDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun writeBundle_rejectsEmptyExport() {
        TrainingDatasetExporter.writeBundle(
            eligibleItems = emptyList(),
            outputDirectory = temporaryFolder.newFolder("exports"),
            createdAtMillis = 3_000,
        )
    }

    private fun sampleHistoryItem(
        status: PickHistoryStatus = PickHistoryStatus.Rated,
        sweetness: SweetnessRating? = SweetnessRating.Sweet,
        texture: TextureRating? = TextureRating.Crisp,
        trainingExportStatus: TrainingExportStatus = TrainingExportStatus.Pending,
    ): PickHistoryItem =
        PickHistoryItem(
            id = 42,
            createdAtMillis = 1_500,
            status = status,
            resultLabel = ResultLabel.GoodCandidate,
            sweetness = sweetness,
            texture = texture,
            visualScore = 74,
            visualConfidencePercent = 66,
            audioScore = 82,
            audioConfidencePercent = 91,
            validKnocks = 3,
            estimatedFrequencyHz = 144,
            finalConfidencePercent = 79,
            trainingExportStatus = trainingExportStatus,
            trainingExportedAtMillis = null,
        )

    private fun sampleCapture(
        photoPath: String? = null,
        audioPath: String? = null,
        expiresAtMillis: Long = 10_000,
    ): TrainingCaptureEntity =
        TrainingCaptureEntity(
            pickHistoryId = 42,
            exportStatus = TrainingExportStatus.Pending,
            exportedAtMillis = null,
            createdAtMillis = 1_600,
            expiresAtMillis = expiresAtMillis,
            photoPath = photoPath,
            photoMimeType = photoPath?.let { "image/jpeg" },
            photoByteSize = photoPath?.let { 128 },
            photoCapturedAtMillis = photoPath?.let { 1_610 },
            photoLastModifiedAtMillis = photoPath?.let { 1_620 },
            photoWidth = photoPath?.let { 640 },
            photoHeight = photoPath?.let { 480 },
            audioPath = audioPath,
            audioMimeType = audioPath?.let { "audio/pcm16+gzip" },
            audioByteSize = audioPath?.let { 96 },
            audioCapturedAtMillis = audioPath?.let { 1_630 },
            audioLastModifiedAtMillis = audioPath?.let { 1_640 },
            audioSampleRateHz = audioPath?.let { 16_000 },
            audioDurationMillis = audioPath?.let { 750 },
        )
}
