package com.agarthavision.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.agarthavision.ui.theme.AgarthaVisionTheme

// Box + Canvas + Image — Capture screen and detection card.
// Displays a raw microscopy frame; slot for DetectionOverlay drawn on top.
// See docs/components.md §6.
@Composable
fun MicroscopyViewport(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        // TODO: AndroidView wrapping Camera2 / CameraX preview surface
        // TODO: Image(bitmap, ...) for still-frame mode (HITL validate card)
    }
}

@Preview(showBackground = true)
@Composable
private fun MicroscopyViewportPreview() {
    AgarthaVisionTheme {
        MicroscopyViewport()
    }
}
