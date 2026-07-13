package com.ryacub.melonsense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MelonSenseDestinationTest {
    @Test
    fun topLevelDestinations_areScanHistoryAndSettings() {
        assertEquals(
            listOf(
                MelonSenseDestination.Scan,
                MelonSenseDestination.History,
                MelonSenseDestination.Settings,
            ),
            MelonSenseDestination.topLevelEntries,
        )
    }

    @Test
    fun knockTestAndResult_areWorkflowDestinations() {
        assertFalse(MelonSenseDestination.KnockTest.isTopLevel)
        assertFalse(MelonSenseDestination.Result.isTopLevel)
        assertTrue(MelonSenseDestination.Scan.isTopLevel)
    }

    @Test
    fun knockTestRequiresCompletedVisualResult() {
        assertTrue(MelonSenseDestination.KnockTest.requiresVisualResult)
        assertFalse(MelonSenseDestination.Scan.requiresVisualResult)
        assertFalse(MelonSenseDestination.History.requiresVisualResult)
    }
}
