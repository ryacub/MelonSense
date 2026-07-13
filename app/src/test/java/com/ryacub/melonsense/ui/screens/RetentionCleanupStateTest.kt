package com.ryacub.melonsense.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionCleanupStateTest {
    @Test
    fun runningCleanup_rejectsDuplicateRequest() {
        val running = RetentionCleanupState().reduce(RetentionCleanupEvent.Requested)

        assertFalse(running.canStart)
        assertEquals(running, running.reduce(RetentionCleanupEvent.Requested))
    }

    @Test
    fun cleanupReportsCountAndCanRunAgain() {
        val completed =
            RetentionCleanupState()
                .reduce(RetentionCleanupEvent.Requested)
                .reduce(RetentionCleanupEvent.Succeeded(purgedCount = 3))

        assertEquals(RetentionCleanupPhase.Succeeded, completed.phase)
        assertEquals(3, completed.purgedCount)
        assertTrue(completed.canStart)
    }

    @Test
    fun failedCleanup_canRetry() {
        val failed =
            RetentionCleanupState()
                .reduce(RetentionCleanupEvent.Requested)
                .reduce(RetentionCleanupEvent.Failed)

        assertEquals(RetentionCleanupPhase.Failed, failed.phase)
        assertEquals(RetentionCleanupPhase.Running, failed.reduce(RetentionCleanupEvent.Requested).phase)
    }
}
