package com.ryacub.melonsense.data.training

import android.content.Context
import android.graphics.BitmapFactory
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.GZIPOutputStream

private const val TRAINING_MEDIA_DIRECTORY = "training-media"
private const val DEFAULT_AUDIO_CAPTURE_WINDOW_MILLIS = 250L

class FileTrainingMediaStore(
    private val rootDirectory: File,
) {
    constructor(context: Context) : this(context.filesDir)

    private val mediaDirectory: File
        get() = File(rootDirectory, TRAINING_MEDIA_DIRECTORY)

    suspend fun createPhotoArtifactFile(capturedAtMillis: Long): File =
        withContext(Dispatchers.IO) {
            ensureMediaDirectory()
            File(mediaDirectory, "photo-$capturedAtMillis-${UUID.randomUUID()}.jpg")
        }

    suspend fun readPhotoArtifactMetadata(
        file: File,
        capturedAtMillis: Long,
    ): TrainingMediaArtifact =
        withContext(Dispatchers.IO) {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, options)

            TrainingMediaArtifact(
                kind = TrainingMediaKind.Photo,
                path = file.absolutePath,
                mimeType = options.outMimeType ?: "image/jpeg",
                byteSize = file.length(),
                capturedAtMillis = capturedAtMillis,
                lastModifiedAtMillis = file.lastModified(),
                width = options.outWidth.takeIf { width -> width > 0 },
                height = options.outHeight.takeIf { height -> height > 0 },
                sampleRateHz = null,
                durationMillis = null,
            )
        }

    suspend fun saveCompressedAudioArtifact(
        samples: ShortArray,
        sampleRateHz: Int,
        capturedAtMillis: Long,
    ): TrainingMediaArtifact =
        withContext(Dispatchers.IO) {
            ensureMediaDirectory()
            val file = File(mediaDirectory, "audio-$capturedAtMillis-${UUID.randomUUID()}.pcm16.gz")
            try {
                GZIPOutputStream(file.outputStream()).use { output ->
                    output.write(samples.toLittleEndianBytes())
                }
            } catch (failure: Throwable) {
                file.delete()
                throw failure
            }
            TrainingMediaArtifact(
                kind = TrainingMediaKind.Audio,
                path = file.absolutePath,
                mimeType = "audio/pcm16+gzip",
                byteSize = file.length(),
                capturedAtMillis = capturedAtMillis,
                lastModifiedAtMillis = file.lastModified(),
                width = null,
                height = null,
                sampleRateHz = sampleRateHz,
                durationMillis =
                    if (sampleRateHz > 0) {
                        samples.size * 1_000L / sampleRateHz
                    } else {
                        DEFAULT_AUDIO_CAPTURE_WINDOW_MILLIS
                    },
            )
        }

    suspend fun deleteArtifacts(
        artifacts: List<TrainingMediaArtifact>,
        shouldDelete: (TrainingMediaArtifact) -> Boolean,
    ): List<TrainingMediaArtifact> =
        withContext(Dispatchers.IO) {
            artifacts.filter { artifact ->
                shouldDelete(artifact) && File(artifact.path).deleteIfPresent()
            }
        }

    suspend fun deleteUntrackedArtifactsOlderThan(
        retainedPaths: Set<String>,
        cutoffMillis: Long,
    ): Int =
        withContext(Dispatchers.IO) {
            mediaDirectory
                .listFiles()
                .orEmpty()
                .filter { file ->
                    file.isFile &&
                        file.absolutePath !in retainedPaths &&
                        file.lastModified() <= cutoffMillis
                }.count { file -> file.delete() }
        }

    private fun ensureMediaDirectory() {
        mediaDirectory.mkdirs()
    }

    private fun File.deleteIfPresent(): Boolean = !exists() || delete()

    private fun ShortArray.toLittleEndianBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(size * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        forEach { sample -> buffer.putShort(sample) }
        return buffer.array()
    }
}
