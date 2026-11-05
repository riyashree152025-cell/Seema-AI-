package com.raju.aiassistant.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.raju.aiassistant.domain.model.Personality
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.TextSecondary

/**
 * AI & Brain → "Seema ka andaaz": how she talks (girlfriend, masti, dost, professional, tapori).
 * The choice existed in the settings but had no screen; Tapori also turns the Home reactor red.
 */
@Composable
fun PersonalityPanel(s: UserSettings, viewModel: SettingsViewModel) {
    HudPanel(title = "Seema ka andaaz (Personality)") {
        Personality.entries.forEach { p ->
            HintRadioRow(p.label, p.hint, s.personality == p) { viewModel.setPersonality(p) }
        }
        Text(
            if (s.personality == Personality.TAPORI) {
                "Tapori mode on: Home ka reactor laal ho gaya hai. Wapas pehle jaisa karne ke liye \"Humsafar / Girlfriend\" chuniye."
            } else {
                "Agle jawab se naya andaaz lagu hoga. Phone ke kaam (call, WhatsApp, timer) har mode mein waise hi chalte hain."
            },
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
    }
}
