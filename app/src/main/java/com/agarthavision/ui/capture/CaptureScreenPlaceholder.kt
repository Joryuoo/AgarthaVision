package com.agarthavision.ui.capture

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Placeholder for the real Capture screen.
 *
 * Owner: jojseph. Implementation track: Phase 2 §B
 * (CaptureScreen + CaptureViewModel + FrameSampler + InferFrameUseCase against
 * [com.agarthavision.data.remote.InferenceApi]).
 */
@Composable
fun CaptureScreenPlaceholder() {
    Text(
        text = "Capture — TODO",
        modifier = Modifier.fillMaxSize().wrapContentSize(),
    )
}
