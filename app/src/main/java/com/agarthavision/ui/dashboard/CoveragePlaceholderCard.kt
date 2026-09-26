package com.agarthavision.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.ui.theme.AgarthaTheme

@Composable
fun CoveragePlaceholderCard(
    period: HomePeriod,
    modifier: Modifier = Modifier,
) {
    val theme = AgarthaTheme.colors
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .testTag("coveragePlaceholderCard")
            .clip(shape)
            .background(theme.surface, shape)
            .border(1.dp, theme.border, shape)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MY COVERAGE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textSecondary,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = when (period) {
                        HomePeriod.TODAY -> "Today"
                        HomePeriod.LAST_7_DAYS -> "7 days"
                        HomePeriod.LAST_30_DAYS -> "30 days"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.textSecondary,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "My coverage",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Geographic distribution of smears and positivity",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
