package com.agarthavision.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.records.AppColors

sealed class Tab(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    data object Home     : Tab("dashboard", "Home",     R.drawable.ic_home)
    data object Sessions : Tab("sessions",  "Sessions", R.drawable.ic_layers)
    data object Records  : Tab("records",   "Records",  R.drawable.ic_chart)
}

val tabs: List<Tab> = listOf(Tab.Home, Tab.Sessions, Tab.Records)

/**
 * Screens that should show the bottom bar.
 * Only the main root level screens have the bottom navigation.
 */
val bottomBarRoutes: Set<String> = setOf(
    "dashboard",
    "sessions",
    "records"
)

private val TopBorderColor = Color(0xFFEEF0F4) // Gray100

/**
 * Custom bottom navigation bar built from basic Compose primitives.
 * Avoids Material3 NavigationBar inset issues entirely.
 */
@Composable
fun AgarthaBottomBar(
    currentRoute: String?,
    onTabSelected: (Tab) -> Unit,
    verifyQueueCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // 1px top border
                drawLine(
                    color = TopBorderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            },
        color = AppColors.White,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route

                val iconColor by animateColorAsState(
                    targetValue = if (selected) AppColors.Blue else AppColors.Gray400,
                    animationSpec = tween(200),
                    label = "iconColor"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (selected) AppColors.Blue else AppColors.Gray500,
                    animationSpec = tween(200),
                    label = "labelColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,       // no ripple — clean tap
                            role = Role.Tab,
                            onClick = { onTabSelected(tab) }
                        )
                        .padding(vertical = 6.dp)
                        .semantics {
                            if (tab is Tab.Sessions && verifyQueueCount > 0) {
                                contentDescription = "Sessions, $verifyQueueCount pending"
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = tab.label,
                            modifier = Modifier.size(22.dp),
                            tint = iconColor
                        )
                        if (tab is Tab.Sessions && verifyQueueCount > 0) {
                            BadgedBox(verifyQueueCount)
                        }
                    }
                    Text(
                        text = tab.label,
                        color = labelColor,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.1.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
}

@Composable
private fun BadgedBox(count: Int) {
    Box(
        modifier = Modifier
            .offset(x = 10.dp, y = (-4).dp)
            .background(AppColors.Red, RoundedCornerShape(999.dp))
            .border(2.dp, AppColors.White, RoundedCornerShape(999.dp))
            .padding(horizontal = 4.dp, vertical = 0.dp)
            .heightIn(min = 16.dp)
            .widthIn(min = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.White,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
        )
    }
}
