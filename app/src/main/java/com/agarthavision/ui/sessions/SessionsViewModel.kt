package com.agarthavision.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.util.sanitizeDateRange
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.GenerateSessionLabelUseCase
import com.agarthavision.domain.usecase.sync.ObserveSyncInProgressUseCase
import android.database.sqlite.SQLiteConstraintException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsState(
    val sessions: List<SessionWithStats> = emptyList(),
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val totalCount: Int = 0,
    /** Frames awaiting review across the filtered sessions. See [SessionsCounts]. */
    val unverifiedCount: Int = 0,
    val canLoadMore: Boolean = false,
    /**
     * The smear the app is currently working in, or null.
     *
     * The list used to read this off `Session.endedAt == null`, which was true for every
     * row because nothing writes `ended_at`. [com.agarthavision.core.session.SessionManager]
     * is the only thing that knows which session is active, so the flag comes from there.
     */
    val activeSessionId: String? = null,
    /**
     * The auto-generated label the New Session sheet opens on, or empty when it could not be
     * built. Empty is the pre-PB-10 behaviour — a field the medtech types into — rather than
     * a blocked sheet: a failure to suggest a name is no reason to refuse a smear.
     */
    val suggestedLabel: String = "",
    /** The patient whose session list this is, or null while loading. */
    val patient: Patient? = null,
    /** The barangay name resolved from [Patient.psgcBarangayCode], or null. */
    val barangayName: String? = null,
)

sealed interface SessionsEvent {
    data class NavigateToCapture(val sessionId: String) : SessionsEvent
    data class NavigateToVerificationQueue(val sessionId: String) : SessionsEvent
    data class ShareExport(val content: String) : SessionsEvent
}

/**
 * Backs the Sessions list and the New Session sheet.
 *
 * The list pipeline mirrors [com.agarthavision.ui.records.RecordsViewModel]:
 * `combine(inputs) → flatMapLatest → combine(page, counts, internal, rawSearch)`, with
 * SQL-backed filtering, pagination, and search. Uses [SharingStarted.WhileSubscribed] so
 * the upstream Room query is cancelled when the screen leaves composition while the
 * replay cache retains the last value. Per ADR-007 (identity dispatch).
 *
 * `TooManyFunctions` is suppressed for the same reason
 * [com.agarthavision.data.local.dao.SessionDao] suppresses it: one screen's callbacks
 * belong to one ViewModel, and splitting them across two classes to satisfy a count would
 * be inconsistent with every other ViewModel here for no functional benefit.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionManager: SessionManager,
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val generateSessionLabelUseCase: GenerateSessionLabelUseCase,
    private val patientRepository: PatientRepository,
    private val psgcRepository: PsgcRepository,
    private val observeSyncInProgressUseCase: ObserveSyncInProgressUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val internalState = MutableStateFlow(SessionsState())
    private val eventChannel = Channel<SessionsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /**
     * The patient whose smears this screen lists, read from the `patients/{patientId}` route.
     *
     * Every session here belongs to that patient — both the ones listed and the ones created:
     * `sessions.patient_id` is NOT NULL with a foreign key onto `patients`, and the list query
     * scopes on it. A null is unreachable through the UI (nothing navigates here without a
     * patient), so it renders an empty list with an error rather than crashing on a route
     * that should not exist.
     */
    private val patientId: String? = savedStateHandle["patientId"]

    init {
        refreshSuggestedLabel()
    }

    // Per ADR-007 (hard-rule fix): identity comes from a use case, not a direct
    // data-source read. Null identity (signed-out / offline) still lists local sessions.
    private val userIdFlow = observeLocalIdentityUseCase().map { it?.userId }

    // Cold, per ObserveSyncInProgressUseCase's contract - no `.onStart { emit(false) }` here,
    // since that would reintroduce a transient "not syncing" reading before the scheduler's
    // real state is known, which is the same confident-zero bug this ticket fixes.
    private val syncInProgressFlow = observeSyncInProgressUseCase()

    private val startDate = MutableStateFlow<LocalDate?>(null)
    private val endDate = MutableStateFlow<LocalDate?>(null)
    private val searchQuery = MutableStateFlow("")
    private val limit = MutableStateFlow(INITIAL_PAGE)

    // Debounced search prevents a new Room query on every keystroke; raw searchQuery
    // is still combined into the final state so the text field reflects input immediately.
    private val debouncedSearch = searchQuery.debounce(SEARCH_DEBOUNCE_MS)

    /** Bundled upstream inputs, re-emitted whenever any input changes. */
    private data class SessionsInputs(
        val userId: String?,
        val activeSessionId: String?,
        val start: LocalDate?,
        val end: LocalDate?,
        val debouncedQuery: String,
        val limit: Int,
    )

    /**
     * The session the medtech is working in, or null. Exempt from the date filter so the
     * open smear is never filtered out of the list it is reached from.
     */
    private val activeSessionIdFlow = sessionManager.state
        .map { (it as? SessionState.Active)?.session?.sessionId }
        .distinctUntilChanged()

    private val queryInputs = combine(
        combine(userIdFlow, activeSessionIdFlow) { uid, activeId -> uid to activeId },
        startDate, endDate, debouncedSearch, limit,
    ) { (uid, activeId), st, en, q, lim -> SessionsInputs(uid, activeId, st, en, q, lim) }

    /**
     * Observes the patient entity and resolves their barangay name for the preview header.
     */
    private val patientFlow: Flow<Pair<Patient?, String?>> = if (patientId.isNullOrBlank()) {
        flowOf(null to null)
    } else {
        patientRepository.observePatientById(patientId).map { patient ->
            val barangayName = patient?.psgcBarangayCode?.let { code ->
                psgcRepository.getBarangay(code)?.name
            }
            patient to barangayName
        }
    }

    private val sessionsStateFlow = queryInputs
        .flatMapLatest { inputs ->
            // Unreachable through the UI. Emitting an empty, non-loading state keeps a
            // malformed route from hanging on the loading skeleton forever, and keeps the
            // patient id out of the SQL as a nullable that would silently match nothing.
            val patient = patientId
            if (patient.isNullOrBlank()) {
                return@flatMapLatest internalState.map {
                    it.copy(isLoading = false, errorMessage = PATIENT_REQUIRED)
                }
            }
            val zone = ZoneId.systemDefault()
            val sinceMillis = Instant.now().minus(Duration.ofDays(RECENT_WINDOW_DAYS)).toEpochMilli()
            val startMillis = inputs.start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            val endMillis = inputs.end
                ?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)?.toEpochMilli()

            // Escape the free-text needle so `%`, `_`, and `\` in user input are literal.
            // Mirrors the escaping in GetRecordsUseCase so SQL behaviour is consistent.
            val escaped = inputs.debouncedQuery
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")

            combine(
                sessionRepository.observeVisibleSessionsPage(
                    inputs.userId, patient, inputs.activeSessionId, sinceMillis, startMillis,
                    endMillis, escaped, inputs.limit,
                ),
                sessionRepository.observeVisibleSessionsCounts(
                    inputs.userId, patient, inputs.activeSessionId, sinceMillis, startMillis,
                    endMillis, escaped,
                ),
                internalState,
                searchQuery,
                syncInProgressFlow,
            ) { sessions, counts, internal, rawSearch, syncing ->
                // An empty, unfiltered result while a sync is running is "unknown" rather than
                // settled-empty: the local DB may simply not have pulled the remote rows yet.
                // A filtered/searched empty result is left alone - the medtech typed a query
                // that has no matches, which is settled regardless of sync state.
                val unfiltered = inputs.debouncedQuery.isEmpty() &&
                    inputs.start == null && inputs.end == null
                val awaitingRemote = syncing && unfiltered && counts.totalCount == 0
                internal.copy(
                    sessions = sessions,
                    isLoading = awaitingRemote,
                    startDate = inputs.start,
                    endDate = inputs.end,
                    searchQuery = rawSearch,
                    totalCount = counts.totalCount,
                    unverifiedCount = counts.unverifiedCount,
                    canLoadMore = sessions.size >= inputs.limit,
                    activeSessionId = inputs.activeSessionId,
                )
            }
        }

    /**
     * Observable UI state for the Sessions screen.
     *
     * Uses [SharingStarted.WhileSubscribed] with a 5-second stop timeout so the upstream
     * Room query is cancelled when there are no active collectors (e.g. the screen leaves
     * composition), but the [StateFlow]'s replay cache retains the last emitted value.
     * A fresh collector therefore receives the last non-loading state immediately — no
     * flicker back to the loading skeleton on resubscribe — while the query eventually
     * restarts and emits a fresh update.
     *
     * [searchQuery] is combined from the raw (un-debounced) flow so the text field
     * reflects every keystroke immediately, while [sessions] and [totalCount]/[unverifiedCount]
     * only update after the debounce window.
     *
     * An empty, unfiltered result while [ObserveSyncInProgressUseCase] reports a sync running
     * is treated as "unknown" rather than settled-empty, so `isLoading` stays true instead of
     * flashing a confident zero the moment before the remote pull lands its rows.
     */
    val state: StateFlow<SessionsState> = combine(
        sessionsStateFlow,
        patientFlow,
    ) { sessionsState, (patient, barangayName) ->
        sessionsState.copy(
            patient = patient,
            barangayName = barangayName,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionsState(),
    )

    /**
     * Updates the free-text search query. Resets pagination.
     */
    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        limit.value = INITIAL_PAGE
    }

    /**
     * Applies an inclusive session-start date range. Resets pagination.
     */
    fun onDateRangeSelected(start: LocalDate?, end: LocalDate?) {
        // Sessions cannot have started in the future; clamp before the range hits SQL.
        val (safeStart, safeEnd) = sanitizeDateRange(start, end)
        startDate.value = safeStart
        endDate.value = safeEnd
        limit.value = INITIAL_PAGE
    }

    /**
     * Requests the next page of results.
     */
    fun onLoadMore() {
        limit.value += PAGE_STEP
    }

    /**
     * Starts a smear for the patient this screen belongs to.
     *
     * **The sheet no longer asks for a barangay or a note.** The barangay lives on the
     * patient — it is the unit surveillance aggregates on, it is what the admin site's
     * geospatial mapping tracks, and it does not change from one smear to the next. The note
     * was only ever an ad-hoc patient identifier, which the patient record now is properly.
     */
    fun onCreateSession(label: String) {
        if (internalState.value.isCreating) return
        // Defence in depth on both. SessionsScreen blocks submit on a blank label, and no
        // navigation reaches this screen without a patient id — a null here would mean a
        // route that does not carry one, which would fail the foreign key anyway.
        val patient = patientId
        // Normalize to uppercase so that manually typed labels and auto-generated labels
        // share the same case, keeping the unique index naturally case-consistent.
        val normalizedLabel = label.trim().uppercase()
        if (normalizedLabel.isBlank() || patient.isNullOrBlank()) {
            val reason = if (normalizedLabel.isBlank()) LABEL_REQUIRED else PATIENT_REQUIRED
            internalState.update { it.copy(errorMessage = reason) }
            return
        }
        internalState.update { it.copy(isCreating = true, errorMessage = null) }
        viewModelScope.launch {
            // Pre-check: reject the label before touching the DB so the medtech sees a
            // friendly message rather than a constraint violation crash.
            if (sessionRepository.isSessionLabelTaken(patient, normalizedLabel)) {
                internalState.update { it.copy(isCreating = false, errorMessage = DUPLICATE_LABEL) }
                return@launch
            }
            runCatching {
                sessionManager.startSession(label = normalizedLabel, patientId = patient)
            }.onSuccess { entity ->
                internalState.update { it.copy(isCreating = false, errorMessage = null) }
                // The smear just created holds the suggestion that was on screen, so the next
                // one has to move on. Recomputed from the database rather than incremented
                // locally: the medtech may have edited the label before submitting it, and a
                // local counter would then hand out a number that is already in use.
                refreshSuggestedLabel()
                eventChannel.send(SessionsEvent.NavigateToCapture(entity.sessionId))
            }.onFailure { error ->
                // Backstop: if a concurrent create slipped past the pre-check and the unique
                // index fired, map the constraint exception to the same user-facing message
                // rather than surfacing a generic or technical error.
                val message = if (error.cause is SQLiteConstraintException ||
                    error is SQLiteConstraintException
                ) {
                    DUPLICATE_LABEL
                } else {
                    error.message ?: "Failed to create session."
                }
                internalState.update { it.copy(isCreating = false, errorMessage = message) }
            }
        }
    }

    /**
     * Recomputes the label the New Session sheet pre-fills with.
     *
     * Silent on failure. The sheet falls back to an empty field, which is what it had before
     * the generator existed; surfacing an error banner for a suggestion the medtech can type
     * over themselves would be noise on a screen they came to to start a smear.
     */
    private fun refreshSuggestedLabel() {
        val patient = patientId ?: return
        viewModelScope.launch {
            generateSessionLabelUseCase(patient).onSuccess { label ->
                internalState.update { it.copy(suggestedLabel = label) }
            }
        }
    }

    fun onResumeSession(sessionId: String) {
        viewModelScope.launch {
            runCatching { sessionManager.resumeSession(sessionId) }
                .onSuccess { eventChannel.send(SessionsEvent.NavigateToCapture(sessionId)) }
                .onFailure { error ->
                    internalState.update {
                        it.copy(errorMessage = error.message ?: "Could not open session.")
                    }
                }
        }
    }

    fun onOpenVerificationQueue(sessionId: String) {
        viewModelScope.launch {
            runCatching { sessionManager.resumeSession(sessionId) }
                .onSuccess { eventChannel.send(SessionsEvent.NavigateToVerificationQueue(sessionId)) }
                .onFailure { error ->
                    internalState.update {
                        it.copy(errorMessage = error.message ?: "Could not open verification queue.")
                    }
                }
        }
    }

    fun onRenameSession(sessionId: String, newLabel: String) {
        if (newLabel.isBlank()) return
        val patient = patientId ?: return
        // Normalize to uppercase so that manually typed labels and auto-generated labels
        // share the same case, keeping the unique index naturally case-consistent.
        val normalizedLabel = newLabel.trim().uppercase()
        viewModelScope.launch {
            // Resolve the session to confirm it belongs to this patient before checking the
            // label. If the session is not found (race or stale state), bail silently —
            // there is no session to rename.
            val session = sessionRepository.getSessionById(sessionId) ?: return@launch
            if (session.patientId != patient) return@launch

            if (sessionRepository.isSessionLabelTaken(
                    patientId = patient,
                    label = normalizedLabel,
                    excludingSessionId = sessionId,
                )
            ) {
                internalState.update { it.copy(errorMessage = DUPLICATE_LABEL) }
                return@launch
            }
            runCatching {
                sessionRepository.updateSessionLabel(sessionId, normalizedLabel)
            }.onFailure { error ->
                val message = if (error.cause is SQLiteConstraintException ||
                    error is SQLiteConstraintException
                ) {
                    DUPLICATE_LABEL
                } else {
                    error.message ?: "Could not rename session."
                }
                internalState.update { it.copy(errorMessage = message) }
            }
        }
    }

    fun onExportSession(sessionId: String) {
        viewModelScope.launch {
            runCatching {
                val session = sessionRepository.getSessionById(sessionId) ?: return@runCatching
                // In a real app, this would query samples and detections and generate a CSV.
                // For now, we generate a basic summary text to share.
                val content = "Export for Session ${session.id} (${session.label ?: "Unnamed"})\n" +
                    "Started: ${Instant.ofEpochMilli(session.startedAt)}"
                eventChannel.send(SessionsEvent.ShareExport(content))
            }.onFailure { error ->
                internalState.update {
                    it.copy(errorMessage = error.message ?: "Could not export session.")
                }
            }
        }
    }

    fun onDismissError() {
        internalState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        private const val RECENT_WINDOW_DAYS = 30L
        private const val INITIAL_PAGE = 5
        private const val PAGE_STEP = 10

        /** Debounce for the session list's free-text search before it hits Room. */
        private const val SEARCH_DEBOUNCE_MS = 300L

        // Copy lives here rather than in strings.xml to match the other ViewModels in this
        // module (see CaptureViewModel). Lifting all of it into resources needs an error-type
        // seam across every screen state, which is a wider change than this ticket.
        private const val LABEL_REQUIRED = "Label is required."

        /** Shown when the medtech picks a label already used by another smear for this patient. */
        private const val DUPLICATE_LABEL = "A smear with this label already exists for this patient."

        /** Unreachable through the UI: every route that opens this screen carries a patient. */
        private const val PATIENT_REQUIRED = "This session has no patient. Open it from a patient."
    }
}
