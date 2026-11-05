package com.agarthavision.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric shadow for [PdfDocument] that allocates an in-memory [Canvas] per page
 * and writes valid minimal PDF bytes to the provided [OutputStream].
 */
@Implements(PdfDocument::class)
class ShadowPdfDocument {
    private val pages = mutableListOf<PdfDocument.PageInfo>()
    private var isClosed = false
    private var currentPage: PdfDocument.Page? = null

    @Implementation
    fun __constructor__() {
        // Avoid nativeCreateDocument() returning 0 in Robolectric
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        check(!isClosed) { "document is closed!" }
        val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val twoArgConstructor = PdfDocument.Page::class.java.declaredConstructors
            .firstOrNull { it.parameterTypes.size == 2 }
            ?.apply { isAccessible = true }
        val page = if (twoArgConstructor != null) {
            twoArgConstructor.newInstance(canvas, pageInfo) as PdfDocument.Page
        } else {
            val defaultConstructor = PdfDocument.Page::class.java.declaredConstructors.first().apply {
                isAccessible = true
            }
            (defaultConstructor.newInstance() as PdfDocument.Page).also { p ->
                runCatching {
                    val field = PdfDocument.Page::class.java.getDeclaredField("mCanvas").apply { isAccessible = true }
                    field.set(p, canvas)
                }
            }
        }
        currentPage = page
        return page
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        pages.add(page.info)
        currentPage = null
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        check(!isClosed) { "document is closed!" }
        val pageCount = maxOf(1, pages.size)
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        val kids = (1..pageCount).joinToString(" ") { "${it + 2} 0 R" }
        sb.append("2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $pageCount >>\nendobj\n")
        for (i in 1..pageCount) {
            val objNum = i + 2
            sb.append("$objNum 0 obj\n<< /Type /Page /Parent 2 0 R >>\nendobj\n")
        }
        sb.append("xref\n0 ${pageCount + 3}\n")
        sb.append("trailer\n<< /Root 1 0 R /Size ${pageCount + 3} >>\n")
        sb.append("startxref\n9999\n%%EOF\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
    }

    @Implementation
    fun close() {
        isClosed = true
    }

    @Implementation
    fun getPages(): List<PdfDocument.PageInfo> = pages.toList()
}

/**
 * Extracts page count from minimal PDF bytes written by [ShadowPdfDocument].
 */
fun extractPdfPageCount(bytes: ByteArray): Int {
    val text = String(bytes, Charsets.US_ASCII)
    val match = Regex("""/Count\s+(\d+)""").find(text)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
