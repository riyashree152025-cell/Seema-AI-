package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.data.tts.local.ModelState
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.model.VoiceMode
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber

private val SLEEP_CHOICES = listOf(30 to "30 sec", 60 to "1 min", 120 to "2 min", 300 to "5 min", 0 to "Never")

/**
 * Voice & Speech → "Sunna aur jagna": how Seema listens, when she sleeps and what wakes her
 * (the offline "Hey Seema" detector). These settings existed but had no screen in this design.
 */
@Composable
fun ListeningPanel(s: UserSettings, viewModel: SettingsViewModel) {
    val states by viewModel.wakeModelState.collectAsStateWithLifecycle()
    val m = viewModel.wakeModel
    val st = states[m.id] ?: ModelState.NotInstalled
    HudPanel(title = "Sunna aur jagna (Listening & wake word)") {
        Text("Mic kab sune", style = MaterialTheme.typography.titleMedium)
        VoiceMode.entries.forEach { mode -> HintRadioRow(mode.label, mode.hint, s.voiceMode == mode) { viewModel.setVoiceMode(mode) } }

        Text("Chup rehne par so jaaye (sleep)", style = MaterialTheme.typography.titleMedium)
        ChipRow(SLEEP_CHOICES.map { it.first.toString() }, s.sleepAfterSeconds.toString(), { v -> SLEEP_CHOICES.first { it.first.toString() == v }.second }) {
            viewModel.setSleepAfterSeconds(it.toInt())
        }

        ToggleRow("Sirf naam se jaage (\"Hey Seema\" / \"Seema\")", s.wakeWord, viewModel::setWakeWord)

        ToggleRow("Shared Mic Engine (Beta)", s.micHub, viewModel::setMicHub)
        Text(
            "Reduces microphone conflicts and wake-up lag using centralized MicHub",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )

        Text("Offline \"Hey Seema\" (${m.sizeBytes / 1_000_000} MB, ${m.license})", style = MaterialTheme.typography.titleMedium)
        Text(
            "Phone par hi \"Hey Seema\" pehchanta hai — internet ya Deepgram key nahi chahiye, kuch record ya bheja nahi jata. " +
                "Iske bina, Deepgram key na ho to Seema har awaaz (TV, baatcheet) par jaag jaati hai. Aapki awaaz par accuracy abhi test nahi hui: na jaage to thoda dheere boliye ya reactor tap kijiye.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
        when (st) {
            is ModelState.Downloading -> {
                Text("Download ho raha hai: ${st.done / 1_000_000} / ${st.total / 1_000_000} MB", color = TextSecondary)
                LinearProgressIndicator(progress = { (st.done.toFloat() / st.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = viewModel::cancelWakeModel) { Text("Cancel") }
            }
            ModelState.Verifying, ModelState.Installing -> Text("Jaanch aur install ho raha hai…", color = TextSecondary)
            is ModelState.Installed -> {
                Text(
                    if (s.wakeWord) "Installed · \"Hey Seema\" se jaagegi" else "Installed · upar \"Sirf naam se jaage\" ON karein",
                    color = if (s.wakeWord) SuccessGreen else WarningAmber,
                )
                OutlinedButton(onClick = viewModel::deleteWakeModel) { Text("Delete", color = ErrorRed) }
            }
            is ModelState.Failed -> {
                Text("Download fail: ${st.reason}", color = ErrorRed)
                Button(onClick = viewModel::downloadWakeModel) { Text("Phir se try karein") }
            }
            ModelState.NotInstalled -> Button(onClick = viewModel::downloadWakeModel) { Text("Download ${m.sizeBytes / 1_000_000} MB") }
        }

        ToggleRow("Beech mein bolkar rokna (barge-in) — headphone aur Deepgram key ke saath hi chalta hai", s.bargeIn, viewModel::setBargeIn)
        Text(
            "Screen lock par sunne ke liye: phone Settings → Apps → Seema AI → Battery → \"Unrestricted\" karein. Mic chalu rehne par notification aur green dot dikhega.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
    }
}
