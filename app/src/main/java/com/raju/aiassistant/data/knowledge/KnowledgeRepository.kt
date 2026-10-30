package com.raju.aiassistant.data.knowledge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.raju.aiassistant.data.db.KbDocumentEntity
import com.raju.aiassistant.domain.knowledge.KnowledgeChunk
import com.raju.aiassistant.domain.knowledge.KnowledgeHit
import com.raju.aiassistant.domain.knowledge.KnowledgeSearch
import com.raju.aiassistant.domain.knowledge.chunkText
import com.raju.aiassistant.domain.knowledge.rankChunks
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The Knowledge Base: documents the user imports are read on the phone, split into passages and
 * stored in the local database. When the user asks something, the matching passages are added to
 * the request (RAG). Nothing is trained; documents never leave the phone except those passages.
 */
class KnowledgeRepository(
    private val context: Context,
    private val dao: KnowledgeDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : KnowledgeSearch {
    val documents: Flow<List<KbDocumentInfo>> = dao.observeDocuments()

    /** Passages kept in memory for fast search; reloaded after every change. */
    @Volatile private var cache: List<KnowledgeChunk>? = null

    sealed interface ImportResult {
        data class Imported(val name: String, val chunks: Int) : ImportResult
        data class Failed(val reason: String) : ImportResult
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "document"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val mime = resolver.getType(uri)
        val kind = TextExtractor.kindOf(name, mime)
            ?: return@withContext ImportResult.Failed("“$name”: only PDF, DOCX, TXT and Markdown files can be read.")
        if (size > TextExtractor.MAX_BYTES) return@withContext ImportResult.Failed("“$name” is too large (max 15 MB).")
        val text = try {
            resolver.openInputStream(uri)?.use { input ->
                when (kind) {
                    TextExtractor.Kind.TEXT -> TextExtractor.plainText(input.readBytes())
                    TextExtractor.Kind.DOCX -> TextExtractor.docxText(input)
                    TextExtractor.Kind.PDF -> {
                        PDFBoxResourceLoader.init(context.applicationContext)
                        PDDocument.load(input).use { doc ->
                            if (doc.isEncrypted) null else PDFTextStripper().getText(doc)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext ImportResult.Failed("“$name” could not be read (${e.javaClass.simpleName}).")
        } catch (e: OutOfMemoryError) {
            return@withContext ImportResult.Failed("“$name” is too large for this phone's memory.")
        }
        val clean = text?.trim()?.take(TextExtractor.MAX_CHARS)
        if (clean.isNullOrBlank()) {
            return@withContext ImportResult.Failed("“$name” has no readable text (a scanned PDF or a password-protected file cannot be read).")
        }
        val chunks = chunkText(clean)
        dao.addDocument(KbDocumentEntity(name = name, mimeType = mime ?: kind.name, text = clean, chunkCount = chunks.size, addedAt = clock()), chunks)
        cache = null
        ImportResult.Imported(name, chunks.size)
    }

    suspend fun remove(id: Long): Boolean = dao.deleteDocument(id).also { cache = null }

    suspend fun rebuildIndex() {
        dao.rebuild { chunkText(it) }
        cache = null
    }

    override suspend fun search(query: String, limit: Int): List<KnowledgeHit> = withContext(Dispatchers.Default) {
        val chunks = cache ?: dao.allChunks().map { KnowledgeChunk(it.documentId, it.name, it.chunkIndex, it.text) }.also { cache = it }
        rankChunks(query, chunks, limit)
    }
}
