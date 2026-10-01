package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The name of a colleague whose records this device holds.
 *
 * Room-only cache of `profiles(id, full_name)`, readable through `profiles_select_colleague`
 * (`supabase/migrations/0008_colleague_names.sql`) for exactly the colleagues who authored a
 * session or report on a patient this medtech is assigned to. It exists so a read-only record
 * can say whose it is offline (14zcqntjph6); nothing here grants or checks access.
 *
 * [fullName] is nullable because `profiles.full_name` is: the profile trigger writes only the id
 * and role, so a colleague whose name was never set shows as "another medtech".
 */
@Entity(tableName = "colleagues")
data class ColleagueEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "full_name")
    val fullName: String?,
)
