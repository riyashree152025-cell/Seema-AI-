package com.raju.aiassistant.data.knowledge

import java.io.InputStream
import java.util.zip.ZipInputStream

/** Text extraction for the Knowledge Base. Only formats with reliable extraction are accepted. */
object TextExtractor {
    /** Documents larger than this are refused (memory on mid-range phones). */
    const val MAX_BYTES = 15L * 1024 * 1024
    /** Extracted text kept per document. */
    const val MAX_CHARS = 400_000

    enum class Kind { TEXT, DOCX, PDF }

    fun kindOf(name: String, mimeType: String?): Kind? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("txt", "md", "markdown", "csv", "log") || mimeType?.startsWith("text/") == true -> Kind.TEXT
            ext == "docx" || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Kind.DOCX
            ext == "pdf" || mimeType == "application/pdf" -> Kind.PDF
            else -> null
        }
    }

    /** UTF-8 text (BOM removed). */
    fun plainText(bytes: ByteArray): String = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")

    /** Text of a .docx: paragraphs from word/document.xml (a zip of XML; no library needed). */
    fun docxText(input: InputStream): String? {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == "word/document.xml") return wordXmlToText(zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    internal fun wordXmlToText(xml: String): String = xml
        .replace(Regex("</w:p>"), "\n\n")
        .replace(Regex("<w:tab/>"), "\t")
        .replace(Regex("<w:br/>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
