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
 * A colleague is someone who authored a session or report on a patient the signed-in user is
 * assigned to, the rule `profiles_select_colleague` (`supabase/migrations/0008_colleague_names.sql`)
 * gives a medtech. The pull works out those ids from the rows on the device and asks for them
 * by id, because the same table also hands an org admin every profile in their laboratory and a
 * super admin every profile (14zcqntjt3p). The caller's own name comes from the signed-in
 * session, not from here.
 *
 * Only `id` and `full_name` are selected. Role is never read from a colleague's row, and the
 * app reads no role or organization from `user_metadata` either.
 */
class ProfileRemoteDataSource @Inject constructor(
    private val supabaseProvider: dagger.Lazy<SupabaseClient>,
) {
    private val supabase: SupabaseClient get() = supabaseProvider.get()

    /** Callers chunk the ids and must guard against an empty list. */
    suspend fun fetchColleagues(colleagueIds: List<String>): List<ColleagueEntity> =
        supabase.postgrest[PROFILES_TABLE].select(Columns.list("id", "full_name")) {
            filter { isIn("id", colleagueIds) }
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
