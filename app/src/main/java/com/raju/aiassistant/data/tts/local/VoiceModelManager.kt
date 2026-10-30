package com.raju.aiassistant.data.tts.local

import android.content.Context
import android.net.ConnectivityManager
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.voice.LOCAL_VOICE_CATALOG
import com.raju.aiassistant.domain.voice.LocalVoiceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Install state of one offline voice model. */
sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(val done: Long, val total: Long) : ModelState
    data object Verifying : ModelState
    data object Installing : ModelState
    data class Installed(val version: Int, val updateAvailable: Boolean) : ModelState
    data class Failed(val reason: String) : ModelState
}

/**
 * Downloads, checks and installs the offline voice models (Settings → Voice Engine Manager).
 * Downloads start only when the user taps Download, resume after a broken connection, are checked
 * against the catalog's SHA-256 and unpacked into app-private storage. After that the voice works
 * without internet.
 */
class VoiceModelManager(
    private val context: Context,
    http: OkHttpClient,
    private val scope: CoroutineScope,
    val catalog: List<LocalVoiceModel> = LOCAL_VOICE_CATALOG,
) {
    // Large files: no call timeout, generous read timeout per chunk.
    private val http = http.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val root = File(context.filesDir, "voices")
    private val jobs = mutableMapOf<String, Job>()

    private val _states = MutableStateFlow(catalog.associate { it.id to stateOnDisk(it) })
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    /** Engines with an installed model (ready to use offline). */
    fun installedEngines(): Set<TtsEngine> = catalog.filter { _states.value[it.id] is ModelState.Installed }.map { it.engine }.toSet()

    fun installedModel(engine: TtsEngine): LocalVoiceModel? = catalog.firstOrNull { it.engine == engine && _states.value[it.id] is ModelState.Installed }

    /** Directory that holds the model's files (only meaningful when installed). */
    fun modelDir(model: LocalVoiceModel) = File(File(root, model.id), model.dirName)

    /** True on mobile data or another metered network (the UI warns before a big download). */
    fun onMeteredNetwork(): Boolean = runCatching { context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true }.getOrDefault(false)

    fun download(model: LocalVoiceModel) {
        if (jobs[model.id]?.isActive == true) return
        jobs[model.id] = scope.launch(Dispatchers.IO) {
            val result = runCatching { install(model) }
            set(model, result.fold({ stateOnDisk(model) }, { ModelState.Failed(it.message ?: it.javaClass.simpleName) }))
        }
    }

    fun cancel(model: LocalVoiceModel) {
        jobs.remove(model.id)?.cancel()
        set(model, stateOnDisk(model))
    }

    fun delete(model: LocalVoiceModel) {
        cancel(model)
        File(root, model.id).deleteRecursively()
        partFile(model).delete()
        set(model, ModelState.NotInstalled)
    }

    private suspend fun install(model: LocalVoiceModel) = withContext(Dispatchers.IO) {
        val part = partFile(model)
        part.parentFile?.mkdirs()
        // Resume: ask only for the bytes that are still missing.
        for (attempt in 1..4) {
            ensureActive()
            val have = if (part.isFile) part.length() else 0L
            if (have >= model.sizeBytes) break
            set(model, ModelState.Downloading(have, model.sizeBytes))
            val request = Request.Builder().url(model.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            val ok = runCatching {
                http.newCall(request).execute().use { r ->
                    if (r.code !in 200..299) error("HTTP ${r.code}")
                    val append = have > 0 && r.code == 206
                    val body = r.body ?: error("empty response")
                    java.io.FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = if (append) have else 0L
                        var lastShown = 0L
                        body.byteStream().use { input ->
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (done - lastShown > 512 * 1024) { lastShown = done; set(model, ModelState.Downloading(done, model.sizeBytes)) }
                            }
                        }
                    }
                }
            }.isSuccess
            if (!ok && attempt == 4) error("Download failed — check the internet and tap Download again (it resumes).")
        }
        set(model, ModelState.Verifying)
        val sha = sha256(part)
        if (sha != model.sha256) {
            part.delete()
            error("Checksum mismatch — the download was damaged. Tap Download again.")
        }
        set(model, ModelState.Installing)
        val target = File(root, model.id)
        val tmp = File(root, model.id + ".tmp")
        tmp.deleteRecursively()
        part.inputStream().buffered().use { extractTarBz2(it, tmp) }
        check(File(File(tmp, model.dirName), model.modelFile).isFile) { "Model file missing in the archive" }
        File(tmp, MARKER).writeText("${model.version}\n${model.sha256}\n")
        target.deleteRecursively()
        check(tmp.renameTo(target)) { "Could not install the model" }
        part.delete()
    }

    private fun stateOnDisk(model: LocalVoiceModel): ModelState {
        val marker = File(File(root, model.id), MARKER)
        if (!marker.isFile) return ModelState.NotInstalled
        val lines = runCatching { marker.readLines() }.getOrDefault(emptyList())
        val version = lines.getOrNull(0)?.toIntOrNull() ?: 0
        val ok = File(modelDir(model), model.modelFile).isFile
        if (!ok) return ModelState.Failed("Model files are missing — delete and download again.")
        return ModelState.Installed(version, updateAvailable = version < model.version || lines.getOrNull(1) != model.sha256)
    }

    private fun set(model: LocalVoiceModel, state: ModelState) {
        _states.value = _states.value + (model.id to state)
    }

    private fun partFile(model: LocalVoiceModel) = File(File(context.cacheDir, "voice-downloads"), "${model.id}.part")

    companion object {
        private const val MARKER = "installed.txt"

        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** Unpacks a .tar.bz2 into [dest]. Entries that would land outside [dest] are refused. */
        fun extractTarBz2(input: InputStream, dest: File) {
            dest.mkdirs()
            val base = dest.canonicalPath + File.separator
            TarArchiveInputStream(BZip2CompressorInputStream(input)).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val out = File(dest, entry.name)
                    require(out.canonicalPath.startsWith(base) || out.canonicalPath == dest.canonicalPath) { "Unsafe path in archive: ${entry.name}" }
                    when {
                        entry.isDirectory -> out.mkdirs()
                        entry.isFile -> {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { tar.copyTo(it) }
                        }
                        // Links and devices are skipped.
                    }
                }
            }
        }
    }
}
