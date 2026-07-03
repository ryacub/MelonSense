package com.ryacub.melonsense.domain.model

data class AudioScanResult(
    val score: Int,
    val confidencePercent: Int,
    val validKnocks: Int,
    val estimatedFrequencyHz: Int,
    val capturedAtMillis: Long,
    val evidence: List<String>,
    val audioArtifact: TrainingMediaArtifact? = null,
)
