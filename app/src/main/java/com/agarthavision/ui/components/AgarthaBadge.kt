@file:Suppress("FunctionNaming")

package com.agarthavision.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.records.AppColors

/**
 * Visual variant for [AgarthaBadge]. Replaces KomoUI's `BadgeVariant`.
 *
 * - [Default]     filled brand blue, white text
 * - [Secondary]   filled neutral gray, muted text
 * - [Outline]     hairline border, transparent fill
 * - [Destructive] soft red tint, red text
 */
enum class AgarthaBadgeVariant { Default, Secondary, Outline, Destructive }

private val BadgeShape = RoundedCornerShape(999.dp)

/**
 * Variant-aware pill badge built on Material3 [Surface], styled from [AppColors].
 * [Surface] propagates `contentColor` down to text/icons in [content].
 * Drop-in replacement for the legacy badge component.
 */
@Composable
fun AgarthaBadge(
    modifier: Modifier = Modifier,
    variant: AgarthaBadgeVariant = AgarthaBadgeVariant.Default,
    content: @Composable () -> Unit,
) {
    val containerColor: Color
    val contentColor: Color
    var border: BorderStroke? = null

    when (variant) {
        AgarthaBadgeVariant.Default -> {
            containerColor = AppColors.Blue
            contentColor = AppColors.White
        }
        AgarthaBadgeVariant.Secondary -> {
            containerColor = AppColors.Gray100
            contentColor = AppColors.Gray700
        }
        AgarthaBadgeVariant.Outline -> {
            containerColor = Color.Transparent
            contentColor = AppColors.Gray700
            border = BorderStroke(1.dp, AppColors.Gray200)
        }
        AgarthaBadgeVariant.Destructive -> {
            containerColor = AppColors.RedTint
            contentColor = AppColors.Red
        }
    }

    Surface(
        modifier = modifier,
        shape = BadgeShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
