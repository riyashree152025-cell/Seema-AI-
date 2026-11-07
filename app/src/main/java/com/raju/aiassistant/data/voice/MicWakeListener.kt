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
 * Sleep mode: a light, on-device voice detector. It follows the room's noise level, notices when
 * someone starts talking (louder than the noise for ~0.1 s), records until they pause, and hands
 * over that utterance including half a second from before the start. Runs only while the app is on
 * screen in hands-free mode; no audio leaves the phone unless the engine sends that utterance on.
 */
class MicWakeListener(
    private val context: Context,
    private val scope: CoroutineScope,
    /** The shared microphone (MicHub). Used only while [useHub] is true; otherwise the recorder below runs as before. */
    hub: MicHub? = null,
    private val useHub: () -> Boolean = { false },
) : WakeListener {
    private var job: Job? = null
    private val hubListener: HubWakeListener? = hub?.let { HubWakeListener(it) }

    override fun start(onUtterance: (ByteArray) -> Unit, onError: () -> Unit) {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError()
            return
        }
        val shared = hubListener
        if (shared != null && useHub()) {
            shared.start(onUtterance, onError)
            return
        }
        job = scope.launch(Dispatchers.IO) {
            val pcm = runCatching { waitForSpeech() }.getOrNull()
            if (!isActive) return@launch
            if (pcm != null) onUtterance(pcm) else onError()
        }
    }

    override fun stop() {
        hubListener?.stop() // a no-op unless the shared microphone was used
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
        var noise = -1.0
        var loudFrames = 0
        // Every ~5 s: was there any sound at all? Pure zeros mean Android is blocking the microphone
        // for a background app (no microphone foreground service, or another app holds the mic).
        var windowFrames = 0
        var windowMax = 0.0
        try {
            recorder.startRecording()
            activeRecorder = recorder
            android.util.Log.d("SeemaWake", "MicWakeListener started recording, awaiting wake word...")
            while (true) {
                if (!coroutineContext.isActive) return null
                // 1) Wait for a voice.
                val frame = readFrame(recorder) ?: return null
                preRoll.addLast(frame)
                if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                val level = rms(frame)
                windowMax = max(windowMax, level)
                if (++windowFrames >= LEVEL_WINDOW_FRAMES) {
                    if (windowMax < 1.0) {
                        android.util.Log.w("SeemaWake", "Mic gives only silence for 5 s — Android is blocking the background microphone (standby service running=${com.raju.aiassistant.voice.SeemaWakeService.running}, or another app/call holds the mic)")
                    } else {
                        android.util.Log.d("SeemaWake", "standby mic ok, loudest level ${windowMax.toInt()} (noise ${noise.toInt()})")
                    }
                    windowFrames = 0
                    windowMax = 0.0
                }
                noise = when {
                    noise < 0 -> level
                    level < noise -> noise * 0.9 + level * 0.1
                    else -> noise * 0.998 + level * 0.002
                }
                loudFrames = if (level > max(noise * 2.2, MIN_SPEECH)) loudFrames + 1 else 0
                if (loudFrames < START_FRAMES) continue

                // 2) Record until a pause.
                android.util.Log.d("SeemaWake", "Voice detected! Level=$level, recording utterance...")
                val out = ByteArrayOutputStream()
                preRoll.forEach { out.write(it) }
                var quietMs = 0
                var speechMs = START_FRAMES * FRAME_MS
                var totalMs = 0
                // A pause is quieter than the voice that started the recording. With the old bar
                // (1.6 x noise, at least 121) a soft voice at 118-196 counted as "quiet" between its
                // own syllables, so "Hey Seema" was thrown away as too short (seen on the phone).
                val pauseThreshold = max(noise * 1.3, MIN_SPEECH * 0.8)
                while (coroutineContext.isActive && totalMs < MAX_UTTERANCE_MS) {
                    val f = readFrame(recorder) ?: break
                    out.write(f)
                    totalMs += FRAME_MS
                    val fRms = rms(f)
                    if (fRms < pauseThreshold) quietMs += FRAME_MS else { quietMs = 0; speechMs += FRAME_MS }
                    if (quietMs >= END_SILENCE_MS) break
                }
                if (speechMs >= MIN_SPEECH_MS) {
                    android.util.Log.d("SeemaWake", "Utterance captured: ${out.size()} bytes (${totalMs}ms)")
                    return out.toByteArray()
                }
                // Too short (a knock, a cough): keep waiting.
                android.util.Log.d("SeemaWake", "Too short, ignored (${speechMs} ms of voice in ${totalMs} ms)")
                preRoll.clear()
                loudFrames = 0
            }
        } finally {
            if (activeRecorder === recorder) activeRecorder = null
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
        const val START_FRAMES = 3 // ~60 ms of voice
        const val MIN_SPEECH = 110.0
        const val MIN_SPEECH_MS = 160
        const val END_SILENCE_MS = 650
        const val MAX_UTTERANCE_MS = 7_500
        const val LEVEL_WINDOW_FRAMES = 5_000 / FRAME_MS
    }
}
