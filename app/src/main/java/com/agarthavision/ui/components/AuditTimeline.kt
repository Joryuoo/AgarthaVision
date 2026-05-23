package com.agarthavision.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.agarthavision.ui.theme.AgarthaSpacing
import com.agarthavision.ui.theme.AgarthaVisionTheme
import com.agarthavision.ui.theme.MonoSmallStyle

data class AuditEvent(val timestamp: String, val actor: String, val action: String)

// LazyColumn of Row + HorizontalDivider — sample audit-trail tab.
// Timestamp in MonoSmallStyle; actor + action in bodySmall / mutedForeground.
// See docs/components.md §6.
@Composable
fun AuditTimeline(
    events: List<AuditEvent> = emptyList(),
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(events) { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgarthaSpacing.screenEdge, vertical = AgarthaSpacing.sm),
            ) {
                Text(
                    text = event.timestamp,
                    style = MonoSmallStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(AgarthaSpacing.md))
                Column {
                    Text(
                        text = event.actor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = event.action,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuditTimelinePreview() {
    AgarthaVisionTheme {
        AuditTimeline(
            events = listOf(
                AuditEvent("2025-06-01 08:14", "J. Novabos", "Validated — Trichuris trichiura 0.91"),
                AuditEvent("2025-06-01 07:52", "Edge AI", "Auto-detection complete — 3 worms found"),
                AuditEvent("2025-06-01 07:48", "Technologist", "Sample captured at Brgy. San Roque"),
            )
        )
    }
}
