package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import java.time.Instant
import java.time.LocalDate

/** Room-only sync state for a freshly written local row. Never a Supabase column. */
private const val PENDING_STATUS = "pending"

/**
 * Converts between the Room patient row and the domain model.
 *
 * **The birthdate conversion lives here and only there.** Room stores it as epoch millis
 * at midnight in [CLINICAL_ZONE]; the domain model holds a [LocalDate]. Doing the
 * conversion at a call site instead is how two screens end up disagreeing about what day a
 * patient was born — pick `systemDefault()` in one place and UTC in another and a patient
 * born just after midnight shifts a day. [Patient.ageYears] resolves its `asOf` in the
 * same zone for that reason.
 */
fun PatientEntity.toDomain(): Patient =
    Patient(
        id = patientId,
        lastname = lastname,
        firstname = firstname,
        middleName = middleName,
        sex = Sex.fromRemote(sex),
        birthdate = Instant.ofEpochMilli(birthdate).atZone(CLINICAL_ZONE).toLocalDate(),
        psgcBarangayCode = psgcBarangayCode,
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

/**
 * Converts the domain model back into a Room row.
 *
 * [supabaseStatus] is not part of [Patient]: it is Room-only bookkeeping, never a Supabase
 * column, and every local write is by definition not yet on the server. It therefore
 * defaults to `pending` here rather than being carried through the domain layer, which
 * keeps a screen from being able to claim a row is synced when it is not.
 *
 * A null [Patient.sex] cannot be written. `patients.sex` is `not null` with a CHECK on
 * `('M', 'F')` both in Room and in `0001_init.sql`, and the form requires a value, so null
 * only ever arrives from reading an unreadable row. Persisting it would mean inventing a
 * sex or writing a value the server will reject — so this refuses instead, and the caller
 * collects one first.
 */
fun Patient.toEntity(supabaseStatus: String = PENDING_STATUS): PatientEntity =
    PatientEntity(
        patientId = id,
        lastname = lastname,
        firstname = firstname,
        middleName = middleName,
        sex = requireNotNull(sex) {
            "Patient $id has no readable sex; the form must supply one before saving."
        }.remoteValue,
        birthdate = birthdate.atStartOfDay(CLINICAL_ZONE).toInstant().toEpochMilli(),
        psgcBarangayCode = psgcBarangayCode,
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        supabaseStatus = supabaseStatus,
    )
