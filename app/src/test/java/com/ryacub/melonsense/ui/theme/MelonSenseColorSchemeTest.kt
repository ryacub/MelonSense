package com.ryacub.melonsense.ui.theme

import androidx.compose.ui.graphics.Color
import com.ryacub.melonsense.domain.model.ResultLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MelonSenseColorSchemeTest {
    private val materialDefaultLavenderSurface = Color(0xFFFFFBFE)

    @Test
    fun lightScheme_usesNeutralOpaqueSurfacesAndBrandGreen() {
        assertEquals(SeedGreen, LightColors.primary)
        assertEquals(NeutralLightBackground, LightColors.background)
        assertEquals(NeutralLightSurface, LightColors.surface)
        assertEquals(NeutralLightSurfaceVariant, LightColors.surfaceVariant)
        assertNotEquals(materialDefaultLavenderSurface, LightColors.surface)
    }

    @Test
    fun darkScheme_keepsGreenAsPrimaryAndUsesNeutralSurfaces() {
        assertEquals(SproutGreen, DarkColors.primary)
        assertEquals(NeutralDarkBackground, DarkColors.background)
        assertEquals(NeutralDarkSurface, DarkColors.surface)
        assertEquals(NeutralDarkSurfaceVariant, DarkColors.surfaceVariant)
        assertNotEquals(FieldSpot, DarkColors.primary)
    }

    @Test
    fun resultLabels_mapToDistinctSemanticRoles() {
        assertEquals(ResultTone.Strong, ResultLabel.StrongPick.resultTone)
        assertEquals(ResultTone.Good, ResultLabel.GoodCandidate.resultTone)
        assertEquals(ResultTone.Caution, ResultLabel.Maybe.resultTone)
        assertEquals(ResultTone.Negative, ResultLabel.Skip.resultTone)
    }

    @Test
    fun resultTones_resolveToMatchingColorSchemeRoles() {
        assertEquals(
            ResultToneColors(LightColors.primaryContainer, LightColors.onPrimaryContainer),
            ResultTone.Strong.colors(LightColors),
        )
        assertEquals(
            ResultToneColors(LightColors.secondaryContainer, LightColors.onSecondaryContainer),
            ResultTone.Good.colors(LightColors),
        )
        assertEquals(
            ResultToneColors(LightColors.tertiaryContainer, LightColors.onTertiaryContainer),
            ResultTone.Caution.colors(LightColors),
        )
        assertEquals(
            ResultToneColors(LightColors.errorContainer, LightColors.onErrorContainer),
            ResultTone.Negative.colors(LightColors),
        )
    }
}
