package com.agarthavision.core.connectivity

import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.remote.InferenceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors inference container reachability while the medtech is on the capture screen.
 *
 * Probes [InferenceApi.health] every [PROBE_INTERVAL_MS]. Two consecutive failures transition
 * [status] to [Status.Disconnected]. The loop is cancelled and [status] resets to
 * [Status.Connected] whenever nobody is capturing, so the next visit starts clean.
 *
 * **Gated on the screen, not on the session.** It used to run for as long as a session was
 * active, which was already wrong whenever the medtech sat on Records with a session open, and
 * became permanent once sessions stopped ending (86d4ab4vm) - a 10-second poll forever, on
 * battery and mobile data, to drive a banner nobody can see. [acquire] and [release] are
 * refcounted so the loop covers exactly the time a capture screen is mounted.
 *
 * See CONTEXT.md.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    private val api: InferenceApi,
    private val sessionManager: SessionManager,
) {
    sealed interface Status {
        data object Connected : Status
        data object Disconnected : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<Status>(Status.Connected)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** How many capture screens are currently mounted. Practically 0 or 1. */
    private val captureScreens = MutableStateFlow(0)

    init {
        scope.launch {
            combine(sessionManager.state, captureScreens) { session, screens ->
                session is SessionState.Active && screens > 0
            }.collectLatest { shouldProbe ->
                if (shouldProbe) runProbeLoop() else _status.value = Status.Connected
            }
        }
    }

    /** Called when a capture screen appears. Pair with [release]. */
    fun acquire() {
        captureScreens.update { it + 1 }
    }

    /** Called when a capture screen goes away. Floors at zero so an extra call is harmless. */
    fun release() {
        captureScreens.update { (it - 1).coerceAtLeast(0) }
    }

    suspend fun probe(): Boolean {
        val healthy = tryProbe()
        _status.value = if (healthy) Status.Connected else Status.Disconnected
        return healthy
    }

    private suspend fun runProbeLoop() {
        var failures = 0
        while (true) {
            delay(PROBE_INTERVAL_MS)
            if (tryProbe()) {
                failures = 0
                _status.value = Status.Connected
            } else {
                failures++
                if (failures >= FAILURE_THRESHOLD) {
                    _status.value = Status.Disconnected
                }
            }
        }
    }

    private suspend fun tryProbe(): Boolean =
        runCatching { api.health().isSuccessful }.getOrDefault(false)

    companion object {
        private const val PROBE_INTERVAL_MS = 10_000L
        private const val FAILURE_THRESHOLD = 2
    }
}
