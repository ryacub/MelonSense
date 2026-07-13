package com.ryacub.melonsense.ui.screens

import androidx.annotation.StringRes
import com.ryacub.melonsense.R

internal enum class SignalStrength(
    @StringRes val labelRes: Int,
) {
    Low(R.string.signal_strength_low),
    Moderate(R.string.signal_strength_moderate),
    High(R.string.signal_strength_high),
}

internal fun signalStrengthFor(confidencePercent: Int): SignalStrength =
    when {
        confidencePercent < 65 -> SignalStrength.Low
        confidencePercent < 85 -> SignalStrength.Moderate
        else -> SignalStrength.High
    }
