package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.ColleagueEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Reads colleagues' names from Supabase `profiles`.
 *
 * `profiles_select_colleague` (`supabase/migrations/0008_colleague_names.sql`) returns a
 * colleague's row only when the caller can already read something that colleague authored on
 * a patient they are both assigned to, so this never lists a laboratory's staff. The caller's
 * own row is filtered out: their name comes from the signed-in session, not from here.
 *
 * Only `id` and `full_name` are selected. Role is never read from a colleague's row, and the
 * app reads no role or organization from `user_metadata` either.
 */
class ProfileRemoteDataSource @Inject constructor(
    private val supabaseProvider: dagger.Lazy<SupabaseClient>,
) {
    private val supabase: SupabaseClient get() = supabaseProvider.get()

    suspend fun fetchColleagues(userId: String): List<ColleagueEntity> =
        supabase.postgrest[PROFILES_TABLE].select(Columns.list("id", "full_name")) {
            filter { neq("id", userId) }
        }.decodeList<ColleagueRow>().map { ColleagueEntity(userId = it.id, fullName = it.fullName) }

    @Serializable
    private data class ColleagueRow(
        @SerialName("id") val id: String,
        @SerialName("full_name") val fullName: String? = null,
    )

    private companion object {
        const val PROFILES_TABLE = "profiles"
    }
}
