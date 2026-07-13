package com.ryacub.melonsense.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrainingExportIntentFactoryInstrumentedTest {
    @Test
    fun createIntent_sharesOnlyProviderZipWithReadPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val archive =
            File(context.filesDir, "training-exports/test-dataset.zip").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
        val spec = TrainingExportIntentFactory.create(archive)

        val intent = TrainingExportIntentFactory.createIntent(context, spec)
        val streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/zip", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(streamUri)
        assertEquals("${context.packageName}.training-exports", streamUri?.authority)
        assertEquals(streamUri, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun hasShareTarget_returnsFalseWhenIntentTargetsMissingPackage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .setPackage("com.ryacub.melonsense.missing.share.target")

        assertEquals(false, TrainingExportIntentFactory.hasShareTarget(context, intent))
    }
}
