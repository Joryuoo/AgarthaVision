package com.agarthavision.ui.theme

import androidx.compose.ui.unit.dp

// docs/components.md §4 spacing scale — use these, never arbitrary dp.
object AgarthaSpacing {
    val xxs  =  4.dp
    val xs   =  8.dp
    val sm   = 12.dp
    val md   = 16.dp
    val lg   = 20.dp
    val xl   = 24.dp
    val xxl  = 32.dp
    val xxxl = 40.dp
    val huge = 56.dp
    val mega = 64.dp

    // Semantic aliases called out explicitly in §4
    val cardPadding = 24.dp  // Card outer padding
    val screenEdge  = 20.dp  // Screen edge padding
    val clusterGap  = 12.dp  // Buttons in a row
}
