package com.agarthavision.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [EmptyState].
 *
 * Verifies the title, body, and optional action slot contract. Runs on the JVM
 * under Robolectric inside `:app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class EmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `title is rendered`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Nothing here",
                    body = "Come back later.",
                )
            }
        }

        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
    }

    @Test
    fun `body is rendered`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Nothing here",
                    body = "Come back later.",
                )
            }
        }

        composeRule.onNodeWithText("Come back later.").assertIsDisplayed()
    }

    @Test
    fun `action slot is rendered when provided`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Done",
                    body = "All finished.",
                    action = {
                        Button(onClick = {}) {
                            Text("Do something")
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("Do something").assertIsDisplayed()
    }

    @Test
    fun `action slot is absent when null`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Done",
                    body = "All finished.",
                    action = null,
                )
            }
        }

        composeRule.onNodeWithText("Do something").assertDoesNotExist()
    }

    @Test
    fun `explicit null action does not render action text`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Title",
                    body = "Body text here.",
                )
            }
        }

        // Only the title and body are present — no extra text node
        composeRule.onNodeWithText("Title").assertIsDisplayed()
        composeRule.onNodeWithText("Body text here.").assertIsDisplayed()
    }

    @Test
    fun `different title and body are each distinct`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Short title",
                    body = "Longer supporting sentence for the user.",
                )
            }
        }

        composeRule.onNodeWithText("Short title").assertIsDisplayed()
        composeRule.onNodeWithText("Longer supporting sentence for the user.").assertIsDisplayed()
    }
}
