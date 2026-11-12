package com.agarthavision.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Whether the application is currently covered by the biometric lock screen.
 *
 * Dialogs, bottom sheets, and popup menus read this to avoid spawning separate Android
 * `WindowManager` sub-windows above [BiometricLockOverlay] while locked, without dismissing or
 * losing their underlying state.
 */
val LocalAppLocked = compositionLocalOf { false }

object BiometricLockTestTags {
    const val OVERLAY = "biometric_lock_overlay"
    const val UNLOCK_BUTTON = "biometric_lock_unlock_button"
}

/**
 * Hosts application [content] behind [BiometricLockOverlay] when [isLocked] is true.
 *
 * Keeps [content] in the composition while locked so unsaved edits, scroll state, and pending
 * dialog/sheet states survive authentication, while skipping placement of [content] so underlying
 * nodes are not drawn, hit-tested, or exposed to accessibility focus until unlocked.
 */
@Composable
fun BiometricLockHost(
    isLocked: Boolean,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppLocked provides isLocked) {
        Box(modifier = modifier.fillMaxSize()) {
            Layout(
                content = content,
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    if (!isLocked) {
                        placeables.forEach { it.placeRelative(0, 0) }
                    }
                }
            }

            if (isLocked) {
                BackHandler(enabled = true) {
                    // Consume system Back while locked so underlying screens or dialogs cannot be invoked.
                }
                BiometricLockOverlay(
                    onUnlockClick = onUnlockClick,
                )
            }
        }
    }
}

/**
 * Full-screen lock overlay displayed when Biometric Lock is active and the app is locked.
 *
 * Covers the entire UI so patient data and session details are completely hidden until
 * biometric authentication succeeds.
 */
@Composable
fun BiometricLockOverlay(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(enabled = true, onClick = {}) // absorbs touches to prevent background clicks
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag(BiometricLockTestTags.OVERLAY),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.brandFill,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.biometric_lock_overlay_title),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.biometric_lock_overlay_subtitle),
                color = colors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onUnlockClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandFill,
                    contentColor = colors.onBrandFill,
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier.testTag(BiometricLockTestTags.UNLOCK_BUTTON),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        tint = colors.onBrandFill,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.biometric_prompt_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
