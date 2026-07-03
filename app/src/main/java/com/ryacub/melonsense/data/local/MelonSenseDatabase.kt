package com.ryacub.melonsense.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.SweetnessRating
import com.ryacub.melonsense.data.history.TextureRating
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.domain.model.ResultLabel

@Database(
    entities = [PickHistoryEntity::class, TrainingCaptureEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(HistoryConverters::class)
abstract class MelonSenseDatabase : RoomDatabase() {
    abstract fun pickHistoryDao(): PickHistoryDao

    abstract fun trainingCaptureDao(): TrainingCaptureDao

    companion object {
        @Volatile
        private var instance: MelonSenseDatabase? = null

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        ALTER TABLE pick_history
                        ADD COLUMN trainingExportStatus TEXT NOT NULL DEFAULT 'NotCaptured'
                        """,
                    )
                    db.execSQL(
                        """
                        ALTER TABLE pick_history
                        ADD COLUMN trainingExportedAtMillis INTEGER
                        """,
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS training_capture (
                            pickHistoryId INTEGER NOT NULL,
                            exportStatus TEXT NOT NULL,
                            exportedAtMillis INTEGER,
                            createdAtMillis INTEGER NOT NULL,
                            expiresAtMillis INTEGER NOT NULL,
                            photoPath TEXT,
                            photoMimeType TEXT,
                            photoByteSize INTEGER,
                            photoCapturedAtMillis INTEGER,
                            photoLastModifiedAtMillis INTEGER,
                            photoWidth INTEGER,
                            photoHeight INTEGER,
                            audioPath TEXT,
                            audioMimeType TEXT,
                            audioByteSize INTEGER,
                            audioCapturedAtMillis INTEGER,
                            audioLastModifiedAtMillis INTEGER,
                            audioSampleRateHz INTEGER,
                            audioDurationMillis INTEGER,
                            PRIMARY KEY(pickHistoryId)
                        )
                        """,
                    )
                }
            }

        fun getInstance(context: Context): MelonSenseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        MelonSenseDatabase::class.java,
                        "melonsense.db",
                    )
                    .addMigrations(MIGRATION_1_2)
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

    @TypeConverter
    fun trainingExportStatusToString(value: TrainingExportStatus?): String? = value?.name

    @TypeConverter
    fun stringToTrainingExportStatus(value: String?): TrainingExportStatus? = value?.let(TrainingExportStatus::valueOf)
}
