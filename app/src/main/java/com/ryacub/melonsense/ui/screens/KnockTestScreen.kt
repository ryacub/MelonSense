package com.ryacub.melonsense.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ryacub.melonsense.R
import com.ryacub.melonsense.domain.audio.KnockAudioAnalyzer
import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.KnockCapture
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KnockTestScreen(
    visualScanResult: VisualScanResult?,
    onAnalyzeResult: (MelonAssessmentResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recommendation = stringResource(R.string.result_headline)
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var isRecording by remember { mutableStateOf(false) }
    var lastCapture by remember { mutableStateOf<KnockCapture?>(null) }
    val validKnocks = remember { mutableStateListOf<KnockCapture>() }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasAudioPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (!hasAudioPermission) {
        MicrophonePermissionContent(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
        return
    }

    KnockTestContent(
        visualScanResult = visualScanResult,
        validKnocks = validKnocks,
        lastCapture = lastCapture,
        isRecording = isRecording,
        onCaptureKnock = {
            if (isRecording || validKnocks.size >= KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT) {
                return@KnockTestContent
            }
            scope.launch {
                isRecording = true
                val capture = captureKnockWindow()
                lastCapture = capture
                if (capture.isValid && validKnocks.size < KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT) {
                    validKnocks += capture
                }
                isRecording = false
            }
        },
        onAnalyzeResult = {
            val audioScanResult = KnockAudioAnalyzer.buildAudioScanResult(validKnocks)
            onAnalyzeResult(
                buildAssessmentResult(
                    visualScanResult = visualScanResult,
                    audioScanResult = audioScanResult,
                    recommendation = recommendation,
                ),
            )
        },
    )
}

@Composable
private fun KnockTestContent(
    visualScanResult: VisualScanResult?,
    validKnocks: List<KnockCapture>,
    lastCapture: KnockCapture?,
    isRecording: Boolean,
    onCaptureKnock: () -> Unit,
    onAnalyzeResult: () -> Unit,
) {
    val validKnockCount = validKnocks.size

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.knock_headline),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.knock_body),
            style = MaterialTheme.typography.bodyLarge,
        )

        VisualSummary(visualScanResult = visualScanResult)

        KnockProgressCard(
            validKnockCount = validKnockCount,
            lastCapture = lastCapture,
            isRecording = isRecording,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onCaptureKnock,
                modifier = Modifier.weight(1f),
                enabled = !isRecording && validKnockCount < KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT,
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text =
                        if (isRecording) {
                            stringResource(R.string.knock_recording)
                        } else {
                            stringResource(R.string.knock_capture_action)
                        },
                )
            }
            Button(
                onClick = onAnalyzeResult,
                modifier = Modifier.weight(1f),
                enabled = validKnockCount >= KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT,
            ) {
                Text(stringResource(R.string.knock_primary_action))
            }
        }
    }
}

@Composable
private fun VisualSummary(visualScanResult: VisualScanResult?) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text =
                visualScanResult?.let { result ->
                    stringResource(
                        R.string.knock_visual_result_available,
                        result.score,
                        result.confidencePercent,
                    )
                } ?: stringResource(R.string.knock_visual_result_missing),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun KnockProgressCard(
    validKnockCount: Int,
    lastCapture: KnockCapture?,
    isRecording: Boolean,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.knock_progress, validKnockCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT) { index ->
                    KnockSlot(index = index, isValid = index < validKnockCount)
                }
            }
            if (isRecording) {
                Text(
                    text = stringResource(R.string.knock_recording),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (lastCapture != null) {
                Text(
                    text =
                        if (lastCapture.isValid) {
                            stringResource(
                                R.string.knock_last_valid,
                                lastCapture.peakAmplitude,
                                lastCapture.estimatedFrequencyHz,
                            )
                        } else {
                            stringResource(
                                R.string.knock_last_invalid,
                                lastCapture.peakAmplitude,
                            )
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun KnockSlot(
    index: Int,
    isValid: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (isValid) Icons.Filled.TaskAlt else Icons.Filled.RadioButtonUnchecked,
            contentDescription =
                stringResource(
                    if (isValid) R.string.knock_slot_valid else R.string.knock_slot_waiting,
                    index + 1,
                ),
            tint =
                if (isValid) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
        )
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun MicrophonePermissionContent(onRequestPermission: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.knock_permission_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.knock_permission_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.knock_permission_action))
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun captureKnockWindow(): KnockCapture =
    withContext(Dispatchers.IO) {
        val minBufferSize =
            AudioRecord.getMinBufferSize(
                KnockAudioAnalyzer.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        val sampleCount = KnockAudioAnalyzer.SAMPLE_RATE_HZ * KnockAudioAnalyzer.CAPTURE_WINDOW_MILLIS / 1_000
        val bufferSizeBytes = maxOf(minBufferSize, sampleCount * Short.SIZE_BYTES)
        val samples = ShortArray(sampleCount)
        var samplesRead = 0

        val audioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                KnockAudioAnalyzer.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeBytes,
            )

        try {
            audioRecord.startRecording()
            while (samplesRead < sampleCount) {
                val read =
                    audioRecord.read(
                        samples,
                        samplesRead,
                        sampleCount - samplesRead,
                    )
                if (read <= 0) break
                samplesRead += read
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        val capturedSamples = samples.copyOf(samplesRead)
        KnockAudioAnalyzer.analyzeSamples(capturedSamples)
    }

private fun buildAssessmentResult(
    visualScanResult: VisualScanResult?,
    audioScanResult: AudioScanResult,
    recommendation: String,
): MelonAssessmentResult {
    val visualConfidence = visualScanResult?.confidencePercent ?: 0
    val finalConfidence =
        ((visualConfidence * 0.45f) + (audioScanResult.confidencePercent * 0.55f))
            .toInt()
            .coerceIn(0, 100)

    return MelonAssessmentResult(
        visualScanResult = visualScanResult,
        audioScanResult = audioScanResult,
        recommendation = recommendation,
        resultLabel = ResultLabel.GoodCandidate,
        confidencePercent = finalConfidence,
    )
}
