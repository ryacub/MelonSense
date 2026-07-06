package com.ryacub.melonsense.ui.screens

import com.ryacub.melonsense.R
import com.ryacub.melonsense.domain.model.VisualScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAssessmentStateTest {
    @Test
    fun ready_allowsCaptureButNotKnockTest() {
        val state = ScanAssessmentState(ScanAssessmentPhase.Ready)

        assertTrue(state.canCapture)
        assertFalse(state.canStartKnockTest)
        assertEquals(R.string.scan_primary_action, state.primaryActionRes)
    }

    @Test
    fun busyStates_disableActions() {
        listOf(ScanAssessmentPhase.Capturing, ScanAssessmentPhase.Analyzing).forEach { phase ->
            val state = ScanAssessmentState(phase)

            assertTrue(state.isBusy)
            assertFalse(state.canCapture)
            assertFalse(state.canStartKnockTest)
        }
    }

    @Test
    fun complete_enablesKnockTestAndRetake() {
        val state = ScanAssessmentState(ScanAssessmentPhase.Complete)

        assertTrue(state.canCapture)
        assertTrue(state.canStartKnockTest)
        assertEquals(R.string.scan_retake_action, state.primaryActionRes)
    }

    @Test
    fun failed_allowsRetakeOnly() {
        val state = ScanAssessmentState(ScanAssessmentPhase.Failed)

        assertTrue(state.canCapture)
        assertFalse(state.canStartKnockTest)
        assertEquals(R.string.scan_retake_action, state.primaryActionRes)
    }

    @Test
    fun reducer_ignoresDuplicateCaptureWhileBusy() {
        val state =
            ScanAssessmentState()
                .reduce(ScanAssessmentEvent.CaptureRequested)
                .reduce(ScanAssessmentEvent.CaptureRequested)

        assertEquals(ScanAssessmentPhase.Capturing, state.phase)
    }

    @Test
    fun reducer_successfulFlowCompletesVisualAssessment() {
        val state =
            ScanAssessmentState()
                .reduce(ScanAssessmentEvent.CaptureRequested)
                .reduce(ScanAssessmentEvent.CaptureSucceeded)
                .reduce(ScanAssessmentEvent.AnalysisSucceeded)

        assertEquals(ScanAssessmentPhase.Complete, state.phase)
        assertTrue(state.canStartKnockTest)
    }

    @Test
    fun reducer_captureFailureAllowsRetake() {
        val state =
            ScanAssessmentState()
                .reduce(ScanAssessmentEvent.CaptureRequested)
                .reduce(ScanAssessmentEvent.CaptureFailed)

        assertEquals(ScanAssessmentPhase.Failed, state.phase)
        assertTrue(state.canCapture)
        assertFalse(state.canStartKnockTest)
    }

    @Test
    fun reducer_analysisFailureAllowsRetake() {
        val state =
            ScanAssessmentState()
                .reduce(ScanAssessmentEvent.CaptureRequested)
                .reduce(ScanAssessmentEvent.CaptureSucceeded)
                .reduce(ScanAssessmentEvent.AnalysisFailed)

        assertEquals(ScanAssessmentPhase.Failed, state.phase)
        assertTrue(state.canCapture)
        assertFalse(state.canStartKnockTest)
    }

    @Test
    fun reducer_ignoresOutOfOrderAnalysisSuccess() {
        val state = ScanAssessmentState().reduce(ScanAssessmentEvent.AnalysisSucceeded)

        assertEquals(ScanAssessmentPhase.Ready, state.phase)
        assertFalse(state.canStartKnockTest)
    }

    @Test
    fun workflow_clearsStaleVisualResultWhenRetakeStarts() {
        val previousResult = sampleVisualResult(score = 80)
        val workflow =
            ScanAssessmentWorkflow(
                state = ScanAssessmentState(ScanAssessmentPhase.Complete),
                visualScanResult = previousResult,
            ).reduce(ScanAssessmentEvent.CaptureRequested)

        assertEquals(ScanAssessmentPhase.Capturing, workflow.state.phase)
        assertEquals(null, workflow.visualScanResult)
    }

    @Test
    fun workflow_clearsVisualResultWhenCaptureOrAnalysisFails() {
        val previousResult = sampleVisualResult(score = 80)
        val captureFailure =
            ScanAssessmentWorkflow(
                state = ScanAssessmentState(ScanAssessmentPhase.Capturing),
                visualScanResult = previousResult,
            ).reduce(ScanAssessmentEvent.CaptureFailed)
        val analysisFailure =
            ScanAssessmentWorkflow(
                state = ScanAssessmentState(ScanAssessmentPhase.Analyzing),
                visualScanResult = previousResult,
            ).reduce(ScanAssessmentEvent.AnalysisFailed)

        assertEquals(null, captureFailure.visualScanResult)
        assertEquals(null, analysisFailure.visualScanResult)
    }

    @Test
    fun workflow_storesVisualResultOnlyAfterSuccessfulAnalysis() {
        val result = sampleVisualResult(score = 91)
        val workflow =
            ScanAssessmentWorkflow(
                state = ScanAssessmentState(ScanAssessmentPhase.Analyzing),
            ).reduce(
                event = ScanAssessmentEvent.AnalysisSucceeded,
                result = result,
            )

        assertEquals(ScanAssessmentPhase.Complete, workflow.state.phase)
        assertEquals(result, workflow.visualScanResult)
    }

    @Test
    fun workflow_ignoresOutOfOrderAnalysisResult() {
        val previousResult = sampleVisualResult(score = 80)
        val newResult = sampleVisualResult(score = 91)
        val workflow =
            ScanAssessmentWorkflow(
                state = ScanAssessmentState(ScanAssessmentPhase.Ready),
                visualScanResult = previousResult,
            ).reduce(
                event = ScanAssessmentEvent.AnalysisSucceeded,
                result = newResult,
            )

        assertEquals(ScanAssessmentPhase.Ready, workflow.state.phase)
        assertEquals(previousResult, workflow.visualScanResult)
    }
}

private fun sampleVisualResult(score: Int): VisualScanResult =
    VisualScanResult(
        score = score,
        confidencePercent = 70,
        capturedAtMillis = 1L,
        evidence = emptyList(),
    )
