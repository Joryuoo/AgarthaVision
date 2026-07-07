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

@Composable
fun SheetActionRow(
    primaryLabel: String,
    secondaryLabel: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    primaryLoading: Boolean = false,
    primaryEnabled: Boolean = true
) {
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
                .background(colors.dangerTint, RoundedCornerShape(14.dp))
                .border(0.5.dp, colors.danger.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                .clickable { onSecondaryClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = secondaryLabel,
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
                .background(if (primaryEnabled) colors.textPrimary else colors.textTertiary, RoundedCornerShape(14.dp))
                .clickable(enabled = primaryEnabled && !primaryLoading) { onPrimaryClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (primaryLoading) "Loading..." else primaryLabel,
                color = colors.background,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }
    }
}
