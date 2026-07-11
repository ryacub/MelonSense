package com.ryacub.melonsense.ui.screens

import com.ryacub.melonsense.domain.audio.KnockAudioAnalyzer
import com.ryacub.melonsense.domain.model.KnockCapture

enum class KnockTestPhase {
    Ready,
    Recording,
    Analyzing,
}

data class KnockTestState(
    val phase: KnockTestPhase = KnockTestPhase.Ready,
) {
    val isRecording: Boolean
        get() = phase == KnockTestPhase.Recording

    val isAnalyzing: Boolean
        get() = phase == KnockTestPhase.Analyzing

    fun canCapture(validKnockCount: Int): Boolean =
        phase == KnockTestPhase.Ready && validKnockCount < KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT

    fun canAnalyze(validKnockCount: Int): Boolean =
        phase == KnockTestPhase.Ready && validKnockCount >= KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT
}

enum class KnockTestEvent {
    CaptureRequested,
    CaptureFinished,
    CaptureFailed,
    AnalyzeRequested,
    AnalyzeFinished,
    AnalyzeFailed,
}

fun KnockTestState.reduce(event: KnockTestEvent): KnockTestState =
    when (event) {
        KnockTestEvent.CaptureRequested ->
            if (phase == KnockTestPhase.Ready) {
                KnockTestState(KnockTestPhase.Recording)
            } else {
                this
            }
        KnockTestEvent.CaptureFinished,
        KnockTestEvent.CaptureFailed,
        ->
            if (phase == KnockTestPhase.Recording) {
                KnockTestState()
            } else {
                this
            }
        KnockTestEvent.AnalyzeRequested ->
            if (phase == KnockTestPhase.Ready) {
                KnockTestState(KnockTestPhase.Analyzing)
            } else {
                this
            }
        KnockTestEvent.AnalyzeFinished,
        KnockTestEvent.AnalyzeFailed,
        ->
            if (phase == KnockTestPhase.Analyzing) {
                KnockTestState()
            } else {
                this
            }
    }

data class CapturedKnockWindow(
    val capture: KnockCapture,
    val samples: ShortArray,
    val capturedAtMillis: Long,
)

data class KnockTestWorkflow(
    val state: KnockTestState = KnockTestState(),
    val lastCapture: KnockCapture? = null,
    val validWindows: List<CapturedKnockWindow> = emptyList(),
) {
    val validKnocks: List<KnockCapture>
        get() = validWindows.map { it.capture }

    fun reduce(event: KnockTestEvent): KnockTestWorkflow = copy(state = state.reduce(event))

    fun captureFinished(window: CapturedKnockWindow): KnockTestWorkflow {
        if (state.phase != KnockTestPhase.Recording) return this
        val acceptedWindows =
            if (window.capture.isValid && validWindows.size < KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT) {
                validWindows + window
            } else {
                validWindows
            }
        return copy(
            state = state.reduce(KnockTestEvent.CaptureFinished),
            lastCapture = window.capture,
            validWindows = acceptedWindows,
        )
    }

    fun restored(): KnockTestWorkflow = copy(state = KnockTestState())
}
