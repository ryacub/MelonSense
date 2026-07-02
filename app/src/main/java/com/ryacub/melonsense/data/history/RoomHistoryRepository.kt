package com.ryacub.melonsense.data.history

import com.ryacub.melonsense.data.local.PickHistoryDao
import com.ryacub.melonsense.data.local.toEntity
import com.ryacub.melonsense.data.local.toHistoryItem
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHistoryRepository(
    private val pickHistoryDao: PickHistoryDao,
) : HistoryRepository {
    override val historyItems: Flow<List<PickHistoryItem>> =
        pickHistoryDao.observeHistory().map { entities ->
            entities.map { entity -> entity.toHistoryItem() }
        }

    override suspend fun savePickedAssessment(assessmentResult: MelonAssessmentResult): Long =
        pickHistoryDao.insert(
            assessmentResult
                .toPendingHistoryItem(id = 0)
                .toEntity(),
        )

    override suspend fun saveOutcome(
        pickId: Long,
        resultLabel: ResultLabel,
        sweetness: SweetnessRating,
        texture: TextureRating,
    ) {
        pickHistoryDao.updateOutcome(
            pickId = pickId,
            status = PickHistoryStatus.Rated.name,
            resultLabel = resultLabel.name,
            sweetness = sweetness.name,
            texture = texture.name,
        )
    }

    override suspend fun getHistoryItem(pickId: Long): PickHistoryItem? = pickHistoryDao.getById(pickId)?.toHistoryItem()
}
