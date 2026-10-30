package com.raju.aiassistant.data.tts.local

import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import com.raju.aiassistant.domain.voice.LocalVoiceModel

/** One engine measured on this phone by Voice Engine Manager → "Test speed on this phone". */
data class VoiceBenchmark(
    val title: String,
    /** Load + first short sentence from cold (what the first reply after opening the app feels like). */
    val coldFirstSentenceMs: Long? = null,
    /** First short sentence with the engine already loaded (every later reply). */
    val warmFirstSentenceMs: Long? = null,
    /** Synthesis time ÷ audio length for a longer reply (below 1 = faster than speech). */
    val rtf: Double? = null,
    /** CPU used by the app while synthesising, as % of the whole phone. */
    val cpuPercent: Int? = null,
    /** App memory (PSS) after loading the engine, MB. */
    val memoryMb: Int? = null,
    /** Battery current while synthesising, mA (null when the phone does not report it). */
    val batteryMa: Int? = null,
    val error: String? = null,
)

private const val SHORT = "नमस्ते! मैं सीमा हूँ।"
private const val LONG = "आज दिल्ली में धूप है, शाम को हल्की बारिश हो सकती है। बाहर जाएँ तो छाता साथ रखिए। क्या मैं आपके लिए कुछ और कर सकती हूँ?"

/** Measures every installed offline engine for real on this phone (nothing is estimated). */
suspend fun benchmarkOfflineVoices(context: Context, host: SherpaTtsHost, models: List<LocalVoiceModel>, speakerFor: (LocalVoiceModel) -> Int): List<VoiceBenchmark> {
    val battery = context.getSystemService(BatteryManager::class.java)
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    return models.map { model ->
        runCatching {
            host.release(model.engine) // cold start
            val sid = speakerFor(model)
            var t = System.nanoTime()
            host.synthesize(model, SHORT, sid, 1f)
            val cold = (System.nanoTime() - t) / 1_000_000
            t = System.nanoTime()
            host.synthesize(model, SHORT, sid, 1f)
            val warm = (System.nanoTime() - t) / 1_000_000

            val currents = mutableListOf<Int>()
            fun sampleCurrent() {
                battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeIf { it != Int.MIN_VALUE && it != 0 }?.let { currents += it }
            }
            sampleCurrent()
            val cpu0 = Process.getElapsedCpuTime()
            t = System.nanoTime()
            val (samples, rate) = host.synthesize(model, LONG, sid, 1f)
            val wallMs = (System.nanoTime() - t) / 1_000_000
            val cpuMs = Process.getElapsedCpuTime() - cpu0
            sampleCurrent()
            val audioMs = samples.size * 1000L / rate.coerceAtLeast(1)
            // CURRENT_NOW is µA on most phones (some report mA); shown as a positive drain in mA.
            val ma = currents.takeIf { it.isNotEmpty() }?.map { kotlin.math.abs(it) }?.average()?.let { if (it > 20_000) it / 1000 else it }?.toInt()
            VoiceBenchmark(
                title = model.title,
                coldFirstSentenceMs = cold,
                warmFirstSentenceMs = warm,
                rtf = if (audioMs > 0) wallMs.toDouble() / audioMs else null,
                cpuPercent = if (wallMs > 0) (cpuMs * 100 / wallMs / cores).toInt() else null,
                memoryMb = (Debug.getPss() / 1024).toInt(),
                batteryMa = ma,
            )
        }.getOrElse { VoiceBenchmark(title = model.title, error = "${it.javaClass.simpleName}: ${it.message?.take(100)}") }
    }
}
