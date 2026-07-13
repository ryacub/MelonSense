package com.ryacub.melonsense.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalStrengthTest {
    @Test
    fun confidenceMapsToHonestSignalBands() {
        assertEquals(SignalStrength.Low, signalStrengthFor(0))
        assertEquals(SignalStrength.Low, signalStrengthFor(64))
        assertEquals(SignalStrength.Moderate, signalStrengthFor(65))
        assertEquals(SignalStrength.Moderate, signalStrengthFor(84))
        assertEquals(SignalStrength.High, signalStrengthFor(85))
        assertEquals(SignalStrength.High, signalStrengthFor(100))
    }
}
