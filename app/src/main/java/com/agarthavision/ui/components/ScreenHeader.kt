package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

/**
 * The fixed header shared by the Sessions, Records and Settings tabs.
 *
 * One composable, so the title lands on the same pixel row on every tab - the three screens
 * used to size and pad their titles separately and the header visibly jumped when switching
 * tabs. It owns the status-bar inset and never scrolls, which keeps screen content from
 * sliding under the phone's status bar.
 *
 * @param title   The screen name.
 * @param purpose One line saying what the screen is for.
 * @param status  Optional live-state line (counts, active filter) shown under [purpose].
 */
@Composable
fun ScreenHeader(
    title: String,
    purpose: String,
    modifier: Modifier = Modifier,
    status: String? = null,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.accent,
        )
        Text(
            text = purpose,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
