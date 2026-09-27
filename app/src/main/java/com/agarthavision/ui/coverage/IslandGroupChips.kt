package com.agarthavision.ui.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.ui.theme.AgarthaColors
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Four filter chips over [IslandGroup] plus "All", each showing a province count. A chip with
 * count 0 is shown but disabled (not clickable, muted) — "All" is only disabled when [total] is
 * itself 0 (no province has data at all this period).
 */
@Composable
fun IslandGroupChips(
    counts: Map<IslandGroup, Int>,
    total: Int,
    selected: IslandGroup?,
    onSelect: (IslandGroup?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val pillShape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(
            label = "All",
            count = total,
            isSelected = selected == null,
            isEnabled = total > 0,
            onClick = { onSelect(null) },
            colors = colors,
            shape = pillShape,
        )
        IslandGroup.entries.forEach { group ->
            val count = counts[group] ?: 0
            Chip(
                label = group.displayName(),
                count = count,
                isSelected = selected == group,
                isEnabled = count > 0,
                onClick = { onSelect(group) },
                colors = colors,
                shape = pillShape,
            )
        }
    }
}

@Suppress("LongParameterList") // Every parameter is a distinct, independent chip attribute.
@Composable
private fun Chip(
    label: String,
    count: Int,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    colors: AgarthaColors,
    shape: RoundedCornerShape,
) {
    val background = when {
        isSelected -> colors.accentTint
        else -> colors.surfaceVariant
    }
    val textColor = when {
        !isEnabled -> colors.textTertiary
        isSelected -> colors.onAccentTint
        else -> colors.textSecondary
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(background, shape)
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label · $count",
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
    }
}

private fun IslandGroup.displayName(): String = when (this) {
    IslandGroup.LUZON -> "Luzon"
    IslandGroup.VISAYAS -> "Visayas"
    IslandGroup.MINDANAO -> "Mindanao"
}
