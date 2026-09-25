package com.agarthavision.ui.records

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Session Detail snackbar host, lifted clear of the system navigation bar.
 *
 * The screen's zeroed `contentWindowInsets` also zero the Scaffold's snackbar offset, and this
 * screen has no bottom bar to sit above, so without its own navigation-bar padding the snackbar
 * lands behind the system buttons and its Share action can't be tapped (14zcqnthuac). Padding
 * the host rather than the content moves only the snackbar. Records needs none of this: it is a
 * tab, and the app's bottom bar already carries the inset.
 */
@Composable
internal fun SessionDetailSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState, modifier = Modifier.navigationBarsPadding())
}
