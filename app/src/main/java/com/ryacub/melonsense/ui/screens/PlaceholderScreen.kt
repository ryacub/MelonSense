package com.ryacub.melonsense.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryacub.melonsense.R

@Composable
fun PlaceholderScreen(
    @StringRes headlineRes: Int,
    modifier: Modifier = Modifier,
    @StringRes bodyRes: Int? = null,
    body: String? = null,
    @StringRes actionRes: Int? = null,
    onAction: (() -> Unit)? = null,
) {
    val bodyText = body ?: bodyRes?.let { stringResource(it) }.orEmpty()

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(headlineRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.placeholder_confidence),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (actionRes != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(actionRes))
                }
            }
        }
    }
}
