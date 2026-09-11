package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectionMapperTest {

    private fun entity(stage: String? = null) = DetectionEntity(
        detectionId = "det-1",
        sampleId = "sample-1",
        classLabel = "Ascaris lumbricoides",
        confidence = 0.9f,
        bboxX = 0.1f,
        bboxY = 0.2f,
        bboxW = 0.3f,
        bboxH = 0.4f,
        verdict = DetectionVerdict.CONFIRMED.value,
        expertClass = null,
        verifiedByUser = true,
        stage = stage,
    )

    @Test
    fun `toDomain maps a known stage value`() {
        val domain = entity(stage = "unfertilized").toDomain()
        assertEquals(EggStage.UNFERTILIZED, domain.stage)
    }

    @Test
    fun `toDomain maps null stage to null`() {
        val domain = entity(stage = null).toDomain()
        assertNull(domain.stage)
    }

    @Test
    fun `toDomain maps an unrecognized legacy stage value to null`() {
        // Guards against a stored row using a value EggStage no longer supports
        // (e.g. a pre-ticket "corticated"/"fertilized" row) crashing the mapper.
        val domain = entity(stage = "corticated").toDomain()
        assertNull(domain.stage)
    }

    @Test
    fun `toEntity maps stage back to its lowercase value`() {
        val domain = entity(stage = "unfertilized").toDomain()
        val roundTripped = domain.toEntity()
        assertEquals("unfertilized", roundTripped.stage)
    }

    @Test
    fun `round trip entity to domain to entity preserves stage`() {
        val original = entity(stage = "larvated")
        val roundTripped = original.toDomain().toEntity()
        assertEquals(original.stage, roundTripped.stage)
    }

    @Test
    fun `round trip preserves null stage`() {
        val original = entity(stage = null)
        val roundTripped = original.toDomain().toEntity()
        assertNull(roundTripped.stage)
    }
}
