package com.ryacub.melonsense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors =
    lightColorScheme(
        primary = SeedGreen,
        secondary = RindGreen,
        tertiary = FleshRed,
        surfaceVariant = FieldSpot.copy(alpha = 0.18f),
    )

private val DarkColors =
    darkColorScheme(
        primary = FieldSpot,
        secondary = RindGreen,
        tertiary = FleshRed,
    )

@Composable
fun MelonSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
