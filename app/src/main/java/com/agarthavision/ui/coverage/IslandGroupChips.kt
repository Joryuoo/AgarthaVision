package com.agarthavision.ui.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
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
        modifier = modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
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

@Suppress("LongParameterList", "CyclomaticComplexMethod") // Every parameter is a distinct, independent chip attribute.
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
    val containerColor = when {
        isSelected -> colors.brandFill
        else -> colors.surface
    }
    val labelColor = when {
        !isEnabled -> colors.textTertiary
        isSelected -> colors.onBrandFill
        else -> colors.textPrimary
    }
    val badgeTextColor = when {
        !isEnabled -> colors.textTertiary
        isSelected -> colors.onBrandFill
        else -> colors.textSecondary
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(containerColor, shape)
            .let { if (isSelected) it else it.border(1.dp, colors.border, shape) }
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.onBrandFill,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor,
            )
            Text(
                text = "$count",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = badgeTextColor,
            )
        }
    }
}

private fun IslandGroup.displayName(): String = when (this) {
    IslandGroup.LUZON -> "Luzon"
    IslandGroup.VISAYAS -> "Visayas"
    IslandGroup.MINDANAO -> "Mindanao"
}
