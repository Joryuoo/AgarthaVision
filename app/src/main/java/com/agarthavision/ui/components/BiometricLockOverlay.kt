package com.agarthavision.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

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
            .navigationBarsPadding(),
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
