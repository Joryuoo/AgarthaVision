package com.agarthavision.ui.dashboard

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.ui.theme.AgarthaTheme

@Composable
fun PeriodToggle(
    selected: HomePeriod,
    onSelect: (HomePeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val pillShape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .clip(pillShape)
            .background(colors.surfaceVariant)
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomePeriod.entries.forEach { period ->
            val isSelected = period == selected
            val label = when (period) {
                HomePeriod.TODAY -> stringResource(R.string.period_today)
                HomePeriod.LAST_7_DAYS -> stringResource(R.string.period_7_days)
                HomePeriod.LAST_30_DAYS -> stringResource(R.string.period_30_days)
            }
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(if (isSelected) colors.surface else colors.surfaceVariant)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(period) },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}
