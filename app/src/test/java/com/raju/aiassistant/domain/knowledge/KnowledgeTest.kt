package com.raju.aiassistant.domain.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeTest {
    @Test
    fun shortTextIsOneChunkAndLongTextIsSplitOnSentences() {
        assertEquals(listOf("Hello world."), chunkText("Hello world."))
        assertTrue(chunkText("   \n ").isEmpty())
        val long = (1..60).joinToString(" ") { "Sentence number $it is here." }
        val chunks = chunkText(long, maxChars = 200, overlap = 40)
        assertTrue(chunks.size > 5)
        assertTrue(chunks.all { it.length <= 200 + 40 + 1 })
        // Every sentence survives chunking.
        (1..60).forEach { n -> assertTrue("sentence $n lost", chunks.any { "number $n is" in it }) }
    }

    @Test
    fun hindiAndBengaliTextIsSplitOnTheDanda() {
        val text = (1..30).joinToString(" ") { "यह वाक्य संख्या $it है।" }
        val chunks = chunkText(text, maxChars = 120, overlap = 0)
        assertTrue(chunks.size > 3)
        assertTrue(chunks.all { it.length <= 120 })
    }

    @Test
    fun theMatchingPassageRanksFirst() {
        val chunks = listOf(
            KnowledgeChunk(1, "shop.pdf", 0, "Opening hours: 9 AM to 8 PM, closed on Sunday."),
            KnowledgeChunk(1, "shop.pdf", 1, "Tomato price is 40 rupees per kg; onion 30 rupees per kg."),
            KnowledgeChunk(2, "notes.txt", 0, "আমার দোকানের ঠিকানা: কলকাতা, পার্ক স্ট্রিট।"),
        )
        val hits = rankChunks("tomato ka price kya hai", chunks)
        assertEquals("shop.pdf", hits.first().documentName)
        assertEquals(1, hits.first().chunkIndex)
        assertEquals("notes.txt", rankChunks("দোকানের ঠিকানা কোথায়", chunks).first().documentName)
        assertTrue(rankChunks("cricket score", chunks).isEmpty())
    }
}
