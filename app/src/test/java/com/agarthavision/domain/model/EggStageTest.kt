package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EggStageTest {

    @Test
    fun `enum has exactly four entries`() {
        assertEquals(4, EggStage.entries.size)
    }

    @Test
    fun `corticated decorticated and fertilized are intentionally absent`() {
        val names = EggStage.entries.map { it.name }
        assertTrue("CORTICATED" !in names)
        assertTrue("DECORTICATED" !in names)
        assertTrue("FERTILIZED" !in names)
    }

    @Test
    fun `validFor Ascaris returns only unfertilized`() {
        assertEquals(listOf(EggStage.UNFERTILIZED), EggStage.validFor(EggSpecies.ASCARIS))
    }

    @Test
    fun `validFor Trichuris returns unembryonated and embryonated`() {
        assertEquals(
            listOf(EggStage.UNEMBRYONATED, EggStage.EMBRYONATED),
            EggStage.validFor(EggSpecies.TRICHURIS),
        )
    }

    @Test
    fun `validFor Hookworm returns unembryonated and larvated`() {
        assertEquals(
            listOf(EggStage.UNEMBRYONATED, EggStage.LARVATED),
            EggStage.validFor(EggSpecies.HOOKWORM),
        )
    }

    @Test
    fun `validFor Other is empty`() {
        assertTrue(EggStage.validFor(EggSpecies.OTHER).isEmpty())
    }

    @Test
    fun `fromValue matches local and remote values`() {
        assertEquals(EggStage.LARVATED, EggStage.fromValue("larvated"))
        assertEquals(EggStage.LARVATED, EggStage.fromValue("LARVATED"))
    }

    @Test
    fun `fromValue returns null for unknown values`() {
        assertNull(EggStage.fromValue("corticated"))
    }

    @Test
    fun `fromValue returns null for other excluded legacy values`() {
        assertNull(EggStage.fromValue("decorticated"))
        assertNull(EggStage.fromValue("fertilized"))
    }

    @Test
    fun `fromValue returns null for empty string`() {
        assertNull(EggStage.fromValue(""))
    }

    @Test
    fun `fromValue round-trips every entry's value and remoteValue`() {
        EggStage.entries.forEach { stage ->
            assertEquals(stage, EggStage.fromValue(stage.value))
            assertEquals(stage, EggStage.fromValue(stage.remoteValue))
        }
    }
}
