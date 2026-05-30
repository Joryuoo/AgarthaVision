package com.agarthavision.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.agarthavision.ui.theme.AgarthaVisionTheme

// Canvas drawn on top of MicroscopyViewport.
// Renders bounding circles and species label chips for each detection.
// See docs/components.md §6.
@Composable
fun DetectionOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // TODO: for each Detection: drawCircle(color = ClinicalBlue, ...) + drawText(species label)
        // TODO: highlight color switches to AlertCoral when confidence < threshold
    }
}

@Preview(showBackground = true)
@Composable
private fun DetectionOverlayPreview() {
    AgarthaVisionTheme {
        DetectionOverlay()
    }
}
