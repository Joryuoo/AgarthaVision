package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaRadius
import com.agarthavision.ui.theme.AgarthaVisionTheme
import com.agarthavision.ui.theme.MonoSmallStyle
import com.komoui.themes.styles

// Badge with leading live-dot — persistent queue counter in the app bar.
// count = 0 hides the component; dot pulses AlertCoral while device is offline.
// See docs/components.md §6.
@Composable
fun OfflineQueueBadge(
    count: Int = 0,
    isOffline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (count == 0) return

    val dotColor = if (isOffline) MaterialTheme.styles.destructive
                   else MaterialTheme.styles.primary

    Row(
        modifier = modifier
            .background(MaterialTheme.styles.secondary, RoundedCornerShape(AgarthaRadius.full))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape)
                .padding(end = 4.dp),
        )
        Text(
            text = count.toString(),
            style = MonoSmallStyle,
            color = MaterialTheme.styles.secondaryForeground,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OfflineQueueBadgePreview() {
    AgarthaVisionTheme {
        OfflineQueueBadge(count = 7, isOffline = true)
    }
}
