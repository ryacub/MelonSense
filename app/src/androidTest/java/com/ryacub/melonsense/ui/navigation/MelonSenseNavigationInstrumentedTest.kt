package com.ryacub.melonsense.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryacub.melonsense.ui.theme.MelonSenseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MelonSenseNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomBar_showsOnlyTopLevelDestinations() {
        composeRule.setContent {
            MelonSenseTheme {
                MelonSenseBottomBar(
                    selectedDestination = MelonSenseDestination.Scan,
                    onNavigate = {},
                )
            }
        }

        composeRule.onNodeWithText("Scan").assertIsDisplayed()
        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Knock Test").assertDoesNotExist()
        composeRule.onNodeWithText("Result").assertDoesNotExist()
    }

    @Test
    fun workflowTopBar_exposesWorkingNavigateUpAction() {
        var navigateUpCalled = false
        composeRule.setContent {
            MelonSenseTheme {
                MelonSenseTopBar(
                    destination = MelonSenseDestination.KnockTest,
                    onNavigateUp = { navigateUpCalled = true },
                )
            }
        }

        composeRule.onNodeWithText("Knock Test").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        composeRule.runOnIdle { assertTrue(navigateUpCalled) }
    }

    @Test
    fun resultToTopLevelHistory_backReturnsToScanInsteadOfStaleResult() {
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = MelonSenseDestination.Scan.route,
            ) {
                composable(MelonSenseDestination.Scan.route) {
                    Button(onClick = { navController.navigate(MelonSenseDestination.KnockTest.route) }) {
                        Text("Go to knock")
                    }
                }
                composable(MelonSenseDestination.KnockTest.route) {
                    Button(onClick = { navController.navigate(MelonSenseDestination.Result.route) }) {
                        Text("Go to result")
                    }
                }
                composable(MelonSenseDestination.Result.route) {
                    Button(onClick = { navController.navigateToTopLevel(MelonSenseDestination.History) }) {
                        Text("Save pick")
                    }
                }
                composable(MelonSenseDestination.History.route) {
                    Button(onClick = navController::popBackStack) {
                        Text("Back from history")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Go to knock").performClick()
        composeRule.onNodeWithText("Go to result").performClick()
        composeRule.onNodeWithText("Save pick").performClick()
        composeRule.onNodeWithText("Back from history").performClick()

        composeRule.onNodeWithText("Go to knock").assertIsDisplayed()
        composeRule.onNodeWithText("Go to result").assertDoesNotExist()
    }
}
