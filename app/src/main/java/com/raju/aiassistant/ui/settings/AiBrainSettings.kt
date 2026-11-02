package com.raju.aiassistant.ui.settings

import com.raju.aiassistant.domain.ai.BUDGET_SAFETY_SHARE
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardOptions
import com.raju.aiassistant.ui.home.visuals.rememberAnimationTime
import com.raju.aiassistant.ui.home.visuals.reactorStyleFor
import com.raju.aiassistant.ui.home.visuals.ArcReactor
import com.raju.aiassistant.ui.components.LocalHudEffects
import com.raju.aiassistant.domain.model.AssistantState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
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
        BrainArc(status)
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

/**
 * Decorative only: a small copy of Home's Arc Reactor at the top of the AI Brain card. Calm and
 * low-cost (few particles, 24 fps, still when animations are off); red tones when the AI fails.
 */
@Composable
private fun BrainArc(status: ProviderStatus) {
    val animate = LocalHudEffects.current.pulses
    val time = rememberAnimationTime(running = animate, maxFps = 24)
    val style = reactorStyleFor(if (status.isError) AssistantState.Error else AssistantState.Idle)
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        ArcReactor(style, particleCount = 18, richEffects = false, time = { time.floatValue }, speechLevel = { 0f }, modifier = Modifier.size(112.dp))
    }
}

/**
 * Settings → AI & Brain → Budget: free-only switch, monthly limit for paid models, this month's
 * estimated use per model and providers resting after a failure. The app never pays or buys
 * credits; it only decides whether a request is sent.
 */
@Composable
fun AiBudgetPanel(s: UserSettings, viewModel: SettingsViewModel) {
    val usage by viewModel.aiUsage.collectAsStateWithLifecycle()
    var capText by remember(s.aiMonthlyBudgetUsd) { mutableStateOf("%.2f".format(s.aiMonthlyBudgetUsd)) }
    val resting = viewModel.aiCooldowns()

    HudPanel(title = "AI budget (cost safety)") {
        ToggleRow("Free-only mode: use only free / free-tier models", s.aiFreeOnly, viewModel::setAiFreeOnly)
        Text(
            if (s.aiFreeOnly) "Paid models are never used. If every free provider is busy, Seema says so instead of spending money."
            else "Paid models (with a published price) may be used up to the monthly limit below; models without a price are never used.",
            style = MaterialTheme.typography.bodyMedium, color = if (s.aiFreeOnly) SuccessGreen else WarningAmber,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = capText,
                onValueChange = { capText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                label = { Text("Monthly limit (US$)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            val newCap = capText.toFloatOrNull()
            OutlinedButton(onClick = { newCap?.let(viewModel::setAiMonthlyBudget) }, enabled = newCap != null && newCap != s.aiMonthlyBudgetUsd) { Text("Save limit") }
        }
        InfoRow(
            "This month",
            "≈ $${"%.2f".format(usage.totalUsd)} of $${"%.2f".format(s.aiMonthlyBudgetUsd)} (estimate; only ${(BUDGET_SAFETY_SHARE * 100).toInt()}% of the limit is used, for taxes and currency)",
            if (usage.totalUsd > 0) WarningAmber else TextPrimary,
        )
        Text(
            "Seema never pays, buys credits or turns on top-ups. Free-tier providers (Gemini, Groq, Cerebras, SambaNova, NVIDIA, Cloudflare) can only charge you if billing is enabled on that account — keep billing off there, or set a spending limit in the provider's own dashboard. The numbers here are estimates, not the provider's invoice.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
        if (usage.models.isEmpty()) Text("No AI requests counted yet this month (${usage.month}).", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        usage.models.sortedByDescending { it.requests }.forEach { u ->
            Text(
                "${u.provider.shortName} · ${u.model}: ${u.requests} replies, ≈ ${u.inputTokens + u.outputTokens} tokens" +
                    (if (u.costUsd > 0) ", ≈ $${"%.3f".format(u.costUsd)}" else ", free") + (if (u.failures > 0) " · ${u.failures} failed" else ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        resting.forEach { (p, v) ->
            Text("${p.shortName} is resting ${v.first / 1000 + 1} s after: ${v.second.userMessage} (other providers answer meanwhile).", style = MaterialTheme.typography.bodySmall, color = WarningAmber)
        }
    }
}
