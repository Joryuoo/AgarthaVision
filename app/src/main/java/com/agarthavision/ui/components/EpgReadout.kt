package com.agarthavision.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.agarthavision.ui.theme.AgarthaVisionTheme
import com.agarthavision.ui.theme.EpgDisplayStyle
import com.agarthavision.ui.theme.MonoSmallStyle

// Composition of Text styles for the large "1,284 EPG" hero display.
// EpgDisplayStyle (Geist 56 sp, tnum) for the numeral; MonoSmallStyle for the unit.
// See docs/components.md §6.
@Composable
fun EpgReadout(
    epg: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = "%,d".format(epg),
            style = EpgDisplayStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "EPG",
            style = MonoSmallStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EpgReadoutPreview() {
    AgarthaVisionTheme {
        EpgReadout(epg = 1284)
    }
}
