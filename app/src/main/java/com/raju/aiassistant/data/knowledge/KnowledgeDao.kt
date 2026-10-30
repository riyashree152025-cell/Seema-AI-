package com.raju.aiassistant.data.knowledge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.raju.aiassistant.data.db.KbChunkEntity
import com.raju.aiassistant.data.db.KbDocumentEntity
import kotlinx.coroutines.flow.Flow

/** A document as listed in Settings (without its full text). */
data class KbDocumentInfo(val id: Long, val name: String, val mimeType: String, val chunkCount: Int, val addedAt: Long)

/** A passage with its document's name, for search. */
data class KbChunkRow(val documentId: Long, val name: String, val chunkIndex: Int, val text: String)

@Dao
abstract class KnowledgeDao {
    @Query("SELECT id, name, mimeType, chunkCount, addedAt FROM kb_documents ORDER BY addedAt DESC")
    abstract fun observeDocuments(): Flow<List<KbDocumentInfo>>

    @Query("SELECT * FROM kb_documents")
    abstract suspend fun allDocuments(): List<KbDocumentEntity>

    @Query("SELECT c.documentId AS documentId, d.name AS name, c.chunkIndex AS chunkIndex, c.text AS text FROM kb_chunks c JOIN kb_documents d ON d.id = c.documentId")
    abstract suspend fun allChunks(): List<KbChunkRow>

    @Insert
    abstract suspend fun insertDocument(doc: KbDocumentEntity): Long

    @Insert
    abstract suspend fun insertChunks(chunks: List<KbChunkEntity>)

    @Query("UPDATE kb_documents SET chunkCount = :count WHERE id = :id")
    abstract suspend fun setChunkCount(id: Long, count: Int)

    @Query("DELETE FROM kb_chunks WHERE documentId = :id")
    abstract suspend fun deleteChunks(id: Long)

    @Query("DELETE FROM kb_documents WHERE id = :id")
    abstract suspend fun deleteDocumentRow(id: Long): Int

    @Query("DELETE FROM kb_chunks")
    abstract suspend fun deleteAllChunks()

    @Transaction
    open suspend fun addDocument(doc: KbDocumentEntity, chunks: List<String>): Long {
        val id = insertDocument(doc.copy(chunkCount = chunks.size))
        insertChunks(chunks.mapIndexed { i, t -> KbChunkEntity(documentId = id, chunkIndex = i, text = t) })
        return id
    }

    @Transaction
    open suspend fun deleteDocument(id: Long): Boolean {
        deleteChunks(id)
        return deleteDocumentRow(id) > 0
    }

    /** Re-chunks every document from its stored text (after a chunking change, or to repair the index). */
    @Transaction
    open suspend fun rebuild(chunker: (String) -> List<String>) {
        deleteAllChunks()
        for (d in allDocuments()) {
            val chunks = chunker(d.text)
            insertChunks(chunks.mapIndexed { i, t -> KbChunkEntity(documentId = d.id, chunkIndex = i, text = t) })
            setChunkCount(d.id, chunks.size)
        }
    }
}
