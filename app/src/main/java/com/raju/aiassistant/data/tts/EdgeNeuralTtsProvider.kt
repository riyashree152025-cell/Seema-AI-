package com.raju.aiassistant.data.tts

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import com.raju.aiassistant.core.audio.PcmAudio
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.voice.EdgeTts
import com.raju.aiassistant.domain.voice.TtsProvider
import com.raju.aiassistant.domain.voice.VoiceMood
import com.raju.aiassistant.domain.voice.WarmProsody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Microsoft Edge neural voices (Swara for Hindi / Hinglish, Tanishaa for Bengali) through the browser's Read-aloud
 * WebSocket (see [EdgeTts]). No key, no credits: always available while online, so Seema keeps a human voice when
 * Sarvam or Cartesia fails. The MP3 that comes back is decoded on the phone by Android's own MediaCodec.
 */
class EdgeNeuralTtsProvider(
    private val http: OkHttpClient,
    private val isOnline: () -> Boolean,
    /** The mood of the reply being spoken: a little lower pitch when she is upset or whispering. */
    private val mood: () -> VoiceMood? = { null },
) : TtsProvider {
    override val engine = TtsEngine.EDGE
    override val label = "Edge Neural (free)"

    override suspend fun isAvailable() = isOnline()

    override suspend fun synthesize(text: String, languageTag: String, rate: Float): AiResult<PcmAudio> {
        val spoken = WarmProsody.text(text)
        if (spoken.isBlank()) return AiResult.Failure(FailureKind.UNKNOWN, "nothing to say")
        val voice = EdgeTts.voiceFor(languageTag)
        val ssml = EdgeTts.ssml(spoken, voice, EdgeTts.ratePercent(voice, rate), EdgeTts.pitchHz(voice, mood()))
        var result = fetchMp3(ssml)
        // 403: the phone's clock is off for Sec-MS-GEC; learn the server's time and try once more.
        if (result is Fetch.Refused && result.skewSeconds != null) {
            skewSeconds = result.skewSeconds
            result = fetchMp3(ssml)
        }
        return when (result) {
            is Fetch.Audio -> withContext(Dispatchers.Default) { decodeMp3(result.mp3) }
                ?.let { AiResult.Success(WarmProsody.withTail(it)) }
                ?: AiResult.Failure(FailureKind.UNKNOWN, "Edge audio could not be decoded")
            is Fetch.Refused -> AiResult.Failure(FailureKind.SERVER, "Edge voice refused the request (HTTP ${result.code})")
            is Fetch.Failed -> AiResult.Failure(FailureKind.NETWORK, "Edge voice: ${result.reason}")
        }
    }

    private sealed interface Fetch {
        class Audio(val mp3: ByteArray) : Fetch
        class Refused(val code: Int, val skewSeconds: Long?) : Fetch
        class Failed(val reason: String) : Fetch
    }

    /** One sentence: speech.config, then the SSML; MP3 frames collected until "turn.end". */
    private suspend fun fetchMp3(ssml: String): Fetch = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val now = System.currentTimeMillis()
            val connectionId = UUID.randomUUID().toString().replace("-", "")
            val request = Request.Builder()
                .url(EdgeTts.connectUrl(connectionId, now, skewSeconds).replaceFirst("wss://", "https://"))
                .header("User-Agent", EdgeTts.USER_AGENT)
                .header("Origin", EdgeTts.ORIGIN)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val audio = ByteArrayOutputStream()
            fun finish(f: Fetch) { if (cont.isActive) cont.resume(f) }
            val socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(EdgeTts.configMessage(now))
                    webSocket.send(EdgeTts.ssmlMessage(connectionId, now, ssml))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (EdgeTts.pathOf(text) == "turn.end") {
                        webSocket.close(1000, null)
                        finish(if (audio.size() > 0) Fetch.Audio(audio.toByteArray()) else Fetch.Failed("no audio received"))
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    EdgeTts.audioOf(bytes.toByteArray())?.let { audio.write(it) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code
                    finish(
                        if (code == 401 || code == 403) Fetch.Refused(code, EdgeTts.skewSeconds(response.header("Date"), System.currentTimeMillis()))
                        else Fetch.Failed(code?.let { "HTTP $it" } ?: t.javaClass.simpleName),
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finish(if (audio.size() > 0) Fetch.Audio(audio.toByteArray()) else Fetch.Failed("closed ($code)"))
                }
            })
            cont.invokeOnCancellation { socket.cancel() }
        }
    } ?: Fetch.Failed("timed out")

    companion object {
        private const val TIMEOUT_MS = 12_000L
        /** Server time minus phone time, learned from a 403 (kept for the app's lifetime). */
        @Volatile private var skewSeconds = 0L

        /** MP3 → 16-bit PCM with Android's own decoder; null when it cannot be decoded. */
        fun decodeMp3(mp3: ByteArray): PcmAudio? = runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(object : MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (position >= mp3.size) return -1
                    val n = minOf(size.toLong(), mp3.size - position).toInt()
                    System.arraycopy(mp3, position.toInt(), buffer, offset, n)
                    return n
                }
                override fun getSize(): Long = mp3.size.toLong()
                override fun close() = Unit
            })
            try {
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: return@runCatching null
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                val pcm = ByteArrayOutputStream()
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()
                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false
                    while (!outputDone) {
                        if (!inputDone) {
                            val inIndex = codec.dequeueInputBuffer(10_000)
                            if (inIndex >= 0) {
                                val buffer = codec.getInputBuffer(inIndex)!!
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                        when {
                            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            outIndex >= 0 -> {
                                val out = codec.getOutputBuffer(outIndex)!!
                                val chunk = ByteArray(info.size)
                                out.position(info.offset)
                                out.get(chunk)
                                pcm.write(chunk)
                                codec.releaseOutputBuffer(outIndex, false)
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            }
                        }
                    }
                } finally {
                    runCatching { codec.stop() }
                    codec.release()
                }
                pcm.toByteArray().takeIf { it.isNotEmpty() }?.let { PcmAudio(sampleRate, channels, it) }
            } finally {
                extractor.release()
            }
        }.getOrNull()
    }
}
