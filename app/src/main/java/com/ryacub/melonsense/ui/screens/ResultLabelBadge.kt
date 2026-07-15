package com.ryacub.melonsense.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.ui.theme.colors
import com.ryacub.melonsense.ui.theme.resultTone

@Composable
internal fun ResultLabelBadge(
    resultLabel: ResultLabel,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = resultLabel.resultTone.colors(MaterialTheme.colorScheme)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
