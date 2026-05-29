@file:Suppress("FunctionNaming")

package com.agarthavision.ui.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.components.SvgIcon

private val RedColor = Color(0xFFDC2626)

@Composable
fun ConnectionLossBanner(
    visible: Boolean,
    isProbing: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCollapsed by remember(visible) { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "BannerCollapse"
        ) { collapsed ->
            if (collapsed) {
                // Collapsed: just the warning icon pill on the left — tap to expand
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(14.dp, RoundedCornerShape(12.dp), spotColor = RedColor.copy(alpha = 0.5f))
                            .background(RedColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                            .clickable { isCollapsed = false }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SvgIcon(
                            pathData = "M 10.29 3.86 L 1.82 18 a 2 2 0 0 0 1.71 3 h 16.94 a 2 2 0 0 0 1.71 -3 L 13.71 3.86 a 2 2 0 0 0 -3.42 0 z M 12 9 v 4 M 12 17 h .01",
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2f
                        )
                    }
                }
            } else {
                // Expanded: full banner with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(24.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                        .background(RedColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Warning Icon
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        SvgIcon(
                            pathData = "M 10.29 3.86 L 1.82 18 a 2 2 0 0 0 1.71 3 h 16.94 a 2 2 0 0 0 1.71 -3 L 13.71 3.86 a 2 2 0 0 0 -3.42 0 z M 12 9 v 4 M 12 17 h .01",
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2f
                        )
                    }

                    // Text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connection lost",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Recording stopped. Tap Resume to reconnect.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    // Resume Button
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(100.dp))
                            .clickable(enabled = !isProbing) { onResume() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProbing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = "Resume",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Close Button — collapses to icon-only
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { isCollapsed = true },
                        contentAlignment = Alignment.Center
                    ) {
                        SvgIcon(
                            pathData = "M 18 6 L 6 18 M 6 6 L 18 18",
                            color = Color.White,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }
    }
}
