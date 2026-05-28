package com.agarthavision.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import coil.compose.AsyncImage

// Shared Modal Sheet Tokens
val SheetBrand = Color(0xFF1E40AF)
val SheetWarning = Color(0xFFFF9F0A)
val SheetDanger = Color(0xFFFF3B30)
val SheetSuccess = Color(0xFF34C759)
val SheetAiPurple = Color(0xFFAF52DE)
val SheetInk = Color(0xFF0F172A)
val SheetMuted = Color(0xFF6E6E73)
val SheetSubtle = Color(0xFF8E8E93)
val SheetSurface = Color(0xFFFFFFFF)
val SheetHairline = Color(60, 60, 67, (0.12f * 255).toInt())
val SheetHairlineSoft = Color(60, 60, 67, (0.08f * 255).toInt())

@Composable
fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 18.dp)
            .width(36.dp)
            .height(5.dp)
            .background(Color(60, 60, 67, (0.3f * 255).toInt()), RoundedCornerShape(100.dp))
    )
}

@Composable
fun SheetTitleRow(
    title: String,
    metaText: String,
    isManual: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = SheetInk,
            letterSpacing = (-0.5).sp
        )
        
        val bg = if (isManual) Color(120, 120, 128, (0.14f * 255).toInt()) else SheetSurface
        val fg = if (isManual) Color(0xFF515154) else SheetMuted
        
        Box(
            modifier = Modifier
                .background(bg, RoundedCornerShape(100.dp))
                .border(if (!isManual) 0.5.dp else 0.dp, if (!isManual) SheetHairline else Color.Transparent, RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = metaText.uppercase(),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                letterSpacing = 0.4.sp
            )
        }
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
                .background(Color(255, 59, 48, (0.12f * 255).toInt()), RoundedCornerShape(14.dp))
                .border(0.5.dp, Color(255, 59, 48, (0.16f * 255).toInt()), RoundedCornerShape(14.dp))
                .clickable { onSecondaryClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = secondaryLabel,
                color = SheetDanger,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }
        
        // Primary Button (Brand Filled)
        Box(
            modifier = Modifier
                .weight(2f)
                .background(if (primaryEnabled) SheetBrand else SheetMuted, RoundedCornerShape(14.dp))
                .clickable(enabled = primaryEnabled && !primaryLoading) { onPrimaryClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (primaryLoading) "Loading..." else primaryLabel,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }
    }
}
