package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * The one back affordance for full screens: a plain arrow, no label.
 *
 * Screens previously rendered back six different ways — a chevron drawable, a Material
 * chevron beside the word "Back", a hand-drawn SVG path, an automirrored arrow, and a
 * text-only link — with the tint split across three colour tokens. This matches
 * `ScreenTopBar`, which the verification sheets already use.
 *
 * Capture keeps its own glass chevron: that one sits on top of the camera and belongs to
 * the immersive chrome rather than to this set.
 */
@Composable
fun BackArrow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.nav_back),
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            tint = AgarthaTheme.colors.textPrimary,
        )
    }
}
