package com.agarthavision.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) DotSeparator(separatorColor)
            StatItem(stat, valueColor, labelColor)
        }
    }
}

@Composable
private fun StatItem(stat: Stat, valueColor: Color, labelColor: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
        Text(
            text = stat.value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            // Tabular numerals so the figures do not jitter as counts change.
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = stat.label,
            fontSize = 13.sp,
            color = labelColor,
        )
    }
}

@Composable
private fun DotSeparator(color: Color) {
    Text(
        text = "·",
        fontSize = 13.sp,
        color = color,
        modifier = Modifier.padding(horizontal = 7.dp),
    )
}
