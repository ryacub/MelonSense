package com.ryacub.melonsense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun ResponsiveActionGroup(
    firstAction: @Composable (Modifier) -> Unit,
    secondAction: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val shouldStack = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f
        if (shouldStack) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                firstAction(Modifier.fillMaxWidth())
                secondAction(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                firstAction(Modifier.weight(1f))
                secondAction(Modifier.weight(1f))
            }
        }
    }
}
