package com.ryacub.melonsense.ui.screens

enum class HistoryTab {
    Picks,
    Training,
}

sealed interface HistoryViewEvent {
    data class SelectTab(
        val tab: HistoryTab,
    ) : HistoryViewEvent

    data class EditOutcome(
        val itemId: Long,
    ) : HistoryViewEvent

    data object DismissEditor : HistoryViewEvent

    data class ItemsChanged(
        val itemIds: Set<Long>,
    ) : HistoryViewEvent
}

data class HistoryViewState(
    val selectedTab: HistoryTab = HistoryTab.Picks,
    val editingItemId: Long? = null,
) {
    fun reduce(event: HistoryViewEvent): HistoryViewState =
        when (event) {
            is HistoryViewEvent.SelectTab -> copy(selectedTab = event.tab, editingItemId = null)
            is HistoryViewEvent.EditOutcome -> copy(selectedTab = HistoryTab.Picks, editingItemId = event.itemId)
            HistoryViewEvent.DismissEditor -> copy(editingItemId = null)
            is HistoryViewEvent.ItemsChanged ->
                if (editingItemId != null && editingItemId !in event.itemIds) {
                    copy(editingItemId = null)
                } else {
                    this
                }
        }
}
