package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.model.TtsEngine.ANDROID
import com.raju.aiassistant.domain.model.TtsEngine.CARTESIA
import com.raju.aiassistant.domain.model.TtsEngine.DEEPGRAM
import com.raju.aiassistant.domain.model.TtsEngine.ELEVENLABS
import com.raju.aiassistant.domain.model.TtsEngine.KOKORO
import com.raju.aiassistant.domain.model.TtsEngine.PIPER
import com.raju.aiassistant.domain.model.TtsEngine.SARVAM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVoiceTest {
    private val both = setOf(PIPER, KOKORO)
    private fun auto(installed: Set<TtsEngine>, stats: Map<TtsEngine, EngineStats> = emptyMap(), chosen: TtsEngine = SARVAM) =
        voiceEngineOrder(true, chosen, installed, stats)

    @Test fun automaticTriesKokoroFirstWhileItIsUnmeasured() {
        assertEquals(listOf(KOKORO, PIPER, ANDROID), auto(both))
    }

    @Test fun automaticKeepsKokoroFirstWhenItIsFastOnThisPhone() {
        assertEquals(listOf(KOKORO, PIPER, ANDROID), auto(both, mapOf(KOKORO to EngineStats(rtf = 0.5))))
    }

    @Test fun slowKokoroSwitchesToPiper() {
        assertEquals(listOf(PIPER, KOKORO, ANDROID), auto(both, mapOf(KOKORO to EngineStats(rtf = 1.4))))
    }

    @Test fun withoutPiperAndWithSlowKokoroAndroidIsStillTheBackup() {
        assertEquals(listOf(KOKORO, ANDROID), auto(setOf(KOKORO), mapOf(KOKORO to EngineStats(rtf = 1.4))))
        assertEquals(listOf(PIPER, ANDROID), auto(setOf(PIPER)))
    }

    @Test fun automaticUsesNoVoiceApiOnceAModelIsInstalled() {
        assertTrue(auto(setOf(PIPER)).all { it.isOffline })
    }

    @Test fun automaticKeepsTheChosenVoiceUntilAModelIsDownloaded() {
        assertEquals(listOf(SARVAM, ELEVENLABS, CARTESIA, DEEPGRAM, ANDROID), auto(emptySet()))
    }

    @Test fun failingEngineMovesBehindTheHealthyOne() {
        val stats = mapOf(KOKORO to EngineStats(rtf = 0.3, recentFailures = MAX_RECENT_FAILURES))
        assertEquals(listOf(PIPER, KOKORO, ANDROID), auto(both, stats))
    }

    @Test fun manualPutsTheChosenEngineFirstThenOfflineFallbacks() {
        assertEquals(listOf(SARVAM, KOKORO, PIPER, ELEVENLABS, CARTESIA, DEEPGRAM, ANDROID), voiceEngineOrder(false, SARVAM, both, emptyMap()))
        assertEquals(listOf(PIPER, KOKORO, SARVAM, ELEVENLABS, CARTESIA, DEEPGRAM, ANDROID), voiceEngineOrder(false, PIPER, both, emptyMap()))
        // An offline engine that is not downloaded is never tried.
        assertEquals(listOf(SARVAM, PIPER, ELEVENLABS, CARTESIA, DEEPGRAM, ANDROID), voiceEngineOrder(false, SARVAM, setOf(PIPER), emptyMap()))
        assertEquals(listOf(ANDROID, SARVAM, ELEVENLABS, CARTESIA, DEEPGRAM), voiceEngineOrder(false, ANDROID, emptySet(), emptyMap()))
    }

    @Test fun statsAreAMovingAverageAndASuccessClearsRecentFailures() {
        val s = EngineStats().withSuccess(1.0, 1000).withSuccess(0.0, 0)
        assertEquals(0.7, s.rtf!!, 1e-9)
        assertEquals(700L, s.firstAudioMs)
        val failed = s.withFailure("x").withFailure("y")
        assertEquals(2, failed.recentFailures)
        assertEquals("y", failed.lastError)
        assertEquals(0, failed.withSuccess(0.5, 100).recentFailures)
        assertEquals(2, failed.withSuccess(0.5, 100).failures)
    }

    @Test fun catalogEntriesAreComplete() {
        assertTrue(LOCAL_VOICE_CATALOG.map { it.engine }.containsAll(listOf(PIPER, KOKORO)))
        LOCAL_VOICE_CATALOG.forEach { m ->
            assertTrue(m.id, m.sizeBytes > 1_000_000)
            assertTrue(m.id, Regex("[0-9a-f]{64}").matches(m.sha256))
            assertTrue(m.id, m.url.startsWith("https://") && m.url.endsWith(".tar.bz2"))
            assertTrue(m.id, m.speakers.isNotEmpty() && m.modelFile.isNotBlank() && m.dirName.isNotBlank())
            assertEquals(m.id, 1, LOCAL_VOICE_CATALOG.count { it.id == m.id })
        }
        val kokoro = LOCAL_VOICE_CATALOG.first { it.engine == KOKORO }
        assertEquals("hi", kokoro.lang)
        assertTrue(kokoro.voicesFile.isNotBlank())
        // The default speaker (UserSettings.kokoroSpeaker = 31) is a Hindi female voice.
        assertEquals("hf_alpha", kokoro.speakers.first { it.id == com.raju.aiassistant.domain.model.UserSettings().kokoroSpeaker }.name)
    }
}
