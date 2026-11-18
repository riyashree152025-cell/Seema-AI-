package com.raju.aiassistant.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.data.tools.PaymentQr
import com.raju.aiassistant.domain.payments.PaymentShare
import com.raju.aiassistant.domain.payments.UpiEntry
import kotlinx.coroutines.launch

/**
 * Apps & Tools → "Payment QR & UPI IDs": one saved QR photo ("mera QR bhej do Rizwan ko") and several named UPI
 * IDs ("Jalal bhai ko UPI ID bhej do" → "kaun si — Paytm ya PhonePe?"). Kept on this phone only.
 */
@Composable
fun PaymentShareCard() {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val settings by container.currentSettings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var thumb by remember { mutableStateOf(PaymentQr.thumbnail(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val ok = PaymentQr.save(context, uri)
            thumb = PaymentQr.thumbnail(context)
            status = if (ok) "QR सेव हो गया।" else "यह फ़ोटो पढ़ी नहीं जा सकी।"
        }
    }
    var label by remember { mutableStateOf("") }
    var upi by remember { mutableStateOf("") }
    var payee by remember(settings.upiPayeeName) { mutableStateOf(settings.upiPayeeName) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "बोलिए: \"मेरा QR भेज दो रिज़वान को\" या \"जलाल भाई को UPI ID भेज दो\"। भेजने से पहले Seema पूछेगी।",
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            thumb?.let { Image(it.asImageBitmap(), contentDescription = "Payment QR", modifier = Modifier.size(84.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (thumb == null) "Save Payment QR" else "QR बदलें")
                }
                if (thumb != null) OutlinedButton(onClick = { PaymentQr.delete(context); thumb = null; status = "QR हटा दिया।" }) { Text("QR हटाएँ") }
            }
        }
        status?.let { Text(it, color = Color(0xFF8CE99A), style = MaterialTheme.typography.bodySmall) }

        Text("UPI IDs (${settings.upiIds.size})", color = Color.White, style = MaterialTheme.typography.titleSmall)
        settings.upiIds.forEach { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${e.label}: ${e.upiId}", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { container.settingsRepository.setUpiIds(settings.upiIds - e) } }) { Text("✕", color = Color(0xFFFF8787)) }
            }
        }
        OutlinedTextField(value = label, onValueChange = { label = it.take(24) }, label = { Text("नाम (जैसे Paytm, PhonePe, R Travels)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = upi, onValueChange = { upi = it.trim().take(80) }, label = { Text("UPI ID (जैसे name@paytm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val valid = label.isNotBlank() && PaymentShare.isValidUpi(upi)
        Button(enabled = valid, onClick = {
            val entry = UpiEntry(label.trim(), upi.trim())
            scope.launch { container.settingsRepository.setUpiIds(settings.upiIds.filterNot { it.label.equals(entry.label, true) } + entry) }
            label = ""
            upi = ""
        }) { Text("+ Add UPI ID") }
        if (upi.isNotBlank() && !PaymentShare.isValidUpi(upi)) Text("UPI ID ऐसी लिखिए: name@bank", color = Color(0xFFFFC078), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = payee, onValueChange = { payee = it.take(40) },
            label = { Text("मैसेज में नाम (Name:)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (payee != settings.upiPayeeName) TextButton(onClick = { scope.launch { container.settingsRepository.setUpiPayeeName(payee) } }) { Text("नाम सेव करें") }
    }
}
