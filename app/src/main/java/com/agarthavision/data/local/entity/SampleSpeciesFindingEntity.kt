package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one species finding the medtech logged on a single frame.
 *
 * Mirrors the Supabase `sample_species_findings` table
 * (`supabase/migrations/0012_polyparasitism_findings.sql`). One frame carries zero or more
 * rows — a field with Ascaris and hookworm in it carries two, and a clean field carries
 * none.
 *
 * The count is **per species**, never a total for the field: WHO infection-intensity
 * thresholds are species-specific and differ by more than an order of magnitude, so a combined
 * "eggs in this field" number cannot be interpreted at all.
 *
 * Zero rows is a meaningful state, not a missing one. It is how a clean field is recorded,
 * which is why [com.agarthavision.domain.model.EggSpecies] has no "no egg" member — a
 * sentinel species would have to be excluded from every species-keyed aggregation forever.
 *
 * **A divergence from Postgres worth knowing about.** SQLite unique indexes always treat
 * NULLs as distinct, so a duplicate `(sample_id, species, null stage)` slips past Room while
 * Postgres rejects it via `sample_species_findings_unique_unstaged`. Since [stage] is always
 * null, that partial index is the one in force. The writer groups by species before inserting,
 * so a duplicate cannot arise; the index here is a backstop, not the mechanism.
 */
@Entity(
    tableName = "sample_species_findings",
    foreignKeys = [
        ForeignKey(
            entity = SampleEntity::class,
            parentColumns = ["sample_id"],
            childColumns = ["sample_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sample_id"),
        Index("species"),
        Index(value = ["sample_id", "species", "stage"], unique = true),
    ],
)
data class SampleSpeciesFindingEntity(
    /**
     * Derived deterministically from `(sampleId, species)` rather than random, so
     * re-submitting an edited sample replaces the row it corrects instead of inserting a
     * duplicate beside it. See `VerificationMapper`.
     */
    @PrimaryKey
    @ColumnInfo(name = "finding_id")
    val findingId: String,

    @ColumnInfo(name = "sample_id")
    val sampleId: String,

    /**
     * Canonical class name, or the medtech's free text when the dropdown does not cover the
     * species. Same convention as `detections.class_label` / `detections.expert_class` so
     * the two group together.
     */
    @ColumnInfo(name = "species")
    val species: String,

    /**
     * Always null.
     *
     * `0012_polyparasitism_findings.sql` is applied and frozen (C6), so the column stays; but
     * 86d4a6jwy, which was to fill it, was reverted on staging (`9dcfd5d`) and deprioritised —
     * its four stage values were never checked against literature, and the CHECK constraint in
     * 0012 hard-codes the same four. Whoever revives the ticket needs a migration to widen that
     * CHECK before writing anything here.
     */
    @ColumnInfo(name = "stage")
    val stage: String? = null,

    /**
     * Eggs of this species and stage in this one low-power field. Always positive: a count
     * of zero is the absence of a row, not a row holding zero.
     */
    @ColumnInfo(name = "egg_count")
    val eggCount: Int,
)
