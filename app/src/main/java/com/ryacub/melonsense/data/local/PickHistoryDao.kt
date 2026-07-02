package com.ryacub.melonsense.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PickHistoryDao {
    @Query("SELECT * FROM pick_history ORDER BY createdAtMillis DESC")
    fun observeHistory(): Flow<List<PickHistoryEntity>>

    @Query("SELECT * FROM pick_history WHERE id = :pickId")
    suspend fun getById(pickId: Long): PickHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PickHistoryEntity): Long

    @Query(
        """
        UPDATE pick_history
        SET status = :status,
            resultLabel = :resultLabel,
            sweetness = :sweetness,
            texture = :texture
        WHERE id = :pickId
        """,
    )
    suspend fun updateOutcome(
        pickId: Long,
        status: String,
        resultLabel: String,
        sweetness: String,
        texture: String,
    )
}
