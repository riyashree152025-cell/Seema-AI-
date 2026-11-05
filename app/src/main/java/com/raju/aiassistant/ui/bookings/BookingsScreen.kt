package com.raju.aiassistant.ui.bookings

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.domain.booking.BOOKING_FOLDER
import com.raju.aiassistant.domain.booking.Booking
import com.raju.aiassistant.domain.booking.BookingStatus
import com.raju.aiassistant.domain.booking.formatSlip
import com.raju.aiassistant.domain.booking.searchBookings
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber
import java.time.LocalDate

/** Booking list: Pending / Completed / Cancelled, search, slips to share, and the daily text file. */
@Composable
fun BookingsScreen(onBack: () -> Unit, viewModel: BookingsViewModel = viewModel(factory = RajuViewModelFactory)) {
    val all by viewModel.bookings.collectAsStateWithLifecycle()
    val savedNumber by viewModel.whatsAppNumber.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(BookingStatus.PENDING) }
    var query by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(savedNumber) { if (number.isEmpty()) number = savedNumber }
    var confirmSend by remember { mutableStateOf<Booking?>(null) }
    var confirmDelete by remember { mutableStateOf<Booking?>(null) }
    val context = LocalContext.current
    val shown = remember(all, tab, query) { searchBookings(all, tab, query) }
    val counts = remember(all) { all.groupingBy { it.status }.eachCount() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Train bookings", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        message?.let { m -> item { Text(m, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) } }
        item {
            HudPanel(title = "बोलकर बुकिंग") {
                Text(
                    "Home पर सीमा से बोलिए: \"कल दिल्ली से पटना, ट्रेन 12309, 3A, आनंद 34 पुरुष, सीता 30 महिला, राकेश ने भेजा\". " +
                        "सीमा पर्ची दिखाकर पूछेगी, फिर सेव करेगी। गलत सुना हो तो बोलिए \"नहीं, नाम आनंद है\" — वही बुकिंग सुधर जाएगी।",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                )
                Text(
                    "हर यात्रा-तारीख की सारी पर्चियाँ फ़ोन में $BOOKING_FOLDER में टेक्स्ट फ़ाइल में अपने-आप सेव होती हैं। सुबह 8 बजे आज/कल की बाकी बुकिंग का नोटिफ़िकेशन आता है।",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.saveFile(LocalDate.now()) }) { Text("आज की फ़ाइल") }
                    OutlinedButton(onClick = { viewModel.saveFile(LocalDate.now().plusDays(1)) }) { Text("कल की फ़ाइल") }
                }
            }
        }
        item {
            HudPanel(title = "WhatsApp पर पर्ची") {
                Text(
                    "नंबर डालेंगे तो हर नई बुकिंग के बाद सीमा पूछेगी \"पर्ची भेजूँ?\" — हाँ करने पर ही WhatsApp में भेजेगी।",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = number, onValueChange = { number = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }.take(20) },
                        label = { Text("WhatsApp नंबर") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    Button(onClick = { viewModel.saveNumber(number) }) { Text("Save") }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookingStatus.entries.forEach { st ->
                    FilterChip(selected = tab == st, onClick = { tab = st }, label = { Text("${st.label} (${counts[st] ?: 0})") })
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("खोजें: नाम, स्टेशन, ट्रेन, तारीख (06-11-2026), #नंबर") },
            )
        }
        if (shown.isEmpty()) item { Text("यहाँ कोई बुकिंग नहीं है।", color = TextSecondary) }
        items(shown, key = { it.id }) { b ->
            BookingCard(
                b,
                onStatus = { viewModel.setStatus(b, it) },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, formatSlip(b))
                    runCatching { context.startActivity(Intent.createChooser(send, "पर्ची भेजें (WhatsApp, Telegram…)")) }
                },
                onWhatsApp = { confirmSend = b },
                onDelete = { confirmDelete = b },
            )
        }
    }

    confirmSend?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmSend = null },
            title = { Text("पर्ची WhatsApp पर भेजूँ?") },
            text = { Text((savedNumber.takeIf { it.isNotBlank() }?.let { "नंबर: $it\n\n" } ?: "चैट आप चुनेंगे\n\n") + formatSlip(b), fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            confirmButton = { TextButton(onClick = { confirmSend = null; viewModel.sendWhatsApp(b) }) { Text("हाँ, भेजो") } },
            dismissButton = { TextButton(onClick = { confirmSend = null }) { Text("नहीं") } },
        )
    }
    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("बुकिंग #${b.id} हटाऊँ?") },
            text = { Text("${b.from} → ${b.to}, ${b.passengers.joinToString { it.name }}. यह वापस नहीं आएगी — रद्द करने के लिए \"Cancelled\" बेहतर है।") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; viewModel.delete(b) }) { Text("हटाओ", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("नहीं") } },
        )
    }
}

@Composable
private fun BookingCard(
    b: Booking,
    onStatus: (BookingStatus) -> Unit,
    onShare: () -> Unit,
    onWhatsApp: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = when (b.status) {
        BookingStatus.PENDING -> WarningAmber
        BookingStatus.COMPLETED -> SuccessGreen
        BookingStatus.CANCELLED -> ErrorRed
    }
    HudPanel(title = "#${b.id} · ${b.status.label}", accent = accent) {
        Text(formatSlip(b), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (b.status != BookingStatus.COMPLETED) OutlinedButton(onClick = { onStatus(BookingStatus.COMPLETED) }) { Text("✓ Completed") }
                if (b.status != BookingStatus.CANCELLED) OutlinedButton(onClick = { onStatus(BookingStatus.CANCELLED) }) { Text("✗ Cancel") }
                if (b.status != BookingStatus.PENDING) OutlinedButton(onClick = { onStatus(BookingStatus.PENDING) }) { Text("↺ Pending") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onWhatsApp) { Text("WhatsApp") }
                OutlinedButton(onClick = onShare) { Text("Share / Telegram") }
                TextButton(onClick = onDelete) { Text("Delete", color = ErrorRed) }
            }
        }
    }
}
