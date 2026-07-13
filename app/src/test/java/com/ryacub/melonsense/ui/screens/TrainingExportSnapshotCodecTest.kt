package com.ryacub.melonsense.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingExportSnapshotCodecTest {
    private val codec = TrainingExportSnapshotCodec()

    @Test
    fun decode_normalizesRunningStateToFailed() {
        val encoded = codec.encode(
            TrainingExportState(
                phase = TrainingExportPhase.Running,
                archivePath = null,
                entryCount = null,
                shareFailed = false,
            ),
        )

        val decoded = codec.decode(encoded)

        assertEquals(TrainingExportPhase.Failed, decoded.phase)
        assertEquals(null, decoded.archivePath)
        assertEquals(null, decoded.entryCount)
    }

    @Test
    fun decode_preservesIdleState() {
        val encoded = codec.encode(TrainingExportState(phase = TrainingExportPhase.Idle))
        val decoded = codec.decode(encoded)

        assertEquals(TrainingExportPhase.Idle, decoded.phase)
    }

    @Test
    fun decode_preservesSucceededStateWithArchive() {
        val encoded = codec.encode(
            TrainingExportState(
                phase = TrainingExportPhase.Succeeded,
                archivePath = "/path/to/archive.zip",
                entryCount = 5,
                shareFailed = false,
            ),
        )

        val decoded = codec.decode(encoded)

        assertEquals(TrainingExportPhase.Succeeded, decoded.phase)
        assertEquals("/path/to/archive.zip", decoded.archivePath)
        assertEquals(5, decoded.entryCount)
    }

    @Test
    fun decode_preservesFailedState() {
        val encoded = codec.encode(TrainingExportState(phase = TrainingExportPhase.Failed))
        val decoded = codec.decode(encoded)

        assertEquals(TrainingExportPhase.Failed, decoded.phase)
    }
}
