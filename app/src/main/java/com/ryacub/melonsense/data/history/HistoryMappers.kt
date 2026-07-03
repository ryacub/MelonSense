package com.ryacub.melonsense.data.history

import com.ryacub.melonsense.domain.model.MelonAssessmentResult

fun MelonAssessmentResult.toPendingHistoryItem(id: Long): PickHistoryItem =
    PickHistoryItem(
        id = id,
        createdAtMillis = audioScanResult.capturedAtMillis,
        status = PickHistoryStatus.PendingOutcome,
        resultLabel = resultLabel,
        sweetness = null,
        texture = null,
        visualScore = visualScanResult?.score,
        visualConfidencePercent = visualScanResult?.confidencePercent,
        audioScore = audioScanResult.score,
        audioConfidencePercent = audioScanResult.confidencePercent,
        validKnocks = audioScanResult.validKnocks,
        estimatedFrequencyHz = audioScanResult.estimatedFrequencyHz,
        finalConfidencePercent = confidencePercent,
        trainingExportStatus =
            if (trainingMedia != null) {
                TrainingExportStatus.Pending
            } else {
                TrainingExportStatus.NotCaptured
            },
        trainingExportedAtMillis = null,
    )
