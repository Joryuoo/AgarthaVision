package com.agarthavision.data.supabase

import java.nio.ByteBuffer
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaboratoryBrandingRemoteDataSourceTest {
    @Test
    fun `a supported PNG is decoded for the offline cache`() {
        val expected = Base64.getDecoder().decode(PNG)

        assertArrayEquals(expected, decodeLaboratoryLogo(PNG))
    }

    @Test
    fun `missing or malformed logos are dropped`() {
        val pngBytes = Base64.getDecoder().decode(PNG)
        val truncated = pngBytes.copyOf(pngBytes.size - 1)

        assertNull(decodeLaboratoryLogo(null))
        assertNull(decodeLaboratoryLogo("not base64"))
        assertNull(decodeLaboratoryLogo("YWJj"))
        assertNull(decodeLaboratoryLogo(Base64.getEncoder().encodeToString(truncated)))
    }

    @Test
    fun `logos exceeding the dimension or byte limit are dropped`() {
        val oversized = Base64.getDecoder().decode(PNG)
        ByteBuffer.wrap(oversized).putInt(PNG_WIDTH_OFFSET, 513)

        assertNull(decodeLaboratoryLogo(Base64.getEncoder().encodeToString(oversized)))
        assertNull(decodeLaboratoryLogo(Base64.getEncoder().encodeToString(ByteArray(131_073))))
    }

    private companion object {
        const val PNG_WIDTH_OFFSET = 16
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j9iQAAAAASUVORK5CYII="
    }
}
