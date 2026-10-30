package com.raju.aiassistant.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TextExtractorTest {
    @Test
    fun formatsAreRecognised() {
        assertEquals(TextExtractor.Kind.PDF, TextExtractor.kindOf("bill.PDF", null))
        assertEquals(TextExtractor.Kind.DOCX, TextExtractor.kindOf("letter.docx", "application/octet-stream"))
        assertEquals(TextExtractor.Kind.TEXT, TextExtractor.kindOf("notes.md", null))
        assertEquals(TextExtractor.Kind.TEXT, TextExtractor.kindOf("x", "text/plain"))
        assertNull(TextExtractor.kindOf("photo.jpg", "image/jpeg"))
    }

    @Test
    fun docxParagraphsBecomeText() {
        val xml = """<w:document><w:body><w:p><w:r><w:t>नमस्ते &amp; Hello</w:t></w:r></w:p><w:p><w:r><w:t>আমার নাম</w:t><w:tab/><w:t>Seema</w:t></w:r></w:p></w:body></w:document>"""
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("[Content_Types].xml")); z.write("<Types/>".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("word/document.xml")); z.write(xml.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertEquals("नमस्ते & Hello\n\nআমার নাম\tSeema", TextExtractor.docxText(ByteArrayInputStream(zip)))
    }

    @Test
    fun plainTextDropsTheByteOrderMark() {
        assertEquals("হ্যালো", TextExtractor.plainText("\uFEFFহ্যালো".toByteArray()))
    }
}
