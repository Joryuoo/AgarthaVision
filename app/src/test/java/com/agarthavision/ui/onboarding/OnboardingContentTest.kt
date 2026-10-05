package com.agarthavision.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class OnboardingContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressLabel_startsAsStep1Of4() {
        composeRule.setContent {
            AgarthaVisionTheme {
                OnboardingContent(
                    onFinish = {},
                    reduceMotion = true,
                )
            }
        }

        composeRule.onNodeWithTag("onboardingProgress")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Step 1 of 4")
            .assertIsDisplayed()
    }

    @Test
    fun progressLabel_updatesOnNextAndBack() {
        composeRule.setContent {
            AgarthaVisionTheme {
                OnboardingContent(
                    onFinish = {},
                    reduceMotion = true,
                )
            }
        }

        composeRule.onNodeWithTag("onboardingNext").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Step 2 of 4")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("onboardingBack").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Step 1 of 4")
            .assertIsDisplayed()
    }

    @Test
    fun page1_backDoesNotExist_andSkipCallsOnFinishOnce() {
        var finishCalls = 0
        composeRule.setContent {
            AgarthaVisionTheme {
                OnboardingContent(
                    onFinish = { finishCalls++ },
                    reduceMotion = true,
                )
            }
        }

        composeRule.onNodeWithTag("onboardingBack")
            .assertDoesNotExist()
        composeRule.onNodeWithTag("onboardingSkip")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("onboardingSkip").performClick()
        assertEquals(1, finishCalls)
    }

    @Test
    fun afterClickingNextThreeTimes_lastPageShowsHeadline_skipDoesNotExist_finishText_andFinishCalled() {
        var finishCalls = 0
        composeRule.setContent {
            AgarthaVisionTheme {
                OnboardingContent(
                    onFinish = { finishCalls++ },
                    reduceMotion = true,
                )
            }
        }

        repeat(3) {
            composeRule.onNodeWithTag("onboardingNext").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("Reports you can share")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("onboardingSkip")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Get started")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("onboardingNext").performClick()
        assertEquals(1, finishCalls)
    }

    @Test
    fun page1_showsFirstIllustrationDescription() {
        composeRule.setContent {
            AgarthaVisionTheme {
                OnboardingContent(
                    onFinish = {},
                    reduceMotion = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Microscope field with three eggs, each marked by a box, and a phone camera framing an egg",
        ).assertIsDisplayed()
    }

    @Test
    fun page1_showsHeadlineText() {
        composeRule.setContent {
            AgarthaVisionTheme {
                OnboardingContent(
                    onFinish = {},
                    reduceMotion = true,
                )
            }
        }

        composeRule.onNodeWithText("A second pair of eyes on every smear")
            .assertIsDisplayed()
    }
}
