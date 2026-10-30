package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.domain.model.CoreAppearance
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.components.hudFrame
import com.raju.aiassistant.ui.home.visuals.ArcReactor
import com.raju.aiassistant.ui.home.visuals.HoloCore
import com.raju.aiassistant.ui.home.visuals.holoCoreStyleFor
import com.raju.aiassistant.ui.home.visuals.reactorStyleFor
import com.raju.aiassistant.ui.home.visuals.rememberAnimationTime
import com.raju.aiassistant.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.sin

/** The preview cycles through these, so each card shows how the design reacts to Seema's states. */
private val PREVIEW_STATES = listOf(AssistantState.Idle, AssistantState.Listening, AssistantState.Thinking, AssistantState.Speaking, AssistantState.Error)

/**
 * Settings → AI core appearance: a card per design with a small live preview, then Apply.
 * Previews run at 12 fps only while Settings is on screen; Home runs only the applied design.
 */
@Composable
internal fun CoreAppearanceSettings(s: UserSettings, autoResolvesTo: CoreAppearance, viewModel: SettingsViewModel) {
    var pending by rememberSaveable(s.coreAppearance) { mutableStateOf(s.coreAppearance) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val time = rememberAnimationTime(running = lifecycle.isAtLeast(Lifecycle.State.STARTED) && !s.reducedMotion, maxFps = 12)
    val previewState = { PREVIEW_STATES[((time.floatValue / 3f).toInt()) % PREVIEW_STATES.size] }

    HudPanel(title = "AI CORE APPEARANCE") {
        Text("Choose how Seema's core looks on Home. Both react to the same states: ready, listening, thinking, speaking, working and error.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        CoreAppearance.entries.forEach { option ->
            val shows = if (option == CoreAppearance.AUTO) autoResolvesTo else option
            val st = previewState()
            val style = if (shows == CoreAppearance.MODERN) holoCoreStyleFor(st) else reactorStyleFor(st)
            val accent = Color(style.primaryArgb)
            Row(
                Modifier
                    .fillMaxWidth()
                    .hudFrame(if (pending == option) accent else TextSecondary, cut = 10.dp, fillAlpha = if (pending == option) 0.12f else 0.04f)
                    .clickable { pending = option }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    val speech = { if (st == AssistantState.Speaking) abs(sin(time.floatValue * 9f)) * 0.8f else 0f }
                    if (shows == CoreAppearance.MODERN) HoloCore(style, 10, false, { time.floatValue }, speech, Modifier.size(104.dp))
                    else ArcReactor(style, 10, false, { time.floatValue }, speech, Modifier.size(104.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pending == option, onClick = { pending = option })
                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(option.hint, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    if (option == CoreAppearance.AUTO) Text("On this phone: ${autoResolvesTo.label}", style = MaterialTheme.typography.bodySmall, color = accent)
                    Text("Preview: ${st.label}", style = MaterialTheme.typography.labelSmall, color = accent)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.setCoreAppearance(pending) }, enabled = pending != s.coreAppearance) { Text("Apply") }
            OutlinedButton(onClick = { pending = CoreAppearance.LEGACY; viewModel.setCoreAppearance(CoreAppearance.LEGACY) }, enabled = s.coreAppearance != CoreAppearance.LEGACY) {
                Text("Reset to default")
            }
        }
        Text("In use now: ${s.coreAppearance.label}. Your choice is saved and kept after restarting the app.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
