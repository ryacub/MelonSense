package com.ryacub.melonsense.data.training

import com.ryacub.melonsense.domain.model.TrainingMediaKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.GZIPInputStream

class FileTrainingMediaStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun saveCompressedAudioArtifact_writesGzippedPcmAndMetadata() =
        runTest {
            val store = FileTrainingMediaStore(temporaryFolder.root)
            val samples = shortArrayOf(10, -20, 30, -40)

            val artifact =
                store.saveCompressedAudioArtifact(
                    samples = samples,
                    sampleRateHz = 16_000,
                    capturedAtMillis = 1_788_000_000_000,
                )

            assertEquals(TrainingMediaKind.Audio, artifact.kind)
            assertEquals("audio/pcm16+gzip", artifact.mimeType)
            assertEquals(16_000, artifact.sampleRateHz)
            assertEquals(0L, artifact.durationMillis)
            assertTrue(File(artifact.path).exists())
            assertArrayEquals(samples, readGzippedPcm16(File(artifact.path)))
        }

    @Test
    fun deleteExpiredArtifacts_removesFilesOlderThanRetentionWindow() =
        runTest {
            val store = FileTrainingMediaStore(temporaryFolder.root)
            val oldArtifact =
                store.saveCompressedAudioArtifact(
                    samples = shortArrayOf(1, 2, 3),
                    sampleRateHz = 16_000,
                    capturedAtMillis = 1_000,
                )
            val freshArtifact =
                store.saveCompressedAudioArtifact(
                    samples = shortArrayOf(4, 5, 6),
                    sampleRateHz = 16_000,
                    capturedAtMillis = 30_000,
                )

            val deleted =
                store.deleteArtifacts(
                    listOf(
                        oldArtifact,
                        freshArtifact,
                    ),
                ) { artifact -> artifact.capturedAtMillis < 14_000 }

            assertEquals(listOf(oldArtifact.path), deleted.map { artifact -> artifact.path })
            assertFalse(File(oldArtifact.path).exists())
            assertTrue(File(freshArtifact.path).exists())
        }

    @Test
    fun deleteUntrackedArtifactsOlderThan_removesAbandonedMediaFiles() =
        runTest {
            val store = FileTrainingMediaStore(temporaryFolder.root)
            val abandonedArtifact =
                store.saveCompressedAudioArtifact(
                    samples = shortArrayOf(1, 2, 3),
                    sampleRateHz = 16_000,
                    capturedAtMillis = 1_000,
                )
            val retainedArtifact =
                store.saveCompressedAudioArtifact(
                    samples = shortArrayOf(4, 5, 6),
                    sampleRateHz = 16_000,
                    capturedAtMillis = 2_000,
                )
            File(abandonedArtifact.path).setLastModified(1_000)
            File(retainedArtifact.path).setLastModified(1_000)

            val deletedCount =
                store.deleteUntrackedArtifactsOlderThan(
                    retainedPaths = setOf(retainedArtifact.path),
                    cutoffMillis = 14_000,
                )

            assertEquals(1, deletedCount)
            assertFalse(File(abandonedArtifact.path).exists())
            assertTrue(File(retainedArtifact.path).exists())
        }

    private fun readGzippedPcm16(file: File): ShortArray {
        val bytes = GZIPInputStream(file.inputStream()).use { stream -> stream.readBytes() }
        return ShortArray(bytes.size / Short.SIZE_BYTES) { index ->
            val low = bytes[index * 2].toInt() and 0xff
            val high = bytes[index * 2 + 1].toInt()
            ((high shl 8) or low).toShort()
        }
    }
}
