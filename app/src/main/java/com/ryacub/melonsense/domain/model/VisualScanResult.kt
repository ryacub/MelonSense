package com.ryacub.melonsense.domain.model

data class VisualScanResult(
    val score: Int,
    val confidencePercent: Int,
    val capturedAtMillis: Long,
    val evidence: List<String>,
    val photoArtifact: TrainingMediaArtifact? = null,
)
