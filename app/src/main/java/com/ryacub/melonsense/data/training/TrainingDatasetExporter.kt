package com.ryacub.melonsense.data.training

import androidx.annotation.VisibleForTesting
import com.ryacub.melonsense.data.history.PickHistoryItem
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.data.local.TrainingCaptureEntity
import com.ryacub.melonsense.data.local.artifacts
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import java.io.File

enum class TrainingQueueBlockReason {
    None,
    NeedsOutcome,
    AlreadyExported,
    ExpiredMedia,
    MissingCapture,
    MissingArtifact,
}

data class TrainingQueueItem(
    val historyItem: PickHistoryItem,
    val capture: TrainingCaptureEntity?,
    val blockReason: TrainingQueueBlockReason,
) {
    val isEligible: Boolean = blockReason == TrainingQueueBlockReason.None
}

data class TrainingDatasetBundle(
    val bundleDirectory: File,
    val manifestFile: File,
    val entryCount: Int,
    val createdAtMillis: Long,
)

object TrainingDatasetExporter {
    fun buildQueue(
        historyItems: List<PickHistoryItem>,
        captures: List<TrainingCaptureEntity>,
        nowMillis: Long,
    ): List<TrainingQueueItem> {
        val capturesByPickId = captures.associateBy { capture -> capture.pickHistoryId }
        return historyItems
            .filter { item ->
                item.trainingExportStatus != TrainingExportStatus.NotCaptured ||
                    capturesByPickId.containsKey(item.id)
            }
            .map { item ->
                val capture = capturesByPickId[item.id]
                TrainingQueueItem(
                    historyItem = item,
                    capture = capture,
                    blockReason = blockReason(item, capture, nowMillis),
                )
            }
    }

    fun writeBundle(
        eligibleItems: List<TrainingQueueItem>,
        outputDirectory: File,
        createdAtMillis: Long,
    ): TrainingDatasetBundle {
        val bundleDirectory = File(outputDirectory, "dataset-$createdAtMillis")
        val mediaDirectory = File(bundleDirectory, "media")
        mediaDirectory.mkdirs()
        val manifestFile = File(bundleDirectory, "manifest.jsonl")
        manifestFile.writeText(
            eligibleItems.joinToString(separator = "\n", postfix = "\n") { item ->
                item.toJsonLine(
                    exportCreatedAtMillis = createdAtMillis,
                    exportedArtifacts = item.copyArtifactsTo(mediaDirectory),
                )
            },
        )
        return TrainingDatasetBundle(
            bundleDirectory = bundleDirectory,
            manifestFile = manifestFile,
            entryCount = eligibleItems.size,
            createdAtMillis = createdAtMillis,
        )
    }

    private fun blockReason(
        item: PickHistoryItem,
        capture: TrainingCaptureEntity?,
        nowMillis: Long,
    ): TrainingQueueBlockReason {
        if (item.trainingExportStatus == TrainingExportStatus.Exported ||
            capture?.exportStatus == TrainingExportStatus.Exported
        ) {
            return TrainingQueueBlockReason.AlreadyExported
        }
        if (item.trainingExportStatus == TrainingExportStatus.Expired ||
            item.status == PickHistoryStatus.ExpiredMedia ||
            capture?.exportStatus == TrainingExportStatus.Expired ||
            (capture != null && capture.expiresAtMillis <= nowMillis)
        ) {
            return TrainingQueueBlockReason.ExpiredMedia
        }
        if (item.status != PickHistoryStatus.Rated || item.sweetness == null || item.texture == null) {
            return TrainingQueueBlockReason.NeedsOutcome
        }
        if (item.trainingExportStatus != TrainingExportStatus.Pending ||
            capture == null ||
            capture.exportStatus != TrainingExportStatus.Pending
        ) {
            return TrainingQueueBlockReason.MissingCapture
        }
        val artifacts = capture.artifacts()
        if (artifacts.isEmpty() || artifacts.any { artifact -> !File(artifact.path).exists() }) {
            return TrainingQueueBlockReason.MissingArtifact
        }
        return TrainingQueueBlockReason.None
    }

    private fun TrainingQueueItem.copyArtifactsTo(mediaDirectory: File): List<ExportedArtifact> {
        val pickHistoryId = historyItem.id
        return requireNotNull(capture)
            .artifacts()
            .map { artifact ->
                val sourceFile = File(artifact.path)
                val exportedFile =
                    File(
                        mediaDirectory,
                        "$pickHistoryId-${artifact.kind.name.lowercase()}-${sourceFile.name}",
                    )
                sourceFile.copyTo(exportedFile, overwrite = true)
                ExportedArtifact(
                    source = artifact,
                    exportedPath = exportedFile.absolutePath,
                )
            }
    }

    private fun TrainingQueueItem.toJsonLine(
        exportCreatedAtMillis: Long,
        exportedArtifacts: List<ExportedArtifact>,
    ): String {
        val item = historyItem
        val capture = requireNotNull(capture)
        return buildString {
            append("{")
            appendProperty("pickHistoryId", item.id)
            append(",")
            appendProperty("createdAtMillis", item.createdAtMillis)
            append(",")
            appendProperty("resultLabel", item.resultLabel.name)
            append(",")
            appendProperty("sweetness", requireNotNull(item.sweetness).name)
            append(",")
            appendProperty("texture", requireNotNull(item.texture).name)
            append(",")
            appendProperty("visualScore", item.visualScore)
            append(",")
            appendProperty("visualConfidencePercent", item.visualConfidencePercent)
            append(",")
            appendProperty("audioScore", item.audioScore)
            append(",")
            appendProperty("audioConfidencePercent", item.audioConfidencePercent)
            append(",")
            appendProperty("validKnocks", item.validKnocks)
            append(",")
            appendProperty("estimatedFrequencyHz", item.estimatedFrequencyHz)
            append(",")
            appendProperty("finalConfidencePercent", item.finalConfidencePercent)
            append(",")
            appendProperty("trainingExportStatus", TrainingExportStatus.Exported.name)
            append(",")
            appendProperty("trainingCaptureStatus", TrainingExportStatus.Exported.name)
            append(",")
            appendProperty("retentionExpiresAtMillis", capture.expiresAtMillis)
            append(",")
            appendProperty("exportCreatedAtMillis", exportCreatedAtMillis)
            append(",")
            append("\"artifacts\":")
            appendArtifacts(exportedArtifacts)
            append("}")
        }
    }

    private fun StringBuilder.appendArtifacts(artifacts: List<ExportedArtifact>) {
        append("[")
        artifacts.forEachIndexed { index, exportedArtifact ->
            val artifact = exportedArtifact.source
            if (index > 0) append(",")
            append("{")
            appendProperty("kind", artifact.kind.name)
            append(",")
            appendProperty("path", exportedArtifact.exportedPath)
            append(",")
            appendProperty("sourcePath", artifact.path)
            append(",")
            appendProperty("mimeType", artifact.mimeType)
            append(",")
            appendProperty("byteSize", artifact.byteSize)
            append(",")
            appendProperty("capturedAtMillis", artifact.capturedAtMillis)
            append(",")
            appendProperty("lastModifiedAtMillis", artifact.lastModifiedAtMillis)
            append(",")
            appendProperty("width", artifact.width)
            append(",")
            appendProperty("height", artifact.height)
            append(",")
            appendProperty("sampleRateHz", artifact.sampleRateHz)
            append(",")
            appendProperty("durationMillis", artifact.durationMillis)
            append("}")
        }
        append("]")
    }

    private data class ExportedArtifact(
        val source: TrainingMediaArtifact,
        val exportedPath: String,
    )

    private fun StringBuilder.appendProperty(
        name: String,
        value: String,
    ) {
        append("\"")
        append(name)
        append("\":\"")
        append(jsonEscape(value))
        append("\"")
    }

    private fun StringBuilder.appendProperty(
        name: String,
        value: Number?,
    ) {
        append("\"")
        append(name)
        append("\":")
        append(value ?: "null")
    }

    @VisibleForTesting
    internal fun jsonEscape(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
}
