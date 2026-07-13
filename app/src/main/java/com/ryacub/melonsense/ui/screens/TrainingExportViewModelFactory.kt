package com.ryacub.melonsense.ui.screens

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.ryacub.melonsense.data.training.TrainingDatasetExportRepository

class TrainingExportViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val exportRepository: TrainingDatasetExportRepository,
) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return TrainingExportViewModel(
            savedStateHandle = handle,
            exportRepository = exportRepository,
        ) as T
    }
}
