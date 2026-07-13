package com.ryacub.melonsense.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryacub.melonsense.data.training.TrainingDatasetExportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrainingExportViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val exportRepository: TrainingDatasetExportRepository,
) : ViewModel() {
    private val snapshotCodec = TrainingExportSnapshotCodec()
    private val restoredState =
        savedStateHandle.get<String>(SAVED_STATE_KEY)
            ?.let(snapshotCodec::decode)
            ?: TrainingExportState()
    private val mutableState = MutableStateFlow(restoredState)
    val state: StateFlow<TrainingExportState> = mutableState.asStateFlow()

    init {
        persist(restoredState)
    }

    fun onExportRequested(nowMillis: Long) {
        if (!mutableState.value.canStart) return
        update { it.reduce(TrainingExportEvent.Requested) }
        viewModelScope.launch {
            try {
                val bundle =
                    exportRepository.exportEligible(
                        nowMillis = nowMillis,
                        createdAtMillis = nowMillis,
                    )
                update {
                    it.reduce(
                        TrainingExportEvent.Succeeded(
                            archivePath = bundle.archiveFile.absolutePath,
                            entryCount = bundle.entryCount,
                        ),
                    )
                }
            } catch (exception: CancellationException) {
                update { it.reduce(TrainingExportEvent.Cancelled) }
                throw exception
            } catch (exception: Exception) {
                update { it.reduce(TrainingExportEvent.Failed) }
            }
        }
    }

    fun onShareStarted() {
        update { it.reduce(TrainingExportEvent.ShareStarted) }
    }

    fun onShareFailed() {
        update { it.reduce(TrainingExportEvent.ShareFailed) }
    }

    private fun update(transform: (TrainingExportState) -> TrainingExportState) {
        val next = transform(mutableState.value)
        mutableState.value = next
        persist(next)
    }

    private fun persist(state: TrainingExportState) {
        savedStateHandle[SAVED_STATE_KEY] = snapshotCodec.encode(state)
    }

    companion object {
        const val SAVED_STATE_KEY = "training_export_v1"
    }
}
