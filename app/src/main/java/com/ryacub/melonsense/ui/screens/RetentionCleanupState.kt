package com.ryacub.melonsense.ui.screens

enum class RetentionCleanupPhase {
    Idle,
    Running,
    Succeeded,
    Failed,
}

sealed interface RetentionCleanupEvent {
    data object Requested : RetentionCleanupEvent

    data class Succeeded(
        val purgedCount: Int,
    ) : RetentionCleanupEvent

    data object Failed : RetentionCleanupEvent
}

data class RetentionCleanupState(
    val phase: RetentionCleanupPhase = RetentionCleanupPhase.Idle,
    val purgedCount: Int? = null,
) {
    val canStart: Boolean
        get() = phase != RetentionCleanupPhase.Running

    fun reduce(event: RetentionCleanupEvent): RetentionCleanupState =
        when (event) {
            RetentionCleanupEvent.Requested ->
                if (canStart) {
                    RetentionCleanupState(RetentionCleanupPhase.Running)
                } else {
                    this
                }
            is RetentionCleanupEvent.Succeeded ->
                if (phase == RetentionCleanupPhase.Running) {
                    RetentionCleanupState(RetentionCleanupPhase.Succeeded, event.purgedCount)
                } else {
                    this
                }
            RetentionCleanupEvent.Failed ->
                if (phase == RetentionCleanupPhase.Running) {
                    RetentionCleanupState(RetentionCleanupPhase.Failed)
                } else {
                    this
                }
        }
}
