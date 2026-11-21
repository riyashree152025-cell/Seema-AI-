package com.raju.aiassistant.ui.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raju.aiassistant.MainActivity
import com.raju.aiassistant.domain.backup.BackupCrypto
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.TextSecondary
import java.time.LocalDate

/**
 * Backup with a password: the khata, diary, chats, memories, reminders and settings in one locked file the
 * user keeps (phone, pen drive, Google Drive) — so a new phone gets everything back. API keys are not in it.
 */
@Composable
fun BackupPanel(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf<String?>(null) } // "save" / "restore" / "keys_save" / "keys_restore"
    var uri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        if (u != null) { uri = u; mode = "save" }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; mode = "restore" }
    }
    val createKeys = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        if (u != null) { uri = u; mode = "keys_save" }
    }
    val openKeys = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; mode = "keys_restore" }
    }

    HudPanel(title = "Backup (password se locked)") {
        Text(
            "Khata, diary, chat, memory, reminders aur settings ek file mein — sirf aapke password se khulti hai. " +
                "Naye phone par isse sab wapas aa jata hai. API keys isme nahi hoti (unhe dobara daalna hoga).",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
        Button(onClick = { create.launch("seema-backup-${LocalDate.now()}.seemabak") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Backup banao") }
        OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Backup se wapas laao") }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White) }
    }

    HudPanel(title = "रोज़ रात अपने-आप backup") {
        Text(
            "यहाँ एक password रख दीजिए (कम से कम 6 अक्षर)। हर रात लगभग 2 बजे Download/Seema फ़ोल्डर में दो locked files बनेंगी: " +
                "seema-auto-backup.seemabak (खाता, डायरी, memory, settings) और seema-auto-keys.seemakeys (API keys)। " +
                "ऊपर के \"वापस लाओ\" बटन से इसी password से खुलेंगी। Password लिखकर रख लीजिए।",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
        val ui by viewModel.uiState.collectAsStateWithLifecycle()
        VoiceEngineSecretCard(
            secretId = com.raju.aiassistant.domain.model.SecretId.AUTO_BACKUP_PASSWORD,
            title = "Auto-backup password",
            secretStatus = ui.secrets[com.raju.aiassistant.domain.model.SecretId.AUTO_BACKUP_PASSWORD] ?: SecretStatus(),
            viewModel = viewModel,
        )
    }

    HudPanel(title = "API keys backup (password se locked)") {
        Text(
            "Gemini, Groq, Sarvam, Cartesia — sab saved API keys ek locked file mein. App dobara install karna pade ya " +
                "naya phone ho, to ek tap se sab keys wapas. File sirf aapke password se khulti hai.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
        Button(onClick = { createKeys.launch("seema-api-keys-${LocalDate.now()}.seemakeys") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("API keys ka backup banao") }
        OutlinedButton(onClick = { openKeys.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("API keys wapas laao") }
    }

    val target = uri
    val m = mode
    if (m != null && target != null) {
        PasswordDialog(
            restore = m == "restore" || m == "keys_restore",
            onCancel = { mode = null; uri = null },
            onOk = { pw ->
                mode = null; uri = null; busy = true
                message = if (m == "save" || m == "keys_save") "Backup ban raha hai…" else "Backup wapas aa raha hai…"
                if (m == "keys_save") {
                    viewModel.exportKeys(target, pw) { msg -> busy = false; message = msg }
                } else if (m == "keys_restore") {
                    viewModel.importKeys(target, pw) { msg -> busy = false; message = msg }
                } else if (m == "save") {
                    viewModel.exportBackup(target, pw) { msg -> busy = false; message = msg }
                } else {
                    viewModel.restoreBackup(target, pw) { msg, ok ->
                        busy = false; message = msg
                        if (ok) restartApp(context)
                    }
                }
            },
        )
    }
}

@Composable
private fun PasswordDialog(restore: Boolean, onCancel: () -> Unit, onOk: (CharArray) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val longEnough = first.length >= BackupCrypto.MIN_PASSWORD
    val ok = if (restore) longEnough else longEnough && first == second
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (restore) "Backup ka password" else "Backup ke liye password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (restore) Text("Is phone ka abhi ka data backup wale data se badal jayega.", color = Color(0xFFFFB74D))
                else Text("Kam se kam ${BackupCrypto.MIN_PASSWORD} akshar. Yeh password kahin likh lijiye — bhool gaye to backup nahi khulega.")
                OutlinedTextField(
                    value = first, onValueChange = { first = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (!restore) OutlinedTextField(
                    value = second, onValueChange = { second = it }, label = { Text("Password dobara") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onOk(first.toCharArray()) }, enabled = ok) { Text(if (restore) "Wapas laao" else "Backup banao") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** The restored data is read when the app starts: open it again in a moment and end this process. */
private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    val pending = PendingIntent.getActivity(context, 4911, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
    runCatching { context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, System.currentTimeMillis() + 800, pending) }
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 1_500)
}
