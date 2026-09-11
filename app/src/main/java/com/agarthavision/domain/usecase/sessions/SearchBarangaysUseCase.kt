package com.agarthavision.domain.usecase.sessions

import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.repository.PsgcRepository
import javax.inject.Inject

/**
 * Searches the offline PSGC dataset for the session barangay picker.
 *
 * Returns an empty list rather than an error for a query shorter than
 * [MIN_QUERY_LENGTH] — a single character matches thousands of the 42,001 barangays, so
 * there is nothing useful to show and no reason to scan for it.
 */
class SearchBarangaysUseCase @Inject constructor(
    private val psgcRepository: PsgcRepository,
) {
    suspend operator fun invoke(query: String): Result<List<PsgcBarangay>> =
        runCatching {
            val trimmed = query.trim()
            if (trimmed.length < MIN_QUERY_LENGTH) {
                emptyList()
            } else {
                psgcRepository.searchBarangays(query = trimmed, limit = RESULT_LIMIT)
            }
        }

    companion object {
        const val MIN_QUERY_LENGTH = 2

        /** Enough to scroll, few enough to stay responsive on a low-end device. */
        const val RESULT_LIMIT = 50
    }
}
