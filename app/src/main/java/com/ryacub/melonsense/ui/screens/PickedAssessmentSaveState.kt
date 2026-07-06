package com.ryacub.melonsense.ui.screens

import androidx.annotation.StringRes
import com.ryacub.melonsense.R

enum class PickedAssessmentSavePhase {
    Idle,
    Saving,
    Saved,
}

data class PickedAssessmentSaveState(
    val phase: PickedAssessmentSavePhase = PickedAssessmentSavePhase.Idle,
) {
    val canSave: Boolean
        get() = phase == PickedAssessmentSavePhase.Idle

    @get:StringRes
    val actionRes: Int
        get() =
            when (phase) {
                PickedAssessmentSavePhase.Idle -> R.string.result_primary_action
                PickedAssessmentSavePhase.Saving -> R.string.result_saving_action
                PickedAssessmentSavePhase.Saved -> R.string.result_saved_action
            }
}

enum class PickedAssessmentSaveEvent {
    ResultChanged,
    SaveRequested,
    SaveSucceeded,
    SaveFailed,
}

fun PickedAssessmentSaveState.reduce(event: PickedAssessmentSaveEvent): PickedAssessmentSaveState =
    when (event) {
        PickedAssessmentSaveEvent.ResultChanged -> PickedAssessmentSaveState()
        PickedAssessmentSaveEvent.SaveRequested ->
            if (canSave) {
                PickedAssessmentSaveState(PickedAssessmentSavePhase.Saving)
            } else {
                this
            }
        PickedAssessmentSaveEvent.SaveSucceeded ->
            if (phase == PickedAssessmentSavePhase.Saving) {
                PickedAssessmentSaveState(PickedAssessmentSavePhase.Saved)
            } else {
                this
            }
        PickedAssessmentSaveEvent.SaveFailed ->
            if (phase == PickedAssessmentSavePhase.Saving) {
                PickedAssessmentSaveState(PickedAssessmentSavePhase.Idle)
            } else {
                this
            }
    }
