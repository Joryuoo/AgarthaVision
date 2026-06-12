@file:Suppress("FunctionNaming")

package com.agarthavision.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.records.AppColors

/**
 * Visual variant for [AgarthaButton]. Replaces KomoUI's `ButtonVariant`.
 *
 * - [Primary]     filled brand blue, white text
 * - [Secondary]   filled neutral gray, dark text
 * - [Outline]     hairline border, transparent fill
 * - [Destructive] filled red, white text
 * - [Ghost]       text-only, no fill or border
 */
enum class AgarthaButtonVariant { Primary, Secondary, Outline, Destructive, Ghost }

/** Height presets for [AgarthaButton]. Replaces KomoUI's `ButtonSize`. */
enum class AgarthaButtonSize(val height: Dp) {
    Default(40.dp),
    Large(48.dp),
}

private val PillShape = RoundedCornerShape(999.dp)

/**
 * Variant-aware button wrapping Material3 [Button] / [OutlinedButton] / [TextButton],
 * styled entirely from [AppColors]. The pill shape and token palette match
 * `agartha-design-system.md`. Drop-in replacement for the legacy button component.
 */
@Composable
fun AgarthaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AgarthaButtonVariant = AgarthaButtonVariant.Primary,
    size: AgarthaButtonSize = AgarthaButtonSize.Default,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val sizedModifier = modifier.height(size.height)

    when (variant) {
        AgarthaButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Blue,
                contentColor = AppColors.White,
            ),
            content = content,
        )

        AgarthaButtonVariant.Secondary -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Gray100,
                contentColor = AppColors.Gray900,
            ),
            content = content,
        )

        AgarthaButtonVariant.Destructive -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Red,
                contentColor = AppColors.White,
            ),
            content = content,
        )

        AgarthaButtonVariant.Outline -> OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = PillShape,
            border = BorderStroke(1.dp, AppColors.Gray200),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AppColors.Gray900,
            ),
            content = content,
        )

        AgarthaButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = PillShape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = AppColors.Gray700,
            ),
            content = content,
        )
    }
}
