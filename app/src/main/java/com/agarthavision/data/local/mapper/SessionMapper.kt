package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.Session

/**
 * Converts a Room session row into the domain model used by records screens.
 */
fun SessionEntity.toDomain(): Session =
    Session(
        id = sessionId,
        userId = userId,
        deviceId = deviceId,
        startedAt = startedAt,
        endedAt = endedAt,
        notes = notes,
    )
