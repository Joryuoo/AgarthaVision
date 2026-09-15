package com.agarthavision.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Sync
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Branded top header: logo, wordmark, and an optional sync control shown when [onSync] is
 * provided. When [needsSync] is true the control is a gold "Sync now" pill; otherwise it is a
 * maroon icon button whose glyph spins while [isSyncing].
 */
@Composable
fun AppHeader(
    isSyncing: Boolean = false,
    needsSync: Boolean = false,
    onSync: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(48.dp)
                .scale(1.4f) // Scales the logo to eliminate the internal SVG padding and make it pop
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = colors.accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.weight(1f))
        if (onSync != null) {
            // Declared unconditionally (rules of composition); only applied while syncing.
            val spinTransition = rememberInfiniteTransition(label = "syncSpin")
            val spin by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing)
                ),
                label = "syncAngle"
            )
            if (needsSync) {
                // Sync required: a gold "Sync now" pill that draws the eye.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.gold)
                        .clickable(onClick = onSync)
                        .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_sync_now),
                        color = colors.onGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = AgarthaIcons.Sync,
                        contentDescription = null,
                        tint = colors.onGold,
                        modifier = Modifier.size(15.dp),
                    )
                }
            } else {
                // Synced (or a sync in flight): a maroon icon button; the glyph spins while
                // a sync actually runs.
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .clickable(onClick = onSync),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AgarthaIcons.Sync,
                        contentDescription = stringResource(R.string.dashboard_sync_now),
                        tint = colors.onAccent,
                        modifier = Modifier
                            .size(17.dp)
                            .rotate(if (isSyncing) spin else 0f),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppHeaderPreview() {
    AppHeader(needsSync = true, onSync = {})
}
