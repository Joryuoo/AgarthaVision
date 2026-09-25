package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room mirror of the Supabase `patient_users` join.
 *
 * **This is what patient visibility resolves through**, not [PatientEntity.createdBy]. A
 * patient links to many users, so an admin can give a second medtech access by inserting a
 * row here; `created_by` stays provenance.
 *
 * Remotely the creator's own row is written by the `on_patient_created` trigger rather than
 * by the client, because the `patients` SELECT policy reads this table and PostgREST returns
 * the inserted row on insert — without the link the inserting medtech could not read back
 * the patient they just created. Locally the row is written alongside the patient, so an
 * offline-created patient is visible before it has ever reached Supabase.
 */
@Entity(
    tableName = "patient_users",
    primaryKeys = ["patient_id", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("user_id")],
)
data class PatientUserEntity(
    @ColumnInfo(name = "patient_id")
    val patientId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "linked_at")
    val linkedAt: Long,
)
