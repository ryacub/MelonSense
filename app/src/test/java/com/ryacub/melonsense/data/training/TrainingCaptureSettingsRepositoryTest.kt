package com.ryacub.melonsense.data.training

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingCaptureSettingsRepositoryTest {
    @Test
    fun missingStoredValue_defaultsToEnabled() {
        val repository = TrainingCaptureSettingsRepository(readStored = { null }, writeStored = {})

        assertTrue(repository.isEnabled)
    }

    @Test
    fun explicitChangesPersistThroughStorageBoundary() {
        var stored: Boolean? = null
        val repository = TrainingCaptureSettingsRepository(readStored = { stored }, writeStored = { stored = it })

        repository.setEnabled(false)

        assertFalse(repository.isEnabled)
    }
}
