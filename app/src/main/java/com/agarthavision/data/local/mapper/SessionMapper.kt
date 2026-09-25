package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSyncStatus

/**
 * Converts a Room session row into the domain model used by records screens.
 *
 * The mapping is now one-to-one: `notes`, `psgc_barangay_code` and `ended_at` are gone from
 * the entity as of Room 13 and gone from [Session] as of PB-09, so there is no longer a
 * field on either side without a counterpart on the other. `patient_id` carries straight
 * through — it is not null in Room or in Postgres.
 */
fun SessionEntity.toDomain(): Session =
    Session(
        id = sessionId,
        userId = userId,
        patientId = patientId,
        deviceId = deviceId,
        startedAt = startedAt,
        label = label,
        supabaseStatus = SessionSyncStatus.fromValue(supabaseStatus),
    )
