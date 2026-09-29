package com.agarthavision.data.geo

import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.geo.GeoProjection
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.geo.ProvinceRef
import com.agarthavision.domain.geo.TownRef

/**
 * Decodes the "AVGE" v1 binary boundary format `tools/geo/build-geo-asset.py` writes.
 *
 * Pure JVM — no Android import — so it is testable with hand-built byte arrays and no
 * Robolectric. See that script's module docstring for the exact byte layout this mirrors.
 * Points are dequantized back to lon/lat and immediately projected with [GeoProjection], so
 * every [AreaShape.rings] value this returns is already in projected map units.
 */
object BoundaryBinaryReader {

    fun readProvinces(bytes: ByteArray, expectedVintage: String): Pair<BoundarySet, AreaDirectory> {
        val reader = Cursor(bytes)
        val frame = reader.readHeader(KIND_PROVINCES, expectedVintage)

        val townCount = reader.readU16()
        val towns = LinkedHashMap<String, TownRef>(townCount)
        repeat(townCount) {
            val code = reader.readI32()
            val provinceKey = reader.readI32()
            val hasGeometry = reader.readU8() != 0
            val name = reader.readName()
            val codeStr = formatCode(code)
            towns[codeStr] = TownRef(
                code = codeStr,
                name = name,
                provinceKey = formatCode(provinceKey),
                hasGeometry = hasGeometry,
            )
        }

        val areaCount = reader.readU16()
        val areas = ArrayList<AreaShape>(areaCount)
        val provinces = LinkedHashMap<String, ProvinceRef>(areaCount)
        repeat(areaCount) {
            val record = reader.readAreaRecord(frame)
            areas.add(record.shape)
            val regionCode = formatCode(record.parentCode)
            provinces[record.shape.code] = ProvinceRef(
                code = record.shape.code,
                name = record.shape.name,
                regionCode = regionCode,
                islandGroup = IslandGroup.fromRegionCode(regionCode),
            )
        }

        val bounds = areas.map { it.bounds }.unionAll()
        return BoundarySet(areas, bounds) to AreaDirectory(towns = towns, provinces = provinces)
    }

    /** Maps province key -> absolute byte range within [bytes], usable directly by [readTowns]. */
    fun readTownsIndex(bytes: ByteArray, expectedVintage: String): Map<String, IntRange> {
        val reader = Cursor(bytes)
        reader.readHeader(KIND_TOWNS, expectedVintage)

        val provinceCount = reader.readU16()
        data class Entry(val provinceKey: Int, val relativeOffset: Int, val areaCount: Int)
        val entries = ArrayList<Entry>(provinceCount)
        repeat(provinceCount) {
            val provinceKey = reader.readI32()
            val offset = reader.readU32()
            val areaCount = reader.readU16()
            entries.add(Entry(provinceKey, offset, areaCount))
        }

        val recordsSectionStart = reader.position
        val recordsSectionLength = bytes.size - recordsSectionStart

        return entries.mapIndexed { index, entry ->
            val relativeEnd = if (index < entries.lastIndex) entries[index + 1].relativeOffset else recordsSectionLength
            val start = recordsSectionStart + entry.relativeOffset
            val end = recordsSectionStart + relativeEnd
            formatCode(entry.provinceKey) to (start until end)
        }.toMap()
    }

    fun readTowns(bytes: ByteArray, byteRange: IntRange): BoundarySet {
        if (byteRange.isEmpty()) return BoundarySet(emptyList(), GeoBounds(0f, 0f, 0f, 0f))

        // The shared frame lives in the file header, not in this slice, so re-read it once
        // from the start of the same byte array the range was carved from.
        val headerReader = Cursor(bytes)
        val frame = headerReader.readHeader(KIND_TOWNS, expectedVintage = null)

        val reader = Cursor(bytes, start = byteRange.first)
        val areas = ArrayList<AreaShape>()
        while (reader.position <= byteRange.last) {
            areas.add(reader.readAreaRecord(frame).shape)
        }
        val bounds = areas.map { it.bounds }.unionAll()
        return BoundarySet(areas, bounds)
    }

    private fun List<GeoBounds>.unionAll(): GeoBounds =
        if (isEmpty()) {
            GeoBounds(0f, 0f, 0f, 0f)
        } else {
            reduce { acc, bounds -> acc.union(bounds) }
        }

    private fun formatCode(code: Int): String = "%010d".format(code)

    private const val MAGIC = "AVGE"
    private const val SUPPORTED_VERSION = 1
    private const val KIND_PROVINCES = 1
    private const val KIND_TOWNS = 2
    private const val QUANT_MAX = 65535

    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val MAGIC_LENGTH = 4
    private const val VARINT_PAYLOAD_MASK = 0x7F
    private const val VARINT_CONTINUATION_BIT = 0x80
    private const val VARINT_SHIFT_STEP = 7
    private const val SHIFT_2_BYTES = 16
    private const val SHIFT_3_BYTES = 24

    private class Frame(val lonMin: Float, val latMin: Float, val lonMax: Float, val latMax: Float) {
        fun dequantLon(q: Int): Double = lonMin + (q.toDouble() / QUANT_MAX) * (lonMax - lonMin)
        fun dequantLat(q: Int): Double = latMin + (q.toDouble() / QUANT_MAX) * (latMax - latMin)
    }

    private class AreaRecord(val shape: AreaShape, val parentCode: Int)

    /** A little-endian cursor over a shared [ByteArray], mutable position for sequential reads. */
    private class Cursor(private val bytes: ByteArray, start: Int = 0) {
        var position: Int = start
            private set

        fun readU8(): Int = bytes[position++].toInt() and BYTE_MASK

        fun readU16(): Int {
            val lo = readU8()
            val hi = readU8()
            return lo or (hi shl BITS_PER_BYTE)
        }

        fun readI32(): Int {
            val b0 = readU8()
            val b1 = readU8()
            val b2 = readU8()
            val b3 = readU8()
            return b0 or (b1 shl BITS_PER_BYTE) or (b2 shl SHIFT_2_BYTES) or (b3 shl SHIFT_3_BYTES)
        }

        fun readU32(): Int = readI32() // callers only need values that fit in Int (byte offsets)

        fun readF32(): Float = Float.fromBits(readI32())

        fun readVarint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val byte = readU8()
                result = result or ((byte and VARINT_PAYLOAD_MASK) shl shift)
                if (byte and VARINT_CONTINUATION_BIT == 0) break
                shift += VARINT_SHIFT_STEP
            }
            return result
        }

        fun readZigzag(): Int {
            val n = readVarint()
            return (n ushr 1) xor -(n and 1)
        }

        fun readName(): String {
            val len = readU8()
            val nameBytes = bytes.copyOfRange(position, position + len)
            position += len
            return String(nameBytes, Charsets.UTF_8)
        }

        fun readHeader(expectedKind: Int, expectedVintage: String?): Frame {
            val magicBytes = bytes.copyOfRange(position, position + MAGIC_LENGTH)
            position += MAGIC_LENGTH
            val magic = String(magicBytes, Charsets.US_ASCII)
            check(magic == MAGIC) { "Bad magic '$magic', expected '$MAGIC'" }

            val version = readU8()
            check(version == SUPPORTED_VERSION) { "Unsupported AVGE version $version" }

            val kind = readU8()
            check(kind == expectedKind) { "Expected kind $expectedKind, got $kind" }

            val vintage = readName()
            if (expectedVintage != null) {
                check(vintage == expectedVintage) {
                    "Vintage mismatch: asset is '$vintage', expected '$expectedVintage'"
                }
            }

            val lonMin = readF32()
            val latMin = readF32()
            val lonMax = readF32()
            val latMax = readF32()
            return Frame(lonMin, latMin, lonMax, latMax)
        }

        fun readAreaRecord(frame: Frame): AreaRecord {
            val code = readI32()
            val parentCode = readI32()
            val name = readName()

            val bboxMinXq = readU16()
            val bboxMinYq = readU16()
            val bboxMaxXq = readU16()
            val bboxMaxYq = readU16()
            val labelXq = readU16()
            val labelYq = readU16()

            val bounds = GeoBounds(
                minX = GeoProjection.x(frame.dequantLon(bboxMinXq)),
                minY = GeoProjection.y(frame.dequantLat(bboxMaxYq)), // max lat -> min screen-y
                maxX = GeoProjection.x(frame.dequantLon(bboxMaxXq)),
                maxY = GeoProjection.y(frame.dequantLat(bboxMinYq)), // min lat -> max screen-y
            )
            val labelX = GeoProjection.x(frame.dequantLon(labelXq))
            val labelY = GeoProjection.y(frame.dequantLat(labelYq))

            val ringCount = readVarint()
            val rings = ArrayList<FloatArray>(ringCount)
            repeat(ringCount) {
                val pointCount = readVarint()
                val coords = FloatArray(pointCount * 2)
                var qx = 0
                var qy = 0
                for (i in 0 until pointCount) {
                    if (i == 0) {
                        qx = readU16()
                        qy = readU16()
                    } else {
                        qx += readZigzag()
                        qy += readZigzag()
                    }
                    coords[i * 2] = GeoProjection.x(frame.dequantLon(qx))
                    coords[i * 2 + 1] = GeoProjection.y(frame.dequantLat(qy))
                }
                rings.add(coords)
            }

            val shape = AreaShape(
                code = formatCode(code),
                name = name,
                parentCode = formatCode(parentCode),
                bounds = bounds,
                labelX = labelX,
                labelY = labelY,
                rings = rings,
            )
            return AreaRecord(shape, parentCode)
        }
    }
}
