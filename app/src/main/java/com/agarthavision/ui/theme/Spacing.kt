package com.agarthavision.ui.theme

import androidx.compose.ui.unit.dp

// Spacing scale from CONTEXT.md.
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

// Compact spacing scale consumed by the records screens and dashboard.
// Kept here so all spacing tokens live in one place.
object Spacing {
    val xs   =  4.dp
    val sm   =  8.dp
    val md   = 12.dp
    val lg   = 16.dp
    val xl   = 20.dp
    val xxl  = 24.dp
    val xxxl = 32.dp
}
