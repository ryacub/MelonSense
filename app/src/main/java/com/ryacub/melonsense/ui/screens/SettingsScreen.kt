package com.ryacub.melonsense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryacub.melonsense.R

@Composable
fun SettingsScreen(
    trainingCaptureEnabled: Boolean,
    cleanupState: RetentionCleanupState,
    onTrainingCaptureEnabledChange: (Boolean) -> Unit,
    onDeleteExpiredMedia: () -> Unit,
) {
    val trainingCaptureLabel = stringResource(R.string.settings_training_capture_title)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_headline),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = trainingCaptureEnabled,
                        role = Role.Switch,
                        onValueChange = onTrainingCaptureEnabledChange,
                    ).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = trainingCaptureLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_training_capture_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = trainingCaptureEnabled,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(
            text = stringResource(R.string.settings_retention_policy),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onDeleteExpiredMedia,
            enabled = cleanupState.canStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (cleanupState.phase == RetentionCleanupPhase.Running) {
                        R.string.settings_cleanup_running
                    } else {
                        R.string.settings_cleanup_action
                    },
                ),
            )
        }
        when (cleanupState.phase) {
            RetentionCleanupPhase.Succeeded ->
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.settings_cleanup_succeeded,
                            cleanupState.purgedCount ?: 0,
                            cleanupState.purgedCount ?: 0,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            RetentionCleanupPhase.Failed ->
                Text(
                    text = stringResource(R.string.settings_cleanup_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            else -> Unit
        }
    }
}
