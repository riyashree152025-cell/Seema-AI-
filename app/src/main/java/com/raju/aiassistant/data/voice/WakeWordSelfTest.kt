package com.raju.aiassistant.data.voice

import android.util.Log
import com.raju.aiassistant.data.tts.local.ModelState
import com.raju.aiassistant.di.AppContainer
import com.raju.aiassistant.domain.voice.WAKE_WORD_MODEL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Debug-only check of the offline wake word on a device or emulator: real download + checksum +
 * install, then the native keyword spotter on the archive's own English test recordings:
 * it must find "LIGHT UP" in 0.wav (the pipeline works) and must not wake for "Hey Seema" in
 * either recording (no false wake on ordinary speech). Logged with the tag SeemaWakeTest.
 */
suspend fun runWakeWordSelfTest(c: AppContainer) {
    fun log(msg: String) { Log.i("SeemaWakeTest", "WAKE_TEST $msg") }
    log("native=${c.sherpaHost.nativeAvailable}")
    c.wakeModels.download(WAKE_WORD_MODEL)
    val end = withTimeoutOrNull(10 * 60_000L) {
        c.wakeModels.states.first { val s = it[WAKE_WORD_MODEL.id]; s is ModelState.Installed || s is ModelState.Failed }[WAKE_WORD_MODEL.id]
    }
    log("install=$end")
    if (end !is ModelState.Installed) return log("result=FAIL reason=install")
    val dir = File(c.wakeModels.modelDir(WAKE_WORD_MODEL), "test_wavs")
    val result = withContext(Dispatchers.Default) {
        runCatching {
            val started = System.nanoTime()
            val lightUp = c.kwsWakeListener.spotInWav(File(dir, "0.wav"), "▁ L IGHT ▁UP @LIGHT_UP")
            val ms = (System.nanoTime() - started) / 1_000_000
            val falseWakes = c.kwsWakeListener.spotInWav(File(dir, "0.wav")) + c.kwsWakeListener.spotInWav(File(dir, "1.wav"))
            Triple(lightUp, falseWakes, ms)
        }
    }
    result.onFailure { log("result=FAIL error=${it.javaClass.simpleName}: ${it.message?.take(120)}") }
    result.onSuccess { (lightUp, falseWakes, ms) ->
        log("light_up=$lightUp (${ms} ms for 0.wav) hey_seema_false_wakes=${falseWakes.size}")
        log("result=" + if ("LIGHT_UP" in lightUp && falseWakes.isEmpty()) "PASS" else "FAIL")
    }
}
