package com.agarthavision.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaVisionTheme
import com.komoui.themes.styles

// Canvas + Maps SDK — sample-site pin on the Reports map view.
// Filled circle in primary color with Ink ring; size scales with sample count.
// See docs/components.md §6.
@Composable
fun GeoMapMarker(
    sampleCount: Int = 1,
    isHighRisk: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val fill = if (isHighRisk) MaterialTheme.styles.destructive
               else MaterialTheme.styles.primary
    val ring = MaterialTheme.styles.foreground

    Canvas(modifier = modifier.size(24.dp)) {
        val radius = (8 + (sampleCount.coerceAtMost(10) * 0.8f))
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(color = fill, radius = radius, center = center)
        drawCircle(color = ring, radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Preview(showBackground = true)
@Composable
private fun GeoMapMarkerPreview() {
    AgarthaVisionTheme {
        GeoMapMarker(sampleCount = 5, isHighRisk = false)
    }
}
