package com.agarthavision.data.inference

import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.inference.Prediction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the persistence contract for `samples.predictions_json`.
 *
 * Introducing the domain [Prediction] must not change a single byte of what is written to or
 * read from that column, or every sample row captured before this change becomes unreadable.
 */
class PredictionMapperTest {

    private val gson = Gson()
    private val listType = object : TypeToken<List<PredictionDto>>() {}.type

    /** Exactly the shape `inference/server.py` returns and every existing row already holds. */
    private val legacyJson =
        """[{"class":"Ascaris lumbricoides","confidence":0.92,"x":320.0,"y":240.0,"width":80.0,"height":60.0}]"""

    private val domainPrediction = Prediction(
        classLabel = "Ascaris lumbricoides",
        confidence = 0.92f,
        x = 320f,
        y = 240f,
        width = 80f,
        height = 60f,
    )

    @Test
    fun `legacy json deserializes into the domain model unchanged`() {
        val dtos = gson.fromJson<List<PredictionDto>>(legacyJson, listType)

        assertEquals(listOf(domainPrediction), dtos.toDomainPredictions())
    }

    @Test
    fun `domain model serializes back to the legacy json shape`() {
        val json = gson.toJson(listOf(domainPrediction).toPredictionDtos())

        assertEquals(legacyJson, json)
    }

    @Test
    fun `round trip through json preserves every field`() {
        val json = gson.toJson(listOf(domainPrediction).toPredictionDtos())
        val restored = gson.fromJson<List<PredictionDto>>(json, listType).toDomainPredictions()

        assertEquals(listOf(domainPrediction), restored)
    }

    @Test
    fun `dto and domain conversions are symmetric`() {
        val dto = PredictionDto("Hookworm", 0.5f, 1f, 2f, 3f, 4f)

        assertEquals(dto, dto.toDomain().toDto())
    }
}
