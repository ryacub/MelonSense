package com.ryacub.melonsense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val LightColors =
    lightColorScheme(
        primary = SeedGreen,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB9F2CD),
        onPrimaryContainer = Color(0xFF00210F),
        secondary = RindGreen,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD2E8D7),
        onSecondaryContainer = Color(0xFF0D1F14),
        tertiary = FieldSpot,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE17F),
        onTertiaryContainer = Color(0xFF241A00),
        error = FleshRed,
        onError = Color.White,
        errorContainer = Color(0xFFFFDADB),
        onErrorContainer = Color(0xFF41000A),
        background = NeutralLightBackground,
        onBackground = Color(0xFF191C19),
        surface = NeutralLightSurface,
        onSurface = Color(0xFF191C19),
        surfaceVariant = NeutralLightSurfaceVariant,
        onSurfaceVariant = Color(0xFF414942),
        outline = Color(0xFF727970),
        outlineVariant = Color(0xFFC1C9C1),
        inverseSurface = Color(0xFF2E312E),
        inverseOnSurface = Color(0xFFF0F1ED),
        inversePrimary = SproutGreen,
        surfaceTint = SeedGreen,
        surfaceDim = Color(0xFFD8DBD7),
        surfaceBright = NeutralLightSurface,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF2F5F1),
        surfaceContainer = Color(0xFFECEFEB),
        surfaceContainerHigh = Color(0xFFE6E9E5),
        surfaceContainerHighest = Color(0xFFE0E3DF),
    )

internal val DarkColors =
    darkColorScheme(
        primary = SproutGreen,
        onPrimary = Color(0xFF00391C),
        primaryContainer = Color(0xFF00522A),
        onPrimaryContainer = Color(0xFF95F6B7),
        secondary = Color(0xFFB7CCBA),
        onSecondary = Color(0xFF22352A),
        secondaryContainer = Color(0xFF394B40),
        onSecondaryContainer = Color(0xFFD2E8D5),
        tertiary = Color(0xFFE7C44F),
        onTertiary = Color(0xFF3B2F00),
        tertiaryContainer = Color(0xFF574600),
        onTertiaryContainer = Color(0xFFFFE17F),
        error = Color(0xFFFFB2B8),
        onError = Color(0xFF670019),
        errorContainer = Color(0xFF861F2F),
        onErrorContainer = Color(0xFFFFDADB),
        background = NeutralDarkBackground,
        onBackground = Color(0xFFDEE4DE),
        surface = NeutralDarkSurface,
        onSurface = Color(0xFFDEE4DE),
        surfaceVariant = NeutralDarkSurfaceVariant,
        onSurfaceVariant = Color(0xFFC2C9C2),
        outline = Color(0xFF8C938C),
        outlineVariant = Color(0xFF424943),
        inverseSurface = Color(0xFFDEE4DE),
        inverseOnSurface = Color(0xFF2E312E),
        inversePrimary = SeedGreen,
        surfaceTint = SproutGreen,
        surfaceDim = NeutralDarkSurface,
        surfaceBright = Color(0xFF363B37),
        surfaceContainerLowest = Color(0xFF0B0F0C),
        surfaceContainerLow = Color(0xFF181D19),
        surfaceContainer = Color(0xFF1C211D),
        surfaceContainerHigh = Color(0xFF272B27),
        surfaceContainerHighest = Color(0xFF323632),
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
