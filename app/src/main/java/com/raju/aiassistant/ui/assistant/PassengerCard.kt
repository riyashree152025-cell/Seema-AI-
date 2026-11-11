package com.raju.aiassistant.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raju.aiassistant.domain.tatkal.Passenger
import com.raju.aiassistant.domain.tatkal.writtenPassengers

/**
 * Tatkal helper card: Name | Age | Gender for every passenger, each with its own copy button, so a
 * value goes into the ticket form with one tap and a paste. It stays open until closed.
 */
@Composable
fun PassengerCardDialog(passengers: List<Passenger>, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf<String?>(null) }
    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        copied = value
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("यात्री — नाम | उम्र | Gender") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                passengers.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    Text("${p.name}  |  ${p.age}  |  ${p.gender ?: "—"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("जन्म ${p.birthYear}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { copy(p.name) }) { Text("नाम कॉपी") }
                        OutlinedButton(onClick = { copy(p.age.toString()) }) { Text("उम्र ${p.age} कॉपी") }
                    }
                }
                copied?.let { Text("कॉपी हुआ: $it", color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { copy(writtenPassengers(passengers)) }) { Text("सब कॉपी") } },
        dismissButton = { TextButton(onClick = onClose) { Text("बंद करें") } },
    )
}
