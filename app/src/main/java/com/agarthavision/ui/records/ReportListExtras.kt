package com.agarthavision.ui.records

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Prev / "Page X of Y" / Next control for the reports list. Shown only when there is more than
 * one page ([REPORTS_PER_PAGE] rows). Pages are DB-limited, so only the current page is loaded.
 */
@Composable
internal fun ReportsPager(
    currentPage: Int,
    totalReports: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val totalPages = ((totalReports + REPORTS_PER_PAGE - 1) / REPORTS_PER_PAGE).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagerButton(
            iconRes = R.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.report_pager_previous),
            enabled = currentPage > 0,
            onClick = onPrev,
        )
        Text(
            text = stringResource(R.string.report_pager_page, currentPage + 1, totalPages),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = AgarthaTheme.colors.textSecondary,
        )
        PagerButton(
            iconRes = R.drawable.ic_chevron_right,
            contentDescription = stringResource(R.string.report_pager_next),
            enabled = currentPage < totalPages - 1,
            onClick = onNext,
        )
    }
}

@Composable
private fun PagerButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = if (enabled) AgarthaTheme.colors.accent else AgarthaTheme.colors.textTertiary,
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp),
    )
}

/** Small outlined tag marking the file format a report was exported as (PDF / CSV). */
@Composable
internal fun FormatChip(label: String) {
    Text(
        text = label,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = AgarthaTheme.colors.textSecondary,
        modifier = Modifier
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
