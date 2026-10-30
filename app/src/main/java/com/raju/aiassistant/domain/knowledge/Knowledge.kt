package com.raju.aiassistant.domain.knowledge

import kotlin.math.ln

/** A passage of an imported document that matches the user's message. */
data class KnowledgeHit(val documentId: Long, val documentName: String, val chunkIndex: Int, val text: String, val score: Double)

/** One stored passage of a document (input for [rankChunks]). */
data class KnowledgeChunk(val documentId: Long, val documentName: String, val chunkIndex: Int, val text: String)

/** Retrieval over the user's imported documents (RAG). This is search, not model training. */
interface KnowledgeSearch {
    suspend fun search(query: String, limit: Int = 3): List<KnowledgeHit>
}

object NoKnowledge : KnowledgeSearch {
    override suspend fun search(query: String, limit: Int) = emptyList<KnowledgeHit>()
}

/**
 * Splits text into passages of at most [maxChars], on paragraph and sentence boundaries where
 * possible, with [overlap] characters repeated from the previous passage so facts on a boundary
 * are not cut in half.
 */
fun chunkText(text: String, maxChars: Int = 900, overlap: Int = 150): List<String> {
    val clean = text.replace("\r\n", "\n").replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
    if (clean.isEmpty()) return emptyList()
    // Pieces no longer than maxChars: paragraphs, then sentences (also Hindi/Bengali danda), then hard cuts.
    val pieces = clean.split(Regex("\n\\s*\n")).flatMap { para ->
        if (para.length <= maxChars) listOf(para)
        else para.split(Regex("(?<=[.!?।])\\s+")).flatMap { s -> if (s.length <= maxChars) listOf(s) else s.chunked(maxChars) }
    }.map { it.trim() }.filter { it.isNotEmpty() }
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    for (p in pieces) {
        if (current.isNotEmpty() && current.length + 1 + p.length > maxChars) {
            val done = current.toString()
            chunks += done
            current.clear()
            if (overlap > 0) current.append(done.takeLast(overlap).substringAfter(' ', done.takeLast(overlap))).append(' ')
        }
        if (current.isNotEmpty() && !current.endsWith(" ")) current.append('\n')
        current.append(p)
    }
    if (current.isNotBlank()) chunks += current.toString().trim()
    return chunks
}

private val TOKEN = Regex("[\\p{L}\\p{M}\\p{N}]+")

/** Words that say nothing about the topic (English, Hinglish, Hindi, Bengali). */
private val STOP = setOf(
    "the", "a", "an", "is", "are", "was", "of", "and", "or", "to", "in", "on", "for", "what", "how", "me", "my", "it", "this", "that", "with",
    "ka", "ki", "ke", "ko", "hai", "hain", "kya", "kaise", "aur", "se", "mein", "me", "batao", "bataiye",
    "का", "की", "के", "को", "है", "हैं", "क्या", "कैसे", "और", "से", "में", "बताओ", "बताइए", "यह", "वह",
    "এর", "কি", "কী", "এবং", "ও", "এই", "সেই", "আছে", "করে", "থেকে", "জন্য", "বলো", "বলুন",
)

fun tokenize(text: String): List<String> = TOKEN.findAll(text.lowercase()).map { it.value }.filter { it.length >= 2 && it !in STOP }.toList()

/**
 * BM25 ranking of [chunks] for [query], on the phone (nothing is sent anywhere for this).
 * Returns only passages that share at least one meaningful word with the query.
 */
fun rankChunks(query: String, chunks: List<KnowledgeChunk>, limit: Int = 3): List<KnowledgeHit> {
    val q = tokenize(query).toSet()
    if (q.isEmpty() || chunks.isEmpty()) return emptyList()
    val docs = chunks.map { tokenize(it.text) }
    val avg = docs.sumOf { it.size }.toDouble() / docs.size
    val df = q.associateWith { term -> docs.count { term in it } }
    val n = docs.size.toDouble()
    val k1 = 1.2; val b = 0.75
    return chunks.indices.mapNotNull { i ->
        val words = docs[i]
        val tf = words.groupingBy { it }.eachCount()
        val score = q.sumOf { term ->
            val f = tf[term] ?: return@sumOf 0.0
            val idf = ln(1 + (n - df.getValue(term) + 0.5) / (df.getValue(term) + 0.5))
            idf * f * (k1 + 1) / (f + k1 * (1 - b + b * words.size / avg.coerceAtLeast(1.0)))
        }
        if (score <= 0.0) null else chunks[i].let { KnowledgeHit(it.documentId, it.documentName, it.chunkIndex, it.text, score) }
    }.sortedByDescending { it.score }.take(limit)
}
