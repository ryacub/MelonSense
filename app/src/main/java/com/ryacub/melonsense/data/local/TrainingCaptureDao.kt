package com.ryacub.melonsense.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ryacub.melonsense.data.history.TrainingExportStatus

@Dao
interface TrainingCaptureDao {
    @Query("SELECT * FROM training_capture WHERE pickHistoryId = :pickHistoryId")
    suspend fun getByPickHistoryId(pickHistoryId: Long): TrainingCaptureEntity?

    @Query("SELECT * FROM training_capture")
    suspend fun getAll(): List<TrainingCaptureEntity>

    @Query("SELECT * FROM training_capture WHERE expiresAtMillis <= :nowMillis AND exportStatus != :expiredStatus")
    suspend fun getExpired(
        nowMillis: Long,
        expiredStatus: String = TrainingExportStatus.Expired.name,
    ): List<TrainingCaptureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrainingCaptureEntity)

    @Query(
        """
        UPDATE training_capture
        SET exportStatus = :exportStatus,
            exportedAtMillis = :exportedAtMillis
        WHERE pickHistoryId = :pickHistoryId
        """,
    )
    suspend fun updateExportStatus(
        pickHistoryId: Long,
        exportStatus: String,
        exportedAtMillis: Long?,
    )
}
