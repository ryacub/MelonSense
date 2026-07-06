package com.ryacub.melonsense.domain.inference

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PytorchVisualModelRunnerInstrumentedTest {
    @Test
    fun packagedModels_loadAndRunOneInferencePerTrack() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val photoFile = File(context.cacheDir, "visual-model-smoke.jpg")
            Bitmap
                .createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                .apply {
                    eraseColor(0xff2f6b2f.toInt())
                    photoFile.outputStream().use { output ->
                        compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }
                    recycle()
                }
            val artifact =
                TrainingMediaArtifact(
                    kind = TrainingMediaKind.Photo,
                    path = photoFile.absolutePath,
                    mimeType = "image/jpeg",
                    byteSize = photoFile.length(),
                    capturedAtMillis = 1L,
                    lastModifiedAtMillis = photoFile.lastModified(),
                    width = 96,
                    height = 96,
                    sampleRateHz = null,
                    durationMillis = null,
                )
            val runner = PytorchVisualModelRunner(context)

            LocalVisualModelCatalog.fallback.tracks.forEach { track ->
                val prediction = runner.predict(track, artifact)

                assertTrue(prediction.label in track.labels)
                assertTrue(prediction.confidencePercent in 0..100)
            }
        }
}
