package com.ryacub.melonsense.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.SweetnessRating
import com.ryacub.melonsense.data.history.TextureRating
import com.ryacub.melonsense.domain.model.ResultLabel

@Database(
    entities = [PickHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(HistoryConverters::class)
abstract class MelonSenseDatabase : RoomDatabase() {
    abstract fun pickHistoryDao(): PickHistoryDao

    companion object {
        @Volatile
        private var instance: MelonSenseDatabase? = null

        fun getInstance(context: Context): MelonSenseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        MelonSenseDatabase::class.java,
                        "melonsense.db",
                    )
                    .build()
                    .also { database -> instance = database }
            }
    }
}

class HistoryConverters {
    @TypeConverter
    fun pickHistoryStatusToString(value: PickHistoryStatus?): String? = value?.name

    @TypeConverter
    fun stringToPickHistoryStatus(value: String?): PickHistoryStatus? = value?.let(PickHistoryStatus::valueOf)

    @TypeConverter
    fun resultLabelToString(value: ResultLabel?): String? = value?.name

    @TypeConverter
    fun stringToResultLabel(value: String?): ResultLabel? = value?.let(ResultLabel::valueOf)

    @TypeConverter
    fun sweetnessRatingToString(value: SweetnessRating?): String? = value?.name

    @TypeConverter
    fun stringToSweetnessRating(value: String?): SweetnessRating? = value?.let(SweetnessRating::valueOf)

    @TypeConverter
    fun textureRatingToString(value: TextureRating?): String? = value?.name

    @TypeConverter
    fun stringToTextureRating(value: String?): TextureRating? = value?.let(TextureRating::valueOf)
}
