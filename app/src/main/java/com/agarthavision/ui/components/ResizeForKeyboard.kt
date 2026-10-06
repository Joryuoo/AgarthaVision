package com.agarthavision.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Switches the activity to adjustResize while the calling screen is shown, then restores the
 * previous mode. The activity defaults to adjustPan, which pans the window up on top of
 * `imePadding()` and leaves a blank band above the keyboard. Other screens rely on the pan, so
 * only screens that use `imePadding()` should call this.
 */
@Composable
fun ResizeForKeyboard() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val previous = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { previous?.let { window.setSoftInputMode(it) } }
    }
}
