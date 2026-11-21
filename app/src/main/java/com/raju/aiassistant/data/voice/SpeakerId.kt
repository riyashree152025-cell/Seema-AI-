package com.raju.aiassistant.data.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.raju.aiassistant.domain.voice.VoiceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Speaker check on the phone (sherpa-onnx CAM++ model, 28 MB, downloaded once on "meri awaaz pehchano", SHA-256 checked).
 * Turns 16 kHz speech into a voice print; the owner's print is kept in app-private preferences (numbers only).
 */
class SpeakerId(private val context: Context, private val http: OkHttpClient) : com.raju.aiassistant.domain.voice.VoiceIdPort {
    private val dir = File(context.filesDir, "speaker")
    private val model = File(dir, MODEL_FILE)
    private val prefs = context.getSharedPreferences("voice_id", Context.MODE_PRIVATE)
    @Volatile private var extractor: SpeakerEmbeddingExtractor? = null

    fun enrolled(): Boolean = prefs.contains(KEY)

    override fun disable() { prefs.edit().remove(KEY).apply() }

    /** Downloads and checks the model when it is not there yet. */
    override suspend fun ensureModel(): Boolean = withContext(Dispatchers.IO) {
        if (model.isFile && model.length() == MODEL_SIZE) return@withContext true
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$MODEL_FILE.part")
            http.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                tmp.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
            }
            val sha = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
            if (sha != MODEL_SHA256) { tmp.delete(); error("checksum") }
            check(tmp.renameTo(model))
            true
        }.getOrDefault(false)
    }

    private fun extractor(): SpeakerEmbeddingExtractor? = extractor ?: runCatching {
        if (!model.isFile || !SherpaNative.available) return null
        SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(model = model.absolutePath, numThreads = 1, debug = false, provider = "cpu"))
    }.getOrNull()?.also { extractor = it }

    /** The voice print of 16 kHz mono PCM16, or null (too short, model missing). */
    fun embed(pcm: ByteArray): FloatArray? {
        if (pcm.size / 2 < VoiceId.MIN_SAMPLES) return null
        val ex = extractor() ?: return null
        val floats = FloatArray(pcm.size / 2) { i -> ((pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)).toShort() / 32768f }
        return runCatching {
            val stream = ex.createStream()
            stream.acceptWaveform(floats, 16_000)
            stream.inputFinished()
            val v = if (ex.isReady(stream)) ex.compute(stream) else null
            stream.release()
            v
        }.getOrNull()
    }

    /** true = his voice, false = not, null = cannot tell (not enrolled / too short): never blocks then. */
    override fun matchesOwner(pcm: ByteArray): Boolean? {
        val owner = VoiceId.decode(prefs.getString(KEY, null)) ?: return null
        return VoiceId.isOwner(owner, embed(pcm))
    }

    /** Records [seconds] of his voice now (the microphone must be free) and keeps its voice print. */
    @SuppressLint("MissingPermission")
    override suspend fun enroll(): Boolean = enrollFor(VoiceId.ENROLL_SECONDS)

    private suspend fun enrollFor(seconds: Int): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@withContext false
        val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 32_000))
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext false }
        val pcm = ByteArray(16_000 * 2 * seconds)
        try {
            rec.startRecording()
            var off = 0
            while (off < pcm.size) {
                val n = rec.read(pcm, off, minOf(4096, pcm.size - off))
                if (n <= 0) break
                off += n
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        val print = embed(pcm) ?: return@withContext false
        prefs.edit().putString(KEY, VoiceId.encode(print)).apply()
        true
    }

    companion object {
        private const val KEY = "owner_print"
        const val MODEL_FILE = "3dspeaker_campplus_16k.onnx"
        const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"
        const val MODEL_SIZE = 28_281_139L
        const val MODEL_SHA256 = "e6af4d4c444f3dab01d991bb9b7d5af98eb979e329f6c8880a19d5553561ed25"
    }
}
