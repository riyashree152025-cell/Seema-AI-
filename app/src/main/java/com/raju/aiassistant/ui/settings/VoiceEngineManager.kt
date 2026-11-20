package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.MAX_RECENT_FAILURES
import com.raju.aiassistant.domain.voice.voiceEngineOrder
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary

/**
 * Settings → Voice: Seema's three streamed voices — Sarvam AI, Cartesia and Edge Neural (free, no key) — with
 * Automatic / Manual selection, the order used right now and how each voice has been doing.
 */
@Composable
fun VoiceEngineManager(s: UserSettings, viewModel: SettingsViewModel) {
    val stats by viewModel.voiceEngineStats.collectAsStateWithLifecycle()

    HudPanel(title = "Seema's voice") {
        Text(
            "Three streamed voices, nothing stored on the phone. When Sarvam or Cartesia runs out of credits or fails, Edge Neural (free, no key) takes over at once — she never goes silent while online.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
        )
        HintRadioRow("Automatic (recommended)", "Sarvam AI → Cartesia → Edge Neural; a voice that keeps failing moves back.", s.voiceEngineAuto) {
            viewModel.setVoiceEngineAuto(true)
        }
        HintRadioRow("Manual", "The voice chosen below first; the others take over if it fails.", !s.voiceEngineAuto) {
            viewModel.setVoiceEngineAuto(false)
        }
        if (!s.voiceEngineAuto) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Voice:", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                listOf(TtsEngine.EDGE, TtsEngine.SARVAM, TtsEngine.CARTESIA).forEach { engine ->
                    HintRadioRow(engine.label, engine.hint, s.ttsEngine == engine) { viewModel.setActiveVoiceEngine(engine) }
                }
            }
        }
        Text(
            "Order right now: " + voiceEngineOrder(s.voiceEngineAuto, s.ttsEngine, stats).joinToString(" → ") { it.label },
            style = MaterialTheme.typography.bodyMedium,
        )
        TtsEngine.entries.forEach { engine ->
            val st = stats[engine] ?: return@forEach
            val failing = st.recentFailures >= MAX_RECENT_FAILURES
            Text(
                "${engine.label}: ${st.successes} sentences" + (st.firstAudioMs?.let { " · about $it ms each" } ?: "") +
                    (if (st.failures > 0) " · ${st.failures} failures" else "") +
                    (st.lastError?.takeIf { failing }?.let { " · now: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (failing) ErrorRed else SuccessGreen,
            )
        }
        TextButton(onClick = viewModel::resetVoiceStats) { Text("Reset measurements") }
    }
}
