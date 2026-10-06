package com.agarthavision.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Generic centred empty-state layout used across screens.
 *
 * Renders either a custom illustration pair ([illustrationLight] and [illustrationDark]),
 * an optional 56 dp circle [icon] container, or no visual if neither is provided.
 * Below the visual, renders a 14 sp SemiBold [title] and a 13 sp [body].
 * An optional [action] slot is appended 20 dp below the body — use it for a CTA button.
 *
 * Callers are responsible for all strings (no hardcoded user text here).
 *
 * @param icon               Material icon to display inside the circle (optional).
 * @param title              Short headline shown below the visual.
 * @param body               Supporting sentence shown below the title.
 * @param modifier           Applied to the outermost [Column].
 * @param illustrationLight  Light-theme vector drawable resource (decorative, uses `ill_empty_*`
 *                           naming). Both light and dark must be provided to render an illustration.
 * @param illustrationDark   Dark-theme vector drawable resource (decorative, uses `ill_empty_*`
 *                           naming). Both light and dark must be provided to render an illustration.
 * @param illustrationWidth  Target width for the illustration (160 dp on full screens, 120 dp
 *                           inside cards). Defaults to 160 dp.
 * @param action             Optional composable rendered 20 dp below [body] (e.g. a [Button]).
 */
@Suppress("LongParameterList")
@Composable
fun EmptyState(
    icon: ImageVector? = null,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes illustrationLight: Int? = null,
    @DrawableRes illustrationDark: Int? = null,
    illustrationWidth: Dp = 160.dp,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (illustrationLight != null && illustrationDark != null) {
            val isDark = colors.isDark
            Image(
                painter = painterResource(if (isDark) illustrationDark else illustrationLight),
                contentDescription = null, // decorative
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(if (isDark) illustrationWidth * (212f / 200f) else illustrationWidth)
                    .aspectRatio(if (isDark) 212f / 182f else 200f / 170f),
            )
        } else if (icon != null) {
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
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
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
