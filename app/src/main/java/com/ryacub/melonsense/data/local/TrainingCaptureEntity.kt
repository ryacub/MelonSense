package com.ryacub.melonsense.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.domain.model.PendingTrainingMedia
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind

@Entity(tableName = "training_capture")
data class TrainingCaptureEntity(
    @PrimaryKey
    val pickHistoryId: Long,
    val exportStatus: TrainingExportStatus,
    val exportedAtMillis: Long?,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val photoPath: String?,
    val photoMimeType: String?,
    val photoByteSize: Long?,
    val photoCapturedAtMillis: Long?,
    val photoLastModifiedAtMillis: Long?,
    val photoWidth: Int?,
    val photoHeight: Int?,
    val audioPath: String?,
    val audioMimeType: String?,
    val audioByteSize: Long?,
    val audioCapturedAtMillis: Long?,
    val audioLastModifiedAtMillis: Long?,
    val audioSampleRateHz: Int?,
    val audioDurationMillis: Long?,
)

fun PendingTrainingMedia.toEntity(pickHistoryId: Long): TrainingCaptureEntity =
    TrainingCaptureEntity(
        pickHistoryId = pickHistoryId,
        exportStatus = TrainingExportStatus.Pending,
        exportedAtMillis = null,
        createdAtMillis = createdAtMillis,
        expiresAtMillis = expiresAtMillis,
        photoPath = photoArtifact?.path,
        photoMimeType = photoArtifact?.mimeType,
        photoByteSize = photoArtifact?.byteSize,
        photoCapturedAtMillis = photoArtifact?.capturedAtMillis,
        photoLastModifiedAtMillis = photoArtifact?.lastModifiedAtMillis,
        photoWidth = photoArtifact?.width,
        photoHeight = photoArtifact?.height,
        audioPath = audioArtifact?.path,
        audioMimeType = audioArtifact?.mimeType,
        audioByteSize = audioArtifact?.byteSize,
        audioCapturedAtMillis = audioArtifact?.capturedAtMillis,
        audioLastModifiedAtMillis = audioArtifact?.lastModifiedAtMillis,
        audioSampleRateHz = audioArtifact?.sampleRateHz,
        audioDurationMillis = audioArtifact?.durationMillis,
    )

fun TrainingCaptureEntity.artifacts(): List<TrainingMediaArtifact> =
    listOfNotNull(
        photoPath?.let { path ->
            TrainingMediaArtifact(
                kind = TrainingMediaKind.Photo,
                path = path,
                mimeType = photoMimeType ?: "image/jpeg",
                byteSize = photoByteSize ?: 0,
                capturedAtMillis = photoCapturedAtMillis ?: createdAtMillis,
                lastModifiedAtMillis = photoLastModifiedAtMillis ?: createdAtMillis,
                width = photoWidth,
                height = photoHeight,
                sampleRateHz = null,
                durationMillis = null,
            )
        },
        audioPath?.let { path ->
            TrainingMediaArtifact(
                kind = TrainingMediaKind.Audio,
                path = path,
                mimeType = audioMimeType ?: "audio/pcm16+gzip",
                byteSize = audioByteSize ?: 0,
                capturedAtMillis = audioCapturedAtMillis ?: createdAtMillis,
                lastModifiedAtMillis = audioLastModifiedAtMillis ?: createdAtMillis,
                width = null,
                height = null,
                sampleRateHz = audioSampleRateHz,
                durationMillis = audioDurationMillis,
            )
        },
    )
