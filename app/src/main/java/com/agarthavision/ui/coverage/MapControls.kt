package com.agarthavision.ui.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agarthavision.R
import com.agarthavision.domain.geo.ViewTransform
import com.agarthavision.ui.theme.AgarthaTheme

private const val ZOOM_STEP = 1.6f
private const val CONTROL_SIZE_DP = 40
private const val CONTROL_CORNER_RADIUS_DP = 12

/** Zooms [current] toward the center of a [width]x[height] viewport by [factor]. */
@Suppress("LongParameterList") // Every parameter is a distinct, independent zoom-math input.
internal fun zoomAroundCenter(
    current: ViewTransform,
    width: Float,
    height: Float,
    factor: Float,
    minScale: Float,
    maxScale: Float,
): ViewTransform = nextCameraTransform(
    current = current,
    centroid = Offset(width / 2f, height / 2f),
    pan = Offset.Zero,
    zoom = factor,
    minScale = minScale,
    maxScale = maxScale,
)

internal fun zoomInFactor(): Float = ZOOM_STEP
internal fun zoomOutFactor(): Float = 1f / ZOOM_STEP

/** Zoom in/out stack plus a recenter button, floating over the coverage map. */
@Suppress("LongParameterList") // Every parameter is a distinct, independent control input.
@Composable
internal fun MapControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val shape = RoundedCornerShape(CONTROL_CORNER_RADIUS_DP.dp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(colors.surface, shape)
                .border(1.dp, colors.border, shape),
        ) {
            IconButton(
                onClick = onZoomIn,
                enabled = canZoomIn,
                modifier = Modifier.size(CONTROL_SIZE_DP.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.coverage_zoom_in),
                    tint = if (canZoomIn) colors.textPrimary else colors.textTertiary,
                )
            }
            Box(
                modifier = Modifier
                    .size(width = CONTROL_SIZE_DP.dp, height = 1.dp)
                    .background(colors.border),
            )
            IconButton(
                onClick = onZoomOut,
                enabled = canZoomOut,
                modifier = Modifier.size(CONTROL_SIZE_DP.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = stringResource(R.string.coverage_zoom_out),
                    tint = if (canZoomOut) colors.textPrimary else colors.textTertiary,
                )
            }
        }
        IconButton(
            onClick = onRecenter,
            modifier = Modifier
                .size(CONTROL_SIZE_DP.dp)
                .clip(CircleShape)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.border, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = stringResource(R.string.coverage_recenter),
                tint = colors.textPrimary,
            )
        }
    }
}
