package com.raju.aiassistant.ui.tools

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import com.raju.aiassistant.notifications.TatkalAlarms
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber

/** The daily Tatkal alarms: see them, cancel one, and switch on exact alarms when Android has them off. */
@Composable
fun TatkalAlarmsPanel() {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(TatkalAlarms.all(context).sortedBy { it.hour * 60 + it.minute }) }
    var exact by remember { mutableStateOf(TatkalAlarms.canExact(context)) }
    // Back from the system's "Alarms & reminders" page (or after a voice command): read both again.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                exact = TatkalAlarms.canExact(context)
                alarms = TatkalAlarms.all(context).sortedBy { it.hour * 60 + it.minute }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    HudPanel(title = "Tatkal alarms") {
        Text("रोज़ बजने वाले alarm। बोलकर लगाइए: \"tatkal alarm 9:58 AC\"", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text("Exact alarm की अनुमति बंद है — alarm देर से बज सकता है।", color = WarningAmber)
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }) { Text("अनुमति दीजिए") }
        }
        if (alarms.isEmpty()) Text("कोई Tatkal alarm नहीं लगा है।", color = TextSecondary)
        alarms.forEach { a ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${a.label} — ${a.timeText} (रोज़)", Modifier.weight(1f))
                IconButton(onClick = {
                    TatkalAlarms.remove(context) { it == a }
                    alarms = TatkalAlarms.all(context).sortedBy { it.hour * 60 + it.minute }
                }) { Icon(Icons.Filled.Delete, contentDescription = "${a.label} ${a.timeText} alarm हटाएँ") }
            }
        }
    }
}
