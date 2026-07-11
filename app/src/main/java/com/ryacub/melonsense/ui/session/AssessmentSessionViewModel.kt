package com.ryacub.melonsense.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.ui.screens.CapturedKnockWindow
import com.ryacub.melonsense.ui.screens.KnockTestEvent
import com.ryacub.melonsense.ui.screens.KnockTestWorkflow
import com.ryacub.melonsense.ui.screens.PickedAssessmentSaveEvent
import com.ryacub.melonsense.ui.screens.ScanAssessmentEvent
import com.ryacub.melonsense.ui.screens.ScanAssessmentWorkflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AssessmentSessionViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val snapshotCodec = AssessmentSessionSnapshotCodec()
    private val restoredState =
        savedStateHandle.get<String>(SAVED_STATE_KEY)
            ?.let(snapshotCodec::decode)
            ?.restored()
            ?: AssessmentSessionState()
    private val mutableState = MutableStateFlow(restoredState)
    val state: StateFlow<AssessmentSessionState> = mutableState.asStateFlow()

    init {
        persist(restoredState)
    }

    fun onScanWorkflowChanged(workflow: ScanAssessmentWorkflow) {
        update { it.onScanWorkflowChanged(workflow) }
    }

    fun onScanEvent(
        event: ScanAssessmentEvent,
        result: com.ryacub.melonsense.domain.model.VisualScanResult? = null,
    ) {
        update { current ->
            current.onScanWorkflowChanged(current.scanWorkflow.reduce(event, result))
        }
    }

    fun onKnockWorkflowChanged(workflow: KnockTestWorkflow) {
        update { it.onKnockWorkflowChanged(workflow) }
    }

    fun onKnockEvent(event: KnockTestEvent) {
        update { current ->
            current.onKnockWorkflowChanged(current.knockWorkflow.reduce(event))
        }
    }

    fun onKnockCaptured(window: CapturedKnockWindow) {
        update { current ->
            current.onKnockWorkflowChanged(current.knockWorkflow.captureFinished(window))
        }
    }

    fun onAssessmentResult(result: MelonAssessmentResult) {
        update { it.onAssessmentResult(result) }
    }

    fun onSaveEvent(event: PickedAssessmentSaveEvent) {
        update { it.onSaveEvent(event) }
    }

    private fun update(transform: (AssessmentSessionState) -> AssessmentSessionState) {
        val next = transform(mutableState.value)
        mutableState.value = next
        persist(next)
    }

    private fun persist(state: AssessmentSessionState) {
        savedStateHandle[SAVED_STATE_KEY] = snapshotCodec.encode(state)
    }

    companion object {
        const val SAVED_STATE_KEY = "assessment_session_v1"
    }
}
