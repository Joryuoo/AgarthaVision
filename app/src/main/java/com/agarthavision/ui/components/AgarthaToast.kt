@file:Suppress("FunctionNaming")

package com.agarthavision.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.records.AppColors

/**
 * Toast severity. Replaces KomoUI's `SonnerVariant`.
 *
 * - [Default]     neutral dark surface
 * - [Destructive] red surface (errors / failures)
 * - [Success]     green surface (confirmations)
 */
enum class AgarthaToastVariant { Default, Destructive, Success }

/**
 * Holds the [SnackbarHostState] plus the variant of the toast currently being shown,
 * so [AgarthaToastHost] can color it. Snackbars are shown one at a time, so a single
 * tracked variant is sufficient. Replaces KomoUI's Sonner host state.
 */
class AgarthaToastState(val snackbarHostState: SnackbarHostState) {

    internal var currentVariant by mutableStateOf(AgarthaToastVariant.Default)
        private set

    /**
     * Show a toast and suspend until it is dismissed or its action is performed.
     * Auto-dismisses after ~4s ([SnackbarDuration.Short]). If [onAction] is provided
     * and the user taps [actionLabel], [onAction] is invoked.
     */
    suspend fun show(
        message: String,
        variant: AgarthaToastVariant = AgarthaToastVariant.Default,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        currentVariant = variant
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            withDismissAction = actionLabel == null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onAction?.invoke()
        }
    }
}

/** Remembers an [AgarthaToastState] for the current composition. */
@Composable
fun rememberAgarthaToastState(): AgarthaToastState {
    val hostState = remember { SnackbarHostState() }
    return remember(hostState) { AgarthaToastState(hostState) }
}

/**
 * Snackbar-based toast host replacing the old toast host. Place it at the bottom
 * of a `Box` (e.g. `Modifier.align(Alignment.BottomCenter)`) for bottom-center toasts.
 * Colors follow the active variant on [state].
 */
@Composable
fun AgarthaToastHost(
    state: AgarthaToastState,
    modifier: Modifier = Modifier,
) {
    val containerColor: Color
    val contentColor: Color = AppColors.White

    when (state.currentVariant) {
        AgarthaToastVariant.Default -> containerColor = AppColors.Gray900
        AgarthaToastVariant.Destructive -> containerColor = AppColors.Red
        AgarthaToastVariant.Success -> containerColor = AppColors.Green
    }

    SnackbarHost(
        hostState = state.snackbarHostState,
        modifier = modifier,
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(12.dp),
            containerColor = containerColor,
            contentColor = contentColor,
            actionContentColor = contentColor,
            dismissActionContentColor = contentColor,
        )
    }
}
