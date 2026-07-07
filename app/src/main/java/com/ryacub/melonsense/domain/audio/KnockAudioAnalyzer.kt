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
        val averageRms = validKnocks.map { it.rmsAmplitude }.averageOrZero()
        val frequencyStats = validKnocks.frequencyStats()
        val score = validKnocks.pickScore(averagePeak, averageRms, frequencyStats)
        val confidence = validKnocks.confidencePercent(frequencyStats)

        return AudioScanResult(
            score = score,
            confidencePercent = confidence,
            validKnocks = validKnocks.size,
            estimatedFrequencyHz = frequencyStats.averageFrequencyHz,
            capturedAtMillis = System.currentTimeMillis(),
            evidence =
                listOf(
                    "${validKnocks.size} amplitude-valid knock windows captured",
                    "Average peak amplitude: ${averagePeak.toInt()}",
                    "Average RMS amplitude: ${averageRms.toInt()}",
                    "Estimated knock frequency: ${frequencyStats.averageFrequencyHz} Hz",
                    "Measured frequencies: ${frequencyStats.measuredCount} / ${validKnocks.size}",
                    "Frequency spread: ${frequencyStats.spreadLabel()}",
                    validKnocks.readinessEvidence(frequencyStats),
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

    private fun List<KnockCapture>.frequencyStats(): FrequencyStats {
        val frequencies = map { it.estimatedFrequencyHz }.filter { it > 0 }
        val spreadHz =
            if (frequencies.size < REQUIRED_KNOCK_COUNT) {
                null
            } else {
                requireNotNull(frequencies.maxOrNull()) - requireNotNull(frequencies.minOrNull())
            }
        return FrequencyStats(
            measuredCount = frequencies.size,
            averageFrequencyHz = frequencies.averageOrZero().toInt(),
            spreadHz = spreadHz,
        )
    }

    private fun List<KnockCapture>.pickScore(
        averagePeak: Double,
        averageRms: Double,
        frequencyStats: FrequencyStats,
    ): Int {
        if (size < REQUIRED_KNOCK_COUNT) {
            return (35 + size * 5 + ((averagePeak - KNOCK_PEAK_THRESHOLD) / 550).toInt())
                .coerceIn(0, 54)
        }

        val peakScore = ((averagePeak - KNOCK_PEAK_THRESHOLD) / 250).toInt().coerceIn(0, 25)
        val rmsScore = ((averageRms - 800) / 120).toInt().coerceIn(0, 10)
        return (40 + peakScore + rmsScore + frequencyConsistencyScore(frequencyStats)).coerceIn(0, 100)
    }

    private fun List<KnockCapture>.confidencePercent(frequencyStats: FrequencyStats): Int {
        if (size < REQUIRED_KNOCK_COUNT) {
            return (24 + size * 8 + if (frequencyStats.averageFrequencyHz > 0) 4 else 0).coerceIn(0, 49)
        }

        return (
            35 +
                size * 10 +
                (if (frequencyStats.averageFrequencyHz > 0) 10 else 0) +
                frequencyConsistencyConfidence(frequencyStats)
        ).coerceIn(0, 100)
    }

    private fun frequencyConsistencyScore(frequencyStats: FrequencyStats): Int {
        val frequencySpread = frequencyStats.spreadHz ?: return -10
        return when {
            frequencySpread <= 12 -> 25
            frequencySpread <= 30 -> 14
            frequencySpread <= 60 -> 2
            else -> -12
        }
    }

    private fun frequencyConsistencyConfidence(frequencyStats: FrequencyStats): Int {
        val frequencySpread = frequencyStats.spreadHz ?: return -12
        return when {
            frequencySpread <= 12 -> 18
            frequencySpread <= 30 -> 8
            frequencySpread <= 60 -> -2
            else -> -18
        }
    }

    private fun List<KnockCapture>.readinessEvidence(frequencyStats: FrequencyStats): String =
        when {
            size < REQUIRED_KNOCK_COUNT -> "Need 3 valid knocks before audio can carry result confidence."
            frequencyStats.measuredCount < REQUIRED_KNOCK_COUNT ->
                "Need 3 measured knock frequencies before audio can carry full confidence."
            else -> "Audio heuristic uses measured peak, RMS, and frequency consistency."
        }

    private fun FrequencyStats.spreadLabel(): String = spreadHz?.let { "$it Hz" } ?: "unavailable"

    private data class FrequencyStats(
        val measuredCount: Int,
        val averageFrequencyHz: Int,
        val spreadHz: Int?,
    )
}
