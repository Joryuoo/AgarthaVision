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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors inference container reachability while a recording session is active.
 *
 * Probes [InferenceApi.health] every [PROBE_INTERVAL_MS]. Two consecutive failures
 * transition [status] to [Status.Disconnected]. The probe loop is cancelled
 * automatically when the session goes [SessionState.Idle], and [status] resets to
 * [Status.Connected] so the next session starts clean.
 *
 * See docs/03_MOBILE_APP_PLAN.md §1.9.
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

    init {
        scope.launch {
            sessionManager.state.collectLatest { state ->
                when (state) {
                    is SessionState.Recording -> runProbeLoop()
                    SessionState.Idle -> _status.value = Status.Connected
                }
            }
        }
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
