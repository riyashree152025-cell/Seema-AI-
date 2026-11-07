package com.raju.aiassistant.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max

/** Android's minimum AudioRecord buffer for Seema's format; the recorder itself gets 4x this. */
fun minMicBufferBytes(): Int =
    AudioRecord.getMinBufferSize(MIC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

/**
 * The real microphone behind [MicHub]: an AudioRecord at 16 kHz mono PCM16. The sources are tried in
 * order (the same fallback the wake listener uses), and the caller must already hold RECORD_AUDIO.
 */
class AudioRecordFrameSource(
    private val sources: IntArray = intArrayOf(
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.DEFAULT,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
    ),
) : FrameSource {
    private var recorder: AudioRecord? = null

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

    override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int =
        recorder?.read(buf, offset, length) ?: -1

    override fun close() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
    }
}
