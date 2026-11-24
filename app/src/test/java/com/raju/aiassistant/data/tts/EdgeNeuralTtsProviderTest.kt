package com.raju.aiassistant.data.tts

import com.raju.aiassistant.core.audio.PcmAudio
import com.raju.aiassistant.domain.ai.AiResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeNeuralTtsProviderTest {
    private val fakePcm = PcmAudio(24000, 1, ByteArray(48000))

    @Test
    fun isAvailableWhenOnlineOrFallbackConfigured() = runBlocking {
        val onlineWithoutFallback = EdgeNeuralTtsProvider(OkHttpClient(), isOnline = { true })
        assertTrue(onlineWithoutFallback.isAvailable())

        val offlineWithFallback = EdgeNeuralTtsProvider(
            http = OkHttpClient(),
            isOnline = { false },
            fallbackSynthesizer = { _, _, _ -> fakePcm }
        )
        assertTrue(offlineWithFallback.isAvailable())
    }

    @Test
    fun fallsBackToLocalSynthesizerWhenOffline() = runBlocking {
        var fallbackCalled = false
        val provider = EdgeNeuralTtsProvider(
            http = OkHttpClient(),
            isOnline = { false },
            fallbackSynthesizer = { text, lang, rate ->
                fallbackCalled = true
                assertTrue(text.startsWith("नमस्ते"))
                assertEquals("hi-IN", lang)
                fakePcm
            }
        )

        val res = provider.synthesize("नमस्ते", "hi-IN", 1.0f)
        assertTrue(fallbackCalled)
        assertTrue(res is AiResult.Success)
    }

    @Test
    fun fallsBackToLocalSynthesizerWhenEdgeConnectionFails() = runBlocking {
        var fallbackCalled = false
        // Using an OkHttpClient that fails connection
        val provider = EdgeNeuralTtsProvider(
            http = OkHttpClient(),
            isOnline = { true },
            fallbackSynthesizer = { text, lang, rate ->
                fallbackCalled = true
                fakePcm
            }
        )

        val res = provider.synthesize("हेलो सीमा", "hi-IN", 1.0f)
        assertTrue("Fallback synthesizer should be called when network call fails", fallbackCalled)
        assertTrue(res is AiResult.Success)
    }
}
