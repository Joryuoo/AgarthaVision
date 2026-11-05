package com.agarthavision.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class RelativeTimeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `long and short styles preserve their resource text and Manila date`() {
        val timestamp = Instant.parse("2026-11-03T16:01:00Z")
        val epochMillis = timestamp.toEpochMilli()
        val cases = listOf(
            Example(Duration.ofSeconds(-1), "Just now", "Just now"),
            Example(Duration.ofSeconds(59), "Just now", "Just now"),
            Example(Duration.ofMinutes(1), "1 minute ago", "1 m ago"),
            Example(Duration.ofMinutes(59), "59 minutes ago", "59 m ago"),
            Example(Duration.ofHours(1), "1 hour ago", "1 h ago"),
            Example(Duration.ofHours(23), "23 hours ago", "23 h ago"),
            Example(Duration.ofDays(1), "1 day ago", "1 d ago"),
            Example(Duration.ofDays(6), "6 days ago", "6 d ago"),
            Example(Duration.ofDays(7), "Nov 4", "Nov 4"),
        )

        composeRule.setContent {
            Column {
                cases.forEachIndexed { index, example ->
                    val nowMillis = timestamp.plus(example.elapsed).toEpochMilli()
                    Text(
                        relativeTimeText(epochMillis, nowMillis),
                        modifier = Modifier.testTag("long-$index"),
                    )
                    Text(
                        relativeTimeText(epochMillis, nowMillis, RelativeTimeStyle.Short),
                        modifier = Modifier.testTag("short-$index"),
                    )
                }
            }
        }

        cases.forEachIndexed { index, example ->
            composeRule.onNodeWithTag("long-$index").assertTextEquals(example.longText)
            composeRule.onNodeWithTag("short-$index").assertTextEquals(example.shortText)
        }
    }

    private data class Example(val elapsed: Duration, val longText: String, val shortText: String)
}
