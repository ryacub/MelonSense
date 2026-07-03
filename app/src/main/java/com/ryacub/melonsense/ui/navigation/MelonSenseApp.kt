package com.ryacub.melonsense.ui.navigation

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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.withTransaction
import com.ryacub.melonsense.data.history.RoomHistoryRepository
import com.ryacub.melonsense.data.local.MelonSenseDatabase
import com.ryacub.melonsense.data.training.FileTrainingMediaStore
import com.ryacub.melonsense.data.training.TrainingRetentionRepository
import com.ryacub.melonsense.data.training.scheduleTrainingRetentionWork
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.VisualScanResult
import com.ryacub.melonsense.ui.screens.HistoryScreen
import com.ryacub.melonsense.ui.screens.KnockTestScreen
import com.ryacub.melonsense.ui.screens.ResultScreen
import com.ryacub.melonsense.ui.screens.ScanScreen
import com.ryacub.melonsense.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MelonSenseApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val database = remember(context) { MelonSenseDatabase.getInstance(context) }
    val mediaStore = remember(context) { FileTrainingMediaStore(context) }
    val historyRepository =
        remember(database) {
            RoomHistoryRepository(
                database = database,
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
    var visualScanResult by remember { mutableStateOf<VisualScanResult?>(null) }
    var melonAssessmentResult by remember { mutableStateOf<MelonAssessmentResult?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedDestination =
        MelonSenseDestination.entries.firstOrNull { destination ->
            currentDestination?.hierarchy?.any { it.route == destination.route } == true
        } ?: MelonSenseDestination.Scan

    LaunchedEffect(trainingRetentionRepository) {
        scheduleTrainingRetentionWork(context)
        trainingRetentionRepository.purgeExpired(System.currentTimeMillis())
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
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = {
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
                    onStartKnockTest = { result ->
                        visualScanResult = result
                        navController.navigate(MelonSenseDestination.KnockTest.route)
                    },
                )
            }
            composable(MelonSenseDestination.KnockTest.route) {
                KnockTestScreen(
                    visualScanResult = visualScanResult,
                    onAnalyzeResult = { result ->
                        melonAssessmentResult = result
                        navController.navigate(MelonSenseDestination.Result.route)
                    },
                )
            }
            composable(MelonSenseDestination.Result.route) {
                ResultScreen(
                    assessmentResult = melonAssessmentResult,
                    onPickedThis = {
                        melonAssessmentResult?.let { assessmentResult ->
                            coroutineScope.launch {
                                historyRepository.savePickedAssessment(assessmentResult)
                                navController.navigate(MelonSenseDestination.History.route)
                            }
                        }
                    },
                )
            }
            composable(MelonSenseDestination.History.route) {
                HistoryScreen(
                    historyItems = historyItems,
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
                SettingsScreen()
            }
        }
    }
}
