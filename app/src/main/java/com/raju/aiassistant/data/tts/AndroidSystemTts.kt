package com.raju.aiassistant.data.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.raju.aiassistant.core.audio.PcmAudio
import com.raju.aiassistant.core.audio.parseWav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Functional interface for local system TTS synthesis fallback.
 */
fun interface SystemTtsSynthesizer {
    suspend fun synthesize(text: String, languageTag: String, rate: Float): PcmAudio?
}

/**
 * Built-in Android TextToSpeech synthesizer. Used as the automatic local fallback
 * for free speech generation (hi-IN / bn-IN) whenever Edge Neural returns 403,
 * times out, or when offline. Synthesizes to a temporary WAV file and decodes
 * to 16-bit PcmAudio so Seema never goes silent or logs unhandled failures.
 */
class AndroidSystemTts(private val context: Context) : SystemTtsSynthesizer {
    private val appContext = context.applicationContext
    private val lock = Mutex()
    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false

    private suspend fun ensureInit(): Boolean = lock.withLock {
        if (isInitialized && tts != null) return true
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Boolean> { cont ->
                try {
                    tts = TextToSpeech(appContext) { status ->
                        isInitialized = (status == TextToSpeech.SUCCESS)
                        if (cont.isActive) cont.resume(isInitialized)
                    }
                } catch (e: Throwable) {
                    isInitialized = false
                    if (cont.isActive) cont.resume(false)
                }
            }
        }
    }

    override suspend fun synthesize(text: String, languageTag: String, rate: Float): PcmAudio? {
        val ready = runCatching { ensureInit() }.getOrDefault(false)
        if (!ready) return null
        val engine = tts ?: return null

        val targetLocale = if (languageTag.startsWith("bn", ignoreCase = true)) {
            Locale("bn", "IN")
        } else {
            Locale("hi", "IN")
        }

        try {
            val res = engine.setLanguage(targetLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                val broad = if (languageTag.startsWith("bn", ignoreCase = true)) Locale("bn") else Locale("hi")
                val res2 = engine.setLanguage(broad)
                if (res2 == TextToSpeech.LANG_MISSING_DATA || res2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.language = Locale.getDefault()
                }
            }
        } catch (_: Throwable) {}

        engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))

        val utteranceId = "sys_tts_${UUID.randomUUID()}"
        val tempFile = File(appContext.cacheDir, "$utteranceId.wav")

        return withContext(Dispatchers.IO) {
            val synthesized = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val listener = object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            if (id == utteranceId && cont.isActive) cont.resume(true)
                        }
                        override fun onError(id: String?) {
                            if (id == utteranceId && cont.isActive) cont.resume(false)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId && cont.isActive) cont.resume(false)
                        }
                    }
                    engine.setOnUtteranceProgressListener(listener)
                    val params = Bundle()
                    val status = engine.synthesizeToFile(text, params, tempFile, utteranceId)
                    if (status != TextToSpeech.SUCCESS && cont.isActive) {
                        cont.resume(false)
                    }
                }
            } ?: false

            if (synthesized && tempFile.exists() && tempFile.length() > 44) {
                val bytes = runCatching { tempFile.readBytes() }.getOrNull()
                runCatching { tempFile.delete() }
                if (bytes != null) {
                    parseWav(bytes)
                } else null
            } else {
                runCatching { tempFile.delete() }
                null
            }
        }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }
}
