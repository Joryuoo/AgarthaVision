package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SexTest {

    @Test
    fun `remote values match the Supabase CHECK`() {
        assertEquals("M", Sex.MALE.remoteValue)
        assertEquals("F", Sex.FEMALE.remoteValue)
    }

    @Test
    fun `fromRemote round-trips both values`() {
        assertEquals(Sex.MALE, Sex.fromRemote("M"))
        assertEquals(Sex.FEMALE, Sex.fromRemote("F"))
    }

    @Test
    fun `fromRemote is case-insensitive`() {
        assertEquals(Sex.FEMALE, Sex.fromRemote("f"))
    }

    @Test
    fun `an unreadable value is null rather than a guessed sex`() {
        assertNull(Sex.fromRemote("X"))
        assertNull(Sex.fromRemote(""))
    }
}
