package com.ryacub.melonsense.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ryacub.melonsense.R
import com.ryacub.melonsense.domain.model.VisualScanResult

@Composable
fun KnockTestScreen(
    visualScanResult: VisualScanResult?,
    onAnalyzeResult: () -> Unit,
) {
    val body =
        visualScanResult?.let { result ->
            stringResource(
                R.string.knock_visual_result_available,
                result.score,
                result.confidencePercent,
            )
        } ?: stringResource(R.string.knock_visual_result_missing)

    PlaceholderScreen(
        headlineRes = R.string.knock_headline,
        body = body,
        actionRes = R.string.knock_primary_action,
        onAction = onAnalyzeResult,
    )
}
