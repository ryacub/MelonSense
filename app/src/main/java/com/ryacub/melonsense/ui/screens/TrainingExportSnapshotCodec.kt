package com.ryacub.melonsense.ui.screens

class TrainingExportSnapshotCodec {
    fun encode(state: TrainingExportState): String {
        val phase = state.phase.name
        val archivePath = state.archivePath ?: ""
        val entryCount = state.entryCount ?: -1
        val shareFailed = state.shareFailed.toString()
        return "$phase|$archivePath|$entryCount|$shareFailed"
    }

    fun decode(encoded: String): TrainingExportState {
        val parts = encoded.split("|", limit = 4)
        if (parts.size != 4) return TrainingExportState()
        return try {
            var phase = TrainingExportPhase.valueOf(parts[0])
            val archivePath = parts[1].takeIf { it.isNotEmpty() }
            val entryCount = parts[2].toIntOrNull()?.takeIf { it >= 0 }
            val shareFailed = parts[3].toBoolean()
            if (phase == TrainingExportPhase.Running) {
                phase = TrainingExportPhase.Failed
            }
            TrainingExportState(
                phase = phase,
                archivePath = archivePath,
                entryCount = entryCount,
                shareFailed = shareFailed,
            )
        } catch (exception: Exception) {
            TrainingExportState()
        }
    }
}
