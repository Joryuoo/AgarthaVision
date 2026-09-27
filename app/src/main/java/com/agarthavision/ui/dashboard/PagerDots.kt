package com.agarthavision.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaTheme

@Composable
fun PagerDots(
    pageCount: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (page in 0 until pageCount) {
            val isSelected = page == current
            val description = when (page) {
                0 -> "Page 1 of $pageCount, Activity tiles"
                1 -> "Page 2 of $pageCount, My coverage"
                else -> "Page ${page + 1} of $pageCount"
            }
            Box(
                modifier = Modifier
                    .semantics {
                        this.role = Role.Tab
                        this.selected = isSelected
                        this.contentDescription = description
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(page) },
                    )
                    .padding(vertical = 10.dp, horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isSelected) {
                                Modifier
                                    .width(18.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                            } else {
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                            }
                        )
                        .background(if (isSelected) colors.accent else colors.borderStrong),
                )
            }
        }
    }
}
