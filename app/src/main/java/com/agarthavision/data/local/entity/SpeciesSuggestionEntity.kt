package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One species name the "Other species" field can suggest.
 *
 * Reference data with **no Supabase mirror**, like `psgc_barangays`: it is derived locally
 * from rows the device already holds, so it works with the radio off — which is exactly
 * when free text gets typed, in a barangay with no signal.
 *
 * Free text is the only path by which a species outside `EggSpecies` enters the corpus.
 * Without suggestions the same organism acquires three spellings, and because those rows
 * land in the table that doubles as the retraining corpus, the damage does not wash out.
 *
 * [species] is the primary key so a re-derivation is idempotent — the same name from a
 * finding and from a detection collapses to one row. [lookup] is the pre-lowercased form
 * the prefix match runs against, lowercased in Kotlin rather than by SQL `lower()`, which
 * folds ASCII only. It carries the index, because the prefix match runs against it and
 * not against the primary key.
 */
@Entity(
    tableName = "species_suggestions",
    indices = [Index("lookup")],
)
data class SpeciesSuggestionEntity(
    @PrimaryKey
    @ColumnInfo(name = "species")
    val species: String,

    @ColumnInfo(name = "lookup")
    val lookup: String,
)
