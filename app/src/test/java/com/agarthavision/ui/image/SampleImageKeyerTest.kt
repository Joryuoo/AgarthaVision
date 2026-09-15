package com.agarthavision.ui.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.File

class SampleImageKeyerTest {

    private val keyer = SampleImageKeyer()
    private val options = mock<coil.request.Options>()

    @Test
    fun `local file present returns local path as key`() {
        val file = File.createTempFile("sample-keyer", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val ref = SampleImageRef(localPath = file.absolutePath, storagePath = "user/sample.jpg")

        val key = keyer.key(ref, options)

        assertEquals(file.absolutePath, key)
    }

    @Test
    fun `storage path only returns storage path as key`() {
        val ref = SampleImageRef(localPath = null, storagePath = "user/sample.jpg")

        val key = keyer.key(ref, options)

        assertEquals("user/sample.jpg", key)
    }

    @Test
    fun `local file missing falls back to storage path`() {
        val ref = SampleImageRef(localPath = "/missing/sample.jpg", storagePath = "user/sample.jpg")

        val key = keyer.key(ref, options)

        assertEquals("user/sample.jpg", key)
    }

    @Test
    fun `both blank returns null`() {
        val ref = SampleImageRef(localPath = null, storagePath = null)

        val key = keyer.key(ref, options)

        assertNull(key)
    }

    @Test
    fun `both empty strings returns null`() {
        val ref = SampleImageRef(localPath = "", storagePath = "")

        val key = keyer.key(ref, options)

        assertNull(key)
    }
}
