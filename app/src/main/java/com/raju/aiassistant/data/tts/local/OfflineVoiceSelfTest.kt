package com.raju.aiassistant.data.tts.local

import android.util.Log
import com.raju.aiassistant.di.AppContainer
import com.raju.aiassistant.domain.model.TtsEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Debug-only end-to-end check of one offline voice (started by the emulator test with
 * `--es seema_voice_test PIPER`): real download + checksum + install, real synthesis, playback through
 * the normal speech path, and the benchmark. Results are logged with the tag SeemaVoiceTest.
 */
suspend fun runOfflineVoiceTest(c: AppContainer, engine: TtsEngine) {
    fun log(msg: String) { Log.i("SeemaVoiceTest", "VOICE_TEST engine=$engine $msg") }
    val model = c.voiceModels.catalog.firstOrNull { it.engine == engine } ?: return log("result=FAIL reason=not-in-catalog")
    log("native=${c.sherpaHost.nativeAvailable}")
    val started = System.nanoTime()
    c.voiceModels.download(model)
    val end = withTimeoutOrNull(15 * 60_000L) {
        c.voiceModels.states.first { val s = it[model.id]; s is ModelState.Installed || s is ModelState.Failed }[model.id]
    }
    log("install=$end seconds=${(System.nanoTime() - started) / 1_000_000_000}")
    if (end !is ModelState.Installed) return log("result=FAIL reason=install")

    val speak = c.speaker.preview(engine, "नमस्ते! मैं सीमा हूँ। आज आपकी क्या मदद कर सकती हूँ?")
    log("speak completed=${speak.completed} via=\"${speak.engineLabel}\" error=${speak.error}")
    val stats = c.voiceEngineStats.stats.value[engine]
    log("stats rtf=${stats?.rtf} sentenceMs=${stats?.firstAudioMs} ok=${stats?.successes} failed=${stats?.failures}")
    val sid = if (engine == TtsEngine.KOKORO) c.currentSettings.value.kokoroSpeaker else model.speakers.first().id
    benchmarkOfflineVoices(c.appContext, c.sherpaHost, listOf(model)) { sid }.forEach { b ->
        log("bench coldMs=${b.coldFirstSentenceMs} warmMs=${b.warmFirstSentenceMs} rtf=${b.rtf} cpu=${b.cpuPercent}% pssMb=${b.memoryMb} error=${b.error}")
    }
    log("result=" + if (speak.completed && speak.engineLabel.contains("offline")) "PASS" else "FAIL")
}
