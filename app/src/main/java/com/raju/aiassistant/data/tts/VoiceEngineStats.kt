package com.raju.aiassistant.data.tts

import android.content.Context
import com.raju.aiassistant.core.audio.PcmAudio
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.voice.EngineStats
import com.raju.aiassistant.domain.voice.TtsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Measured speed and failures of every voice engine, kept across app
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
        val rawFailures = p.getOrNull(3)?.toIntOrNull() ?: 0
        val successes = p.getOrNull(2)?.toIntOrNull() ?: 0
        val isEdgeWithLegacyFailures = (e == TtsEngine.EDGE && successes == 0 && rawFailures > 0)
        val failures = if (isEdgeWithLegacyFailures) 0 else rawFailures
        val recentFailures = if (isEdgeWithLegacyFailures) 0 else (p.getOrNull(4)?.toIntOrNull() ?: 0)
        e to EngineStats(
            rtf = p.getOrNull(0)?.toDoubleOrNull(),
            firstAudioMs = p.getOrNull(1)?.toLongOrNull(),
            successes = successes,
            failures = failures,
            recentFailures = recentFailures,
        )
    }.toMap()
}

/**
 * Wraps any voice engine and records its real speed (synthesis time ÷ audio length, time per
 * sentence) and failures, for Automatic mode and Settings → Voice Engine Manager.
 */
class MeasuredTtsProvider(private val inner: TtsProvider, private val stats: VoiceEngineStatsStore) : TtsProvider {
    override val engine get() = inner.engine
    override val label get() = inner.label
    override suspend fun isAvailable() = inner.isAvailable()
    override fun supportsLanguage(languageTag: String) = inner.supportsLanguage(languageTag)

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
