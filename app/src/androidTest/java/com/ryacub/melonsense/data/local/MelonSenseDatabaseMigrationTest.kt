package com.ryacub.melonsense.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ryacub.melonsense.data.history.TrainingExportStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MelonSenseDatabaseMigrationTest {
    private val databaseName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationFrom1To2_preservesHistoryAndAddsTrainingDefaults() =
        runBlocking {
            context.getDatabasePath(databaseName).parentFile?.mkdirs()
            SQLiteDatabase
                .openOrCreateDatabase(context.getDatabasePath(databaseName), null)
                .apply {
                    createVersionOneHistorySchema()
                    insertVersionOneHistoryRow()
                    version = 1
                    close()
                }

            val migratedDatabase =
                Room
                    .databaseBuilder(
                        context,
                        MelonSenseDatabase::class.java,
                        databaseName,
                    )
                    .addMigrations(MelonSenseDatabase.MIGRATION_1_2)
                    .build()
            val migratedItem = migratedDatabase.pickHistoryDao().getById(1)

            requireNotNull(migratedItem)
            assertEquals(TrainingExportStatus.NotCaptured, migratedItem.trainingExportStatus)
            assertEquals(null, migratedItem.trainingExportedAtMillis)
            migratedDatabase.close()
        }

    private fun SQLiteDatabase.createVersionOneHistorySchema() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS pick_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                createdAtMillis INTEGER NOT NULL,
                status TEXT NOT NULL,
                resultLabel TEXT NOT NULL,
                sweetness TEXT,
                texture TEXT,
                visualScore INTEGER,
                visualConfidencePercent INTEGER,
                audioScore INTEGER NOT NULL,
                audioConfidencePercent INTEGER NOT NULL,
                validKnocks INTEGER NOT NULL,
                estimatedFrequencyHz INTEGER NOT NULL,
                finalConfidencePercent INTEGER NOT NULL
            )
            """,
        )
    }

    private fun SQLiteDatabase.insertVersionOneHistoryRow() {
        execSQL(
            """
            INSERT INTO pick_history (
                id,
                createdAtMillis,
                status,
                resultLabel,
                sweetness,
                texture,
                visualScore,
                visualConfidencePercent,
                audioScore,
                audioConfidencePercent,
                validKnocks,
                estimatedFrequencyHz,
                finalConfidencePercent
            ) VALUES (
                1,
                1788000000000,
                'PendingOutcome',
                'GoodCandidate',
                NULL,
                NULL,
                72,
                64,
                81,
                88,
                3,
                142,
                77
            )
            """,
        )
    }
}
