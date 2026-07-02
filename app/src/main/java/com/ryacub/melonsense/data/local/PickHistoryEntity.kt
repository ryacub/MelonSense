package com.ryacub.melonsense.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ryacub.melonsense.data.history.PickHistoryItem
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.SweetnessRating
import com.ryacub.melonsense.data.history.TextureRating
import com.ryacub.melonsense.domain.model.ResultLabel

@Entity(tableName = "pick_history")
data class PickHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
)

fun PickHistoryEntity.toHistoryItem(): PickHistoryItem =
    PickHistoryItem(
        id = id,
        createdAtMillis = createdAtMillis,
        status = status,
        resultLabel = resultLabel,
        sweetness = sweetness,
        texture = texture,
        visualScore = visualScore,
        visualConfidencePercent = visualConfidencePercent,
        audioScore = audioScore,
        audioConfidencePercent = audioConfidencePercent,
        validKnocks = validKnocks,
        estimatedFrequencyHz = estimatedFrequencyHz,
        finalConfidencePercent = finalConfidencePercent,
    )

fun PickHistoryItem.toEntity(): PickHistoryEntity =
    PickHistoryEntity(
        id = id,
        createdAtMillis = createdAtMillis,
        status = status,
        resultLabel = resultLabel,
        sweetness = sweetness,
        texture = texture,
        visualScore = visualScore,
        visualConfidencePercent = visualConfidencePercent,
        audioScore = audioScore,
        audioConfidencePercent = audioConfidencePercent,
        validKnocks = validKnocks,
        estimatedFrequencyHz = estimatedFrequencyHz,
        finalConfidencePercent = finalConfidencePercent,
    )
