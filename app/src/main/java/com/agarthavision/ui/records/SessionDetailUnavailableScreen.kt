package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agarthavision.R
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

/**
 * What Session Detail shows once the session has resolved to something it cannot display:
 * missing from this device, or owned by a different account. A plain statement with a way
 * back, instead of the skeleton it used to sit on forever.
 */
@Composable
internal fun SessionDetailUnavailableScreen(
    unavailable: SessionUnavailable,
    onBack: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(start = Spacing.xs, end = Spacing.sm, top = 14.dp, bottom = 12.dp),
            ) {
                BackArrow(
                    onBack = onBack,
                    contentDescription = stringResource(R.string.session_detail_back),
                )
            }
        },
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentAlignment = Alignment.Center,
        ) {
            when (unavailable) {
                SessionUnavailable.NOT_FOUND -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.session_detail_not_found_title),
                    body = stringResource(R.string.session_detail_not_found_body),
                )
                SessionUnavailable.NOT_VISIBLE -> EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.session_detail_not_visible_title),
                    body = stringResource(R.string.session_detail_not_visible_body),
                )
            }
        }
    }
}
