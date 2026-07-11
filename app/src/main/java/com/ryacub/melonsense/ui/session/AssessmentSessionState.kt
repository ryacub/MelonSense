package com.ryacub.melonsense.ui.session

import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.ui.screens.KnockTestWorkflow
import com.ryacub.melonsense.ui.screens.PickedAssessmentSaveEvent
import com.ryacub.melonsense.ui.screens.PickedAssessmentSavePhase
import com.ryacub.melonsense.ui.screens.PickedAssessmentSaveState
import com.ryacub.melonsense.ui.screens.ScanAssessmentPhase
import com.ryacub.melonsense.ui.screens.ScanAssessmentState
import com.ryacub.melonsense.ui.screens.ScanAssessmentWorkflow
import com.ryacub.melonsense.ui.screens.reduce

data class AssessmentSessionState(
    val scanWorkflow: ScanAssessmentWorkflow = ScanAssessmentWorkflow(),
    val knockWorkflow: KnockTestWorkflow = KnockTestWorkflow(),
    val assessmentResult: MelonAssessmentResult? = null,
    val pickedAssessmentSaveState: PickedAssessmentSaveState = PickedAssessmentSaveState(),
) {
    fun onScanWorkflowChanged(nextWorkflow: ScanAssessmentWorkflow): AssessmentSessionState {
        val visualIdentityChanged = nextWorkflow.visualScanResult != scanWorkflow.visualScanResult
        return if (visualIdentityChanged) {
            copy(
                scanWorkflow = nextWorkflow,
                knockWorkflow = KnockTestWorkflow(),
                assessmentResult = null,
                pickedAssessmentSaveState = PickedAssessmentSaveState(),
            )
        } else {
            copy(scanWorkflow = nextWorkflow)
        }
    }

    fun onKnockWorkflowChanged(nextWorkflow: KnockTestWorkflow): AssessmentSessionState = copy(knockWorkflow = nextWorkflow)

    fun onAssessmentResult(result: MelonAssessmentResult): AssessmentSessionState {
        val activeVisualResult = scanWorkflow.visualScanResult
        if (activeVisualResult == null || result.visualScanResult != activeVisualResult) return this
        return copy(
            assessmentResult = result,
            pickedAssessmentSaveState =
                pickedAssessmentSaveState.reduce(PickedAssessmentSaveEvent.ResultChanged),
        )
    }

    fun onSaveEvent(event: PickedAssessmentSaveEvent): AssessmentSessionState =
        copy(pickedAssessmentSaveState = pickedAssessmentSaveState.reduce(event))

    fun restored(): AssessmentSessionState {
        val restoredScanWorkflow = scanWorkflow.restored()
        val activeVisualResult = restoredScanWorkflow.visualScanResult
        val restoredAssessment = assessmentResult?.takeIf { it.visualScanResult == activeVisualResult }
        return copy(
            scanWorkflow = restoredScanWorkflow,
            knockWorkflow = if (activeVisualResult == null) KnockTestWorkflow() else knockWorkflow.restored(),
            assessmentResult = restoredAssessment,
            pickedAssessmentSaveState =
                when {
                    restoredAssessment == null -> PickedAssessmentSaveState()
                    pickedAssessmentSaveState.phase == PickedAssessmentSavePhase.Saving -> PickedAssessmentSaveState()
                    else -> pickedAssessmentSaveState
                },
        )
    }
}

private fun ScanAssessmentWorkflow.restored(): ScanAssessmentWorkflow =
    when (state.phase) {
        ScanAssessmentPhase.Capturing,
        ScanAssessmentPhase.Analyzing,
        ->
            copy(
                state =
                    if (visualScanResult == null) {
                        ScanAssessmentState()
                    } else {
                        ScanAssessmentState(ScanAssessmentPhase.Complete)
                    },
            )
        else -> this
    }
