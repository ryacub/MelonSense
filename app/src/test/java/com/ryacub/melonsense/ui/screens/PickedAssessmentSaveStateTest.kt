package com.ryacub.melonsense.ui.screens

import com.ryacub.melonsense.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickedAssessmentSaveStateTest {
    @Test
    fun idle_allowsSavingPickedAssessment() {
        val state = PickedAssessmentSaveState()

        assertTrue(state.canSave)
        assertEquals(R.string.result_primary_action, state.actionRes)
    }

    @Test
    fun saveRequested_disablesDuplicateSaves() {
        val state =
            PickedAssessmentSaveState()
                .reduce(PickedAssessmentSaveEvent.SaveRequested)
                .reduce(PickedAssessmentSaveEvent.SaveRequested)

        assertEquals(PickedAssessmentSavePhase.Saving, state.phase)
        assertFalse(state.canSave)
        assertEquals(R.string.result_saving_action, state.actionRes)
    }

    @Test
    fun saveSucceeded_marksPickSaved() {
        val state =
            PickedAssessmentSaveState()
                .reduce(PickedAssessmentSaveEvent.SaveRequested)
                .reduce(PickedAssessmentSaveEvent.SaveSucceeded)

        assertEquals(PickedAssessmentSavePhase.Saved, state.phase)
        assertFalse(state.canSave)
        assertEquals(R.string.result_saved_action, state.actionRes)
    }

    @Test
    fun saveFailed_allowsRetry() {
        val state =
            PickedAssessmentSaveState()
                .reduce(PickedAssessmentSaveEvent.SaveRequested)
                .reduce(PickedAssessmentSaveEvent.SaveFailed)

        assertEquals(PickedAssessmentSavePhase.Idle, state.phase)
        assertTrue(state.canSave)
    }

    @Test
    fun resultChanged_resetsSavedStateForNextAssessment() {
        val state =
            PickedAssessmentSaveState(PickedAssessmentSavePhase.Saved)
                .reduce(PickedAssessmentSaveEvent.ResultChanged)

        assertEquals(PickedAssessmentSavePhase.Idle, state.phase)
        assertTrue(state.canSave)
    }
}
