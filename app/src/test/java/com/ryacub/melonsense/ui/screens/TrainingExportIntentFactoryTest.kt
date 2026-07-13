package com.ryacub.melonsense.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrainingExportIntentFactoryTest {
    @Test
    fun create_buildsReadOnlyZipShareSpecForCompletedArchive() {
        val archive = File("/exports/dataset-123.zip")

        val spec = TrainingExportIntentFactory.create(archive)

        assertEquals(archive, spec.archiveFile)
        assertEquals("application/zip", spec.mimeType)
        assertTrue(spec.grantReadPermission)
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_rejectsNonZipOutput() {
        TrainingExportIntentFactory.create(File("/exports/manifest.jsonl"))
    }
}
