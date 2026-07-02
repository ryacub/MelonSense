package com.ryacub.melonsense.domain.model

data class KnockCapture(
    val peakAmplitude: Int,
    val rmsAmplitude: Int,
    val estimatedFrequencyHz: Int,
    val isValid: Boolean,
)
