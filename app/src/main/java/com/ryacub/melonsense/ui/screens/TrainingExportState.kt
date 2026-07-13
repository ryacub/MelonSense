package com.ryacub.melonsense.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

enum class TrainingExportPhase {
    Idle,
    Running,
    Succeeded,
    Failed,
}

sealed interface TrainingExportEvent {
    data object Requested : TrainingExportEvent

    data class Succeeded(
        val archivePath: String,
        val entryCount: Int,
    ) : TrainingExportEvent

    data object Failed : TrainingExportEvent

    data object Cancelled : TrainingExportEvent

    data object ShareStarted : TrainingExportEvent

    data object ShareFailed : TrainingExportEvent
}

data class TrainingExportState(
    val phase: TrainingExportPhase = TrainingExportPhase.Idle,
    val archivePath: String? = null,
    val entryCount: Int? = null,
    val shareFailed: Boolean = false,
) {
    val canStart: Boolean
        get() = phase != TrainingExportPhase.Running

    val canShare: Boolean
        get() = phase == TrainingExportPhase.Succeeded && archivePath != null

    fun reduce(event: TrainingExportEvent): TrainingExportState =
        when (event) {
            TrainingExportEvent.Requested -> if (canStart) TrainingExportState(TrainingExportPhase.Running) else this
            is TrainingExportEvent.Succeeded ->
                if (phase == TrainingExportPhase.Running) {
                    TrainingExportState(
                        phase = TrainingExportPhase.Succeeded,
                        archivePath = event.archivePath,
                        entryCount = event.entryCount,
                    )
                } else {
                    this
                }
            TrainingExportEvent.Failed ->
                if (phase == TrainingExportPhase.Running) {
                    TrainingExportState(
                        TrainingExportPhase.Failed,
                    )
                } else {
                    this
                }
            TrainingExportEvent.Cancelled -> if (phase == TrainingExportPhase.Running) TrainingExportState() else this
            TrainingExportEvent.ShareStarted -> if (canShare) copy(shareFailed = false) else this
            TrainingExportEvent.ShareFailed -> if (canShare) copy(shareFailed = true) else this
        }
}

data class TrainingExportShareSpec(
    val archiveFile: File,
    val mimeType: String,
    val grantReadPermission: Boolean,
)

object TrainingExportIntentFactory {
    fun create(archiveFile: File): TrainingExportShareSpec {
        require(archiveFile.extension.equals("zip", ignoreCase = true)) { "Only ZIP exports can be shared." }
        return TrainingExportShareSpec(
            archiveFile = archiveFile,
            mimeType = "application/zip",
            grantReadPermission = true,
        )
    }

    fun createIntent(
        context: Context,
        spec: TrainingExportShareSpec,
    ): Intent {
        val archiveUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.training-exports",
                spec.archiveFile,
            )
        return Intent(Intent.ACTION_SEND)
            .setType(spec.mimeType)
            .putExtra(Intent.EXTRA_STREAM, archiveUri)
            .apply { clipData = ClipData.newRawUri(spec.archiveFile.name, archiveUri) }
            .apply {
                if (spec.grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }

    fun hasShareTarget(
        context: Context,
        intent: Intent,
    ): Boolean =
        context.packageManager
            .queryIntentActivities(intent, 0)
            .isNotEmpty()
}
