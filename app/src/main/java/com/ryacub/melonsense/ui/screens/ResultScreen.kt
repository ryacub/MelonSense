package com.ryacub.melonsense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryacub.melonsense.R
import com.ryacub.melonsense.domain.model.MelonAssessmentResult

@Composable
fun ResultScreen(
    assessmentResult: MelonAssessmentResult?,
    pickedAssessmentSaveState: PickedAssessmentSaveState,
    onPickedThis: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.result_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (assessmentResult == null) {
            Text(
                text = stringResource(R.string.result_missing),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }

        ResultSummary(assessmentResult = assessmentResult)

        Button(
            onClick = onPickedThis,
            modifier = Modifier.fillMaxWidth(),
            enabled = pickedAssessmentSaveState.canSave,
        ) {
            Text(stringResource(pickedAssessmentSaveState.actionRes))
        }
    }
}

@Composable
private fun ResultSummary(assessmentResult: MelonAssessmentResult) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.result_recommendation, assessmentResult.recommendation),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    stringResource(
                        R.string.result_signal,
                        stringResource(signalStrengthFor(assessmentResult.confidencePercent).labelRes),
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.result_signal_caveat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            assessmentResult.visualScanResult?.let { visualResult ->
                Text(
                    text = stringResource(R.string.result_visual_score, visualResult.score),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = stringResource(R.string.result_audio_score, assessmentResult.audioScanResult.score),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    stringResource(
                        R.string.result_audio_knocks,
                        assessmentResult.audioScanResult.validKnocks,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    stringResource(
                        R.string.result_audio_frequency,
                        assessmentResult.audioScanResult.estimatedFrequencyHz,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
