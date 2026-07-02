package com.ryacub.melonsense.domain.audio

import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.KnockCapture
import kotlin.math.sqrt

object KnockAudioAnalyzer {
    const val REQUIRED_KNOCK_COUNT = 3
    const val KNOCK_PEAK_THRESHOLD = 1_500
    const val SAMPLE_RATE_HZ = 44_100
    const val CAPTURE_WINDOW_MILLIS = 520

    fun analyzeSamples(samples: ShortArray): KnockCapture {
        val peakAmplitude = samples.maxOfOrNull { sample -> sample.absoluteValue() } ?: 0
        val rmsAmplitude = samples.rmsAmplitude()
        val estimatedFrequencyHz = samples.estimateFrequencyHz()

        return KnockCapture(
            peakAmplitude = peakAmplitude,
            rmsAmplitude = rmsAmplitude,
            estimatedFrequencyHz = estimatedFrequencyHz,
            isValid = peakAmplitude >= KNOCK_PEAK_THRESHOLD,
        )
    }

    fun buildAudioScanResult(validKnocks: List<KnockCapture>): AudioScanResult {
        val averagePeak = validKnocks.map { it.peakAmplitude }.averageOrZero()
        val averageFrequency = validKnocks.map { it.estimatedFrequencyHz }.averageOrZero().toInt()
        val score =
            (56 + validKnocks.size * 8 + (averagePeak / 700).toInt())
                .coerceIn(0, 100)
        val confidence =
            (42 + validKnocks.size * 14 + if (averageFrequency > 0) 8 else 0)
                .coerceIn(0, 100)

        return AudioScanResult(
            score = score,
            confidencePercent = confidence,
            validKnocks = validKnocks.size,
            estimatedFrequencyHz = averageFrequency,
            capturedAtMillis = System.currentTimeMillis(),
            evidence =
                listOf(
                    "Three amplitude-valid knock windows captured",
                    "Estimated resonance placeholder: $averageFrequency Hz",
                    "FFT-style scoring placeholder ready for refinement",
                ),
        )
    }

    private fun Short.absoluteValue(): Int {
        val value = toInt()
        return if (value == Short.MIN_VALUE.toInt()) Short.MAX_VALUE.toInt() else kotlin.math.abs(value)
    }

    private fun ShortArray.rmsAmplitude(): Int {
        if (isEmpty()) return 0
        val meanSquare = map { sample -> sample.toDouble() * sample.toDouble() }.average()
        return sqrt(meanSquare).toInt()
    }

    private fun ShortArray.estimateFrequencyHz(): Int {
        if (size < 2) return 0
        var crossings = 0
        for (index in 1 until size) {
            val previous = this[index - 1]
            val current = this[index]
            if ((previous < 0 && current >= 0) || (previous >= 0 && current < 0)) {
                crossings += 1
            }
        }
        val durationSeconds = size.toDouble() / SAMPLE_RATE_HZ
        if (durationSeconds <= 0.0) return 0
        return (crossings / (2.0 * durationSeconds)).toInt().coerceAtLeast(0)
    }

    private fun List<Int>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
