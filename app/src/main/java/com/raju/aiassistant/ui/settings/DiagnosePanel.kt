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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary

/** One line of the check-up: what, whether it is fine, and where to fix it (null = nothing to open). */
private data class Check(val label: String, val ok: Boolean, val fixHint: String, val fix: Intent?, val newTask: Boolean = true)

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
                    TextButton(onClick = { runCatching { context.startActivity(if (c.newTask) c.fix.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else c.fix) } }) {
                        Text("Theek karein")
                    }
                }
            }
        }
        TextButton(onClick = { refresh++ }) { Text("Dobara jaanchein") }
        SelfTests()
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
        Check("Call log (aakhiri call, call ke baad SMS jawab)", granted(Manifest.permission.READ_CALL_LOG), "Permissions mein Call logs Allow karein", appDetails),
        Check("Phone (\"utha lo\" / \"kaat do\" se call uthana-kaatna)", granted(Manifest.permission.ANSWER_PHONE_CALLS), "Permissions mein Phone Allow karein", appDetails),
        Check("SMS bhejna", granted(Manifest.permission.SEND_SMS), "Permissions mein SMS Allow karein", appDetails),
        Check(
            "Notification dikhana (PNR / briefing alerts)",
            android.os.Build.VERSION.SDK_INT < 33 || granted("android.permission.POST_NOTIFICATIONS"),
            "Permissions mein Notifications Allow karein", appDetails,
        ),
        Check(
            "Call screening (aane wali call par naam / number bolna)",
            callScreening(context),
            "Seema ko Call screening app chuniye",
            callScreeningIntent(context), newTask = false,
        ),
        Check("Notification padhna", notificationAccess, "Notification access mein Seema AI ON karein", Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
        Check(
            "Doosre app ke upar kholna (peeche se app / page / alarm kholna)",
            runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false),
            "\"Display over other apps\" mein Seema AI Allow karein",
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")),
        ),
        Check("Accessibility (WhatsApp auto-send, doosre app se kaam)", accessibility, "Accessibility mein Seema AI ON karein (pehle App info → ⋮ → Allow restricted settings)", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        Check("\"Hey Seema\" se jaagna", wakeWord, "Neeche \"Sirf naam se jaage\" ON karein", null),
        Check("Doosre app ke upar sunna (notification \"सीमा AI तैयार है\")", com.raju.aiassistant.voice.SeemaWakeService.running, "Seema ON karke app ek baar kholiye (Mic Allow ho)", appDetails),
        Check("Battery Unrestricted", battery, "Battery → Unrestricted chunein", appDetails),
        Check("Deepgram key (sunna, \"Hey Seema\")", key(SecretId.DEEPGRAM_API_KEY), "Neeche Deepgram key save karein", null),
        Check("AI key (Gemini ya Groq)", key(SecretId.GEMINI_API_KEY) || key(SecretId.GROQ_API_KEY), "AI & Brain mein key save karein", null),
    )
}

/** Seema holds the call-screening role (Android 10+): Android then tells her who is calling. */
private fun callScreening(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 29) return false
    val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return false
    return runCatching { rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) }.getOrDefault(false)
}

private fun callScreeningIntent(context: Context): Intent? {
    if (android.os.Build.VERSION.SDK_INT < 29) return null
    val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return null
    return runCatching { rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING) }.getOrNull()
}

/**
 * Live tests: the microphone (3 s while the user speaks: is anything heard, how loud) and the voice
 * (Seema speaks a line through the chosen engine). Results are shown here only.
 */
@Composable
private fun SelfTests() {
    val context = LocalContext.current
    val container = (context.applicationContext as com.raju.aiassistant.RajuApp).container
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var micResult by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var voiceResult by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = {
            micResult = "Sun rahi hoon… 3 second kuch boliye"
            scope.launch {
                var frames = 0
                var loudest = 0f
                val lease = container.micHub.lease(com.raju.aiassistant.data.voice.MicPurpose.STT, { frame ->
                    frames++
                    loudest = maxOf(loudest, com.raju.aiassistant.data.voice.frameLevel(frame))
                })
                kotlinx.coroutines.delay(3_000)
                lease.release()
                val v = com.raju.aiassistant.domain.voice.micVerdict(frames, loudest)
                micResult = com.raju.aiassistant.domain.voice.micVerdictText(v, loudest)
            }
        }) { Text("Mic test") }
        TextButton(onClick = {
            voiceResult = "Bol rahi hoon…"
            container.speaker.test("Namaste! Main Seema hoon, meri aawaz theek aa rahi hai?") { r ->
                voiceResult = if (r.completed) "✓ Aawaz chali (${r.engineLabel})" else "✗ Aawaz nahi chali (${r.engineLabel}${r.error?.let { ": $it" } ?: ""})"
            }
        }) { Text("Aawaz test") }
    }
    micResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    voiceResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
