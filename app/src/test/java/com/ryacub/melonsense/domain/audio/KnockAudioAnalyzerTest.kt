package com.ryacub.melonsense.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnockAudioAnalyzerTest {
    @Test
    fun analyzeSamples_marksPeakAboveThresholdAsValid() {
        val samples =
            ShortArray(128) { index ->
                if (index % 2 == 0) 2_000 else (-2_000).toShort()
            }

        val capture = KnockAudioAnalyzer.analyzeSamples(samples)

        assertTrue(capture.isValid)
        assertEquals(2_000, capture.peakAmplitude)
        assertTrue(capture.estimatedFrequencyHz > 0)
    }

    @Test
    fun analyzeSamples_rejectsQuietInput() {
        val samples = ShortArray(128) { 8 }

        val capture = KnockAudioAnalyzer.analyzeSamples(samples)

        assertFalse(capture.isValid)
        assertEquals(8, capture.peakAmplitude)
    }

    @Test
    fun buildAudioScanResult_scoresThreeValidKnocks() {
        val validKnocks =
            List(KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT) {
                KnockAudioAnalyzer.analyzeSamples(
                    ShortArray(128) { index ->
                        if (index % 2 == 0) 2_200 else (-2_200).toShort()
                    },
                )
            }

        val result = KnockAudioAnalyzer.buildAudioScanResult(validKnocks)

        assertEquals(3, result.validKnocks)
        assertTrue(result.score > 0)
        assertTrue(result.confidencePercent > 0)
    }
}
