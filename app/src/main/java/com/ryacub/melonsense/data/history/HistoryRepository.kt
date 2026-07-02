package com.ryacub.melonsense.data.history

import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    val historyItems: Flow<List<PickHistoryItem>>

    suspend fun savePickedAssessment(assessmentResult: MelonAssessmentResult): Long

    suspend fun saveOutcome(
        pickId: Long,
        resultLabel: ResultLabel,
        sweetness: SweetnessRating,
        texture: TextureRating,
    )

    suspend fun getHistoryItem(pickId: Long): PickHistoryItem?
}
