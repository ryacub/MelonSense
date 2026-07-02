package com.ryacub.melonsense.ui.screens

import androidx.compose.runtime.Composable
import com.ryacub.melonsense.R

@Composable
fun ScanScreen(onStartKnockTest: () -> Unit) {
    PlaceholderScreen(
        headlineRes = R.string.scan_headline,
        bodyRes = R.string.scan_body,
        actionRes = R.string.scan_primary_action,
        onAction = onStartKnockTest,
    )
}
