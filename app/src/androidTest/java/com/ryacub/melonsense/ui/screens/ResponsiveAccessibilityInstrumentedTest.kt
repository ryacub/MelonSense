package com.ryacub.melonsense.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.ui.theme.MelonSenseTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ResponsiveAccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun responsiveActions_stackForCompactLargeText() {
        setContentWithOverride(
            DeviceConfigurationOverride.FontScale(2f) then
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 480.dp)),
        ) {
            TestActionGroup()
        }

        val compactFirst = composeRule.onNodeWithTag("first-action").getUnclippedBoundsInRoot()
        val compactSecond = composeRule.onNodeWithTag("second-action").getUnclippedBoundsInRoot()
        assertTrue(compactSecond.top >= compactFirst.bottom)
    }

    @Test
    fun responsiveActions_stayInlineAtNormalSize() {
        setContentWithOverride(
            DeviceConfigurationOverride.FontScale(1f) then
                DeviceConfigurationOverride.ForcedSize(DpSize(700.dp, 480.dp)),
        ) {
            TestActionGroup()
        }

        val normalFirst = composeRule.onNodeWithTag("first-action").getUnclippedBoundsInRoot()
        val normalSecond = composeRule.onNodeWithTag("second-action").getUnclippedBoundsInRoot()
        assertTrue(normalSecond.left >= normalFirst.right)
    }

    @Test
    fun settingsActionsRemainReachable_andTheLabeledRowOwnsSwitchState() {
        var cleanupCalled = false
        var observedCaptureEnabled = true
        setCompactLargeText {
            var captureEnabled by remember { mutableStateOf(true) }
            SettingsScreen(
                trainingCaptureEnabled = captureEnabled,
                cleanupState = RetentionCleanupState(RetentionCleanupPhase.Failed),
                onTrainingCaptureEnabledChange = {
                    captureEnabled = it
                    observedCaptureEnabled = it
                },
                onDeleteExpiredMedia = { cleanupCalled = true },
            )
        }

        val switchRow =
            composeRule.onNode(
                hasText("Keep training media", substring = true) and hasClickAction(),
            )
        switchRow.assertIsOn().performClick().assertIsOff()
        composeRule.runOnIdle { assertFalse(observedCaptureEnabled) }

        composeRule
            .onNodeWithText("Delete Expired Media Now")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(cleanupCalled) }
        composeRule
            .onNode(
                hasText("Cleanup failed. Try again.") and
                    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun resultActionRemainsReachable_andUsesSignalBandInsteadOfProbabilityWording() {
        var picked = false
        var confidencePercent by mutableIntStateOf(64)
        setCompactLargeText {
            ResultScreen(
                assessmentResult = assessment(confidencePercent = confidencePercent),
                pickedAssessmentSaveState = PickedAssessmentSaveState(),
                onPickedThis = { picked = true },
            )
        }

        composeRule.onNodeWithText("Overall signal: Low").assertIsDisplayed()
        composeRule.onNodeWithText("Confidence: 64%").assertDoesNotExist()
        composeRule.runOnIdle { confidencePercent = 70 }
        composeRule.onNodeWithText("Overall signal: Moderate").assertIsDisplayed()
        composeRule.runOnIdle { confidencePercent = 90 }
        composeRule.onNodeWithText("Overall signal: High").assertIsDisplayed()
        composeRule
            .onNodeWithText("Signal strength is not a guarantee of sweetness or texture.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("I Picked This")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(picked) }
    }

    @Test
    fun knockActionsRemainReachable_andSlotsHaveOneAnnouncement() {
        setCompactLargeText {
            KnockTestContent(
                visualScanResult = null,
                validKnocks = emptyList(),
                lastCapture = null,
                knockTestState = KnockTestState(),
                onCaptureKnock = {},
                onAnalyzeResult = {},
            )
        }

        composeRule.onNodeWithText("Capture Knock").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Analyze Result").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Knock 1 waiting").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertDoesNotExist()
        composeRule
            .onNode(
                hasText("0 / 3 valid knocks") and
                    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            ).assertIsDisplayed()
    }

    @Test
    fun scanStatusUsesPoliteAnnouncement() {
        composeRule.setContent {
            MelonSenseTheme {
                VisualScanStatus(
                    scanAssessmentState = ScanAssessmentState(),
                    visualScanResult = null,
                )
            }
        }

        composeRule
            .onNode(
                hasText("Ready for best-frame capture") and
                    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            ).assertIsDisplayed()
    }

    @Composable
    private fun TestActionGroup() {
        ResponsiveActionGroup(
            firstAction = { modifier ->
                Button(
                    onClick = {},
                    modifier = modifier.testTag("first-action"),
                ) {
                    Text("First Action")
                }
            },
            secondAction = { modifier ->
                Button(
                    onClick = {},
                    modifier = modifier.testTag("second-action"),
                ) {
                    Text("Second Action")
                }
            },
        )
    }

    private fun setCompactLargeText(content: @Composable () -> Unit) {
        setContentWithOverride(
            DeviceConfigurationOverride.FontScale(2f) then
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 480.dp)),
            content,
        )
    }

    private fun setContentWithOverride(
        override: DeviceConfigurationOverride,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(override) {
                MelonSenseTheme(content = content)
            }
        }
    }

    private fun assessment(confidencePercent: Int): MelonAssessmentResult =
        MelonAssessmentResult(
            visualScanResult = null,
            audioScanResult =
                AudioScanResult(
                    score = 70,
                    confidencePercent = confidencePercent,
                    validKnocks = 3,
                    estimatedFrequencyHz = 220,
                    capturedAtMillis = 1,
                    evidence = emptyList(),
                ),
            recommendation = "Good Candidate",
            resultLabel = ResultLabel.GoodCandidate,
            confidencePercent = confidencePercent,
        )
}
