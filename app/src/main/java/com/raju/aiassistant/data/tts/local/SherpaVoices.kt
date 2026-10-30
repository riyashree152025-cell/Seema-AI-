package com.raju.aiassistant.data.tts.local

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.raju.aiassistant.core.audio.PcmAudio
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.EngineStats
import com.raju.aiassistant.domain.voice.LocalModelKind
import com.raju.aiassistant.domain.voice.LocalVoiceModel
import com.raju.aiassistant.domain.voice.TtsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Float samples (−1..1) from sherpa-onnx → 16-bit little-endian PCM for the audio player. */
fun floatToPcm16(samples: FloatArray): ByteArray {
    val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
    return buf.array()
}

/**
 * Measured speed and failures of every voice engine (also cloud and Android ones), kept across app
 * starts so Automatic mode knows which engine is fast on this phone.
 */
class VoiceEngineStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("voice_engine_stats", Context.MODE_PRIVATE)
    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<Map<TtsEngine, EngineStats>> = _stats.asStateFlow()

    fun success(engine: TtsEngine, synthMs: Long, audioMs: Long) {
        if (audioMs <= 0) return
        update(engine) { it.withSuccess(synthMs.toDouble() / audioMs, synthMs) }
    }

    fun failure(engine: TtsEngine, error: String) = update(engine) { it.withFailure(error) }

    fun reset() {
        prefs.edit().clear().apply()
        _stats.value = emptyMap()
    }

    private fun update(engine: TtsEngine, f: (EngineStats) -> EngineStats) {
        val next = f(_stats.value[engine] ?: EngineStats())
        _stats.value = _stats.value + (engine to next)
        prefs.edit()
            .putString(engine.name, listOf(next.rtf ?: "", next.firstAudioMs ?: "", next.successes, next.failures, next.recentFailures).joinToString(";"))
            .apply()
    }

    private fun load(): Map<TtsEngine, EngineStats> = TtsEngine.entries.mapNotNull { e ->
        val p = prefs.getString(e.name, null)?.split(';') ?: return@mapNotNull null
        e to EngineStats(
            rtf = p.getOrNull(0)?.toDoubleOrNull(),
            firstAudioMs = p.getOrNull(1)?.toLongOrNull(),
            successes = p.getOrNull(2)?.toIntOrNull() ?: 0,
            failures = p.getOrNull(3)?.toIntOrNull() ?: 0,
            recentFailures = p.getOrNull(4)?.toIntOrNull() ?: 0,
        )
    }.toMap()
}

/**
 * Keeps one sherpa-onnx engine per installed model loaded ("warm"), so only the first sentence pays
 * the load time. Synthesis runs on a background thread, one sentence at a time per engine.
 */
class SherpaTtsHost(private val manager: VoiceModelManager) {
    private class Loaded(val modelId: String, val tts: OfflineTts)

    private val loaded = mutableMapOf<TtsEngine, Loaded>()
    private val lock = Mutex()

    /** False when the native library cannot run on this phone (unsupported CPU); then the engine is skipped. */
    val nativeAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("sherpa-onnx-jni"); true }.getOrDefault(false)
    }

    suspend fun synthesize(model: LocalVoiceModel, text: String, speakerId: Int, speed: Float): Pair<FloatArray, Int> = withContext(Dispatchers.Default) {
        lock.withLock {
            val tts = engineFor(model)
            val audio = tts.generate(text, sid = speakerId, speed = speed.coerceIn(0.5f, 2f))
            audio.samples to audio.sampleRate
        }
    }

    /** Frees the native memory (low memory, or the model was deleted). The next sentence loads it again. */
    suspend fun release(engine: TtsEngine? = null) = lock.withLock {
        val keys = engine?.let { listOf(it) } ?: loaded.keys.toList()
        keys.forEach { loaded.remove(it)?.tts?.release() }
    }

    private fun engineFor(model: LocalVoiceModel): OfflineTts {
        loaded[model.engine]?.takeIf { it.modelId == model.id }?.let { return it.tts }
        loaded.remove(model.engine)?.tts?.release()
        val dir = manager.modelDir(model)
        fun path(name: String) = File(dir, name).absolutePath
        // Two threads: fast enough on 6–8 GB phones without heating them up.
        val modelConfig = when (model.kind) {
            LocalModelKind.PIPER_VITS -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(model = path(model.modelFile), tokens = path("tokens.txt"), dataDir = path("espeak-ng-data")),
                numThreads = 2,
            )
            LocalModelKind.KOKORO -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = path(model.modelFile), voices = path(model.voicesFile), tokens = path("tokens.txt"),
                    dataDir = path("espeak-ng-data"), lang = model.lang,
                ),
                numThreads = 2,
            )
        }
        val tts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
        loaded[model.engine] = Loaded(model.id, tts)
        return tts
    }
}

/**
 * An offline voice engine (Piper or Kokoro) running on the phone through sherpa-onnx.
 * Speaks Hindi (Devanagari and Hinglish); Bengali goes to the next engine (no Bengali model yet).
 */
class LocalTtsProvider(
    override val engine: TtsEngine,
    private val host: SherpaTtsHost,
    private val manager: VoiceModelManager,
    private val settings: () -> UserSettings,
) : TtsProvider {
    override val label = engine.label.substringBefore(" —") + " (offline)"

    override suspend fun isAvailable() = host.nativeAvailable && manager.installedModel(engine) != null

    override suspend fun synthesize(text: String, languageTag: String, rate: Float): AiResult<PcmAudio> {
        val model = manager.installedModel(engine) ?: return AiResult.Failure(FailureKind.NOT_CONFIGURED, "voice model not downloaded")
        if (languageTag.startsWith("bn")) return AiResult.Failure(FailureKind.UNKNOWN, "no Bengali offline voice yet")
        val speaker = if (model.engine == TtsEngine.KOKORO) settings().kokoroSpeaker.takeIf { id -> model.speakers.any { it.id == id } } ?: model.speakers.first().id
        else model.speakers.first().id
        return try {
            val (samples, sampleRate) = host.synthesize(model, text, speaker, rate)
            if (samples.isEmpty() || sampleRate <= 0) AiResult.Failure(FailureKind.UNKNOWN, "produced no audio")
            else AiResult.Success(PcmAudio(sampleRate, 1, floatToPcm16(samples)))
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AiResult.Failure(FailureKind.UNKNOWN, "offline engine error (${e.javaClass.simpleName}: ${e.message?.take(80)})")
        }
    }
}

/**
 * Wraps any voice engine and records its real speed (synthesis time ÷ audio length, time per
 * sentence) and failures, for Automatic mode and Settings → Voice Engine Manager.
 */
class MeasuredTtsProvider(private val inner: TtsProvider, private val stats: VoiceEngineStatsStore) : TtsProvider {
    override val engine get() = inner.engine
    override val label get() = inner.label
    override suspend fun isAvailable() = inner.isAvailable()

    override suspend fun synthesize(text: String, languageTag: String, rate: Float): AiResult<PcmAudio> {
        val started = System.nanoTime()
        val r = inner.synthesize(text, languageTag, rate)
        val ms = (System.nanoTime() - started) / 1_000_000
        when (r) {
            is AiResult.Success -> stats.success(engine, ms, r.value.frameCount * 1000L / r.value.sampleRate.coerceAtLeast(1))
            is AiResult.Failure -> if (r.kind != FailureKind.NOT_CONFIGURED) stats.failure(engine, r.message)
        }
        return r
    }
}
