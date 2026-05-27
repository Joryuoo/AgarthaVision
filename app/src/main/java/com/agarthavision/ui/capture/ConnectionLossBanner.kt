@file:Suppress("FunctionNaming")

package com.agarthavision.ui.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaSpacing
import com.komoui.components.Button
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
import com.komoui.themes.styles

@Composable
fun ConnectionLossBanner(
    visible: Boolean,
    isProbing: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.styles.destructive)
                .padding(horizontal = AgarthaSpacing.screenEdge, vertical = AgarthaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.connection_lost_title),
                color = MaterialTheme.styles.destructiveForeground,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onResume,
                size = ButtonSize.Sm,
                variant = ButtonVariant.Outline,
                enabled = !isProbing,
                loading = isProbing,
            ) {
                Text(
                    text = if (isProbing) {
                        stringResource(R.string.connection_lost_probing)
                    } else {
                        stringResource(R.string.connection_lost_resume)
                    },
                        color = MaterialTheme.styles.destructiveForeground,
                )
            }
        }
    }
}
