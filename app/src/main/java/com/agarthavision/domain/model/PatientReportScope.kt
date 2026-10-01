package com.agarthavision.domain.model

import java.time.LocalDate

/**
 * What a patient report should cover: a date range on `session.startedAt`, and/or an explicit
 * subset of the patient's sessions.
 *
 * Every field defaults to "no restriction", so `PatientReportScope()` — the default a caller
 * gets by omitting the argument — pools every one of the patient's sessions. [startDate] and
 * [endDate] are inclusive and resolved in [CLINICAL_ZONE], matching the convention
 * `ui/sessions/SessionsViewModel`'s date-range filter already uses.
 *
 * [sessionIds], when non-null, is a hard allow-list: a session id in it that does not belong to
 * the patient (or to the current user) is a caller error, not a silent drop — see
 * `resolvePatientReportSessions`.
 */
data class PatientReportScope(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val sessionIds: Set<String>? = null,
)
