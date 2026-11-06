package com.raju.aiassistant.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    /** In a hands-free conversation the voice goes through the call path, so the echo canceller can remove it. */
    private val callAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val mediaFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(mediaAttributes).build()
    private val callFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(callAttributes).build()
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

    /**
     * Her voice on the loudspeaker unless earphones or Bluetooth are connected. A phone left in
     * call mode (after a call placed on speaker, or a call app) played it from the earpiece,
     * where it could only be heard with the phone at the ear.
     */
    private fun preferLoudspeaker(track: AudioTrack) {
        runCatching {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val external = outputs.any { it.type in EXTERNAL_OUTPUTS }
            if (external) return
            outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { track.setPreferredDevice(it) }
        }
    }

    override suspend fun play(audio: PcmAudio, onLevel: (Float) -> Unit): Unit = withContext(Dispatchers.IO) {
        val channelMask = if (audio.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(audio.sampleRate)
            .setChannelMask(channelMask)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(audio.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(if (callStyle()) callAttributes else mediaAttributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (!callStyle()) preferLoudspeaker(track)
        val bytesPerFrame = 2 * audio.channels
        val chunk = (audio.sampleRate / 20) * bytesPerFrame // ~50 ms
        try {
            current = track to audio
            track.play()
            var offset = 0
            var stalls = 0
            while (offset < audio.data.size) {
                coroutineContext.ensureActive()
                val len = minOf(chunk, audio.data.size - offset)
                onLevel(pcmLevel(audio.data, offset, len))
                val written = track.write(audio.data, offset, len)
                if (written < 0) throw IllegalStateException("audio output error ($written)")
                if (written == 0 && ++stalls > 50) throw IllegalStateException("audio output is not accepting data")
                offset += written
            }
            // Wait for the buffered tail to finish playing — but never longer than the audio itself
            // plus a second (a stuck output must not leave Seema "speaking" forever).
            val totalFrames = audio.frameCount
            val deadline = System.nanoTime() + (totalFrames * 1_000_000_000L / audio.sampleRate) + 1_000_000_000L
            while (track.playbackHeadPosition < totalFrames && track.playState == AudioTrack.PLAYSTATE_PLAYING && System.nanoTime() < deadline) {
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

    private companion object {
        val EXTERNAL_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        )
    }
}
