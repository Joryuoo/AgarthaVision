@file:Suppress("FunctionNaming")

package com.agarthavision.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a toast stays up before dismissing itself. */
const val AGARTHA_TOAST_DURATION_MS = 2_000L

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
 * so [AgarthaToastHost] can color it. Replaces KomoUI's Sonner host state.
 *
 * Toasts **replace** rather than queue: [show] cancels whatever is on screen before
 * putting up the new one. During a capture run detections arrive faster than a
 * snackbar's natural lifetime, and a queue would leave the medtech reading messages
 * about frames that scrolled past minutes ago.
 */
class AgarthaToastState(
    val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {

    internal var currentVariant by mutableStateOf(AgarthaToastVariant.Default)
        private set

    private var showJob: Job? = null

    /**
     * Show a toast, replacing any toast already on screen.
     *
     * Dismisses itself after [durationMillis]. Material3 has no preset shorter than
     * `Short` (~4s), so this drives an [SnackbarDuration.Indefinite] snackbar from a
     * timer instead. If [onAction] is provided and the user taps [actionLabel],
     * [onAction] is invoked.
     */
    fun show(
        message: String,
        variant: AgarthaToastVariant = AgarthaToastVariant.Default,
        actionLabel: String? = null,
        durationMillis: Long = AGARTHA_TOAST_DURATION_MS,
        onAction: (() -> Unit)? = null,
    ) {
        showJob?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()
        currentVariant = variant

        showJob = scope.launch {
            val timer = launch {
                delay(durationMillis)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = false,
                duration = SnackbarDuration.Indefinite,
            )
            timer.cancel()
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
    }
}

/** Remembers an [AgarthaToastState] for the current composition. */
@Composable
fun rememberAgarthaToastState(): AgarthaToastState {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { AgarthaToastState(hostState, scope) }
}

/**
 * Snackbar-based toast host replacing the old toast host. Place it at the bottom
 * of a `Box` (e.g. `Modifier.align(Alignment.BottomCenter)`) for bottom-center toasts.
 * Colors follow the active variant on [state].
 *
 * Each toast is swipeable in either direction — with a 2s life and no dismiss X
 * (the slot is taken by the action button), a swipe is the only way to clear one
 * early.
 */
@Composable
fun AgarthaToastHost(
    state: AgarthaToastState,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val containerColor: Color
    // Inverse-surface text for the neutral toast; on-accent for bright fills.
    val contentColor: Color

    when (state.currentVariant) {
        AgarthaToastVariant.Default -> {
            containerColor = colors.textPrimary
            contentColor = colors.background
        }
        AgarthaToastVariant.Destructive -> {
            containerColor = colors.danger
            contentColor = colors.onAccent
        }
        AgarthaToastVariant.Success -> {
            containerColor = colors.success
            contentColor = colors.onAccent
        }
    }

    SnackbarHost(
        hostState = state.snackbarHostState,
        modifier = modifier,
    ) { data ->
        val dismissState = rememberSwipeToDismissBoxState()

        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                data.dismiss()
            }
        }

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
        ) {
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
}
