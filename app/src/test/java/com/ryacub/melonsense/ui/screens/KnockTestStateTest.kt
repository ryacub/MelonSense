package com.ryacub.melonsense.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnockTestStateTest {
    @Test
    fun ready_allowsCaptureButNotAnalyzeWithoutThreeValidKnocks() {
        val state = KnockTestState()

        assertTrue(state.canCapture(validKnockCount = 0))
        assertFalse(state.canAnalyze(validKnockCount = 0))
    }

    @Test
    fun recording_blocksCaptureAndAnalyze() {
        val state = KnockTestState().reduce(KnockTestEvent.CaptureRequested)

        assertFalse(state.canCapture(validKnockCount = 0))
        assertFalse(state.canAnalyze(validKnockCount = 3))
    }

    @Test
    fun captureFailure_returnsToReadySoUserCanRetry() {
        val state =
            KnockTestState()
                .reduce(KnockTestEvent.CaptureRequested)
                .reduce(KnockTestEvent.CaptureFailed)

        assertTrue(state.canCapture(validKnockCount = 0))
    }

    @Test
    fun threeValidKnocks_allowsAnalyzeAndStopsMoreCapture() {
        val state = KnockTestState()

        assertFalse(state.canCapture(validKnockCount = 3))
        assertTrue(state.canAnalyze(validKnockCount = 3))
    }

    @Test
    fun analyzing_blocksDuplicateAnalyzeAndCapture() {
        val state = KnockTestState().reduce(KnockTestEvent.AnalyzeRequested)

        assertFalse(state.canAnalyze(validKnockCount = 3))
        assertFalse(state.canCapture(validKnockCount = 2))
    }

    @Test
    fun analyzeFailure_returnsToReadySoUserCanRetry() {
        val state =
            KnockTestState()
                .reduce(KnockTestEvent.AnalyzeRequested)
                .reduce(KnockTestEvent.AnalyzeFailed)

        assertTrue(state.canAnalyze(validKnockCount = 3))
    }
}
