package com.raju.aiassistant.ui.tools

import androidx.compose.foundation.layout.Row
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
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary

/**
 * The udhaar book on screen: who owes what, and a way to remove the newest entry (after a confirm),
 * the same as saying "aakhri entry hatao".
 */
@Composable
fun LedgerPanel(balances: List<ClientBalance>, newest: LedgerEntryInfo?, onRemove: (Long) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    HudPanel(title = "Udhaar khata") {
        Text("बोलकर: \"Rahul se 500 mile\", \"Zaid ko 300 diye\", \"Rahul ka hisaab batao\"", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        if (balances.isEmpty()) Text("अभी कोई हिसाब नहीं है।", color = TextSecondary)
        balances.forEach { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        val what = if (newest.kind == LedgerKind.GOT) "${newest.name} से ${formatRupees(newest.amountPaise)} मिले"
            else "${newest.name} को ${formatRupees(newest.amountPaise)} दिए"
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Entry हटाएँ?") },
            text = { Text("आख़िरी entry: $what") },
            confirmButton = { TextButton(onClick = { onRemove(newest.id); asking = false }) { Text("हटाएँ") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("रहने दें") } },
        )
    }
}
