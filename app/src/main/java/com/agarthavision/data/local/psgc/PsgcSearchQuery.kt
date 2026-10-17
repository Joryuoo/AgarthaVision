package com.agarthavision.data.local.psgc

/**
 * Turns what a medtech typed into `LIKE` terms for
 * [com.agarthavision.data.local.dao.PsgcBarangayDao.search].
 *
 * Splits on whitespace and commas and matches every term independently, because PSA
 * spells cities "City of Cebu" rather than "Cebu City" — a single contiguous `LIKE` finds
 * nothing for the word order people actually type. Matching per term also makes
 * "lahug cebu" and "adams ilocos" narrow to one barangay each.
 *
 * A comma splits the barangay name from what comes after it — "lahug, city of cebu" — so
 * the segment before the first comma alone drives name ranking ([Parsed.nameTerms]) while
 * every segment still narrows the `WHERE` filter ([Parsed.terms]). 133 barangay names
 * themselves contain a comma (e.g. "Bgy. No. 42, Apaya"), which is why the comma is a
 * ranking hint rather than a hard field separator.
 *
 * Pure Kotlin so the folding and escaping rules are unit-testable without Room.
 */
object PsgcSearchQuery {
    /**
     * The result of parsing a raw search string: [terms] for the `WHERE` filter and
     * [nameTerms] for ranking against the `name` column.
     */
    data class Parsed(val terms: List<String>, val nameTerms: List<String>)

    /**
     * Lowercased, `LIKE`-escaped terms, in the order typed. Empty when there is nothing to
     * search for.
     *
     * Case is folded here rather than by SQL `lower()`, which handles ASCII only — 439
     * barangay names contain `ñ`. Wildcards are escaped so typing `%` searches for a
     * literal `%` instead of matching every barangay in the country.
     */
    fun terms(raw: String): List<String> = parse(raw).terms

    /**
     * Splits [raw] on commas into segments, then each segment on whitespace, folding case
     * and escaping `LIKE` wildcards per term throughout.
     *
     * [Parsed.terms] is every term from every segment, flattened in order — this is what
     * narrows the `WHERE` clause. [Parsed.nameTerms] is the first segment's terms when
     * [raw] contains a comma and that segment produced any terms — this is what drives
     * name ranking, so "lahug, city of cebu" ranks "Lahug" barangays by "lahug" alone
     * rather than being diluted by "city of cebu". When there is no comma, or the segment
     * before the first comma is blank (e.g. ", cebu" or ","), [Parsed.nameTerms] falls
     * back to [Parsed.terms].
     */
    fun parse(raw: String): Parsed {
        val segments = raw.lowercase().split(',')
        val segmentTerms = segments.map { segment -> segment.split(' ', '\t', '\n', '\r')
            .filter { it.isNotBlank() }
            .map { term -> term.escapeLike() }
        }
        val terms = segmentTerms.flatten()
        val hasComma = raw.contains(',')
        val firstSegmentTerms = segmentTerms.firstOrNull().orEmpty()
        val nameTerms = if (hasComma && firstSegmentTerms.isNotEmpty()) firstSegmentTerms else terms
        return Parsed(terms = terms, nameTerms = nameTerms)
    }

    private fun String.escapeLike(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
