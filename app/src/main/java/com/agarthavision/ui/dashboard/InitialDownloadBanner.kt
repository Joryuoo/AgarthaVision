package com.agarthavision.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Honest status of the first data download, shown under the dashboard header. Renders nothing
 * when [initialDownload] is [InitialDownload.DONE]. Standalone so it can be tested without Hilt.
 */
@Composable
internal fun InitialDownloadBanner(
    initialDownload: InitialDownload,
    modifier: Modifier = Modifier,
) {
    if (initialDownload == InitialDownload.DONE) return

    val colors = AgarthaTheme.colors
    val inProgress = initialDownload == InitialDownload.IN_PROGRESS
    val containerColor = if (inProgress) colors.surfaceMuted else colors.goldTint
    val contentColor = if (inProgress) colors.textSecondary else colors.goldText
    val text = stringResource(
        if (inProgress) {
            R.string.dashboard_initial_download_in_progress
        } else {
            R.string.dashboard_initial_download_incomplete
        },
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = text,
            fontSize = 13.sp,
            color = contentColor,
            lineHeight = 16.sp,
        )
    }
}
