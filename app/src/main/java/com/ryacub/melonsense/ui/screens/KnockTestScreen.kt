package com.ryacub.melonsense.ui.screens

import androidx.compose.runtime.Composable
import com.ryacub.melonsense.R

@Composable
fun KnockTestScreen(onAnalyzeResult: () -> Unit) {
    PlaceholderScreen(
        headlineRes = R.string.knock_headline,
        bodyRes = R.string.knock_body,
        actionRes = R.string.knock_primary_action,
        onAction = onAnalyzeResult,
    )
}
