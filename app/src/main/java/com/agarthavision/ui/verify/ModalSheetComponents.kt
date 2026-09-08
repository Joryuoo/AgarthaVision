package com.agarthavision.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.TextStyle
import coil.compose.AsyncImage
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppTypography

@Composable
fun ScreenTopBar(
    title: String,
    metaText: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = AppTypography.headlineSmall,
                color = colors.textPrimary,
            )
            Text(
                metaText.uppercase(),
                fontSize = 11.sp,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * Snapshot [SheetActionRow] renders from — bundled since this primary/secondary action pair
 * always travels together at both its call sites ([VerificationSheet], [ManualSheet]).
 */
data class SheetActionRowState(
    val primaryLabel: String,
    val secondaryLabel: String,
    val onPrimaryClick: () -> Unit,
    val onSecondaryClick: () -> Unit,
    val primaryLoading: Boolean = false,
    val primaryEnabled: Boolean = true
)

@Composable
fun SheetActionRow(state: SheetActionRowState) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Secondary Button (Destructive Ghost)
        Box(
            modifier = Modifier
                .weight(1f)
                .testTag(VerifyTestTags.SHEET_SECONDARY_ACTION)
                .background(colors.dangerTint, RoundedCornerShape(14.dp))
                .border(0.5.dp, colors.danger.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                .clickable { state.onSecondaryClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.secondaryLabel,
                color = colors.dangerText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }

        // Primary Button — inverse surface (dark chip in light mode, light chip in dark)
        Box(
            modifier = Modifier
                .weight(2f)
                .testTag(VerifyTestTags.SHEET_PRIMARY_ACTION)
                .background(
                    if (state.primaryEnabled) colors.textPrimary else colors.textTertiary,
                    RoundedCornerShape(14.dp),
                )
                .clickable(enabled = state.primaryEnabled && !state.primaryLoading) { state.onPrimaryClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.primaryLoading) "Loading..." else state.primaryLabel,
                color = colors.background,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }
    }
}

/**
 * Compact pill button. When [enabled] is false it dims and stops accepting taps —
 * used to show the ends of the verification queue.
 */
@Composable
internal fun SmallToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val borderColor = when {
        !enabled -> AgarthaTheme.colors.border
        selected -> AgarthaTheme.colors.accent
        else -> AgarthaTheme.colors.borderStrong
    }
    val labelColor = when {
        !enabled -> AgarthaTheme.colors.textTertiary
        selected -> AgarthaTheme.colors.accent
        else -> AgarthaTheme.colors.textSecondary
    }

    Box(
        modifier = modifier
            .background(
                if (selected && enabled) AgarthaTheme.colors.accentTint else AgarthaTheme.colors.surface,
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
