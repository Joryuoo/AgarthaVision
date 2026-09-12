@file:Suppress("FunctionNaming")

package com.agarthavision.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.components.SkeletonBox

/**
 * Loading placeholder for [SampleDetailScreen]. Mirrors the loaded layout — nav bar,
 * segmented control, square image card, metadata strip — with shimmer stencils, so the
 * screen keeps its shape on resume instead of flashing a blank white spinner.
 */
@Composable
fun SampleDetailSkeleton(onBack: () -> Unit) {
    SampleDetailNavBar(title = "", onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
    ) {
        // Segmented-control-height placeholder
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape = RoundedCornerShape(0.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Square image-card placeholder
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(16.dp),
            )
            // Metadata strip placeholder
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}
