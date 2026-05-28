package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.navigation.Screen

// Design Tokens
private val Brand = Color(0xFF1E40AF)
private val Muted = Color(0xFF6E6E73)
private val Hairline = Color(0x1E3C3C43)

@Composable
fun AgarthaBottomBar(
    activeRoute: String,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(83.dp)
            .background(Color.White.copy(alpha = 0.95f))
            .border(0.5.dp, Hairline),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabItem(
                label = "Today",
                icon = Icons.Outlined.Home,
                isActive = activeRoute == Screen.Dashboard.route,
                onClick = { onNavigate(Screen.Dashboard.route) }
            )
            TabItem(
                label = "Sessions",
                icon = Icons.Outlined.Layers,
                isActive = activeRoute == Screen.Sessions.route,
                onClick = { onNavigate(Screen.Sessions.route) }
            )
            TabItem(
                label = "Records",
                icon = Icons.Outlined.Inventory2,
                isActive = activeRoute == Screen.Records.route,
                onClick = { onNavigate(Screen.Records.route) }
            )
            TabItem(
                label = "Settings",
                icon = Icons.Outlined.Settings,
                isActive = activeRoute == Screen.Settings.route,
                onClick = { onNavigate(Screen.Settings.route) }
            )
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Brand else Muted,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = if (isActive) Brand else Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp
        )
    }
}
