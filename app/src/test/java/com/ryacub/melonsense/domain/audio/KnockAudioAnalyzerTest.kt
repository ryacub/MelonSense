package com.ryacub.melonsense.domain.audio

import com.ryacub.melonsense.domain.model.KnockCapture
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

    @Test
    fun buildAudioScanResult_penalizesInsufficientKnockEvidence() {
        val result =
            KnockAudioAnalyzer.buildAudioScanResult(
                listOf(validKnock(peakAmplitude = 7_000, rmsAmplitude = 1_600, estimatedFrequencyHz = 155)),
            )

        assertTrue(result.score < 55)
        assertTrue(result.confidencePercent < 50)
        assertTrue(result.evidence.any { evidence -> evidence.contains("Need 3 valid knocks") })
    }

    @Test
    fun buildAudioScanResult_rewardsConsistentMeasuredKnockFeatures() {
        val result =
            KnockAudioAnalyzer.buildAudioScanResult(
                listOf(
                    validKnock(peakAmplitude = 8_400, rmsAmplitude = 2_000, estimatedFrequencyHz = 148),
                    validKnock(peakAmplitude = 8_100, rmsAmplitude = 1_900, estimatedFrequencyHz = 151),
                    validKnock(peakAmplitude = 8_250, rmsAmplitude = 1_950, estimatedFrequencyHz = 149),
                ),
            )

        assertEquals(3, result.validKnocks)
        assertTrue(result.score >= 70)
        assertTrue(result.confidencePercent >= 70)
        assertEquals(149, result.estimatedFrequencyHz)
        assertFalse(result.evidence.any { evidence -> evidence.contains("placeholder", ignoreCase = true) })
        assertTrue(result.evidence.any { evidence -> evidence.contains("Average peak amplitude: 8250") })
        assertTrue(result.evidence.any { evidence -> evidence.contains("Frequency spread: 3 Hz") })
    }

    @Test
    fun buildAudioScanResult_penalizesInconsistentMeasuredFrequencies() {
        val result =
            KnockAudioAnalyzer.buildAudioScanResult(
                listOf(
                    validKnock(peakAmplitude = 8_400, rmsAmplitude = 2_000, estimatedFrequencyHz = 112),
                    validKnock(peakAmplitude = 8_100, rmsAmplitude = 1_900, estimatedFrequencyHz = 188),
                    validKnock(peakAmplitude = 8_250, rmsAmplitude = 1_950, estimatedFrequencyHz = 151),
                ),
            )

        assertTrue(result.score < 70)
        assertTrue(result.confidencePercent < 70)
        assertTrue(result.evidence.any { evidence -> evidence.contains("Frequency spread: 76 Hz") })
    }

    @Test
    fun buildAudioScanResult_penalizesMissingFrequencyEvidence() {
        val result =
            KnockAudioAnalyzer.buildAudioScanResult(
                listOf(
                    validKnock(peakAmplitude = 8_400, rmsAmplitude = 2_000, estimatedFrequencyHz = 0),
                    validKnock(peakAmplitude = 8_100, rmsAmplitude = 1_900, estimatedFrequencyHz = 0),
                    validKnock(peakAmplitude = 8_250, rmsAmplitude = 1_950, estimatedFrequencyHz = 150),
                ),
            )

        assertTrue(result.score < 70)
        assertTrue(result.confidencePercent < 70)
        assertTrue(result.evidence.any { evidence -> evidence.contains("Measured frequencies: 1 / 3") })
        assertTrue(result.evidence.any { evidence -> evidence.contains("Need 3 measured knock frequencies") })
    }

    private fun validKnock(
        peakAmplitude: Int,
        rmsAmplitude: Int,
        estimatedFrequencyHz: Int,
    ) = KnockCapture(
        peakAmplitude = peakAmplitude,
        rmsAmplitude = rmsAmplitude,
        estimatedFrequencyHz = estimatedFrequencyHz,
        isValid = true,
    )
}
