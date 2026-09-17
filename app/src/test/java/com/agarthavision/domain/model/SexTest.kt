package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
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
    fun `fromRemote is total so a hand-edited row cannot crash the patient list`() {
        assertEquals(Sex.MALE, Sex.fromRemote("X"))
        assertEquals(Sex.MALE, Sex.fromRemote(""))
    }
}
