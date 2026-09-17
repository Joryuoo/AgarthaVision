package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSyncStatus

/**
 * Converts a Room session row into the domain model used by records screens.
 *
 * [Session.endedAt] is always null. The column is gone as of Room 13 — sessions do not end
 * (86d4ab4vm) — and the domain field survives only because the Sessions list still derives
 * its active/resumable state from it. PB-09 gives that a real source.
 */
fun SessionEntity.toDomain(): Session =
    Session(
        id = sessionId,
        userId = userId,
        deviceId = deviceId,
        startedAt = startedAt,
        endedAt = null,
        label = label,
        supabaseStatus = SessionSyncStatus.fromValue(supabaseStatus),
    )
