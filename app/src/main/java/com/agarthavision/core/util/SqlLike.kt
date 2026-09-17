package com.agarthavision.core.util

/**
 * Escapes a free-text needle so `%`, `_` and `\` reach SQL as literals.
 *
 * A `LIKE` pattern treats `%` and `_` as wildcards, so a surname containing an underscore
 * silently matches far more than it should, and a query of a single `%` matches everything.
 * The query using this must declare the same escape character: `LIKE … ESCAPE '\'`.
 *
 * Backslash is replaced first. Doing it last would re-escape the backslashes this function
 * just introduced, turning `%` into `\\%` — a literal backslash followed by a wildcard.
 *
 * Needles drawn from a fixed enum are passed through unescaped on purpose; see the species
 * predicate in `SessionDao`'s `RECORDS_FILTER`.
 */
fun escapeLike(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")
