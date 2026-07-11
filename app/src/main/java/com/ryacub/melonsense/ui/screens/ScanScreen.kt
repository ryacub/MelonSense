package com.ryacub.melonsense.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ryacub.melonsense.R
import com.ryacub.melonsense.data.training.FileTrainingMediaStore
import com.ryacub.melonsense.domain.inference.MelonInferenceEngine
import com.ryacub.melonsense.domain.inference.VisualInferenceInput
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.VisualScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun ScanScreen(
    inferenceEngine: MelonInferenceEngine,
    assessmentWorkflow: ScanAssessmentWorkflow,
    onAssessmentEvent: (ScanAssessmentEvent, VisualScanResult?) -> Unit,
    onStartKnockTest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaStore = remember(context) { FileTrainingMediaStore(context) }
    val cameraController =
        remember {
            LifecycleCameraController(context).apply {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            }
        }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        ScanContent(
            cameraController = cameraController,
            scanAssessmentState = assessmentWorkflow.state,
            visualScanResult = assessmentWorkflow.visualScanResult,
            onCaptureFrame = {
                if (!assessmentWorkflow.state.canCapture) {
                    return@ScanContent
                }
                onAssessmentEvent(ScanAssessmentEvent.CaptureRequested, null)
                scope.launch {
                    val photoArtifact =
                        try {
                            capturePhotoArtifact(
                                context = context,
                                cameraController = cameraController,
                                mediaStore = mediaStore,
                            )
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            onAssessmentEvent(ScanAssessmentEvent.CaptureFailed, null)
                            return@launch
                        }
                    onAssessmentEvent(ScanAssessmentEvent.CaptureSucceeded, null)
                    try {
                        val result =
                            inferenceEngine.scoreVisual(
                                VisualInferenceInput(
                                    photoArtifact = photoArtifact,
                                ),
                            )
                        onAssessmentEvent(ScanAssessmentEvent.AnalysisSucceeded, result)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        onAssessmentEvent(ScanAssessmentEvent.AnalysisFailed, null)
                    }
                }
            },
            onStartKnockTest = onStartKnockTest,
        )
    } else {
        CameraPermissionContent(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

@Composable
private fun ScanContent(
    cameraController: LifecycleCameraController,
    scanAssessmentState: ScanAssessmentState,
    visualScanResult: VisualScanResult?,
    onCaptureFrame: () -> Unit,
    onStartKnockTest: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            CameraPreview(
                cameraController = cameraController,
                modifier = Modifier.fillMaxSize(),
            )
        }

        VisualScanStatus(
            scanAssessmentState = scanAssessmentState,
            visualScanResult = visualScanResult,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onCaptureFrame,
                modifier = Modifier.weight(1f),
                enabled = scanAssessmentState.canCapture,
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(scanAssessmentState.primaryActionRes))
            }
            Button(
                onClick = onStartKnockTest,
                modifier = Modifier.weight(1f),
                enabled = scanAssessmentState.canStartKnockTest && visualScanResult != null,
            ) {
                Text(stringResource(R.string.scan_continue_action))
            }
        }
    }
}

@Composable
private fun CameraPreview(
    cameraController: LifecycleCameraController,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, cameraController) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewContext ->
            PreviewView(previewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
            }
        },
    )
}

@Composable
private fun VisualScanStatus(
    scanAssessmentState: ScanAssessmentState,
    visualScanResult: VisualScanResult?,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(scanAssessmentState.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = stringResource(scanAssessmentState.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (scanAssessmentState.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (visualScanResult != null) {
                Text(
                    text = stringResource(R.string.scan_visual_score, visualScanResult.score),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        stringResource(
                            R.string.scan_visual_confidence,
                            visualScanResult.confidencePercent,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                visualScanResult.evidence.forEach { evidence ->
                    Text(
                        text = evidence,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionContent(onRequestPermission: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.scan_permission_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.scan_permission_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.scan_permission_action))
        }
    }
}

private suspend fun capturePhotoArtifact(
    context: Context,
    cameraController: LifecycleCameraController,
    mediaStore: FileTrainingMediaStore,
): TrainingMediaArtifact {
    val capturedAtMillis = System.currentTimeMillis()
    val photoFile = mediaStore.createPhotoArtifactFile(capturedAtMillis)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    suspendCancellableCoroutine<Unit> { continuation ->
        cameraController.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    continuation.resume(Unit)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            },
        )
    }
    return mediaStore.readPhotoArtifactMetadata(photoFile, capturedAtMillis)
}
