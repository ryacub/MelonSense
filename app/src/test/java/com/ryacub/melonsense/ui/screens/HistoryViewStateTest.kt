package com.ryacub.melonsense.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryViewStateTest {
    @Test
    fun defaultsToPicksWithoutOpenEditor() {
        assertEquals(HistoryTab.Picks, HistoryViewState().selectedTab)
        assertNull(HistoryViewState().editingItemId)
    }

    @Test
    fun selectingTrainingDismissesEditor() {
        val state =
            HistoryViewState()
                .reduce(HistoryViewEvent.EditOutcome(7))
                .reduce(HistoryViewEvent.SelectTab(HistoryTab.Training))

        assertEquals(HistoryTab.Training, state.selectedTab)
        assertNull(state.editingItemId)
    }

    @Test
    fun selectingAndDismissingEditor_preservesPicksTab() {
        val selected = HistoryViewState().reduce(HistoryViewEvent.EditOutcome(7))

        assertEquals(HistoryTab.Picks, selected.selectedTab)
        assertEquals(7L, selected.editingItemId)
        assertNull(selected.reduce(HistoryViewEvent.DismissEditor).editingItemId)
    }

    @Test
    fun missingEditedItem_isDismissedWhenItemsChange() {
        val state =
            HistoryViewState()
                .reduce(HistoryViewEvent.EditOutcome(7))
                .reduce(HistoryViewEvent.ItemsChanged(setOf(8, 9)))

        assertNull(state.editingItemId)
    }
}
