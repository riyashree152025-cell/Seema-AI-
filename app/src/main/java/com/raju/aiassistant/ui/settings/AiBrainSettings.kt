package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.ai.ProviderStatus
import com.raju.aiassistant.domain.ai.providerStatus
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.components.StatusChip
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.HoloCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextPrimary
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber
import java.text.DateFormat
import java.util.Date

fun ProviderStatus.color(): Color = when (this) {
    ProviderStatus.CONNECTED -> SuccessGreen
    ProviderStatus.TESTING -> HoloCyan
    ProviderStatus.KEY_SAVED, ProviderStatus.NOT_CONFIGURED, ProviderStatus.RATE_LIMITED, ProviderStatus.QUOTA_EXCEEDED -> WarningAmber
    else -> ErrorRed
}

private fun time(at: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(at))

/** Label + value on one line, readable at a glance and as one sentence for screen readers. */
@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(132.dp), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

/** Status chip of one provider, from what is really known (saved key, running test, last real test). */
@Composable
fun ProviderStatusChip(status: ProviderStatus) {
    StatusChip(label = status.label, color = status.color(), modifier = Modifier.semantics { contentDescription = "Status: ${status.label}. ${status.help}" })
}

/**
 * Top of Settings → AI & Brain: the provider and model that are really used, the key and
 * connection state, the last test and error, and a real test through the chat path.
 */
@Composable
fun ActiveAiPanel(s: UserSettings, keySaved: (ProviderId) -> Boolean, testing: (ProviderId) -> Boolean, viewModel: SettingsViewModel) {
    val route by viewModel.lastRoute.collectAsStateWithLifecycle()
    val test by viewModel.activeTest.collectAsStateWithLifecycle()
    val running by viewModel.activeTesting.collectAsStateWithLifecycle()
    val p = s.defaultProvider
    val lastTest = s.lastTests[p]
    val status = providerStatus(keySaved(p), testing(p), lastTest)

    HudPanel(title = "Active AI") {
        InfoRow("Provider", p.displayName)
        InfoRow("Model", s.modelFor(p) ?: "auto — the best model the key offers is picked on first use")
        InfoRow("API key", if (keySaved(p)) "Saved (encrypted on this phone)" else "Not saved", if (keySaved(p)) SuccessGreen else WarningAmber)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connection", Modifier.width(132.dp), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            ProviderStatusChip(status)
        }
        InfoRow("Last test", lastTest?.let { time(it.timestamp) } ?: "never")
        if (lastTest != null && !lastTest.success) InfoRow("Last error", lastTest.message, ErrorRed)
        route?.let { r ->
            val answered = r.answeredBy
            InfoRow(
                "Last reply",
                when {
                    answered == null -> "${r.requested.shortName} failed — ${r.error ?: "no answer"}"
                    r.usedFallback -> "answered by ${answered.shortName} · ${r.model} (fallback: ${r.requested.shortName} failed)"
                    else -> "${answered.shortName} · ${r.model}"
                } + " · " + time(r.at),
                when {
                    answered == null -> ErrorRed
                    r.usedFallback -> WarningAmber
                    else -> TextPrimary
                },
            )
        }

        Button(onClick = viewModel::testActiveAi, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) "TESTING ACTIVE AI…" else "TEST ACTIVE AI")
        }
        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
        test?.let { t ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    t.ok && !t.usedFallback -> Text("✓ ${t.answeredBy?.shortName} · ${t.model} answered in ${t.elapsedMs} ms: \"${t.reply}\"", color = SuccessGreen, style = MaterialTheme.typography.bodyMedium)
                    t.ok -> {
                        Text("⚠ ${t.requested.shortName} failed: ${t.fallbackReason}", color = WarningAmber, style = MaterialTheme.typography.bodyMedium)
                        Text("Replies currently come from ${t.answeredBy?.shortName} · ${t.model} (automatic fallback), in ${t.elapsedMs} ms.", color = WarningAmber, style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Text("✗ No AI answered: ${t.error}", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            "Test active AI sends one tiny message through the same path as a chat message (chosen provider and model, then the other providers with a saved key if it fails). Nothing is added to your chat.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
    }
}
