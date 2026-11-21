package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceIdTest {
    @Test
    fun sameVoiceAndAnotherVoice() {
        val owner = floatArrayOf(0.9f, 0.1f, 0.3f, -0.2f)
        assertEquals(1f, VoiceId.cosine(owner, owner), 1e-5f)
        assertEquals(true, VoiceId.isOwner(owner, floatArrayOf(0.85f, 0.15f, 0.25f, -0.25f)))
        assertEquals(false, VoiceId.isOwner(owner, floatArrayOf(-0.2f, 0.9f, -0.1f, 0.4f)))
        assertNull(VoiceId.isOwner(null, owner)) // not set up: never blocks
        assertNull(VoiceId.isOwner(owner, null)) // too short to judge: never blocks
        assertEquals(0f, VoiceId.cosine(owner, floatArrayOf(1f)))
    }

    @Test
    fun voicePrintRoundTrip() {
        val v = floatArrayOf(0.123456f, -1.5f, 0f)
        assertArrayEquals(v, VoiceId.decode(VoiceId.encode(v)), 1e-6f)
        assertNull(VoiceId.decode(null))
        assertNull(VoiceId.decode(""))
    }

    @Test
    fun commands() {
        assertEquals(true, VoiceId.command("meri awaaz pehchano"))
        assertEquals(true, VoiceId.command("मेरी आवाज़ याद रखो"))
        assertEquals(true, VoiceId.command("আমার গলা চিনে রাখো"))
        assertEquals(false, VoiceId.command("awaaz pehchanna band karo"))
        assertNull(VoiceId.command("meri awaaz kaisi hai"))
        assertEquals(AssistantAction.VoiceIdSetup(true), LocalCommands.parse("meri awaaz pehchano"))
        assertFalse(LocalCommands.parse("awaaz pehchanna band karo") == AssistantAction.VoiceIdSetup(true))
        assertTrue(LocalCommands.parse("awaaz pehchanna band karo") == AssistantAction.VoiceIdSetup(false))
    }
}
