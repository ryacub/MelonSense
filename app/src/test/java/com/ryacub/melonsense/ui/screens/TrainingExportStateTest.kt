package com.ryacub.melonsense.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingExportStateTest {
    @Test
    fun runningExport_rejectsDuplicateRequest() {
        val running = TrainingExportState().reduce(TrainingExportEvent.Requested)

        assertEquals(TrainingExportPhase.Running, running.phase)
        assertFalse(running.canStart)
        assertEquals(running, running.reduce(TrainingExportEvent.Requested))
    }

    @Test
    fun failedExport_canRetry() {
        val failed =
            TrainingExportState()
                .reduce(TrainingExportEvent.Requested)
                .reduce(TrainingExportEvent.Failed)

        assertEquals(TrainingExportPhase.Failed, failed.phase)
        assertTrue(failed.canStart)
        assertEquals(TrainingExportPhase.Running, failed.reduce(TrainingExportEvent.Requested).phase)
    }

    @Test
    fun cancelledExport_returnsToIdle() {
        val cancelled =
            TrainingExportState()
                .reduce(TrainingExportEvent.Requested)
                .reduce(TrainingExportEvent.Cancelled)

        assertEquals(TrainingExportState(), cancelled)
    }

    @Test
    fun successfulExport_retainsArchiveAndEntryCount() {
        val succeeded =
            TrainingExportState()
                .reduce(TrainingExportEvent.Requested)
                .reduce(TrainingExportEvent.Succeeded(archivePath = "/exports/dataset.zip", entryCount = 3))

        assertEquals(TrainingExportPhase.Succeeded, succeeded.phase)
        assertEquals("/exports/dataset.zip", succeeded.archivePath)
        assertEquals(3, succeeded.entryCount)
        assertTrue(succeeded.canShare)
    }

    @Test
    fun shareFailure_preservesCompletedExportAndCanClearOnRetry() {
        val succeeded =
            TrainingExportState()
                .reduce(TrainingExportEvent.Requested)
                .reduce(TrainingExportEvent.Succeeded(archivePath = "/exports/dataset.zip", entryCount = 2))
        val shareFailed = succeeded.reduce(TrainingExportEvent.ShareFailed)

        assertEquals(TrainingExportPhase.Succeeded, shareFailed.phase)
        assertTrue(shareFailed.shareFailed)
        assertEquals("/exports/dataset.zip", shareFailed.archivePath)
        assertFalse(shareFailed.reduce(TrainingExportEvent.ShareStarted).shareFailed)
    }
}
