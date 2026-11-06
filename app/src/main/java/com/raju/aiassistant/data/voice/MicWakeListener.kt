package com.raju.aiassistant.data.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.raju.aiassistant.domain.assistant.WakeListener
import com.raju.aiassistant.domain.voice.WakeEndpointer
import com.raju.aiassistant.domain.voice.normalizePcm16
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Sleep mode: a light, on-device voice detector ([WakeEndpointer]). It follows the room's noise
 * level (an AC is learnt within seconds), notices when someone starts talking, records until they
 * pause, drops steady noise, and hands over the utterance (made louder when quiet) including half a
 * second from before the start. Runs only while the app is on
 * screen in hands-free mode; no audio leaves the phone unless the engine sends that utterance on.
 */
class MicWakeListener(private val context: Context, private val scope: CoroutineScope) : WakeListener {
    private var job: Job? = null

    override fun start(onUtterance: (ByteArray) -> Unit, onError: () -> Unit) {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError()
            return
        }
        job = scope.launch(Dispatchers.IO) {
            val pcm = runCatching { waitForSpeech() }.getOrNull()
            if (!isActive) return@launch
            if (pcm != null) onUtterance(pcm) else onError()
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        // Release the microphone immediately: the speech recogniser usually starts right after this,
        // and while this recorder is still capturing it would only get silence.
        activeRecorder?.let { runCatching { it.stop() } }
    }

    @Volatile private var activeRecorder: AudioRecord? = null

    @SuppressLint("MissingPermission") // checked in start()
    private suspend fun waitForSpeech(): ByteArray? {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val bufSize = max(min * 2, FRAME_BYTES * 10)
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )
        var recorder: AudioRecord? = null
        for (src in sources) {
            try {
                val rec = AudioRecord(src, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    recorder = rec
                    break
                } else {
                    rec.release()
                }
            } catch (e: Exception) {
                // Try next source
            }
        }
        if (recorder == null) {
            android.util.Log.w("SeemaWake", "AudioRecord initialization failed for all sources")
            return null
        }
        val preRoll = ArrayDeque<ByteArray>()
        // AC and fan noise: the phone's own noise suppressor, where it has one (most do on MIC).
        val suppressor = attachNoiseSuppressor(recorder.audioSessionId)
        val gate = WakeEndpointer(frameMs = FRAME_MS, maxMs = MAX_UTTERANCE_MS)
        try {
            recorder.startRecording()
            activeRecorder = recorder
            android.util.Log.d("SeemaWake", "MicWakeListener started recording, awaiting wake word... (noise suppressor ${if (suppressor != null) "on" else "n/a"})")
            var out: ByteArrayOutputStream? = null
            while (true) {
                if (!coroutineContext.isActive) return null
                val frame = readFrame(recorder) ?: return null
                val level = rms(frame)
                when (gate.onFrame(level)) {
                    WakeEndpointer.Event.WAITING -> {
                        preRoll.addLast(frame)
                        if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                    }
                    WakeEndpointer.Event.STARTED -> {
                        android.util.Log.d("SeemaWake", "Voice detected! Level=${level.toInt()} noise=${gate.noise.toInt()}, recording utterance...")
                        out = ByteArrayOutputStream().also { o -> preRoll.forEach { o.write(it) }; o.write(frame) }
                        preRoll.clear()
                    }
                    WakeEndpointer.Event.RECORDING -> out?.write(frame)
                    WakeEndpointer.Event.DONE -> {
                        val pcm = out?.toByteArray() ?: continue
                        android.util.Log.d("SeemaWake", "Utterance captured: ${pcm.size} bytes (${gate.utteranceMs}ms)")
                        // Quiet phone microphones (speech at RMS 120-400) made the recogniser return nothing.
                        return normalizePcm16(pcm)
                    }
                    WakeEndpointer.Event.DISCARDED -> {
                        // A knock, or a steady sound like the AC: not sent anywhere, keep waiting.
                        android.util.Log.d("SeemaWake", "Not a voice (${gate.utteranceMs}ms, noise now ${gate.noise.toInt()}), still waiting")
                        out = null
                    }
                }
            }
        } finally {
            if (activeRecorder === recorder) activeRecorder = null
            runCatching { suppressor?.release() }
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun readFrame(recorder: AudioRecord): ByteArray? {
        val frame = ByteArray(FRAME_BYTES)
        var read = 0
        while (read < FRAME_BYTES) {
            val n = recorder.read(frame, read, FRAME_BYTES - read)
            if (n <= 0) return null
            read += n
        }
        return frame
    }

    private fun rms(frame: ByteArray): Double {
        var sum = 0.0
        var i = 0
        while (i + 1 < frame.size) {
            val s = ((frame[i + 1].toInt() shl 8) or (frame[i].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s
            i += 2
        }
        return sqrt(sum / (frame.size / 2))
    }

    private companion object {
        const val RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_BYTES = RATE / 1000 * FRAME_MS * 2
        const val PRE_ROLL_FRAMES = 25 // 0.5 s
        /** Long enough for "सीमा, Rizwan Travels को मैसेज भेजो कि …"; pauses end it much sooner. */
        const val MAX_UTTERANCE_MS = 8_000
    }
}

/** The phone's noise suppressor on [sessionId], or null when it has none. Release it with the recorder. */
internal fun attachNoiseSuppressor(sessionId: Int): android.media.audiofx.NoiseSuppressor? = runCatching {
    if (!android.media.audiofx.NoiseSuppressor.isAvailable()) return@runCatching null
    android.media.audiofx.NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
}.getOrNull()
