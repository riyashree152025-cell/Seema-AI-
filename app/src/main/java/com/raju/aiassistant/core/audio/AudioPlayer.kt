package com.raju.aiassistant.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.raju.aiassistant.domain.voice.PcmSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Plays 16-bit PCM through AudioTrack and reports the loudness of what is playing, so the
 * reactor can pulse with the voice. Cancelling the coroutine stops playback immediately.
 */
class AudioPlayer(context: Context, private val callStyle: () -> Boolean = { false }) : PcmSink {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    // Media usage: follows the phone's main (media) volume. "Assistant" usage played softer on a
    // Samsung phone.
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    /** In a hands-free conversation the voice goes through the call path, so the echo canceller can remove it. */
    private val callAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    /**
     * Another app took the audio for good (music, a video): she stops talking. A short, temporary loss is
     * ignored on purpose: her own microphone asks for temporary focus while listening, and a phone call
     * is already handled by the engine (it pauses her).
     */
    @Volatile private var focusLostAt = 0L
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS) focusLostAt = android.os.SystemClock.elapsedRealtime()
    }

    /**
     * A reply is often spoken in two parts (first sentence, then the rest), each asking for focus again.
     * The loss stays in force for a few seconds, so the rest of the same reply is not spoken over the
     * music or video the user just started; a new question later is answered normally.
     */
    private val focusLost: Boolean
        get() = focusLostAt != 0L && android.os.SystemClock.elapsedRealtime() - focusLostAt < FOCUS_LOSS_HOLD_MS
    private val mediaFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(mediaAttributes).setOnAudioFocusChangeListener(focusListener).build()
    private val callFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(callAttributes).setOnAudioFocusChangeListener(focusListener).build()
    @Volatile private var focus: AudioFocusRequest = mediaFocus

    /** What is playing right now, for [levelNow]. */
    @Volatile private var current: Pair<AudioTrack, PcmAudio>? = null

    override fun requestFocus() {
        focus = if (callStyle()) callFocus else mediaFocus
        audioManager.requestAudioFocus(focus)
    }

    override fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focus)
    }

    /**
     * 0..1 loudness of the samples leaving the speaker at this moment (from the track's playback
     * head, so it is exact to ~20 ms). Used to tell the assistant's own echo from the user's voice.
     */
    fun levelNow(): Float {
        val (track, audio) = current ?: return 0f
        val head = runCatching { track.playbackHeadPosition }.getOrNull() ?: return 0f
        val bytesPerFrame = 2 * audio.channels
        val start = head * bytesPerFrame
        if (start >= audio.data.size) return 0f
        return pcmLevel(audio.data, start, (audio.sampleRate / 50) * bytesPerFrame)
    }

    override suspend fun play(audio: PcmAudio, onLevel: (Float) -> Unit): Unit = withContext(Dispatchers.IO) {
        // Quiet voices are raised to near full scale (never clipped).
        val pcm = louder(audio)
        val channelMask = if (pcm.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(pcm.sampleRate)
            .setChannelMask(channelMask)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(if (callStyle()) callAttributes else mediaAttributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val bytesPerFrame = 2 * pcm.channels
        val chunk = (pcm.sampleRate / 20) * bytesPerFrame // ~50 ms
        try {
            current = track to pcm
            track.play()
            var offset = 0
            var stalls = 0
            while (offset < pcm.data.size && !focusLost) {
                coroutineContext.ensureActive()
                val len = minOf(chunk, pcm.data.size - offset)
                onLevel(pcmLevel(pcm.data, offset, len))
                val written = track.write(pcm.data, offset, len)
                if (written < 0) throw IllegalStateException("pcm output error ($written)")
                if (written == 0 && ++stalls > 50) throw IllegalStateException("pcm output is not accepting data")
                offset += written
            }
            // Wait for the buffered tail to finish playing — but never longer than the pcm itself
            // plus a second (a stuck output must not leave Seema "speaking" forever).
            val totalFrames = pcm.frameCount
            val deadline = System.nanoTime() + (totalFrames * 1_000_000_000L / pcm.sampleRate) + 1_000_000_000L
            while (!focusLost && track.playbackHeadPosition < totalFrames && track.playState == AudioTrack.PLAYSTATE_PLAYING && System.nanoTime() < deadline) {
                coroutineContext.ensureActive()
                delay(20)
            }
        } finally {
            current = null
            onLevel(0f)
            runCatching { track.pause(); track.flush(); track.stop() }
            track.release()
        }
    }
}

/** How long another app's taking of the audio keeps her quiet (the rest of the reply). */
private const val FOCUS_LOSS_HOLD_MS = 6_000L
