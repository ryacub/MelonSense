package com.ryacub.melonsense.domain.model

enum class TrainingMediaKind {
    Photo,
    Audio,
}

data class TrainingMediaArtifact(
    val kind: TrainingMediaKind,
    val path: String,
    val mimeType: String,
    val byteSize: Long,
    val capturedAtMillis: Long,
    val lastModifiedAtMillis: Long,
    val width: Int?,
    val height: Int?,
    val sampleRateHz: Int?,
    val durationMillis: Long?,
)

data class PendingTrainingMedia(
    val photoArtifact: TrainingMediaArtifact?,
    val audioArtifact: TrainingMediaArtifact?,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
)
