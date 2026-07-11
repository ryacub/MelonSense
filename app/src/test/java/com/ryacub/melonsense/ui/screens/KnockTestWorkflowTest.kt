package com.ryacub.melonsense.ui.screens

import com.ryacub.melonsense.domain.model.KnockCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnockTestWorkflowTest {
    @Test
    fun validCapture_isRetainedForLaterAnalysis() {
        val window = sampleKnockWindow()

        val workflow =
            KnockTestWorkflow()
                .reduce(KnockTestEvent.CaptureRequested)
                .captureFinished(window)

        assertEquals(KnockTestPhase.Ready, workflow.state.phase)
        assertEquals(window.capture, workflow.lastCapture)
        assertEquals(1, workflow.validWindows.size)
        assertTrue(workflow.validWindows.single().samples.contentEquals(window.samples))
    }

    @Test
    fun invalidCapture_isShownButNotAccepted() {
        val window = sampleKnockWindow(isValid = false)

        val workflow =
            KnockTestWorkflow()
                .reduce(KnockTestEvent.CaptureRequested)
                .captureFinished(window)

        assertEquals(window.capture, workflow.lastCapture)
        assertTrue(workflow.validWindows.isEmpty())
    }

    @Test
    fun workflow_neverAcceptsMoreThanRequiredKnocks() {
        var workflow = KnockTestWorkflow()
        repeat(4) { index ->
            workflow =
                workflow
                    .reduce(KnockTestEvent.CaptureRequested)
                    .captureFinished(sampleKnockWindow(capturedAtMillis = index.toLong()))
        }

        assertEquals(3, workflow.validWindows.size)
        assertFalse(workflow.state.canCapture(workflow.validWindows.size))
    }
}

internal fun sampleKnockWindow(
    isValid: Boolean = true,
    capturedAtMillis: Long = 30L,
): CapturedKnockWindow =
    CapturedKnockWindow(
        capture =
            KnockCapture(
                peakAmplitude = if (isValid) 9_000 else 4,
                rmsAmplitude = if (isValid) 2_400 else 2,
                estimatedFrequencyHz = 230,
                isValid = isValid,
            ),
        samples = shortArrayOf(1, -2, 3, -4),
        capturedAtMillis = capturedAtMillis,
    )
