package com.raju.aiassistant.data.tts

import com.raju.aiassistant.domain.assistant.SpeakResult
import com.raju.aiassistant.domain.assistant.Speaker
import com.raju.aiassistant.domain.language.SeemaLanguage
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.SpeechPipeline
import com.raju.aiassistant.domain.voice.TtsProvider
import com.raju.aiassistant.domain.voice.providerOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Speech output. One utterance at a time: a new speak() or stop() interrupts the current one,
 * so responses are never spoken twice or on top of each other. Engines are tried in the order
 * chosen in Settings (selected engine first, then the rest); a failing engine falls back to the
 * next one and the reason is reported. Lives in the app scope, so it survives screen rotation.
 */
class TtsManager(
    private val scope: CoroutineScope,
    private val pipeline: SpeechPipeline,
    private val providers: List<TtsProvider>,
    private val androidTts: AndroidTts,
    private val settings: StateFlow<UserSettings>,
    /** The conversation's language (Hindi or Bengali), used when "Auto" is chosen. */
    private val conversationLanguage: StateFlow<SeemaLanguage>,
    /** Which engines to try, in order (Automatic / Manual voice engine selection). */
    private val engineOrder: (UserSettings) -> List<com.raju.aiassistant.domain.model.TtsEngine> = { providerOrder(it.ttsEngine) },
) : Speaker {
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private val _playing = MutableStateFlow(false)
    /** True only once audio is really coming out of the speaker (not while it is being synthesised). */
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    private val _activeEngine = MutableStateFlow<String?>(null)
    /** Label of the engine used for the current/last utterance. */
    val activeEngine: StateFlow<String?> = _activeEngine.asStateFlow()

    @Volatile private var chunksDone = 0
    override val playedChunks: Int get() = chunksDone

    private val lock = Mutex()
    private var current: Job? = null

    override suspend fun speak(text: String): SpeakResult = speak(text, only = null)

    /** Voice Engine Manager → Preview: speaks [text] with exactly [engine] (no fallback). */
    suspend fun preview(engine: com.raju.aiassistant.domain.model.TtsEngine, text: String): SpeakResult = speak(text, only = engine)

    private suspend fun speak(text: String, only: com.raju.aiassistant.domain.model.TtsEngine?): SpeakResult {
        stop()
        chunksDone = 0
        var result = SpeakResult(false, "none")
        val job = scope.launch {
            lock.withLock {
                _speaking.value = true
                try {
                    val s = settings.value
                    val ordered = (only?.let { listOf(it) } ?: engineOrder(s)).mapNotNull { e -> providers.firstOrNull { it.engine == e } }
                    result = pipeline.speak(
                        text = text,
                        providers = ordered,
                        preferredLanguage = (SeemaLanguage.fromTag(s.speechLanguage) ?: conversationLanguage.value).tag,
                        rate = s.speechRate,
                        // Called right before each chunk is played: from here on she is really speaking.
                        onEngine = { _activeEngine.value = it; _playing.value = true },
                        onLevel = { _level.value = it },
                        onChunkPlayed = { chunksDone++ },
                    )
                } finally {
                    _speaking.value = false
                    _playing.value = false
                    _level.value = 0f
                }
            }
        }
        current = job
        try {
            job.join()
        } catch (e: CancellationException) {
            job.cancel() // caller was stopped: stop the audio too
            throw e
        }
        return if (job.isCancelled) result.copy(completed = false) else result
    }

    override fun stop() {
        current?.cancel()
        current = null
        androidTts.stop()
        _playing.value = false
        _level.value = 0f
    }

    /** Settings → Test voice. Really synthesises and plays a sample. */
    fun test(sample: String = "Namaste! Main Seema AI hoon. आपकी क्या मदद कर सकती हूँ?", onDone: (SpeakResult) -> Unit) {
        scope.launch { onDone(speak(sample)) }
    }
}
