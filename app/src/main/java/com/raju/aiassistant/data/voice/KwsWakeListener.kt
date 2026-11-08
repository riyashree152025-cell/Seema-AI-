package com.raju.aiassistant.data.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.raju.aiassistant.data.tts.local.VoiceModelManager
import com.raju.aiassistant.domain.assistant.WakeListener
import com.raju.aiassistant.domain.voice.WAKE_WORD_KEYWORDS
import com.raju.aiassistant.domain.voice.WAKE_WORD_THRESHOLD
import com.raju.aiassistant.domain.voice.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

/**
 * Sleep mode with the offline wake word: a tiny keyword-spotting model (sherpa-onnx) listens on
 * the phone for "Hey Seema" and nothing else. No audio is recorded, kept or sent anywhere; when the
 * phrase is heard the microphone is released and Seema wakes up. Same [WakeListener] contract as
 * [MicWakeListener], so the assistant engine does not change.
 */
class KwsWakeListener(
    private val context: Context,
    private val scope: CoroutineScope,
    private val models: VoiceModelManager<WakeWordModel>,
    private val model: WakeWordModel,
    /** False when the native runtime cannot run on this phone. */
    private val nativeAvailable: () -> Boolean,
    /** The shared microphone (MicHub). Used only while [useHub] is true; otherwise the recorder below runs as before. */
    hub: MicHub? = null,
    private val useHub: () -> Boolean = { false },
    private val log: (String) -> Unit = {},
) : WakeListener {
    override val spotsWakeWord = true

    /** Shared-microphone path: 20 ms frames batched to 100 ms, then the same spotter. */
    private val hubListener: HubWakeListener? = hub?.let { h ->
        HubWakeListener(h) { KwsFrameDetector(openStream = { NativeKeywordStream(spotter()) }, log = log) }
    }

    private var job: Job? = null
    @Volatile private var activeRecorder: AudioRecord? = null
    private var spotter: KeywordSpotter? = null

    /** Usable when the model is downloaded and the runtime loads. */
    fun isReady(): Boolean = models.isInstalled(model) && nativeAvailable()

    override fun start(onUtterance: (ByteArray) -> Unit, onError: () -> Unit) {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || !isReady()) {
            onError()
            return
        }
        val shared = hubListener
        if (shared != null && useHub()) {
            log("kws listening (shared mic)")
            shared.start(onUtterance, onError)
            return
        }
        job = scope.launch(Dispatchers.IO) {
            val heard = runCatching { listenForWakeWord() }.onFailure { log("kws error ${it.javaClass.simpleName}") }.getOrNull()
            if (!isActive) return@launch
            if (heard == true) onUtterance(ByteArray(0)) else onError()
        }
    }

    override fun stop() {
        hubListener?.stop() // a no-op unless the shared microphone was used
        job?.cancel()
        job = null
        activeRecorder?.let { runCatching { it.stop() } }
    }

    /** Frees the model's memory (app in the background without a voice session). */
    @Synchronized fun release() {
        hubListener?.stop() // its stream belongs to this spotter: close it first
        spotter?.release()
        spotter = null
    }

    /**
     * Debug self-test (emulator): runs the installed model over a 16-kHz mono WAV with the given
     * keywords ("" = the "Hey Seema" phrases) and returns the keywords it found.
     */
    fun spotInWav(wav: File, keywords: String = ""): List<String> {
        val pcm = com.raju.aiassistant.core.audio.parseWav(wav.readBytes()) ?: return emptyList()
        val kws = spotter()
        val stream = if (keywords.isBlank()) kws.createStream() else kws.createStream(keywords)
        val samples = FloatArray(pcm.frameCount) { i ->
            val lo = pcm.data[i * 2 * pcm.channels].toInt() and 0xFF
            val hi = pcm.data[i * 2 * pcm.channels + 1].toInt()
            ((hi shl 8) or lo).toShort() / 32768f
        }
        val found = mutableListOf<String>()
        try {
            stream.acceptWaveform(samples, pcm.sampleRate)
            stream.acceptWaveform(FloatArray(pcm.sampleRate), pcm.sampleRate) // 1 s of silence to flush
            stream.inputFinished()
            while (kws.isReady(stream)) {
                kws.decode(stream)
                kws.getResult(stream).keyword.takeIf { it.isNotBlank() }?.let { found += it; kws.reset(stream) }
            }
        } finally {
            stream.release()
        }
        return found
    }

    @Synchronized private fun spotter(): KeywordSpotter {
        spotter?.let { return it }
        val dir = models.modelDir(model)
        fun path(name: String) = File(dir, name).absolutePath
        // Only our phrases: the archive's own keywords.txt ("HEY SIRI", "HELLO WORLD", …) is not used.
        val keywords = File(dir, "seema_keywords.txt").apply { writeText(WAKE_WORD_KEYWORDS.replace("/", "\n") + "\n") }
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(encoder = path(model.modelFile), decoder = path(model.decoderFile), joiner = path(model.joinerFile)),
                tokens = path("tokens.txt"),
                numThreads = 1, // light on CPU and battery
            ),
            keywordsFile = keywords.absolutePath,
            keywordsThreshold = WAKE_WORD_THRESHOLD,
        )
        return KeywordSpotter(config = config).also { spotter = it }
    }

    @SuppressLint("MissingPermission") // checked in start()
    private suspend fun listenForWakeWord(): Boolean {
        val kws = spotter()
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, FRAME * 2 * 4))
        if (recorder.state != AudioRecord.STATE_INITIALIZED) { recorder.release(); return false }
        val stream = kws.createStream()
        activeRecorder = recorder
        try {
            recorder.startRecording()
            log("kws listening")
            val buf = ShortArray(FRAME)
            val floats = FloatArray(FRAME)
            var lastLevelLog = 0L
            while (currentCoroutineContext().isActive) {
                val n = recorder.read(buf, 0, buf.size)
                if (n <= 0) return false // microphone taken away (call, another app) or stopped
                var sum = 0.0
                for (i in 0 until n) {
                    floats[i] = buf[i] / 32768f
                    sum += buf[i].toDouble() * buf[i]
                }
                // Diagnostics for the phone log: shows whether a voice reaches the spotter at all
                // (at most every 3 s, only when something louder than a quiet room is heard).
                val level = kotlin.math.sqrt(sum / n)
                val now = System.currentTimeMillis()
                if (level > VOICE_LEVEL && now - lastLevelLog > 3_000) {
                    lastLevelLog = now
                    log("kws hears sound, level ${level.toInt()}")
                }
                stream.acceptWaveform(if (n == FRAME) floats else floats.copyOf(n), RATE)
                while (kws.isReady(stream)) {
                    kws.decode(stream)
                    if (kws.getResult(stream).keyword.isNotBlank()) {
                        log("kws: wake word")
                        kws.reset(stream)
                        return true
                    }
                }
            }
            return false
        } finally {
            activeRecorder = null
            runCatching { recorder.stop() }
            recorder.release()
            stream.release()
        }
    }

    /** One sherpa-onnx stream on the shared spotter. */
    private class NativeKeywordStream(private val kws: KeywordSpotter) : KeywordStream {
        private val stream = kws.createStream()

        override fun accept(samples: FloatArray): Boolean {
            stream.acceptWaveform(samples, RATE)
            while (kws.isReady(stream)) {
                kws.decode(stream)
                if (kws.getResult(stream).keyword.isNotBlank()) {
                    kws.reset(stream)
                    return true
                }
            }
            return false
        }

        override fun release() = stream.release()
    }

    private companion object {
        const val RATE = 16_000
        /** 100 ms of audio per read. */
        const val FRAME = 1_600
        /** Microphone level (RMS of 16-bit samples) above a quiet room: someone may be talking. */
        const val VOICE_LEVEL = 150.0
    }
}

/**
 * Picks the sleep-mode listener each time it starts: the offline "Hey Seema" detector when it is
 * ready and calling by name is on, otherwise the existing voice detector (+ Deepgram name check).
 */
class SwitchingWakeListener(
    private val offline: KwsWakeListener,
    private val fallback: WakeListener,
    /**
     * Check the name with Deepgram instead (key saved and online). On the real phone the offline
     * English model heard the voice ("kws hears sound") but never recognised an Indian "Hey Seema";
     * Deepgram does. Without internet the offline detector is used.
     */
    private val preferCloud: () -> Boolean = { false },
    private val wantsName: () -> Boolean,
) : WakeListener {
    @Volatile private var current: WakeListener = fallback
    override val spotsWakeWord: Boolean get() = current.spotsWakeWord

    /** True while sleep mode listens with the offline wake word. */
    val offlineActive: Boolean get() = current === offline

    override fun start(onUtterance: (ByteArray) -> Unit, onError: () -> Unit) {
        current.stop()
        current = if (wantsName() && offline.isReady() && !preferCloud()) offline else fallback
        current.start(onUtterance, onError)
    }

    override fun stop() = current.stop()
}
