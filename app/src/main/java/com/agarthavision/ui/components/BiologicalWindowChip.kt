package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaRadius
import com.agarthavision.ui.theme.AgarthaVisionTheme
import com.agarthavision.ui.theme.MonoSmallStyle
import com.komoui.themes.styles
import kotlinx.coroutines.delay

// Badge + countdown LaunchedEffect — "BIO 47:12" app-bar timer.
// Shows the remaining biological-window time for the active sample.
// Fill switches to destructive color when ≤ 10 minutes remain.
// See docs/components.md §6.
@Composable
fun BiologicalWindowChip(
    remainingSeconds: Int = 0,
    modifier: Modifier = Modifier,
) {
    var remaining by remember { mutableIntStateOf(remainingSeconds) }

    LaunchedEffect(remainingSeconds) {
        remaining = remainingSeconds
        while (remaining > 0) {
            delay(1_000L)
            remaining--
        }
    }

    val critical = remaining <= 600
    val chipColor = if (critical) MaterialTheme.styles.destructive
                    else MaterialTheme.styles.primary
    val chipForeground = if (critical) MaterialTheme.styles.destructiveForeground
                         else MaterialTheme.styles.primaryForeground

    val mm = remaining / 60
    val ss = remaining % 60

    Row(
        modifier = modifier
            .background(chipColor, RoundedCornerShape(AgarthaRadius.full))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "BIO %02d:%02d".format(mm, ss),
            style = MonoSmallStyle,
            color = chipForeground,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BiologicalWindowChipPreview() {
    AgarthaVisionTheme {
        BiologicalWindowChip(remainingSeconds = 2832)
    }
}
