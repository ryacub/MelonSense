package com.ryacub.melonsense.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MelonSenseDestinationTest {
    @Test
    fun knockTestRequiresCompletedVisualResult() {
        assertTrue(MelonSenseDestination.KnockTest.requiresVisualResult)
        assertFalse(MelonSenseDestination.Scan.requiresVisualResult)
        assertFalse(MelonSenseDestination.History.requiresVisualResult)
    }
}
