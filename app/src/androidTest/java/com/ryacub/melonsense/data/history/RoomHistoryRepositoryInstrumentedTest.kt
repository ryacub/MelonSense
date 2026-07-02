package com.ryacub.melonsense.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ryacub.melonsense.data.local.MelonSenseDatabase
import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHistoryRepositoryInstrumentedTest {
    private val databaseName = "history-persistence-test.db"
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
    fun savedPickSurvivesDatabaseReopen() =
        runBlocking {
            val firstDatabase = openDatabase()
            val savedId =
                RoomHistoryRepository(firstDatabase.pickHistoryDao())
                    .savePickedAssessment(sampleAssessment())
            firstDatabase.close()

            val reopenedDatabase = openDatabase()
            val savedItem =
                RoomHistoryRepository(reopenedDatabase.pickHistoryDao())
                    .getHistoryItem(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(PickHistoryStatus.PendingOutcome, savedItem.status)
            assertEquals(ResultLabel.GoodCandidate, savedItem.resultLabel)
            assertEquals(72, savedItem.visualScore)
            assertEquals(81, savedItem.audioScore)
            reopenedDatabase.close()
        }

    @Test
    fun savedOutcomeSurvivesDatabaseReopen() =
        runBlocking {
            val firstDatabase = openDatabase()
            val firstRepository = RoomHistoryRepository(firstDatabase.pickHistoryDao())
            val savedId = firstRepository.savePickedAssessment(sampleAssessment())

            firstRepository.saveOutcome(
                pickId = savedId,
                resultLabel = ResultLabel.StrongPick,
                sweetness = SweetnessRating.VerySweet,
                texture = TextureRating.Crisp,
            )
            firstDatabase.close()

            val reopenedDatabase = openDatabase()
            val savedItem =
                RoomHistoryRepository(reopenedDatabase.pickHistoryDao())
                    .getHistoryItem(savedId)

            assertNotNull(savedItem)
            requireNotNull(savedItem)
            assertEquals(PickHistoryStatus.Rated, savedItem.status)
            assertEquals(ResultLabel.StrongPick, savedItem.resultLabel)
            assertEquals(SweetnessRating.VerySweet, savedItem.sweetness)
            assertEquals(TextureRating.Crisp, savedItem.texture)
            reopenedDatabase.close()
        }

    private fun openDatabase(): MelonSenseDatabase =
        Room
            .databaseBuilder(
                context,
                MelonSenseDatabase::class.java,
                databaseName,
            )
            .build()

    private fun sampleAssessment(): MelonAssessmentResult =
        MelonAssessmentResult(
            visualScanResult =
                VisualScanResult(
                    score = 72,
                    confidencePercent = 64,
                    capturedAtMillis = 1_788_000_000_000,
                    evidence = listOf("visual placeholder"),
                ),
            audioScanResult =
                AudioScanResult(
                    score = 81,
                    confidencePercent = 88,
                    validKnocks = 3,
                    estimatedFrequencyHz = 142,
                    capturedAtMillis = 1_788_000_001_000,
                    evidence = listOf("audio placeholder"),
                ),
            recommendation = "Good Candidate",
            resultLabel = ResultLabel.GoodCandidate,
            confidencePercent = 77,
        )
}
