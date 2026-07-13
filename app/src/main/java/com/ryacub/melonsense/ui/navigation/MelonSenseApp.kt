package com.ryacub.melonsense.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.withTransaction
import com.ryacub.melonsense.R
import com.ryacub.melonsense.data.history.RoomHistoryRepository
import com.ryacub.melonsense.data.local.MelonSenseDatabase
import com.ryacub.melonsense.data.training.FileTrainingMediaStore
import com.ryacub.melonsense.data.training.TrainingCaptureSettingsRepository
import com.ryacub.melonsense.data.training.TrainingDatasetExportRepository
import com.ryacub.melonsense.data.training.TrainingMediaRetentionController
import com.ryacub.melonsense.data.training.TrainingQueueItem
import com.ryacub.melonsense.data.training.TrainingRetentionRepository
import com.ryacub.melonsense.data.training.scheduleTrainingRetentionWork
import com.ryacub.melonsense.domain.inference.LocalVisualMelonInferenceEngine
import com.ryacub.melonsense.domain.inference.LocalVisualModelCatalog
import com.ryacub.melonsense.domain.inference.LocalVisualModelScorer
import com.ryacub.melonsense.domain.inference.PytorchVisualModelRunner
import com.ryacub.melonsense.ui.screens.HistoryScreen
import com.ryacub.melonsense.ui.screens.KnockTestScreen
import com.ryacub.melonsense.ui.screens.PickedAssessmentSaveEvent
import com.ryacub.melonsense.ui.screens.ResultScreen
import com.ryacub.melonsense.ui.screens.RetentionCleanupEvent
import com.ryacub.melonsense.ui.screens.RetentionCleanupState
import com.ryacub.melonsense.ui.screens.ScanScreen
import com.ryacub.melonsense.ui.screens.SettingsScreen
import com.ryacub.melonsense.ui.screens.TrainingExportEvent
import com.ryacub.melonsense.ui.screens.TrainingExportIntentFactory
import com.ryacub.melonsense.ui.screens.TrainingExportState
import com.ryacub.melonsense.ui.session.AssessmentSessionViewModel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MelonSenseApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val assessmentSessionViewModel: AssessmentSessionViewModel = viewModel()
    val assessmentSessionState by assessmentSessionViewModel.state.collectAsState()
    val database = remember(context) { MelonSenseDatabase.getInstance(context) }
    val mediaStore = remember(context) { FileTrainingMediaStore(context) }
    val retentionController = remember(mediaStore) { TrainingMediaRetentionController(mediaStore) }
    val captureSettingsRepository = remember(context) { TrainingCaptureSettingsRepository(context) }
    val inferenceEngine =
        remember(context) {
            val modelCatalog =
                LocalVisualModelCatalog.loadFromAssets { assetPath ->
                    context.assets.open(assetPath).bufferedReader().use { reader -> reader.readText() }
                }
            LocalVisualMelonInferenceEngine(
                catalog = modelCatalog,
                visualModelScorer =
                    LocalVisualModelScorer(
                        runner = PytorchVisualModelRunner(context),
                        catalog = modelCatalog,
                    ),
            )
        }
    val historyRepository =
        remember(database) {
            RoomHistoryRepository(
                database = database,
            )
        }
    val trainingDatasetExportRepository =
        remember(database, context) {
            TrainingDatasetExportRepository(
                database = database,
                outputDirectory = File(context.filesDir, "training-exports"),
            )
        }
    val trainingRetentionRepository =
        remember(database, mediaStore) {
            TrainingRetentionRepository(
                trainingCaptureDao = database.trainingCaptureDao(),
                pickHistoryDao = database.pickHistoryDao(),
                mediaStore = mediaStore,
                runInTransaction = { block -> database.withTransaction { block() } },
            )
        }
    val historyItems by historyRepository.historyItems.collectAsState(initial = emptyList())
    var trainingQueueItems by remember { mutableStateOf<List<TrainingQueueItem>>(emptyList()) }
    var trainingExportState by remember { mutableStateOf(TrainingExportState()) }
    var trainingCaptureEnabled by remember { mutableStateOf(captureSettingsRepository.isEnabled) }
    var retentionCleanupState by remember { mutableStateOf(RetentionCleanupState()) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedDestination =
        MelonSenseDestination.entries.firstOrNull { destination ->
            currentDestination?.hierarchy?.any { it.route == destination.route } == true
        } ?: MelonSenseDestination.Scan

    LaunchedEffect(trainingRetentionRepository) {
        scheduleTrainingRetentionWork(context)
        try {
            trainingRetentionRepository.purgeExpired(System.currentTimeMillis())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            retentionCleanupState = retentionCleanupState.reduce(RetentionCleanupEvent.Failed)
        }
    }

    LaunchedEffect(historyItems, trainingDatasetExportRepository) {
        trainingQueueItems = trainingDatasetExportRepository.getQueue(System.currentTimeMillis())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(selectedDestination.titleRes)) },
            )
        },
        bottomBar = {
            NavigationBar {
                MelonSenseDestination.entries.forEach { destination ->
                    val destinationEnabled =
                        !destination.requiresVisualResult || assessmentSessionState.scanWorkflow.visualScanResult != null
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        enabled = destinationEnabled,
                        onClick = {
                            if (!destinationEnabled) {
                                navController.navigate(MelonSenseDestination.Scan.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                return@NavigationBarItem
                            }
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.titleRes),
                            )
                        },
                        label = { Text(stringResource(destination.titleRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MelonSenseDestination.Scan.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(MelonSenseDestination.Scan.route) {
                ScanScreen(
                    inferenceEngine = inferenceEngine,
                    retentionController = retentionController,
                    retainTrainingMedia = trainingCaptureEnabled,
                    assessmentWorkflow = assessmentSessionState.scanWorkflow,
                    onAssessmentEvent = assessmentSessionViewModel::onScanEvent,
                    onStartKnockTest = {
                        navController.navigate(MelonSenseDestination.KnockTest.route)
                    },
                )
            }
            composable(MelonSenseDestination.KnockTest.route) {
                val currentVisualScanResult = assessmentSessionState.scanWorkflow.visualScanResult
                if (currentVisualScanResult == null) {
                    LaunchedEffect(Unit) {
                        navController.navigate(MelonSenseDestination.Scan.route) {
                            popUpTo(MelonSenseDestination.Scan.route) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                } else {
                    KnockTestScreen(
                        inferenceEngine = inferenceEngine,
                        retentionController = retentionController,
                        retainTrainingMedia = trainingCaptureEnabled,
                        visualScanResult = currentVisualScanResult,
                        workflow = assessmentSessionState.knockWorkflow,
                        onWorkflowEvent = assessmentSessionViewModel::onKnockEvent,
                        onKnockCaptured = assessmentSessionViewModel::onKnockCaptured,
                        onAnalyzeResult = { result ->
                            assessmentSessionViewModel.onAssessmentResult(result)
                            navController.navigate(MelonSenseDestination.Result.route)
                        },
                    )
                }
            }
            composable(MelonSenseDestination.Result.route) {
                ResultScreen(
                    assessmentResult = assessmentSessionState.assessmentResult,
                    pickedAssessmentSaveState = assessmentSessionState.pickedAssessmentSaveState,
                    onPickedThis = {
                        if (!assessmentSessionState.pickedAssessmentSaveState.canSave) {
                            return@ResultScreen
                        }
                        val assessmentResult = assessmentSessionState.assessmentResult ?: return@ResultScreen
                        assessmentSessionViewModel.onSaveEvent(PickedAssessmentSaveEvent.SaveRequested)
                        coroutineScope.launch {
                            try {
                                historyRepository.savePickedAssessment(assessmentResult)
                                assessmentSessionViewModel.onSaveEvent(PickedAssessmentSaveEvent.SaveSucceeded)
                                navController.navigate(MelonSenseDestination.History.route)
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                assessmentSessionViewModel.onSaveEvent(PickedAssessmentSaveEvent.SaveFailed)
                            }
                        }
                    },
                )
            }
            composable(MelonSenseDestination.History.route) {
                HistoryScreen(
                    historyItems = historyItems,
                    trainingQueueItems = trainingQueueItems,
                    trainingExportState = trainingExportState,
                    onExportTrainingDataset = {
                        if (!trainingExportState.canStart) return@HistoryScreen
                        trainingExportState = trainingExportState.reduce(TrainingExportEvent.Requested)
                        coroutineScope.launch {
                            try {
                                val nowMillis = System.currentTimeMillis()
                                val bundle =
                                    trainingDatasetExportRepository.exportEligible(
                                        nowMillis = nowMillis,
                                        createdAtMillis = nowMillis,
                                    )
                                trainingExportState =
                                    trainingExportState.reduce(
                                        TrainingExportEvent.Succeeded(
                                            archivePath = bundle.archiveFile.absolutePath,
                                            entryCount = bundle.entryCount,
                                        ),
                                    )
                                trainingQueueItems = emptyList()
                            } catch (exception: CancellationException) {
                                trainingExportState = trainingExportState.reduce(TrainingExportEvent.Cancelled)
                                throw exception
                            } catch (exception: Exception) {
                                trainingExportState = trainingExportState.reduce(TrainingExportEvent.Failed)
                            }
                        }
                    },
                    onShareTrainingDataset = {
                        val archivePath = trainingExportState.archivePath ?: return@HistoryScreen
                        trainingExportState = trainingExportState.reduce(TrainingExportEvent.ShareStarted)
                        try {
                            val shareSpec = TrainingExportIntentFactory.create(File(archivePath))
                            val shareIntent = TrainingExportIntentFactory.createIntent(context, shareSpec)
                            if (!TrainingExportIntentFactory.hasShareTarget(context, shareIntent)) {
                                trainingExportState = trainingExportState.reduce(TrainingExportEvent.ShareFailed)
                                return@HistoryScreen
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.training_queue_share_chooser),
                                ),
                            )
                        } catch (exception: Exception) {
                            trainingExportState = trainingExportState.reduce(TrainingExportEvent.ShareFailed)
                        }
                    },
                    onSaveOutcome = { pickId, resultLabel, sweetness, texture ->
                        coroutineScope.launch {
                            historyRepository.saveOutcome(
                                pickId = pickId,
                                resultLabel = resultLabel,
                                sweetness = sweetness,
                                texture = texture,
                            )
                        }
                    },
                )
            }
            composable(MelonSenseDestination.Settings.route) {
                SettingsScreen(
                    trainingCaptureEnabled = trainingCaptureEnabled,
                    cleanupState = retentionCleanupState,
                    onTrainingCaptureEnabledChange = { enabled ->
                        captureSettingsRepository.setEnabled(enabled)
                        trainingCaptureEnabled = enabled
                    },
                    onDeleteExpiredMedia = {
                        if (!retentionCleanupState.canStart) return@SettingsScreen
                        retentionCleanupState = retentionCleanupState.reduce(RetentionCleanupEvent.Requested)
                        coroutineScope.launch {
                            try {
                                val purgedCount = trainingRetentionRepository.purgeExpired(System.currentTimeMillis())
                                retentionCleanupState =
                                    retentionCleanupState.reduce(RetentionCleanupEvent.Succeeded(purgedCount))
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                retentionCleanupState = retentionCleanupState.reduce(RetentionCleanupEvent.Failed)
                            }
                        }
                    },
                )
            }
        }
    }
}
