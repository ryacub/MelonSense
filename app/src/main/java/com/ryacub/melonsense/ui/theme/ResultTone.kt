package com.ryacub.melonsense.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.ryacub.melonsense.domain.model.ResultLabel

internal enum class ResultTone {
    Strong,
    Good,
    Caution,
    Negative,
}

internal val ResultLabel.resultTone: ResultTone
    get() =
        when (this) {
            ResultLabel.StrongPick -> ResultTone.Strong
            ResultLabel.GoodCandidate -> ResultTone.Good
            ResultLabel.Maybe -> ResultTone.Caution
            ResultLabel.Skip -> ResultTone.Negative
        }

internal data class ResultToneColors(
    val container: Color,
    val content: Color,
)

internal fun ResultTone.colors(colorScheme: ColorScheme): ResultToneColors =
    when (this) {
        ResultTone.Strong -> ResultToneColors(colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
        ResultTone.Good -> ResultToneColors(colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
        ResultTone.Caution -> ResultToneColors(colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        ResultTone.Negative -> ResultToneColors(colorScheme.errorContainer, colorScheme.onErrorContainer)
    }
