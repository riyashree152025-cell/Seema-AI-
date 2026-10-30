package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.TextSecondary

/** Settings → Performance & Battery: every switch that trades visuals or convenience for battery and heat. */
@Composable
internal fun PerformanceAndBattery(s: UserSettings, viewModel: SettingsViewModel) {
    val resources by viewModel.resources.collectAsStateWithLifecycle()
    HudPanel(title = "Performance & Battery") {
        ToggleRow("Smart power management (slow down, then stop animations when nobody uses the screen)", s.smartPower, viewModel::setSmartPower)
        ToggleRow("Battery saver visuals (lower frame rate, fewer effects)", s.lowPowerMode, viewModel::setLowPowerMode)
        ToggleRow("Thermal protection (lighter visuals when the phone gets warm)", s.thermalProtection, viewModel::setThermalProtection)
        ToggleRow("Automatic sleep / session timeout (time is set under Voice → Sleep mode)", s.sleepAfterSeconds > 0) { on ->
            viewModel.setSleepAfterSeconds(if (on) 60 else 0)
        }
        ToggleRow("Background automation (morning briefing and weekly report notifications)", s.businessReminders, viewModel::setBusinessReminders)
        ToggleRow("Minimal animation (still picture)", s.reducedMotion, viewModel::setReducedMotion)
        Text(
            "Always listening is chosen under Voice → Voice mode. With smart power on, the phone's own Battery Saver also switches Seema to battery-saver visuals. " +
                "Seema never uses a background service or wake lock to stay alive, and makes no AI request unless you ask (or a reminder you enabled runs).",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
        )

        Text("Resource usage", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = viewModel::refreshResources) { Text(if (resources.isEmpty()) "Show current usage" else "Refresh") }
        resources.forEach { (label, value) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        if (resources.isNotEmpty()) {
            Text(
                "Values come from Android at the moment you tap the button; nothing is estimated and nothing runs in the background to collect them.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
            )
        }
    }
}
