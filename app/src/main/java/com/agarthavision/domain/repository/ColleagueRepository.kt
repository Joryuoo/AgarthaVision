package com.agarthavision.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Colleagues' names, as the last pull brought them down.
 *
 * Only the colleagues whose records this medtech can read are ever here
 * (`supabase/migrations/0008_colleague_names.sql`). A name is a label, not a permission:
 * nothing reads this to decide what may be seen or changed.
 */
interface ColleagueRepository {
    /** The colleague's full name, or null when none is known on this device. */
    suspend fun nameOf(userId: String): String?

    /** Every known name, keyed by user id. */
    fun observeNames(): Flow<Map<String, String?>>
}
