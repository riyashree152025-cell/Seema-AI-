package com.raju.aiassistant.data.voice

import com.raju.aiassistant.domain.voice.LiveTranscript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

/**
 * One live Deepgram transcription (WebSocket, wss://api.deepgram.com/v1/listen): the microphone frames are
 * sent while the user talks, so the words are ready the moment the turn ends — no upload and wait after.
 * The key goes only in the Authorization header over TLS and is never logged. Any failure returns null
 * from [finish] and the caller falls back to the one-shot upload, so nothing is lost.
 */
class DeepgramLiveSession(
    private val http: OkHttpClient,
    /** The words on screen while the user talks (confirmed + newest guess). */
    private val onText: (String) -> Unit = {},
) {
    private val transcript = LiveTranscript()
    private val closed = CompletableDeferred<Boolean>()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var failed = false

    fun open(key: String, model: String, language: String, keyterms: List<String>) {
        val url = HttpUrl.Builder().scheme("https").host("api.deepgram.com").addPathSegments("v1/listen")
            .addQueryParameter("model", model)
            .addQueryParameter("language", language)
            .addQueryParameter("encoding", "linear16")
            .addQueryParameter("sample_rate", "16000")
            .addQueryParameter("channels", "1")
            .addQueryParameter("interim_results", "true")
            .addQueryParameter("smart_format", "true")
            .addQueryParameter("punctuate", "true")
            .apply {
                // Key terms only where Deepgram documents them (English and multi): a refused parameter
                // would fail the live link on every turn and each answer would wait for the upload instead.
                if (model.startsWith("nova-3") && (language == "multi" || language.startsWith("en"))) {
                    keyterms.filter { it.isNotBlank() }.distinct().take(MAX_KEYTERMS).forEach { addQueryParameter("keyterm", it) }
                }
            }
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Token $key").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val changed = synchronized(transcript) { transcript.onMessage(text) }
                if (changed) onText(synchronized(transcript) { transcript.shown() })
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failed = true
                closed.complete(false)
            }
        })
    }

    /** One 20 ms frame of 16 kHz mono 16-bit audio (copied: the caller reuses its buffer). */
    fun send(frame: ByteArray) {
        if (failed) return
        socket?.send(frame.toByteString())
    }

    /**
     * The turn ended: asks Deepgram for the last words and waits up to [timeoutMs] for the socket to close.
     * Null when the live link failed or heard nothing (then the recording is uploaded instead).
     */
    suspend fun finish(timeoutMs: Long = FINISH_TIMEOUT_MS): Result? {
        val ws = socket ?: return null
        if (!failed) {
            ws.send("""{"type":"Finalize"}""")
            ws.send("""{"type":"CloseStream"}""")
        }
        val clean = withTimeoutOrNull(timeoutMs) { closed.await() } == true
        if (!clean) ws.cancel()
        if (!clean || failed) return null
        val (text, confidence) = synchronized(transcript) { transcript.text() to transcript.confidence() }
        return if (text.isBlank()) null else Result(text, confidence)
    }

    fun cancel() {
        socket?.cancel()
        socket = null
    }

    data class Result(val text: String, val confidence: Double)

    private companion object {
        const val MAX_KEYTERMS = 40
        const val FINISH_TIMEOUT_MS = 2_500L
    }
}

