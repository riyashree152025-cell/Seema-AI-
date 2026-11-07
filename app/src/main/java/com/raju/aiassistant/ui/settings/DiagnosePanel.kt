package com.raju.aiassistant.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary

/** One line of the check-up: what, whether it is fine, and where to fix it (null = nothing to open). */
private data class Check(val label: String, val ok: Boolean, val fixHint: String, val fix: Intent?)

/**
 * "Seema ki jaanch": everything Seema needs, on one card — microphone, contacts, notification
 * access, Seema Accessibility (WhatsApp auto-send, opening apps from behind another app), the
 * "Hey Seema" wake word and its background microphone service, battery "Unrestricted", and the keys for
 * listening (Deepgram) and the brain (Gemini / Groq). Each missing item has a button to the
 * right Android screen. Nothing is sent anywhere; it only reads the phone's settings.
 */
@Composable
fun DiagnosePanel(secrets: Map<SecretId, SecretStatus>, wakeWord: Boolean) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val checks = remember(refresh, secrets, wakeWord) { checksFor(context, secrets, wakeWord) }

    HudPanel(title = "Seema ki jaanch (Diagnose)") {
        val missing = checks.count { !it.ok }
        Text(
            if (missing == 0) "Sab theek hai ✓" else "$missing cheez(en) theek karni hain:",
            style = MaterialTheme.typography.bodyMedium,
            color = if (missing == 0) SuccessGreen else ErrorRed,
        )
        checks.forEach { c ->
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    (if (c.ok) "✓ " else "✗ ") + c.label + if (c.ok) "" else " — ${c.fixHint}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (c.ok) SuccessGreen else ErrorRed,
                )
                if (!c.ok && c.fix != null) {
                    TextButton(onClick = { runCatching { context.startActivity(c.fix.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                        Text("Theek karein")
                    }
                }
            }
        }
        TextButton(onClick = { refresh++ }) { Text("Dobara jaanchein") }
        Text(
            "Keys wale items ke liye upar ya AI & Brain mein key save karke Test dabaiye.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
    }
}

private fun checksFor(context: Context, secrets: Map<SecretId, SecretStatus>, wakeWord: Boolean): List<Check> {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    val battery = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    val notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val accessibility = com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
    fun key(id: SecretId) = secrets[id]?.configured == true
    return listOf(
        Check("Microphone", granted(Manifest.permission.RECORD_AUDIO), "Permissions mein Microphone Allow karein", appDetails),
        Check("Contacts", granted(Manifest.permission.READ_CONTACTS), "Permissions mein Contacts Allow karein", appDetails),
        Check("Notification padhna", notificationAccess, "Notification access mein Seema AI ON karein", Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
        Check("Accessibility (WhatsApp auto-send, doosre app se kaam)", accessibility, "Accessibility mein Seema AI ON karein (pehle App info → ⋮ → Allow restricted settings)", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        Check("\"Hey Seema\" se jaagna", wakeWord, "Neeche \"Sirf naam se jaage\" ON karein", null),
        Check("Doosre app ke upar sunna (notification \"सीमा AI तैयार है\")", com.raju.aiassistant.voice.SeemaWakeService.running, "Seema ON karke app ek baar kholiye (Mic Allow ho)", appDetails),
        Check("Battery Unrestricted", battery, "Battery → Unrestricted chunein", appDetails),
        Check("Deepgram key (sunna, \"Hey Seema\")", key(SecretId.DEEPGRAM_API_KEY), "Neeche Deepgram key save karein", null),
        Check("AI key (Gemini ya Groq)", key(SecretId.GEMINI_API_KEY) || key(SecretId.GROQ_API_KEY), "AI & Brain mein key save karein", null),
    )
}
