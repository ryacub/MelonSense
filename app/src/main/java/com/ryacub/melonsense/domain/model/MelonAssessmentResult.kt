package com.ryacub.melonsense.domain.model

data class MelonAssessmentResult(
    val visualScanResult: VisualScanResult?,
    val audioScanResult: AudioScanResult,
    val recommendation: String,
    val resultLabel: ResultLabel,
    val confidencePercent: Int,
    val trainingMedia: PendingTrainingMedia? = null,
)
