package com.agarthavision.domain.usecase.verify

import com.agarthavision.core.util.escapeLike
import com.agarthavision.data.local.dao.SpeciesSuggestionDao
import javax.inject.Inject

/**
 * Suggests species already present on this device for the "Other species" free-text field.
 *
 * Local only, and deliberately so: the index is derived from rows the device already holds,
 * so it answers with the radio off. That is the case that matters — a medtech in a barangay
 * with no signal is exactly who is typing free text.
 *
 * Returns [Result] per C4, and an **empty list rather than a failure** for a short query.
 */
class SearchSpeciesSuggestionsUseCase @Inject constructor(
    private val dao: SpeciesSuggestionDao,
) {
    suspend operator fun invoke(query: String): Result<List<String>> = runCatching {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            emptyList()
        } else {
            // Escaped, or a species name typed with an underscore becomes a wildcard.
            dao.searchByPrefix(prefix = escapeLike(trimmed.lowercase()), limit = RESULT_LIMIT)
                .map { it.species }
        }
    }

    companion object {
        /** One character matches most of a short index; two is where a prefix starts earning. */
        const val MIN_QUERY_LENGTH = 2

        /** Capped the way SearchBarangaysUseCase caps, for the same reason. */
        const val RESULT_LIMIT = 50
    }
}
