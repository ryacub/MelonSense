package com.ryacub.melonsense.ui.screens

import androidx.compose.runtime.Composable
import com.ryacub.melonsense.R

@Composable
fun ResultScreen(onPickedThis: () -> Unit) {
    PlaceholderScreen(
        headlineRes = R.string.result_headline,
        bodyRes = R.string.result_body,
        actionRes = R.string.result_primary_action,
        onAction = onPickedThis,
    )
}
