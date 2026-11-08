package com.raju.aiassistant.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.math.max

/** Android's minimum AudioRecord buffer for Seema's format; the recorder itself gets 4x this. */
fun minMicBufferBytes(): Int =
    AudioRecord.getMinBufferSize(MIC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

/**
 * The real microphone behind [MicHub]: an AudioRecord at 16 kHz mono PCM16. The sources are tried in
 * order (the same fallback the wake listener uses), and the caller must already hold RECORD_AUDIO.
 */
class AudioRecordFrameSource(
    /**
     * VOICE_RECOGNITION first: speech recognition (Deepgram) and the offline wake word already use it,
     * and one recorder now serves all of them. MIC and DEFAULT are fallbacks.
     */
    private val sources: IntArray = intArrayOf(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.DEFAULT,
    ),
    /**
     * True while the phone rings: the device's echo canceller and noise suppressor are switched on
     * for this recorder, so "kaat do / bol do …" is heard over the ringtone. Checked on every read.
     */
    private val enhance: () -> Boolean = { false },
) : FrameSource {
    private var recorder: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var enhanced = false

    private fun applyEffects(on: Boolean) {
        val rec = recorder ?: return
        if (on) {
            if (echoCanceler == null && AcousticEchoCanceler.isAvailable()) {
                echoCanceler = runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull()
            }
            if (noiseSuppressor == null && NoiseSuppressor.isAvailable()) {
                noiseSuppressor = runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull()
            }
        } else {
            releaseEffects()
        }
    }

    private fun releaseEffects() {
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        echoCanceler = null
        noiseSuppressor = null
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the caller before a lease is taken
    override fun open(): Boolean {
        val min = minMicBufferBytes()
        if (min <= 0) return false
        val bufferBytes = max(min * 4, MIC_FRAME_BYTES * 10)
        for (source in sources) {
            val rec = try {
                AudioRecord(source, MIC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            } catch (e: Exception) {
                null
            } ?: continue
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                continue
            }
            try {
                rec.startRecording()
            } catch (e: Exception) {
                rec.release()
                continue
            }
            recorder = rec
            return true
        }
        return false
    }

    override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int {
        val want = runCatching { enhance() }.getOrDefault(false)
        if (want != enhanced) {
            applyEffects(want)
            enhanced = want
        }
        return recorder?.read(buf, offset, length) ?: -1
    }

    override fun close() {
        releaseEffects()
        enhanced = false
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
    }
}
