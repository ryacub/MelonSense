package com.ryacub.melonsense.domain.inference

import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class LocalVisualModelScorerTest {
    @Test
    fun catalog_matchesPackagedLeakageSafeModelMetadata() {
        assertEquals("melonsense-visual-runtime-v0", LocalVisualModelCatalog.ID)
        assertEquals("runtime-v0", LocalVisualModelCatalog.VERSION)
        assertEquals(
            listOf("ripe", "unripe"),
            LocalVisualModelCatalog.tracks.first { track -> track.id == "ripeness" }.labels,
        )
        assertEquals(
            listOf("not_sweet", "sweet"),
            LocalVisualModelCatalog.tracks.first { track -> track.id == "sweetness" }.labels,
        )
        assertEquals("models/visual-runtime-ripeness-v0.ptl", LocalVisualModelCatalog.tracks[0].assetPath)
        assertEquals("models/visual-sweetness-fa99cb0.ptl", LocalVisualModelCatalog.tracks[1].assetPath)
        assertEquals(568_133L, LocalVisualModelCatalog.tracks[0].assetByteSize)
        assertEquals(568_323L, LocalVisualModelCatalog.tracks[1].assetByteSize)
    }

    @Test
    fun packagedModelAssets_areLiteInterpreterArchivesAndMatchCatalogSizes() {
        LocalVisualModelCatalog.tracks.forEach { track ->
            val assetFile = requireAssetFile(track.assetPath)

            assertEquals(track.assetByteSize, assetFile.length())
            ZipFile(assetFile).use { archive ->
                assertTrue(archive.entries().asSequence().any { entry -> entry.name.endsWith("bytecode.pkl") })
            }
        }
    }

    @Test
    fun score_combinesRipenessAndSweetnessPredictions() =
        runTest {
            val scorer =
                LocalVisualModelScorer(
                    runner =
                        FakeVisualModelRunner(
                            predictions =
                                mapOf(
                                    "ripeness" to VisualModelPrediction(label = "ripe", confidencePercent = 80),
                                    "sweetness" to VisualModelPrediction(label = "sweet", confidencePercent = 60),
                                ),
                        ),
                    nowMillis = { 1234L },
                )

            val result = scorer.score(samplePhotoArtifact())

            assertEquals(90, result.score)
            assertEquals(74, result.confidencePercent)
            assertEquals(1234L, result.capturedAtMillis)
            assertEquals(samplePhotoArtifact(), result.photoArtifact)
            assertTrue(result.evidence.contains("ripeness: ripe (80%)"))
            assertTrue(result.evidence.contains("sweetness: sweet (60%)"))
        }

    @Test
    fun score_usesSuccessfulTrackWhenOneModelFails() =
        runTest {
            val scorer =
                LocalVisualModelScorer(
                    runner =
                        FakeVisualModelRunner(
                            predictions =
                                mapOf(
                                    "ripeness" to VisualModelPrediction(label = "unripe", confidencePercent = 70),
                                ),
                            failedTrackIds = setOf("sweetness"),
                        ),
                    nowMillis = { 1234L },
                )

            val result = scorer.score(samplePhotoArtifact())

            assertEquals(48, result.score)
            assertEquals(70, result.confidencePercent)
            assertTrue(result.evidence.contains("sweetness: unavailable"))
        }

    @Test(expected = CancellationException::class)
    fun score_preservesCancellation() =
        runTest {
            val scorer =
                LocalVisualModelScorer(
                    runner =
                        FakeVisualModelRunner(
                            predictions = emptyMap(),
                            cancelledTrackIds = setOf("ripeness"),
                        ),
                )

            scorer.score(samplePhotoArtifact())
        }

    private class FakeVisualModelRunner(
        private val predictions: Map<String, VisualModelPrediction>,
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
            return requireNotNull(predictions[track.id])
        }
    }
}

private fun samplePhotoArtifact(): TrainingMediaArtifact =
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

private fun requireAssetFile(assetPath: String): File {
    val moduleRelative = File("src/main/assets/$assetPath")
    if (moduleRelative.exists()) {
        return moduleRelative
    }
    val repoRelative = File("app/src/main/assets/$assetPath")
    if (repoRelative.exists()) {
        return repoRelative
    }
    error("Missing asset $assetPath")
}
