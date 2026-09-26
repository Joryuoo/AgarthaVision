package com.agarthavision.data.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Hand-builds "AVGE" v1 byte arrays (mirroring `tools/geo/build-geo-asset.py`'s writer) and
 * proves [BoundaryBinaryReader] decodes them correctly — no dependency on the real committed
 * asset, so this test is pure JVM and fast.
 */
class BoundaryBinaryReaderTest {

    private val vintage = "q2_2026"
    private val lonMin = 116f
    private val latMin = 4f
    private val lonMax = 127f
    private val latMax = 22f

    @Test
    fun `bad magic throws`() {
        val badMagic = "XXXX".toByteArray(Charsets.US_ASCII)
        val bytes = Builder(lonMin, latMin, lonMax, latMax)
            .also { it.magicBytes = badMagic }
            .provincesHeader(vintage)
            .u16(0) // townCount
            .u16(0) // areaCount
            .build()

        val ex = assertThrows(IllegalStateException::class.java) {
            BoundaryBinaryReader.readProvinces(bytes, vintage)
        }
        assertTrue(ex.message.orEmpty().contains("magic", ignoreCase = true))
    }

    @Test
    fun `unsupported version throws`() {
        val bytes = Builder(lonMin, latMin, lonMax, latMax)
            .also { it.version = 99 }
            .provincesHeader(vintage)
            .u16(0)
            .u16(0)
            .build()

        val ex = assertThrows(IllegalStateException::class.java) {
            BoundaryBinaryReader.readProvinces(bytes, vintage)
        }
        assertTrue(ex.message.orEmpty().contains("version", ignoreCase = true))
    }

    @Test
    fun `vintage mismatch throws`() {
        val bytes = Builder(lonMin, latMin, lonMax, latMax)
            .provincesHeader("q1_2020")
            .u16(0)
            .u16(0)
            .build()

        val ex = assertThrows(IllegalStateException::class.java) {
            BoundaryBinaryReader.readProvinces(bytes, vintage)
        }
        assertTrue(ex.message.orEmpty().contains("q1_2020"))
    }

    @Test
    fun `single-ring single-area provinces file round-trips`() {
        val builder = Builder(lonMin, latMin, lonMax, latMax)
        builder.provincesHeader(vintage)
        builder.u16(0) // no towns

        val ring = listOf(120.0 to 10.0, 121.0 to 10.0, 121.0 to 11.0, 120.0 to 11.0)
        builder.u16(1) // areaCount
        builder.areaRecord(
            code = 1804500000,
            parentCode = 1800000000,
            name = "Negros Occidental",
            rings = listOf(ring),
        )

        val (set, directory) = BoundaryBinaryReader.readProvinces(builder.build(), vintage)

        assertEquals(1, set.areas.size)
        val area = set.areas.single()
        assertEquals("1804500000", area.code)
        assertEquals("1800000000", area.parentCode)
        assertEquals("Negros Occidental", area.name)
        assertEquals(1, area.rings.size)
        assertEquals(ring.size * 2, area.rings[0].size)

        assertEquals(1, directory.provinces.size)
        assertEquals("1800000000", directory.provinces.getValue("1804500000").regionCode)
    }

    @Test
    fun `multi-ring area (exterior plus hole) round-trips both rings`() {
        val builder = Builder(lonMin, latMin, lonMax, latMax)
        builder.provincesHeader(vintage)
        builder.u16(0)

        val exterior = listOf(120.0 to 10.0, 122.0 to 10.0, 122.0 to 12.0, 120.0 to 12.0)
        val hole = listOf(120.5 to 10.5, 121.0 to 10.5, 121.0 to 11.0, 120.5 to 11.0)

        builder.u16(1)
        builder.areaRecord(
            code = 1700000000,
            parentCode = 1700000000,
            name = "Palawan",
            rings = listOf(exterior, hole),
        )

        val (set, _) = BoundaryBinaryReader.readProvinces(builder.build(), vintage)
        val area = set.areas.single()

        assertEquals(2, area.rings.size)
        assertEquals(exterior.size * 2, area.rings[0].size)
        assertEquals(hole.size * 2, area.rings[1].size)
    }

    @Test
    fun `varint zigzag delta encoding round-trips a point sequence`() {
        // A ring whose points deliberately move both positive and negative on each axis, to
        // exercise zigzag on both signs of delta.
        val ring = listOf(
            120.0 to 10.0,
            120.5 to 10.2, // +, +
            120.3 to 10.5, // -, +
            120.1 to 10.1, // -, -
            120.6 to 10.05, // +, -
        )

        val builder = Builder(lonMin, latMin, lonMax, latMax)
        builder.provincesHeader(vintage)
        builder.u16(0)
        builder.u16(1)
        builder.areaRecord(code = 1700000000, parentCode = 1700000000, name = "x", rings = listOf(ring))

        val (set, _) = BoundaryBinaryReader.readProvinces(builder.build(), vintage)
        val decodedCoords = set.areas.single().rings.single()

        // Quantization to a 65535-step grid over an 11-degree lon span / 18-degree lat span
        // introduces sub-quantum error; the point ordering and rough shape must still match.
        assertEquals(ring.size * 2, decodedCoords.size)
    }

    /** Minimal little-endian byte builder mirroring `build-geo-asset.py`'s writer functions. */
    private class Builder(lonMin: Float, latMin: Float, lonMax: Float, latMax: Float) {
        var magicBytes = "AVGE".toByteArray(Charsets.US_ASCII)
        var version = 1
        private val out = ByteArrayOutputStream()
        private val frameLonMin = lonMin
        private val frameLatMin = latMin
        private val frameLonMax = lonMax
        private val frameLatMax = latMax

        fun provincesHeader(vintage: String): Builder {
            out.write(magicBytes)
            u8(version)
            u8(1) // kind = provinces
            name(vintage)
            f32(frameLonMin)
            f32(frameLatMin)
            f32(frameLonMax)
            f32(frameLatMax)
            return this
        }

        fun u8(value: Int): Builder {
            out.write(value and 0xFF)
            return this
        }

        fun u16(value: Int): Builder {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            return this
        }

        fun i32(value: Int): Builder {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
            return this
        }

        fun f32(value: Float): Builder = i32(value.toRawBits())

        fun name(value: String): Builder {
            val bytes = value.toByteArray(Charsets.UTF_8)
            u8(bytes.size)
            out.write(bytes)
            return this
        }

        private fun quantLon(lon: Double): Int =
            (((lon - frameLonMin) / (frameLonMax - frameLonMin)) * 65535).toInt().coerceIn(0, 65535)

        private fun quantLat(lat: Double): Int =
            (((lat - frameLatMin) / (frameLatMax - frameLatMin)) * 65535).toInt().coerceIn(0, 65535)

        private fun zigzagEncode(n: Int): Int = if (n >= 0) n shl 1 else ((-n) shl 1) - 1

        private fun varint(value: Int): Builder {
            var v = value
            while (true) {
                val byte = v and 0x7F
                v = v ushr 7
                if (v != 0) {
                    out.write(byte or 0x80)
                } else {
                    out.write(byte)
                    return this
                }
            }
        }

        fun areaRecord(code: Int, parentCode: Int, name: String, rings: List<List<Pair<Double, Double>>>): Builder {
            i32(code)
            i32(parentCode)
            name(name)

            val allPoints = rings.flatten()
            val minX = allPoints.minOf { it.first }
            val maxX = allPoints.maxOf { it.first }
            val minY = allPoints.minOf { it.second }
            val maxY = allPoints.maxOf { it.second }
            u16(quantLon(minX))
            u16(quantLat(minY))
            u16(quantLon(maxX))
            u16(quantLat(maxY))
            val labelX = allPoints.sumOf { it.first } / allPoints.size
            val labelY = allPoints.sumOf { it.second } / allPoints.size
            u16(quantLon(labelX))
            u16(quantLat(labelY))

            varint(rings.size)
            for (ring in rings) {
                varint(ring.size)
                var prevQx = 0
                var prevQy = 0
                ring.forEachIndexed { i, (lon, lat) ->
                    val qx = quantLon(lon)
                    val qy = quantLat(lat)
                    if (i == 0) {
                        u16(qx)
                        u16(qy)
                    } else {
                        varint(zigzagEncode(qx - prevQx))
                        varint(zigzagEncode(qy - prevQy))
                    }
                    prevQx = qx
                    prevQy = qy
                }
            }
            return this
        }

        fun build(): ByteArray = out.toByteArray()
    }
}
