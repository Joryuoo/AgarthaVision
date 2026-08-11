package com.agarthavision.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Branded top header: logo, wordmark, and an optional light/dark theme toggle
 * shown when [onToggleTheme] is provided.
 */
@Composable
fun AppHeader(
    isDarkMode: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(48.dp)
                .scale(1.4f) // Scales the logo to eliminate the internal SVG padding and make it pop
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = AgarthaTheme.colors.accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.weight(1f))
        if (onToggleTheme != null) {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    painter = painterResource(
                        if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon
                    ),
                    contentDescription = stringResource(
                        if (isDarkMode) R.string.theme_toggle_to_light else R.string.theme_toggle_to_dark
                    ),
                    tint = AgarthaTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppHeaderPreview() {
    AppHeader(isDarkMode = false, onToggleTheme = {})
}
