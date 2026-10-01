package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agarthavision.data.local.entity.ColleagueEntity
import kotlinx.coroutines.flow.Flow

/** Colleagues' names, cached by the pull so read-only records can name their author offline. */
@Dao
interface ColleagueDao {
    /** Writes or refreshes names. A colleague no longer returned keeps the last name known. */
    @Upsert
    suspend fun upsertColleagues(colleagues: List<ColleagueEntity>)

    @Query("SELECT full_name FROM colleagues WHERE user_id = :userId")
    suspend fun getFullName(userId: String): String?

    @Query("SELECT * FROM colleagues")
    fun observeColleagues(): Flow<List<ColleagueEntity>>
}
