package com.ryacub.melonsense.data.history

import com.ryacub.melonsense.domain.model.ResultLabel

enum class PickHistoryStatus {
    PendingOutcome,
    Rated,
    ExpiredMedia,
}

enum class SweetnessRating {
    Bland,
    Mild,
    Good,
    Sweet,
    VerySweet,
}

enum class TextureRating {
    Mushy,
    Soft,
    Okay,
    Crisp,
    VeryCrisp,
}

enum class TrainingExportStatus {
    NotCaptured,
    Pending,
    Exported,
    Expired,
}

data class PickHistoryItem(
    val id: Long,
    val createdAtMillis: Long,
    val status: PickHistoryStatus,
    val resultLabel: ResultLabel,
    val sweetness: SweetnessRating?,
    val texture: TextureRating?,
    val visualScore: Int?,
    val visualConfidencePercent: Int?,
    val audioScore: Int,
    val audioConfidencePercent: Int,
    val validKnocks: Int,
    val estimatedFrequencyHz: Int,
    val finalConfidencePercent: Int,
    val trainingExportStatus: TrainingExportStatus,
    val trainingExportedAtMillis: Long?,
)
