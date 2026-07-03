package com.ryacub.melonsense.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryacub.melonsense.R
import com.ryacub.melonsense.data.history.PickHistoryItem
import com.ryacub.melonsense.data.history.PickHistoryStatus
import com.ryacub.melonsense.data.history.SweetnessRating
import com.ryacub.melonsense.data.history.TextureRating
import com.ryacub.melonsense.data.history.TrainingExportStatus
import com.ryacub.melonsense.data.training.TrainingQueueBlockReason
import com.ryacub.melonsense.data.training.TrainingQueueItem
import com.ryacub.melonsense.domain.model.ResultLabel

@Composable
fun HistoryScreen(
    historyItems: List<PickHistoryItem>,
    trainingQueueItems: List<TrainingQueueItem>,
    lastDatasetExportPath: String?,
    onExportTrainingDataset: () -> Unit,
    onSaveOutcome: (
        pickId: Long,
        resultLabel: ResultLabel,
        sweetness: SweetnessRating,
        texture: TextureRating,
    ) -> Unit,
) {
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    val selectedItem =
        historyItems.firstOrNull { item -> item.id == selectedItemId }
            ?: historyItems.firstOrNull()

    LaunchedEffect(historyItems) {
        if (selectedItemId == null || historyItems.none { item -> item.id == selectedItemId }) {
            selectedItemId = historyItems.firstOrNull()?.id
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (historyItems.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }

        TrainingQueueSection(
            queueItems = trainingQueueItems,
            lastDatasetExportPath = lastDatasetExportPath,
            onExportTrainingDataset = onExportTrainingDataset,
        )

        historyItems.forEach { item ->
            HistoryItemCard(
                item = item,
                isSelected = item.id == selectedItem?.id,
                onSelect = { selectedItemId = item.id },
            )
        }

        selectedItem?.let { item ->
            OutcomeEditor(
                item = item,
                onSaveOutcome = onSaveOutcome,
            )
        }
    }
}

@Composable
private fun TrainingQueueSection(
    queueItems: List<TrainingQueueItem>,
    lastDatasetExportPath: String?,
    onExportTrainingDataset: () -> Unit,
) {
    val eligibleCount = queueItems.count { item -> item.isEligible }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.training_queue_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.training_queue_summary, eligibleCount, queueItems.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (queueItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.training_queue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                queueItems.take(5).forEach { item ->
                    TrainingQueueRow(item)
                }
            }
            Button(
                onClick = onExportTrainingDataset,
                enabled = eligibleCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.training_queue_export))
            }
            lastDatasetExportPath?.let { path ->
                Text(
                    text = stringResource(R.string.training_queue_last_export, path),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrainingQueueRow(item: TrainingQueueItem) {
    val statusText =
        if (item.isEligible) {
            stringResource(R.string.training_queue_ready)
        } else {
            stringResource(R.string.training_queue_blocked, stringResource(item.blockReason.labelRes))
        }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(item.historyItem.resultLabel.labelRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (item.isEligible) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
    }
}

@Composable
private fun HistoryItemCard(
    item: PickHistoryItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(item.resultLabel.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(item.status.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.history_scores, item.visualScore ?: 0, item.audioScore),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.history_training_status, stringResource(item.trainingExportStatus.labelRes)),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onSelect) {
                Text(
                    text =
                        stringResource(
                            if (isSelected) {
                                R.string.history_editing
                            } else {
                                R.string.history_edit_outcome
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun OutcomeEditor(
    item: PickHistoryItem,
    onSaveOutcome: (
        pickId: Long,
        resultLabel: ResultLabel,
        sweetness: SweetnessRating,
        texture: TextureRating,
    ) -> Unit,
) {
    var resultLabel by remember(item.id) { mutableStateOf(item.resultLabel) }
    var sweetness by remember(item.id) { mutableStateOf(item.sweetness ?: SweetnessRating.Good) }
    var texture by remember(item.id) { mutableStateOf(item.texture ?: TextureRating.Okay) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.history_outcome_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            SelectorGroup(
                titleRes = R.string.history_result_label,
                values = ResultLabel.entries,
                selected = resultLabel,
                labelFor = { label -> label.labelRes },
                onSelected = { label -> resultLabel = label },
            )
            SelectorGroup(
                titleRes = R.string.history_sweetness,
                values = SweetnessRating.entries,
                selected = sweetness,
                labelFor = { rating -> rating.labelRes },
                onSelected = { rating -> sweetness = rating },
            )
            SelectorGroup(
                titleRes = R.string.history_texture,
                values = TextureRating.entries,
                selected = texture,
                labelFor = { rating -> rating.labelRes },
                onSelected = { rating -> texture = rating },
            )

            Button(
                onClick = {
                    onSaveOutcome(item.id, resultLabel, sweetness, texture)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.history_save_outcome))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> SelectorGroup(
    @StringRes titleRes: Int,
    values: List<T>,
    selected: T,
    labelFor: (T) -> Int,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = { Text(stringResource(labelFor(value))) },
                )
            }
        }
    }
}

private val PickHistoryStatus.labelRes: Int
    get() =
        when (this) {
            PickHistoryStatus.PendingOutcome -> R.string.history_status_pending
            PickHistoryStatus.Rated -> R.string.history_status_rated
            PickHistoryStatus.ExpiredMedia -> R.string.history_status_expired
        }

private val ResultLabel.labelRes: Int
    get() =
        when (this) {
            ResultLabel.StrongPick -> R.string.result_label_strong_pick
            ResultLabel.GoodCandidate -> R.string.result_label_good_candidate
            ResultLabel.Maybe -> R.string.result_label_maybe
            ResultLabel.Skip -> R.string.result_label_skip
        }

private val SweetnessRating.labelRes: Int
    get() =
        when (this) {
            SweetnessRating.Bland -> R.string.sweetness_bland
            SweetnessRating.Mild -> R.string.sweetness_mild
            SweetnessRating.Good -> R.string.sweetness_good
            SweetnessRating.Sweet -> R.string.sweetness_sweet
            SweetnessRating.VerySweet -> R.string.sweetness_very_sweet
        }

private val TextureRating.labelRes: Int
    get() =
        when (this) {
            TextureRating.Mushy -> R.string.texture_mushy
            TextureRating.Soft -> R.string.texture_soft
            TextureRating.Okay -> R.string.texture_okay
            TextureRating.Crisp -> R.string.texture_crisp
            TextureRating.VeryCrisp -> R.string.texture_very_crisp
        }

private val TrainingExportStatus.labelRes: Int
    get() =
        when (this) {
            TrainingExportStatus.NotCaptured -> R.string.training_status_not_captured
            TrainingExportStatus.Pending -> R.string.training_status_pending
            TrainingExportStatus.Exported -> R.string.training_status_exported
            TrainingExportStatus.Expired -> R.string.training_status_expired
        }

private val TrainingQueueBlockReason.labelRes: Int
    get() =
        when (this) {
            TrainingQueueBlockReason.None -> R.string.training_block_none
            TrainingQueueBlockReason.NeedsOutcome -> R.string.training_block_needs_outcome
            TrainingQueueBlockReason.AlreadyExported -> R.string.training_block_already_exported
            TrainingQueueBlockReason.ExpiredMedia -> R.string.training_block_expired_media
            TrainingQueueBlockReason.MissingCapture -> R.string.training_block_missing_capture
            TrainingQueueBlockReason.MissingArtifact -> R.string.training_block_missing_artifact
        }
