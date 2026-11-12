package com.raju.aiassistant.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raju.aiassistant.domain.business.formatRupees
import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.ledger.LedgerEntryInfo
import com.raju.aiassistant.domain.ledger.LedgerKind
import com.raju.aiassistant.domain.ledger.ledgerDay
import com.raju.aiassistant.domain.ledger.ledgerStatement
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary

/**
 * The udhaar book on screen: who owes what; tap a name for every entry of that person (each can be
 * removed after a confirm, and the account copied as text), and a quick way to remove the newest entry,
 * the same as saying "aakhri entry hatao".
 */
@Composable
fun LedgerPanel(
    balances: List<ClientBalance>,
    newest: LedgerEntryInfo?,
    onRemove: (Long) -> Unit,
    entries: List<LedgerEntryInfo> = emptyList(),
    onCopy: (String) -> Unit = {},
) {
    var asking by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    HudPanel(title = "Udhaar khata") {
        Text("बोलकर: \"Rahul se 500 mile\", \"Zaid ko 300 diye\", \"Rahul ka hisaab batao\" · नाम दबाएँ: पूरा हिसाब", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        if (balances.isEmpty()) Text("अभी कोई हिसाब नहीं है।", color = TextSecondary)
        balances.forEach { b ->
            Row(Modifier.fillMaxWidth().clickable { open = b.name }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                val (text, color) = when {
                    b.balancePaise > 0 -> "बाकी ${formatRupees(b.balancePaise)}" to ErrorRed
                    b.balancePaise < 0 -> "देने हैं ${formatRupees(-b.balancePaise)}" to SuccessGreen
                    else -> "बराबर" to TextSecondary
                }
                Text(text, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (newest != null) {
            TextButton(onClick = { asking = true }) { Text("आख़िरी entry हटाएँ") }
        }
    }
    if (asking && newest != null) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Entry हटाएँ?") },
            text = { Text("आख़िरी entry: ${describe(newest)}") },
            confirmButton = { TextButton(onClick = { onRemove(newest.id); asking = false }) { Text("हटाएँ") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("रहने दें") } },
        )
    }
    open?.let { name ->
        ClientAccountDialog(name, entries.filter { it.name == name }, onRemove, onCopy) { open = null }
    }
}

private fun describe(e: LedgerEntryInfo): String =
    if (e.kind == LedgerKind.GOT) "${e.name} से ${formatRupees(e.amountPaise)} मिले" else "${e.name} को ${formatRupees(e.amountPaise)} दिए"

/** One person's account: every entry (newest first), remove one after a confirm, copy the account. */
@Composable
private fun ClientAccountDialog(
    name: String,
    entries: List<LedgerEntryInfo>,
    onRemove: (Long) -> Unit,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    var removing by remember { mutableStateOf<LedgerEntryInfo?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("$name का हिसाब") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entries.isEmpty()) Text("कोई entry नहीं बची।", color = TextSecondary)
                entries.forEach { e ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (e.kind == LedgerKind.GAVE) "दिए ${formatRupees(e.amountPaise)}" else "मिले ${formatRupees(e.amountPaise)}",
                                color = if (e.kind == LedgerKind.GAVE) ErrorRed else SuccessGreen, fontWeight = FontWeight.SemiBold,
                            )
                            Text(ledgerDay(e.at, zone), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { removing = e }) { Text("हटाएँ") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(ledgerStatement(name, entries, zone)) }, enabled = entries.isNotEmpty()) { Text("हिसाब copy करें") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("बंद करें") } },
    )
    removing?.let { e ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("यह entry हटाएँ?") },
            text = { Text("${describe(e)} — ${ledgerDay(e.at, zone)}") },
            confirmButton = { TextButton(onClick = { onRemove(e.id); removing = null }) { Text("हटाएँ") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("रहने दें") } },
        )
    }
}
