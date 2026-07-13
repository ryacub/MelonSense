package com.ryacub.melonsense.data.training

import androidx.annotation.VisibleForTesting
import com.ryacub.melonsense.data.history.PickHistoryItem
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.data.local.TrainingCaptureEntity
import com.ryacub.melonsense.data.local.artifacts
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    val archiveFile: File,
    val entryCount: Int,
    val createdAtMillis: Long,
) {
    fun deleteOutput() {
        val archiveDeleted = !archiveFile.exists() || archiveFile.delete()
        check(archiveDeleted) { "Cannot remove incomplete export output." }
    }
}

object TrainingDatasetExporter {
    private const val SCHEMA_VERSION = 1
    private const val LABEL_SOURCE = "user_feedback"

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
        copyArtifact: (source: File, destination: File) -> Unit = { source, destination ->
            source.copyTo(destination, overwrite = false)
        },
    ): TrainingDatasetBundle {
        require(eligibleItems.isNotEmpty()) { "Cannot export an empty training dataset." }
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) { "Cannot create export directory." }
        deletePreviousExportOutputs(outputDirectory)
        val bundleName = "dataset-$createdAtMillis"
        val bundleDirectory = File(outputDirectory, bundleName)
        val archiveFile = File(outputDirectory, "$bundleName.zip")
        check(!bundleDirectory.exists() && !archiveFile.exists()) { "An export already exists for this timestamp." }

        val temporarySuffix = UUID.randomUUID().toString()
        val temporaryDirectory = File(outputDirectory, ".$bundleName-$temporarySuffix.tmp")
        val temporaryArchive = File(outputDirectory, ".$bundleName-$temporarySuffix.zip.tmp")
        var publishedDirectory = false
        try {
            val mediaDirectory = File(temporaryDirectory, "media")
            check(mediaDirectory.mkdirs()) { "Cannot create temporary export directory." }
            val manifestFile = File(temporaryDirectory, "manifest.jsonl")
            manifestFile.writeText(
                eligibleItems.joinToString(separator = "\n", postfix = "\n") { item ->
                    item.toJsonLine(
                        exportCreatedAtMillis = createdAtMillis,
                        exportedArtifacts = item.copyArtifactsTo(mediaDirectory, copyArtifact),
                    )
                },
            )
            writeArchive(temporaryDirectory, temporaryArchive)
            moveOrThrow(temporaryDirectory, bundleDirectory)
            publishedDirectory = true
            moveOrThrow(temporaryArchive, archiveFile)
            bundleDirectory.deleteRecursively()
            return TrainingDatasetBundle(
                archiveFile = archiveFile,
                entryCount = eligibleItems.size,
                createdAtMillis = createdAtMillis,
            )
        } catch (exception: Exception) {
            temporaryArchive.delete()
            temporaryDirectory.deleteRecursively()
            if (publishedDirectory) bundleDirectory.deleteRecursively()
            archiveFile.delete()
            throw exception
        }
    }

    private fun deletePreviousExportOutputs(outputDirectory: File) {
        outputDirectory.listFiles()?.forEach { file ->
            val isDatasetDirectory = file.isDirectory && file.name.startsWith("dataset-")
            val isZipArchive = file.isFile && file.name.endsWith(".zip") && file.name.startsWith("dataset-")
            if (isDatasetDirectory || isZipArchive) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
        }
    }

    private fun writeArchive(
        bundleDirectory: File,
        archiveFile: File,
    ) {
        ZipOutputStream(archiveFile.outputStream().buffered()).use { output ->
            bundleDirectory
                .walkTopDown()
                .filter { file -> file.isFile }
                .sortedBy { file -> file.relativeTo(bundleDirectory).invariantSeparatorsPath }
                .forEach { file ->
                    output.putNextEntry(ZipEntry(file.relativeTo(bundleDirectory).invariantSeparatorsPath))
                    file.inputStream().buffered().use { input -> input.copyTo(output) }
                    output.closeEntry()
                }
        }
    }

    private fun moveOrThrow(
        source: File,
        destination: File,
    ) {
        if (!source.renameTo(destination)) {
            throw IOException("Cannot publish export output ${destination.name}.")
        }
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

    private fun TrainingQueueItem.copyArtifactsTo(
        mediaDirectory: File,
        copyArtifact: (source: File, destination: File) -> Unit,
    ): List<ExportedArtifact> {
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
                copyArtifact(sourceFile, exportedFile)
                ExportedArtifact(
                    source = artifact,
                    exportedPath = "media/${exportedFile.name}",
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
            appendProperty("schemaVersion", SCHEMA_VERSION)
            append(",")
            appendProperty("labelSource", LABEL_SOURCE)
            append(",")
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
