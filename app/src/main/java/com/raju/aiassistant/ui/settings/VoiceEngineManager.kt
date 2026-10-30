package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.data.tts.local.ModelState
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.LocalVoiceModel
import com.raju.aiassistant.domain.voice.voiceEngineOrder
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber

private fun mb(bytes: Long) = "%.0f MB".format(bytes / 1_000_000.0)

/**
 * Settings → Voice Engine Manager: offline voices (Piper, Kokoro via sherpa-onnx) with download,
 * update, delete, preview, Automatic / Manual selection and speed measured on this phone.
 */
@Composable
fun VoiceEngineManager(s: UserSettings, viewModel: SettingsViewModel) {
    val states by viewModel.voiceModelStates.collectAsStateWithLifecycle()
    val stats by viewModel.voiceEngineStats.collectAsStateWithLifecycle()
    val bench by viewModel.benchmark.collectAsStateWithLifecycle()
    val benchRunning by viewModel.benchmarkRunning.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<LocalVoiceModel?>(null) }

    HudPanel(title = "Voice Engine Manager (offline voices)") {
        Text(
            "Seema's voice can be made entirely on this phone: download a voice model once, then every reply is spoken offline — no voice API, no cost per reply. The AI answers still come from Gemini/Groq/Cerebras over the internet.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
        )
        if (!viewModel.offlineRuntimeAvailable) {
            Text("The offline voice runtime cannot run on this phone's processor; Android TTS is used instead.", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
        }

        Text("Engine selection", style = MaterialTheme.typography.titleMedium)
        HintRadioRow("Automatic (recommended)", "Kokoro when it is fast enough on this phone, otherwise Piper; Android TTS if neither works. Once a model is downloaded only on-phone engines are used — no voice API costs. Until then the voice chosen under Voice is used.", s.voiceEngineAuto) {
            viewModel.setVoiceEngineAuto(true)
        }
        HintRadioRow("Manual", "Uses the engine chosen under Voice → Speech output first; offline engines and Android TTS are the fallback.", !s.voiceEngineAuto) {
            viewModel.setVoiceEngineAuto(false)
        }
        val installed = viewModel.voiceModelCatalog.filter { states[it.id] is ModelState.Installed }.map { it.engine }.toSet()
        Text(
            "Order right now: " + voiceEngineOrder(s.voiceEngineAuto, s.ttsEngine, installed, stats).joinToString(" → ") { it.label.substringBefore(" —").substringBefore(" (") },
            style = MaterialTheme.typography.bodyMedium,
        )

        viewModel.voiceModelCatalog.forEach { m ->
            val st = states[m.id] ?: ModelState.NotInstalled
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleSmall)
                Text("${mb(m.sizeBytes)} · ${m.language} · license ${m.license}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                when (st) {
                    ModelState.NotInstalled -> Text("Not downloaded", color = TextSecondary)
                    is ModelState.Downloading -> {
                        Text("Downloading ${mb(st.done)} / ${mb(st.total)}", color = TextSecondary)
                        LinearProgressIndicator(progress = { (st.done.toFloat() / st.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                    ModelState.Verifying -> Text("Checking the download (SHA-256)…", color = TextSecondary)
                    ModelState.Installing -> Text("Installing…", color = TextSecondary)
                    is ModelState.Installed -> Text(if (st.updateAvailable) "Installed · update available" else "Installed · works offline", color = if (st.updateAvailable) WarningAmber else SuccessGreen)
                    is ModelState.Failed -> Text("Failed: ${st.reason}", color = ErrorRed)
                }
                stats[m.engine]?.let { e ->
                    Text(
                        "Measured: " + listOfNotNull(
                            e.rtf?.let { "speed ${"%.2f".format(it)}× real time" },
                            e.firstAudioMs?.let { "≈ $it ms per sentence" },
                            "${e.successes} ok / ${e.failures} failed",
                        ).joinToString(" · ") + (e.lastError?.let { " · last error: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                    )
                }
                if (m.engine == TtsEngine.KOKORO && st is ModelState.Installed) {
                    ChipRow(m.speakers.map { it.id.toString() }, s.kokoroSpeaker.toString(), { id -> m.speakers.first { it.id.toString() == id }.name }) {
                        viewModel.setKokoroSpeaker(it.toInt())
                    }
                    m.speakers.firstOrNull { it.id == s.kokoroSpeaker }?.let { Text(it.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (st) {
                        ModelState.NotInstalled, is ModelState.Failed -> Button(onClick = {
                            if (viewModel.onMeteredNetwork()) confirm = m else viewModel.downloadVoiceModel(m)
                        }) { Text("Download ${mb(m.sizeBytes)}") }
                        is ModelState.Downloading, ModelState.Verifying, ModelState.Installing ->
                            OutlinedButton(onClick = { viewModel.cancelVoiceModel(m) }) { Text("Cancel") }
                        is ModelState.Installed -> {
                            Button(onClick = { viewModel.previewEngine(m.engine) }) { Text("Preview") }
                            if (st.updateAvailable) OutlinedButton(onClick = { viewModel.downloadVoiceModel(m) }) { Text("Update") }
                            OutlinedButton(onClick = { viewModel.deleteVoiceModel(m) }) { Text("Delete", color = ErrorRed) }
                        }
                    }
                }
            }
        }

        Text("Speed on this phone", style = MaterialTheme.typography.titleMedium)
        Text(
            "Measures each downloaded engine for real: first sentence from cold and warm, speed (real-time factor), CPU, app memory and battery current. Takes up to a minute; keep the screen on.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::runVoiceBenchmark, enabled = !benchRunning && installed.isNotEmpty()) { Text(if (benchRunning) "Measuring…" else "Test speed on this phone") }
            TextButton(onClick = viewModel::resetVoiceStats) { Text("Reset measurements") }
        }
        if (benchRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
        bench?.forEach { b ->
            Text(
                if (b.error != null) "${b.title}: failed — ${b.error}"
                else "${b.title}: first sentence ${b.coldFirstSentenceMs} ms cold / ${b.warmFirstSentenceMs} ms warm · speed ${"%.2f".format(b.rtf ?: 0.0)}× real time · CPU ${b.cpuPercent ?: "?"}% · memory ${b.memoryMb ?: "?"} MB" +
                    (b.batteryMa?.let { " · battery ≈ $it mA" } ?: " · battery current not reported by this phone"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    confirm?.let { m ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Download ${mb(m.sizeBytes)} on mobile data?") },
            text = { Text("You are not on Wi-Fi. The model is downloaded once and then works offline.") },
            confirmButton = { TextButton(onClick = { confirm = null; viewModel.downloadVoiceModel(m) }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
