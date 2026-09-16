package com.agarthavision.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.theme.AgarthaTheme

/** One `value label` pair in a [StatRun], e.g. `12 samples`. */
internal data class Stat(val value: String, val label: String)

/**
 * The `12 confirmed · 2 species · 30 samples` run shared by the record screen and the
 * records list.
 *
 * Both screens previously carried their own near-identical composable — 13sp SemiBold
 * with a dot separator on one, 14sp Bold with a 14dp gap on the other — so the same
 * three numbers never lined up between them.
 */
@Composable
internal fun StatRun(
    stats: List<Stat>,
    modifier: Modifier = Modifier,
    valueColor: Color = AgarthaTheme.colors.textPrimary,
    labelColor: Color = AgarthaTheme.colors.textSecondary,
    separatorColor: Color = AgarthaTheme.colors.textTertiary,
) {
    // Every element aligns on the shared text baseline so the tabular-figure values sit on
    // the same line as their labels and the dot separators — not dropped below them, which
    // is what Alignment.Bottom produced once `tnum` changed the value's font metrics.
    Row(modifier = modifier) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) DotSeparator(separatorColor, Modifier.alignByBaseline())
            StatItem(stat, valueColor, labelColor, Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun StatItem(stat: Stat, valueColor: Color, labelColor: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Start) {
        Text(
            text = stat.value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            // Tabular numerals so the figures do not jitter as counts change.
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = stat.label,
            fontSize = 13.sp,
            color = labelColor,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun DotSeparator(color: Color, modifier: Modifier = Modifier) {
    Text(
        text = "·",
        fontSize = 13.sp,
        color = color,
        modifier = modifier.padding(horizontal = 7.dp),
    )
}
