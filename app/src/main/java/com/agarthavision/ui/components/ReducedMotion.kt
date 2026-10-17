package com.agarthavision.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal fun isAnimationOff(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** True when the system "Remove animations" setting is on (animator duration scale is 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        isAnimationOff(
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
}
