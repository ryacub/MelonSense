package com.ryacub.melonsense.domain.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.IOException
import kotlin.math.exp

class PytorchVisualModelRunner(
    context: Context,
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VisualModelRunner {
    private val appContext = context.applicationContext
    private val modules = mutableMapOf<String, Module>()
    private val predictionMutex = Mutex()

    override suspend fun predict(
        track: LocalVisualModelTrack,
        photoArtifact: TrainingMediaArtifact,
    ): VisualModelPrediction {
        val outcome = predictAll(listOf(track), photoArtifact).single()
        return outcome.prediction ?: error("Local visual model unavailable for ${track.id}")
    }

    override suspend fun predictAll(
        tracks: List<LocalVisualModelTrack>,
        photoArtifact: TrainingMediaArtifact,
    ): List<VisualModelTrackPrediction> =
        withContext(inferenceDispatcher) {
            predictionMutex.withLock {
                val input = decodePhoto(photoArtifact)
                val tensorData =
                    VisualModelPreprocessor.normalizeRgbBytesToChwFloatArray(
                        rgbBytes = input.rgbBytes,
                        width = input.width,
                        height = input.height,
                        imageSize = VisualModelPreprocessor.DEFAULT_IMAGE_SIZE,
                    )
                val tensor =
                    Tensor.fromBlob(
                        tensorData,
                        longArrayOf(
                            1,
                            3,
                            VisualModelPreprocessor.DEFAULT_IMAGE_SIZE.toLong(),
                            VisualModelPreprocessor.DEFAULT_IMAGE_SIZE.toLong(),
                        ),
                    )
                tracks.map { track ->
                    try {
                        val logits =
                            moduleFor(track)
                                .forward(IValue.from(tensor))
                                .toTensor()
                                .dataAsFloatArray
                                .take(track.labels.size)
                        val probabilities = logits.softmax()
                        val bestIndex = probabilities.indices.maxBy { index -> probabilities[index] }
                        VisualModelTrackPrediction(
                            track = track,
                            prediction =
                                VisualModelPrediction(
                                    label = track.labels[bestIndex],
                                    confidencePercent = (probabilities[bestIndex] * 100f).toInt().coerceIn(0, 100),
                                ),
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
        }

    private fun moduleFor(track: LocalVisualModelTrack): Module =
        modules.getOrPut(track.id) {
            LiteModuleLoader.load(assetFile(track).absolutePath)
        }

    private fun assetFile(track: LocalVisualModelTrack): File {
        val outputFile = File(appContext.noBackupFilesDir, track.assetPath.replace("/", "_"))
        if (outputFile.exists() && outputFile.length() == track.assetByteSize) {
            return outputFile
        }
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        outputFile.parentFile?.mkdirs()
        tempFile.delete()
        appContext.assets.open(track.assetPath).use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        check(tempFile.length() == track.assetByteSize) {
            "Copied model asset ${track.assetPath} has unexpected size"
        }
        outputFile.delete()
        check(tempFile.renameTo(outputFile)) {
            "Could not install model asset ${track.assetPath}"
        }
        return outputFile
    }

    private fun decodePhoto(photoArtifact: TrainingMediaArtifact): VisualModelInput {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeFile(photoArtifact.path, options)
        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight)
            }
        val decodedBitmap =
            BitmapFactory.decodeFile(photoArtifact.path, decodeOptions)
                ?: error("Could not decode photo artifact")
        val bitmap = decodedBitmap.applyExifOrientation(photoArtifact.path)
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            VisualModelInput(
                width = bitmap.width,
                height = bitmap.height,
                rgbBytes = pixels.toRgbBytes(),
            )
        } finally {
            if (bitmap !== decodedBitmap) {
                decodedBitmap.recycle()
            }
            bitmap.recycle()
        }
    }

    private fun FloatArray.softmax(): FloatArray {
        val max = maxOrNull() ?: 0f
        val expValues = map { value -> exp((value - max).toDouble()).toFloat() }
        val sum = expValues.sum().takeIf { it > 0f } ?: 1f
        return expValues.map { value -> value / sum }.toFloatArray()
    }

    private fun List<Float>.softmax(): FloatArray = toFloatArray().softmax()

    private fun IntArray.toRgbBytes(): ByteArray {
        val rgbBytes = ByteArray(size * 3)
        forEachIndexed { index, pixel ->
            val offset = index * 3
            rgbBytes[offset] = ((pixel shr 16) and 0xff).toByte()
            rgbBytes[offset + 1] = ((pixel shr 8) and 0xff).toByte()
            rgbBytes[offset + 2] = (pixel and 0xff).toByte()
        }
        return rgbBytes
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
    ): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth / 2 >= MAX_DECODED_DIMENSION && sampledHeight / 2 >= MAX_DECODED_DIMENSION) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun Bitmap.applyExifOrientation(path: String): Bitmap {
        val orientation =
            try {
                ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (exception: IOException) {
                ExifInterface.ORIENTATION_NORMAL
            }
        val matrix =
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
                ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { preScale(-1f, 1f) }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { preScale(1f, -1f) }
                ExifInterface.ORIENTATION_TRANSPOSE ->
                    Matrix().apply {
                        preScale(-1f, 1f)
                        postRotate(90f)
                    }
                ExifInterface.ORIENTATION_TRANSVERSE ->
                    Matrix().apply {
                        preScale(-1f, 1f)
                        postRotate(270f)
                    }
                else -> null
            }
        return if (matrix == null) {
            this
        } else {
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }
    }

    private companion object {
        const val MAX_DECODED_DIMENSION = 512
    }
}

private data class VisualModelInput(
    val width: Int,
    val height: Int,
    val rgbBytes: ByteArray,
)
