package com.ryacub.melonsense.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ryacub.melonsense.data.history.PickHistoryItem
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.SweetnessRating
import com.ryacub.melonsense.data.history.TextureRating
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.ui.theme.MelonSenseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistoryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun picksAndTrainingAreSeparated_withoutFabricatedVisualScore() {
        setHistoryContent()

        composeRule.onNodeWithText("Picks").assertIsDisplayed()
        composeRule.onNodeWithText("Good Candidate").assertIsDisplayed()
        composeRule.onNodeWithText("Sweet / Crisp").assertIsDisplayed()
        composeRule.onNodeWithText("Visual 0 / Audio 65").assertDoesNotExist()
        composeRule.onNodeWithText("Training Queue").assertDoesNotExist()

        composeRule.onNodeWithText("Training").performClick()

        composeRule.onNodeWithText("Training Queue").assertIsDisplayed()
        composeRule.onNodeWithText("Good Candidate").assertDoesNotExist()
    }

    @Test
    fun editOutcomeSheet_preservesSelectionsAndDismissesAfterSave() {
        var savedResult: ResultLabel? = null
        var savedSweetness: SweetnessRating? = null
        var savedTexture: TextureRating? = null
        setHistoryContent { _, result, sweetness, texture ->
            savedResult = result
            savedSweetness = sweetness
            savedTexture = texture
        }

        composeRule.onNodeWithText("Edit Outcome").performClick()
        composeRule.onNodeWithText("Outcome").assertIsDisplayed()
        composeRule.onNodeWithText("Save Outcome").performScrollTo().assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(ResultLabel.GoodCandidate, savedResult)
            assertEquals(SweetnessRating.Sweet, savedSweetness)
            assertEquals(TextureRating.Crisp, savedTexture)
        }
        composeRule.onNodeWithText("Outcome").assertDoesNotExist()
    }

    @Test
    fun addOutcomeSheet_requiresRatingsAndSavesExplicitSelections() {
        var savedResult: ResultLabel? = null
        var savedSweetness: SweetnessRating? = null
        var savedTexture: TextureRating? = null
        setHistoryContent(historyItems = listOf(pendingItem())) { _, result, sweetness, texture ->
            savedResult = result
            savedSweetness = sweetness
            savedTexture = texture
        }

        composeRule.onNodeWithText("Add Outcome").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Save Outcome").assertIsNotEnabled()
        composeRule.onNodeWithText("Sweet").performScrollTo().performClick()
        composeRule.onNodeWithText("Crisp").performScrollTo().performClick()
        composeRule.onNodeWithText("Save Outcome").performScrollTo().assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(ResultLabel.StrongPick, savedResult)
            assertEquals(SweetnessRating.Sweet, savedSweetness)
            assertEquals(TextureRating.Crisp, savedTexture)
        }
        composeRule.onNodeWithText("Outcome").assertDoesNotExist()
    }

    private fun setHistoryContent(
        historyItems: List<PickHistoryItem> = listOf(ratedItem()),
        onSaveOutcome: (Long, ResultLabel, SweetnessRating, TextureRating) -> Unit = { _, _, _, _ -> },
    ) {
        composeRule.setContent {
            MelonSenseTheme {
                HistoryScreen(
                    historyItems = historyItems,
                    trainingQueueItems = emptyList(),
                    trainingExportState = TrainingExportState(),
                    onExportTrainingDataset = {},
                    onShareTrainingDataset = {},
                    onSaveOutcome = onSaveOutcome,
                )
            }
        }
    }

    private fun ratedItem(): PickHistoryItem =
        PickHistoryItem(
            id = 7,
            createdAtMillis = 1_783_913_451_375,
            status = PickHistoryStatus.Rated,
            resultLabel = ResultLabel.GoodCandidate,
            sweetness = SweetnessRating.Sweet,
            texture = TextureRating.Crisp,
            visualScore = null,
            visualConfidencePercent = null,
            audioScore = 65,
            audioConfidencePercent = 75,
            validKnocks = 3,
            estimatedFrequencyHz = 220,
            finalConfidencePercent = 78,
            trainingExportStatus = TrainingExportStatus.Pending,
            trainingExportedAtMillis = null,
        )

    private fun pendingItem(): PickHistoryItem =
        ratedItem().copy(
            id = 8,
            status = PickHistoryStatus.PendingOutcome,
            resultLabel = ResultLabel.StrongPick,
            sweetness = null,
            texture = null,
        )
}
