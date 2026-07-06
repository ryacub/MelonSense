package com.ryacub.melonsense.ui.screens

import androidx.annotation.StringRes
import com.ryacub.melonsense.R
import com.ryacub.melonsense.domain.model.VisualScanResult

enum class ScanAssessmentPhase {
    Ready,
    Capturing,
    Analyzing,
    Complete,
    Failed,
}

data class ScanAssessmentState(
    val phase: ScanAssessmentPhase = ScanAssessmentPhase.Ready,
) {
    val isBusy: Boolean
        get() = phase == ScanAssessmentPhase.Capturing || phase == ScanAssessmentPhase.Analyzing

    val canCapture: Boolean
        get() = !isBusy

    val canStartKnockTest: Boolean
        get() = phase == ScanAssessmentPhase.Complete

    @get:StringRes
    val titleRes: Int
        get() =
            when (phase) {
                ScanAssessmentPhase.Ready -> R.string.scan_ready_state
                ScanAssessmentPhase.Capturing -> R.string.scan_capturing_state
                ScanAssessmentPhase.Analyzing -> R.string.scan_analyzing_state
                ScanAssessmentPhase.Complete -> R.string.scan_complete_state
                ScanAssessmentPhase.Failed -> R.string.scan_failed_state
            }

    @get:StringRes
    val bodyRes: Int
        get() =
            when (phase) {
                ScanAssessmentPhase.Ready -> R.string.scan_body
                ScanAssessmentPhase.Capturing -> R.string.scan_capturing_body
                ScanAssessmentPhase.Analyzing -> R.string.scan_analyzing_body
                ScanAssessmentPhase.Complete -> R.string.scan_complete_body
                ScanAssessmentPhase.Failed -> R.string.scan_failed_body
            }

    @get:StringRes
    val primaryActionRes: Int
        get() =
            when (phase) {
                ScanAssessmentPhase.Complete,
                ScanAssessmentPhase.Failed,
                -> R.string.scan_retake_action
                else -> R.string.scan_primary_action
            }
}

enum class ScanAssessmentEvent {
    CaptureRequested,
    CaptureFailed,
    CaptureSucceeded,
    AnalysisFailed,
    AnalysisSucceeded,
}

fun ScanAssessmentState.reduce(event: ScanAssessmentEvent): ScanAssessmentState =
    when (event) {
        ScanAssessmentEvent.CaptureRequested ->
            if (canCapture) {
                ScanAssessmentState(ScanAssessmentPhase.Capturing)
            } else {
                this
            }
        ScanAssessmentEvent.CaptureFailed -> ScanAssessmentState(ScanAssessmentPhase.Failed)
        ScanAssessmentEvent.CaptureSucceeded ->
            if (phase == ScanAssessmentPhase.Capturing) {
                ScanAssessmentState(ScanAssessmentPhase.Analyzing)
            } else {
                this
            }
        ScanAssessmentEvent.AnalysisFailed -> ScanAssessmentState(ScanAssessmentPhase.Failed)
        ScanAssessmentEvent.AnalysisSucceeded ->
            if (phase == ScanAssessmentPhase.Analyzing) {
                ScanAssessmentState(ScanAssessmentPhase.Complete)
            } else {
                this
            }
    }

data class ScanAssessmentWorkflow(
    val state: ScanAssessmentState = ScanAssessmentState(),
    val visualScanResult: VisualScanResult? = null,
) {
    fun reduce(
        event: ScanAssessmentEvent,
        result: VisualScanResult? = null,
    ): ScanAssessmentWorkflow {
        val nextState = state.reduce(event)
        val nextResult =
            when (event) {
                ScanAssessmentEvent.CaptureRequested,
                ScanAssessmentEvent.CaptureFailed,
                ScanAssessmentEvent.AnalysisFailed,
                -> null
                ScanAssessmentEvent.AnalysisSucceeded ->
                    if (state.phase == ScanAssessmentPhase.Analyzing && nextState.phase == ScanAssessmentPhase.Complete) {
                        result
                    } else {
                        visualScanResult
                    }
                ScanAssessmentEvent.CaptureSucceeded -> visualScanResult
            }
        return copy(
            state = nextState,
            visualScanResult = nextResult,
        )
    }
}
