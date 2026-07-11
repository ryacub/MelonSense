package com.ryacub.melonsense.ui.session

import androidx.lifecycle.SavedStateHandle
import com.ryacub.melonsense.ui.screens.ScanAssessmentEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssessmentSessionViewModelTest {
    @Test
    fun updatedSession_isPersistedAndRestoredFromSavedStateHandle() {
        val handle = SavedStateHandle()
        val first = AssessmentSessionViewModel(handle)
        val visual = sampleVisualResult()

        first.onScanWorkflowChanged(completeScanWorkflow(visual))
        first.onAssessmentResult(sampleAssessment(visual))

        val recreated = AssessmentSessionViewModel(handle)

        assertEquals(visual, recreated.state.value.scanWorkflow.visualScanResult)
        assertEquals(sampleAssessment(visual), recreated.state.value.assessmentResult)
    }

    @Test
    fun retakeImmediatelyPersistsInvalidatedAssessment() {
        val handle = SavedStateHandle()
        val viewModel = AssessmentSessionViewModel(handle)
        val visual = sampleVisualResult()
        viewModel.onScanWorkflowChanged(completeScanWorkflow(visual))
        viewModel.onAssessmentResult(sampleAssessment(visual))

        viewModel.onScanWorkflowChanged(
            viewModel.state.value.scanWorkflow.reduce(ScanAssessmentEvent.CaptureRequested),
        )

        val recreated = AssessmentSessionViewModel(handle)
        assertNull(recreated.state.value.assessmentResult)
    }
}
