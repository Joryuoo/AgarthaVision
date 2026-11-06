package com.agarthavision.data.supabase

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RowDecodingTest {

    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TestDummyRow(val id: String, val count: Int) {
        fun toEntity(): TestDummyEntity = TestDummyEntity(id = id, count = count)
    }

    private data class TestDummyEntity(val id: String, val count: Int)

    @Test
    fun `AC1 - decodeRowsSkippingMalformed returns decoded entities in input order for valid rows`() {
        val input = listOf(
            buildJsonObject {
                put("id", "row-1")
                put("count", 10)
            },
            buildJsonObject {
                put("id", "row-2")
                put("count", 20)
            }
        )

        val result = input.decodeRowsSkippingMalformed<TestDummyRow, TestDummyEntity>(
            json = jsonDecoder,
            tag = "TestTag",
            rowKind = "dummy"
        ) { it.toEntity() }

        assertEquals(2, result.size)
        assertEquals("row-1", result[0].id)
        assertEquals(10, result[0].count)
        assertEquals("row-2", result[1].id)
        assertEquals(20, result[1].count)
    }

    @Test
    fun `AC2 - drops malformed row and retains valid rows with warning log`() {
        val input = listOf(
            buildJsonObject {
                put("id", "valid-1")
                put("count", 1)
            },
            buildJsonObject {
                // Malformed: missing "count" field and "id" is an int instead of string
                put("id", 999)
            },
            buildJsonObject {
                put("id", "valid-2")
                put("count", 2)
            }
        )

        val result = input.decodeRowsSkippingMalformed<TestDummyRow, TestDummyEntity>(
            json = jsonDecoder,
            tag = "TestTag",
            rowKind = "dummy"
        ) { it.toEntity() }

        assertEquals(2, result.size)
        assertEquals("valid-1", result[0].id)
        assertEquals("valid-2", result[1].id)
    }

    @Test
    fun `AC3 - empty input list returns empty list`() {
        val input = emptyList<kotlinx.serialization.json.JsonElement>()

        val result = input.decodeRowsSkippingMalformed<TestDummyRow, TestDummyEntity>(
            json = jsonDecoder,
            tag = "TestTag",
            rowKind = "dummy"
        ) { it.toEntity() }

        assertTrue(result.isEmpty())
    }
}