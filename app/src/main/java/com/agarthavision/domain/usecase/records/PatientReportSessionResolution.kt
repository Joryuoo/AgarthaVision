package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.Session
import java.time.ZoneId

/**
 * Resolves which of [sessions] (already scoped to one patient and one user) a patient report
 * should cover, applying [scope]'s date range and/or explicit session-id subset.
 *
 * A pure function per C2 so the selection rule is unit-testable without a database — the same
 * reason [com.agarthavision.domain.usecase.reports.aggregateLpfPerSpecies] is one.
 *
 * @param zone the zone [scope]'s inclusive dates are resolved in, matching the convention
 *   `ui/sessions/SessionsViewModel`'s date-range filter already uses.
 * @throws IllegalArgumentException when [PatientReportScope.sessionIds] names an id not present
 *   in [sessions] — a caller error (a foreign or stale session id), not a silent drop.
 */
fun resolvePatientReportSessions(
    sessions: List<Session>,
    scope: PatientReportScope,
    zone: ZoneId,
): List<Session> {
    val requestedIds = scope.sessionIds
    if (requestedIds != null) {
        val knownIds = sessions.mapTo(mutableSetOf()) { it.id }
        val unknown = requestedIds - knownIds
        require(unknown.isEmpty()) {
            "Session id(s) $unknown do not belong to this patient."
        }
    }

    val startMillis = scope.startDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val endMillis = scope.endDate
        ?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)?.toEpochMilli()

    return sessions.filter { session ->
        val inSelection = requestedIds == null || session.id in requestedIds
        val afterStart = startMillis == null || session.startedAt >= startMillis
        val beforeEnd = endMillis == null || session.startedAt <= endMillis
        inSelection && afterStart && beforeEnd
    }
}
