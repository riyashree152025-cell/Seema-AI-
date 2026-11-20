package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.core.audio.PcmAudio
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.assistant.SpeakResult
import com.raju.aiassistant.domain.assistant.chunkForSpeech
import com.raju.aiassistant.domain.model.TtsEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** One text-to-speech engine (cloud or on-device). New engines (e.g. an offline neural voice) implement this. */
interface TtsProvider {
    val engine: TtsEngine
    val label: String

    /** False when the provider can't be used right now (no key, offline, not installed). Skipped silently. */
    suspend fun isAvailable(): Boolean

    suspend fun synthesize(text: String, languageTag: String, rate: Float): AiResult<PcmAudio>

    /**
     * False when this voice cannot speak [languageTag] at all (an offline English/Hindi-only model asked for
     * Bengali): it is passed over for that sentence without a failed attempt, and stays in use for others.
     */
    fun supportsLanguage(languageTag: String): Boolean = true
}

/** Audio output used by the pipeline (AudioTrack on Android, a fake in tests). */
interface PcmSink {
    fun requestFocus()
    fun abandonFocus()
    suspend fun play(audio: PcmAudio, onLevel: (Float) -> Unit)
    /** Loudness for what follows (a whisper is quieter); the real speaker then applies its limiter. */
    fun setGain(gain: Float) {}
}

/**
 * True when synthesised audio is far too short or quiet for the words it should contain: about
 * 40 ms of sound per letter is expected; under a quarter of that (or pure silence) means the voice
 * could not read the text (e.g. Devanagari given to an English-only voice).
 */
fun isSilent(audio: PcmAudio, text: String): Boolean {
    val letters = text.count { it.isLetterOrDigit() }
    if (letters < 3 || audio.sampleRate <= 0) return false
    val seconds = audio.frameCount.toDouble() / audio.sampleRate
    if (seconds < letters * 0.010) return true
    return com.raju.aiassistant.core.audio.pcmLevel(audio.data, 0, audio.data.size) < 0.002f
}

/** The user's chosen engine first, then the others in their default order — so there is always a fallback. */
fun providerOrder(selected: TtsEngine): List<TtsEngine> = listOf(selected) + TtsEngine.entries.filter { it != selected }

private val DEVANAGARI = Regex("[\\u0900-\\u097F]")
private val BENGALI = Regex("[\\u0980-\\u09FF]")

/**
 * Language for one chunk: Bengali script is spoken as Bengali (bn-IN), Devanagari as Hindi (hi-IN);
 * Roman-script text (Hinglish) uses the conversation's language, so a Hindi voice reads Hinglish naturally.
 */
fun languageFor(chunk: String, preferredTag: String): String =
    when (com.raju.aiassistant.domain.language.detectScript(chunk)) {
        // The script most of the chunk is written in (one Bengali name in a Hindi sentence stays Hindi).
        com.raju.aiassistant.domain.language.Detection.BENGALI -> "bn-IN"
        com.raju.aiassistant.domain.language.Detection.HINDI -> "hi-IN"
        com.raju.aiassistant.domain.language.Detection.MIXED -> if (BENGALI.containsMatchIn(chunk)) "bn-IN" else "hi-IN"
        // Plain English gets an English voice; Roman Hindi / Roman Bengali words decide by themselves.
        com.raju.aiassistant.domain.language.Detection.LATIN -> if (com.raju.aiassistant.domain.language.isLikelyEnglish(chunk)) "en-IN" else when (com.raju.aiassistant.domain.language.detectLatinLanguage(chunk)) {
            com.raju.aiassistant.domain.language.SeemaLanguage.HINDI -> "hi-IN"
            com.raju.aiassistant.domain.language.SeemaLanguage.BENGALI -> "bn-IN"
            null -> preferredTag
        }
        else -> preferredTag
    }

/**
 * Speaks text chunk by chunk. While one chunk plays the next is already being synthesised
 * (one chunk prefetch), so there are no long pauses between sentences. If a provider fails it is
 * dropped for the rest of the utterance and the next provider takes over from the same chunk.
 * Cancelling the calling coroutine stops synthesis and playback immediately.
 */
/** How long one chunk may take to synthesise before the next voice is tried: 8 s plus 25 ms a letter. */
fun synthTimeoutMs(chunk: String): Long = 8_000L + chunk.length * 25L

class SpeechPipeline(
    private val sink: PcmSink,
    /** Detects a voice that "succeeded" but produced no real sound (see [isSilent]). */
    private val silent: (PcmAudio, String) -> Boolean = ::isSilent,
) {
    /**
     * Short phrases she says often ("हाँ जी, बोलिए?", "एक सेकंड…") keep their audio, so the second time
     * they play at once instead of waiting for the voice service. Keyed by words, language and speed.
     */
    private val phraseCache = object : LinkedHashMap<String, Pair<String, PcmAudio>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, PcmAudio>>?) = size > PHRASE_CACHE_SIZE
    }

    private fun cacheKey(voice: String, chunk: String, language: String, rate: Float) = "$voice|$language|$rate|${chunk.trim()}"

    suspend fun speak(
        text: String,
        providers: List<TtsProvider>,
        preferredLanguage: String,
        rate: Float,
        onEngine: (String) -> Unit = {},
        onLevel: (Float) -> Unit = {},
        onChunkPlayed: () -> Unit = {},
        /** Loudness of this utterance (a whisper is quieter); the limiter then keeps every peak clean. */
        gain: Float = 1f,
    ): SpeakResult {
        // Amounts are said in words ("आठ हज़ार नौ सौ पचासी रुपये"), never digit by digit.
        val chunks = chunkForSpeech(com.raju.aiassistant.domain.language.NumberWords.amountsInWords(text))
        if (chunks.isEmpty()) return SpeakResult(true, "none")
        val usable = providers.filter { runCatching { it.isAvailable() }.getOrDefault(false) }.toMutableList()
        if (usable.isEmpty()) return SpeakResult(false, "none", "No text-to-speech engine is available.")

        var fallbackNote: String? = null
        var lastLabel = usable.first().label
        val ready = Channel<Result<Pair<String, PcmAudio>>>(capacity = 1)

        return coroutineScope {
            val producer = launch {
                for (chunk in chunks) {
                    var audio: PcmAudio? = null
                    // The voice in use is part of the key: switching voices never replays an old voice.
                    val key = cacheKey(usable.firstOrNull()?.label.orEmpty(), chunk, preferredLanguage, rate)
                    val cached = if (chunk.length <= PHRASE_CACHE_MAX_CHARS) synchronized(phraseCache) { phraseCache[key] } else null
                    if (cached != null) {
                        ready.send(Result.success(cached))
                        continue
                    }
                    val lang = languageFor(chunk, preferredLanguage)
                    var usedLabel = ""
                    while (audio == null && usable.isNotEmpty()) {
                        // A voice that cannot speak this language is skipped (no failed try, no note); when none
                        // can, the first one is tried anyway rather than staying silent.
                        val provider = usable.firstOrNull { it.supportsLanguage(lang) } ?: usable.first()
                        // A voice service that hangs must not keep her silent (the HTTP client allows 90 s):
                        // after a fair wait the next voice takes over from this same chunk.
                        val r = kotlinx.coroutines.withTimeoutOrNull(synthTimeoutMs(chunk)) { provider.synthesize(chunk, lang, rate) }
                            ?: com.raju.aiassistant.domain.ai.AiResult.Failure(com.raju.aiassistant.domain.ai.FailureKind.TIMEOUT, "no audio within ${synthTimeoutMs(chunk) / 1000} s")
                        when (r) {
                            // A voice that returns (almost) no sound for real words has failed, even if it says OK.
                            is AiResult.Success -> if (silent(r.value, chunk)) {
                                usable.remove(provider)
                                fallbackNote = "${provider.label} returned no sound" + (usable.firstOrNull()?.let { " — using ${it.label}." } ?: ".")
                            } else {
                                audio = r.value
                                usedLabel = provider.label
                            }
                            is AiResult.Failure -> {
                                usable.remove(provider)
                                fallbackNote = "${provider.label} failed (${r.message})" +
                                    (usable.firstOrNull()?.let { " — using ${it.label}." } ?: ".")
                            }
                        }
                    }
                    if (audio == null) {
                        ready.send(Result.failure(IllegalStateException(fallbackNote ?: "Speech synthesis failed.")))
                        break
                    }
                    val made = usedLabel to audio
                    if (chunk.length <= PHRASE_CACHE_MAX_CHARS) synchronized(phraseCache) { phraseCache[key] = made }
                    ready.send(Result.success(made))
                }
                ready.close()
            }

            sink.setGain(gain)
            sink.requestFocus()
            try {
                for (item in ready) {
                    val (label, audio) = item.getOrElse { e ->
                        producer.cancel()
                        return@coroutineScope SpeakResult(false, lastLabel, e.message)
                    }
                    lastLabel = label
                    onEngine(label)
                    try {
                        sink.play(audio, onLevel)
                    } catch (e: IllegalStateException) {
                        producer.cancel()
                        return@coroutineScope SpeakResult(false, lastLabel, "The phone's audio output failed (${e.message}).")
                    }
                    onChunkPlayed()
                }
                SpeakResult(true, lastLabel, fallbackNote)
            } finally {
                onLevel(0f)
                sink.abandonFocus()
            }
        }
    }
}

/** Phrases up to this length are kept (acknowledgements, short answers), at most [PHRASE_CACHE_SIZE] of them. */
const val PHRASE_CACHE_MAX_CHARS = 48
const val PHRASE_CACHE_SIZE = 24
