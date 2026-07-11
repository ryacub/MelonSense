package com.ryacub.melonsense.ui.session

import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.VisualScanResult
import com.ryacub.melonsense.ui.screens.KnockTestEvent
import com.ryacub.melonsense.ui.screens.KnockTestPhase
import com.ryacub.melonsense.ui.screens.KnockTestState
import com.ryacub.melonsense.ui.screens.KnockTestWorkflow
import com.ryacub.melonsense.ui.screens.PickedAssessmentSavePhase
import com.ryacub.melonsense.ui.screens.PickedAssessmentSaveState
import com.ryacub.melonsense.ui.screens.ScanAssessmentEvent
import com.ryacub.melonsense.ui.screens.ScanAssessmentPhase
import com.ryacub.melonsense.ui.screens.ScanAssessmentState
import com.ryacub.melonsense.ui.screens.ScanAssessmentWorkflow
import com.ryacub.melonsense.ui.screens.sampleKnockWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssessmentSessionStateTest {
    @Test
    fun newCapture_invalidatesPreviousAssessmentAndKnocks() {
        val visual = sampleVisualResult(capturedAtMillis = 10L)
        val state = completedSession(visual)

        val next =
            state.onScanWorkflowChanged(
                state.scanWorkflow.reduce(ScanAssessmentEvent.CaptureRequested),
            )

        assertNull(next.assessmentResult)
        assertEquals(PickedAssessmentSavePhase.Idle, next.pickedAssessmentSaveState.phase)
        assertEquals(0, next.knockWorkflow.validWindows.size)
    }

    @Test
    fun mismatchedAssessmentResult_isRejected() {
        val activeVisual = sampleVisualResult(capturedAtMillis = 10L)
        val staleAssessment = sampleAssessment(sampleVisualResult(capturedAtMillis = 9L))
        val state =
            AssessmentSessionState(
                scanWorkflow = completeScanWorkflow(activeVisual),
            )

        val next = state.onAssessmentResult(staleAssessment)

        assertNull(next.assessmentResult)
    }

    @Test
    fun matchingAssessmentResult_isStoredAndResetsSaveState() {
        val visual = sampleVisualResult(capturedAtMillis = 10L)
        val result = sampleAssessment(visual)
        val state =
            AssessmentSessionState(
                scanWorkflow = completeScanWorkflow(visual),
                pickedAssessmentSaveState = PickedAssessmentSaveState(PickedAssessmentSavePhase.Saved),
            )

        val next = state.onAssessmentResult(result)

        assertEquals(result, next.assessmentResult)
        assertEquals(PickedAssessmentSavePhase.Idle, next.pickedAssessmentSaveState.phase)
    }

    @Test
    fun restored_normalizesInterruptedOperationsButPreservesStableProgress() {
        val visual = sampleVisualResult(capturedAtMillis = 10L)
        val state =
            completedSession(visual).copy(
                scanWorkflow =
                    ScanAssessmentWorkflow(
                        state = ScanAssessmentState(ScanAssessmentPhase.Analyzing),
                        visualScanResult = visual,
                    ),
                knockWorkflow =
                    completedSession(visual).knockWorkflow.copy(
                        state = KnockTestState(KnockTestPhase.Recording),
                    ),
                pickedAssessmentSaveState = PickedAssessmentSaveState(PickedAssessmentSavePhase.Saving),
            )

        val restored = state.restored()

        assertEquals(ScanAssessmentPhase.Complete, restored.scanWorkflow.state.phase)
        assertEquals(visual, restored.scanWorkflow.visualScanResult)
        assertEquals(KnockTestPhase.Ready, restored.knockWorkflow.state.phase)
        assertEquals(1, restored.knockWorkflow.validWindows.size)
        assertEquals(PickedAssessmentSavePhase.Idle, restored.pickedAssessmentSaveState.phase)
    }

    private fun completedSession(visual: VisualScanResult): AssessmentSessionState {
        val knockWorkflow =
            KnockTestWorkflow()
                .reduce(KnockTestEvent.CaptureRequested)
                .captureFinished(sampleKnockWindow())
        return AssessmentSessionState(
            scanWorkflow = completeScanWorkflow(visual),
            knockWorkflow = knockWorkflow,
            assessmentResult = sampleAssessment(visual),
            pickedAssessmentSaveState = PickedAssessmentSaveState(PickedAssessmentSavePhase.Saved),
        )
    }
}

internal fun completeScanWorkflow(visual: VisualScanResult): ScanAssessmentWorkflow =
    ScanAssessmentWorkflow(
        state = ScanAssessmentState(ScanAssessmentPhase.Complete),
        visualScanResult = visual,
    )

internal fun sampleVisualResult(capturedAtMillis: Long = 10L): VisualScanResult =
    VisualScanResult(
        score = 82,
        confidencePercent = 76,
        capturedAtMillis = capturedAtMillis,
        evidence = listOf("ripe"),
    )

internal fun sampleAssessment(visual: VisualScanResult): MelonAssessmentResult =
    MelonAssessmentResult(
        visualScanResult = visual,
        audioScanResult =
            AudioScanResult(
                score = 71,
                confidencePercent = 68,
                validKnocks = 3,
                estimatedFrequencyHz = 220,
                capturedAtMillis = 20L,
                evidence = listOf("clear resonance"),
            ),
        recommendation = "Good candidate",
        resultLabel = ResultLabel.GoodCandidate,
        confidencePercent = 73,
    )
