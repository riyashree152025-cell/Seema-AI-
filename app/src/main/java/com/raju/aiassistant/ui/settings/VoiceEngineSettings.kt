package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.raju.aiassistant.domain.model.SttEngine
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.VoiceCatalog
import com.raju.aiassistant.domain.voice.VoiceModelOption
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber

/** Radio row with a one-line hint under the label (used for engine choices). */
@Composable
internal fun HintRadioRow(label: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

/** "Label: current value  [Change]" with a dropdown of options; ★ marks recommended ones. */
@Composable
internal fun OptionPicker(
    label: String,
    current: String,
    options: List<VoiceModelOption>,
    onSelect: (VoiceModelOption) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $current", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { open = true }) { Text("Change") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text((if (o.recommended) "★ " else "") + o.title)
                                Text(o.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        },
                        onClick = { onSelect(o); open = false },
                    )
                }
            }
        }
    }
    options.firstOrNull { it.id == current || it.title == current }?.let {
        Text(it.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** Speech input (listening) engine choice. */
@Composable
internal fun SpeechInputSettings(s: UserSettings, viewModel: SettingsViewModel, deepgramKeySaved: Boolean) {
    Text("Speech input (listening)", style = MaterialTheme.typography.titleMedium)
    SttEngine.entries.forEach { e -> HintRadioRow(e.label, e.hint, s.sttEngine == e) { viewModel.setSttEngine(e) } }
    if (s.sttEngine == SttEngine.DEEPGRAM) {
        val option = VoiceCatalog.sttOption(s.deepgramSttModel)
        OptionPicker(
            "Model",
            option.title,
            VoiceCatalog.deepgramSttModels.map { VoiceModelOption(it.id, it.title, it.note, it.recommended) },
        ) { viewModel.setDeepgramSttModel(it.id) }
        Text(
            if (deepgramKeySaved) "Listens until you pause for about a second, then sends that recording to Deepgram. Free credit: \$200."
            else "Save a Deepgram key above first. Until then the Android recogniser is used.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (deepgramKeySaved) TextSecondary else WarningAmber,
        )
    }
}
