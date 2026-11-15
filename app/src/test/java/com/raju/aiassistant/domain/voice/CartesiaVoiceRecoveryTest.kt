package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartesiaVoiceRecoveryTest {
    private val account = listOf(
        CloudVoice("new-hindi", "Priya", "Hindi female", female = true, hindi = true),
        CloudVoice("old-gone", "Meera", "removed", female = true, hindi = true),
        CloudVoice("en-1", "Sarah", "English", female = true, hindi = false),
    )

    @Test
    fun aMissingVoiceErrorIsRecognised() {
        assertTrue(VoiceCatalog.isMissingVoice("Unexpected error (HTTP 404): The requested voice was not found."))
        assertFalse(VoiceCatalog.isMissingVoice("Unexpected error (HTTP 401): invalid key"))
    }

    @Test
    fun theReplacementComesFromTheAccountAndIsNeverTheMissingVoice() {
        val r = VoiceCatalog.replacementVoice(account, missing = "old-gone")!!
        assertTrue(r.id != "old-gone")
        assertEquals("new-hindi", r.id) // a Hindi female voice first
        assertNull(VoiceCatalog.replacementVoice(listOf(account[1]), missing = "old-gone"))
    }

    @Test
    fun builtInVoicesAreOfferedOnlyWhenTheAccountListIsMissing() {
        val merged = VoiceCatalog.mergeCartesiaVoices(account)
        assertEquals(account.map { it.id }.toSet(), merged.map { it.id }.toSet()) // no built-in ids the account lacks
        assertEquals(VoiceCatalog.defaultCartesiaVoices, VoiceCatalog.mergeCartesiaVoices(emptyList()))
    }
}
