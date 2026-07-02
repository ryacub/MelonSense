package com.ryacub.melonsense.data.history

import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryHistoryRepository : HistoryRepository {
    private val items = MutableStateFlow<List<PickHistoryItem>>(emptyList())
    private var nextId = 1L

    override val historyItems: Flow<List<PickHistoryItem>> = items.asStateFlow()

    override suspend fun savePickedAssessment(assessmentResult: MelonAssessmentResult): Long {
        val id = nextId++
        val item = assessmentResult.toPendingHistoryItem(id = id)
        items.update { current -> listOf(item) + current }
        return id
    }

    override suspend fun saveOutcome(
        pickId: Long,
        resultLabel: ResultLabel,
        sweetness: SweetnessRating,
        texture: TextureRating,
    ) {
        items.update { current ->
            current.map { item ->
                if (item.id == pickId) {
                    item.copy(
                        status = PickHistoryStatus.Rated,
                        resultLabel = resultLabel,
                        sweetness = sweetness,
                        texture = texture,
                    )
                } else {
                    item
                }
            }
        }
    }

    override suspend fun getHistoryItem(pickId: Long): PickHistoryItem? = items.value.firstOrNull { item -> item.id == pickId }
}
