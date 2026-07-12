package com.ryacub.melonsense.ui.screens

import com.ryacub.melonsense.data.history.PickHistoryItem
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.SweetnessRating
import com.ryacub.melonsense.data.history.TextureRating
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.domain.model.ResultLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeEditorStateTest {
    @Test
    fun pendingOutcome_startsWithoutFabricatedRatings() {
        val state = OutcomeEditorState.from(sampleHistoryItem())

        assertEquals(ResultLabel.GoodCandidate, state.resultLabel)
        assertNull(state.sweetness)
        assertNull(state.texture)
        assertFalse(state.canSave)
    }

    @Test
    fun saveRequiresBothExplicitRatings() {
        val initial = OutcomeEditorState.from(sampleHistoryItem())

        val sweetnessOnly = initial.selectSweetness(SweetnessRating.Sweet)
        val complete = sweetnessOnly.selectTexture(TextureRating.Crisp)

        assertFalse(sweetnessOnly.canSave)
        assertTrue(complete.canSave)
        assertEquals(SweetnessRating.Sweet, complete.sweetness)
        assertEquals(TextureRating.Crisp, complete.texture)
    }

    @Test
    fun ratedOutcome_preservesExistingSelections() {
        val state =
            OutcomeEditorState.from(
                sampleHistoryItem(
                    status = PickHistoryStatus.Rated,
                    resultLabel = ResultLabel.StrongPick,
                    sweetness = SweetnessRating.VerySweet,
                    texture = TextureRating.VeryCrisp,
                ),
            )

        assertEquals(ResultLabel.StrongPick, state.resultLabel)
        assertEquals(SweetnessRating.VerySweet, state.sweetness)
        assertEquals(TextureRating.VeryCrisp, state.texture)
        assertTrue(state.canSave)
    }

    private fun sampleHistoryItem(
        status: PickHistoryStatus = PickHistoryStatus.PendingOutcome,
        resultLabel: ResultLabel = ResultLabel.GoodCandidate,
        sweetness: SweetnessRating? = null,
        texture: TextureRating? = null,
    ): PickHistoryItem =
        PickHistoryItem(
            id = 1L,
            createdAtMillis = 10L,
            status = status,
            resultLabel = resultLabel,
            sweetness = sweetness,
            texture = texture,
            visualScore = 70,
            visualConfidencePercent = 80,
            audioScore = 65,
            audioConfidencePercent = 75,
            validKnocks = 3,
            estimatedFrequencyHz = 220,
            finalConfidencePercent = 78,
            trainingExportStatus = TrainingExportStatus.Pending,
            trainingExportedAtMillis = null,
        )
}
