package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.ColleagueEntity
import com.agarthavision.domain.model.LocalIdentity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColleagueRowDecodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decoding colleague row with non-null full_name populates fullName`() {
        val payload = """{"id":"colleague-1","full_name":"Maria Santos"}"""
        val row = json.decodeFromString<ProfileRemoteDataSource.ColleagueRow>(payload)

        assertEquals("colleague-1", row.id)
        assertEquals("Maria Santos", row.fullName)
    }

    @Test
    fun `decoding colleague row with null full_name does not crash and defaults to placeholder`() {
        val payload = """{"id":"colleague-2","full_name":null}"""
        val row = json.decodeFromString<ProfileRemoteDataSource.ColleagueRow>(payload)

        assertEquals("colleague-2", row.id)
        assertNull(row.fullName)

        val entity = ColleagueEntity(
            userId = row.id,
            fullName = row.fullName?.trim()?.takeIf { it.isNotEmpty() }
                ?: LocalIdentity.PENDING_PROFILE_PLACEHOLDER,
        )
        assertEquals(LocalIdentity.PENDING_PROFILE_PLACEHOLDER, entity.fullName)
    }

    @Test
    fun `decoding colleague row with missing full_name field defaults to null and placeholder`() {
        val payload = """{"id":"colleague-3"}"""
        val row = json.decodeFromString<ProfileRemoteDataSource.ColleagueRow>(payload)

        assertEquals("colleague-3", row.id)
        assertNull(row.fullName)

        val entity = ColleagueEntity(
            userId = row.id,
            fullName = row.fullName?.trim()?.takeIf { it.isNotEmpty() }
                ?: LocalIdentity.PENDING_PROFILE_PLACEHOLDER,
        )
        assertEquals(LocalIdentity.PENDING_PROFILE_PLACEHOLDER, entity.fullName)
    }
}
