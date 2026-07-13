package com.ryacub.melonsense.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
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
import com.ryacub.melonsense.ui.screens.TrainingExportIntentFactory
import com.ryacub.melonsense.ui.screens.TrainingExportViewModel
import com.ryacub.melonsense.ui.screens.TrainingExportViewModelFactory
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
    var trainingCaptureEnabled by remember { mutableStateOf(captureSettingsRepository.isEnabled) }
    var retentionCleanupState by remember { mutableStateOf(RetentionCleanupState()) }
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    val trainingExportViewModel: TrainingExportViewModel =
        viewModel(factory = TrainingExportViewModelFactory(savedStateRegistryOwner, trainingDatasetExportRepository))
    val trainingExportState by trainingExportViewModel.state.collectAsState()
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

    LaunchedEffect(historyItems, trainingExportState.phase) {
        trainingQueueItems = trainingDatasetExportRepository.getQueue(System.currentTimeMillis())
    }

    Scaffold(
        topBar = {
            MelonSenseTopBar(
                destination = selectedDestination,
                onNavigateUp = navController::popBackStack,
            )
        },
        bottomBar = {
            if (selectedDestination.isTopLevel) {
                MelonSenseBottomBar(
                    selectedDestination = selectedDestination,
                    onNavigate = navController::navigateToTopLevel,
                )
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
                                navController.navigateToTopLevel(MelonSenseDestination.History)
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
                        trainingExportViewModel.onExportRequested(System.currentTimeMillis())
                    },
                    onShareTrainingDataset = {
                        val archivePath = trainingExportState.archivePath ?: return@HistoryScreen
                        trainingExportViewModel.onShareStarted()
                        try {
                            val shareSpec = TrainingExportIntentFactory.create(File(archivePath))
                            val shareIntent = TrainingExportIntentFactory.createIntent(context, shareSpec)
                            if (!TrainingExportIntentFactory.hasShareTarget(context, shareIntent)) {
                                trainingExportViewModel.onShareFailed()
                                return@HistoryScreen
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.training_queue_share_chooser),
                                ),
                            )
                        } catch (exception: Exception) {
                            trainingExportViewModel.onShareFailed()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MelonSenseTopBar(
    destination: MelonSenseDestination,
    onNavigateUp: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(destination.titleRes)) },
        navigationIcon = {
            if (!destination.isTopLevel) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_up),
                    )
                }
            }
        },
    )
}

@Composable
internal fun MelonSenseBottomBar(
    selectedDestination: MelonSenseDestination,
    onNavigate: (MelonSenseDestination) -> Unit,
) {
    NavigationBar {
        MelonSenseDestination.topLevelEntries.forEach { destination ->
            NavigationBarItem(
                selected = selectedDestination == destination,
                onClick = { onNavigate(destination) },
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
}

internal fun NavHostController.navigateToTopLevel(destination: MelonSenseDestination) {
    require(destination.isTopLevel) { "Only top-level destinations can use top-level navigation." }
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
