package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Generic centred empty-state layout used across screens.
 *
 * Renders a 56 dp circle icon container, a 14 sp SemiBold [title], and a 13 sp [body].
 * An optional [action] slot is appended 20 dp below the body — use it for a CTA button.
 *
 * Callers are responsible for all strings (no hardcoded user text here). Icon must be
 * an [ImageVector] from `Icons.*`; screens that need a painter icon should draw their
 * own layout instead.
 *
 * @param icon     Material icon to display inside the circle.
 * @param title    Short headline shown below the icon.
 * @param body     Supporting sentence shown below the title.
 * @param modifier Applied to the outermost [Column].
 * @param action   Optional composable rendered 20 dp below [body] (e.g. a [Button]).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            fontSize = 13.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
