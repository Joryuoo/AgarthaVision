package com.agarthavision.data.local.psgc

/**
 * Turns what a medtech typed into `LIKE` terms for
 * [com.agarthavision.data.local.dao.PsgcBarangayDao.search].
 *
 * Splits on whitespace and matches every term independently, because PSA spells cities
 * "City of Cebu" rather than "Cebu City" — a single contiguous `LIKE` finds nothing for
 * the word order people actually type. Matching per term also makes "lahug cebu" and
 * "adams ilocos" narrow to one barangay each.
 *
 * Pure Kotlin so the folding and escaping rules are unit-testable without Room.
 */
object PsgcSearchQuery {
    /**
     * Lowercased, `LIKE`-escaped terms, in the order typed. Empty when there is nothing to
     * search for.
     *
     * Case is folded here rather than by SQL `lower()`, which handles ASCII only — 439
     * barangay names contain `ñ`. Wildcards are escaped so typing `%` searches for a
     * literal `%` instead of matching every barangay in the country.
     */
    fun terms(raw: String): List<String> =
        raw
            .lowercase()
            .split(' ', '\t', '\n')
            .filter { it.isNotBlank() }
            .map { term ->
                term
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_")
            }
}
