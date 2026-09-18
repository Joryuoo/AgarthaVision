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
 * Loading placeholder for [SampleDetailScreen].
 *
 * Mirrors the loaded layout — nav bar, square frame, a few detection rows — so the screen keeps
 * its shape on resume instead of flashing a blank spinner. The segmented control it used to
 * stencil is gone with the tabs: there is one screen now, not three.
 */
@Composable
fun SampleDetailSkeleton(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SampleDetailNavBar(title = "", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(16.dp),
            )
            repeat(SKELETON_DETECTION_ROWS) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

/** Enough rows to read as a list without claiming a count the sample may not have. */
private const val SKELETON_DETECTION_ROWS = 3
