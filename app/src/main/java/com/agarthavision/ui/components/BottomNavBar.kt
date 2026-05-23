package com.agarthavision.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaVisionTheme

// Surface + Row of IconButton — phone navigation, shown instead of Sidebar.
// Destinations: Capture · Queue · Validate · Reports · Settings.
// Selected icon uses primary color; unselected uses mutedForeground.
// See docs/components.md §6.
@Composable
fun BottomNavBar(
    selectedIndex: Int = 0,
    onDestinationSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // TODO: add 5 IconButton items (Capture, Queue, Validate, Reports, Settings)
            // TODO: each item: Icon(selected ? primary : mutedForeground) + labelSmall text below
            // TODO: wire onDestinationSelected(index) on each click
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomNavBarPreview() {
    AgarthaVisionTheme {
        BottomNavBar(selectedIndex = 0)
    }
}
