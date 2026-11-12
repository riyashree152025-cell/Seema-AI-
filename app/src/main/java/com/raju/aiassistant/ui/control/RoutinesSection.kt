package com.raju.aiassistant.ui.control

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.assistant.LocalCommands
import com.raju.aiassistant.domain.routine.Routine
import com.raju.aiassistant.domain.routine.RoutineCheck
import com.raju.aiassistant.domain.routine.checkRoutine
import com.raju.aiassistant.notifications.RoutineStore
import com.raju.aiassistant.ui.components.Premium
import com.raju.aiassistant.ui.components.SciFiCard
import com.raju.aiassistant.ui.components.SciFiCyanGlow

/**
 * The user's own routines on the Automation screen: each with its steps and time, a switch, a Run button
 * and tap-to-edit; "+ Add New Routine" opens the same editor. Saved on the phone (RoutineStore), and a
 * routine with a time is set as a daily alarm.
 */
@Composable
fun RoutinesSection() {
    val context = LocalContext.current
    var routines by remember { mutableStateOf(RoutineStore.all(context).sortedBy { it.name.lowercase() }) }
    var editing by remember { mutableStateOf<Routine?>(null) }
    var creating by remember { mutableStateOf(false) }
    fun refresh() { routines = RoutineStore.all(context).sortedBy { it.name.lowercase() } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (routines.isEmpty()) {
            SciFiCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "अभी कोई routine नहीं है। नीचे \"+ Add New Routine\" से बनाइए — जैसे \"Good Night\": torch band karo, volume 20 karo। फिर बोलिए \"good night chalao\", या उसका समय रख दीजिए।",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 14.5.sp,
                )
            }
        }
        routines.forEach { r ->
            RoutineCard(
                routine = r,
                onEdit = { editing = r },
                onToggle = { on -> RoutineStore.save(context, r.copy(enabled = on)); refresh() },
                onRun = {
                    val c = (context.applicationContext as RajuApp).container
                    if (c.currentSettings.value.assistantPaused) {
                        Toast.makeText(context, "Seema अभी बंद है — ऊपर Automation Service चालू कीजिए।", Toast.LENGTH_LONG).show()
                    } else if (!r.enabled) {
                        Toast.makeText(context, "यह routine बंद है — पहले इसका switch चालू कीजिए।", Toast.LENGTH_LONG).show()
                    } else {
                        c.engine.runRoutineNow(r.id)
                        Toast.makeText(context, "${r.name} चला रही हूँ…", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF16324F), Color(0xFF1E4A6E), Color(0xFF16324F))))
                .border(1.dp, Brush.linearGradient(listOf(Premium.Gold.copy(alpha = 0.7f), Premium.Aqua.copy(alpha = 0.45f))), RoundedCornerShape(18.dp))
                .clickable { creating = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("+  Add New Routine", color = Premium.TextHigh, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    val open = editing
    if (creating || open != null) {
        RoutineEditor(
            routine = open,
            existing = routines,
            onDismiss = { creating = false; editing = null },
            onSave = { r -> RoutineStore.save(context, r); refresh(); creating = false; editing = null },
            onDelete = { r -> RoutineStore.remove(context, r.id); refresh(); creating = false; editing = null },
        )
    }
}

@Composable
private fun RoutineCard(routine: Routine, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onRun: () -> Unit) {
    SciFiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CutCornerShape(8.dp)).background(Color(0x2600E5FF))
                    .border(1.2.dp, SciFiCyanGlow.copy(alpha = 0.8f), CutCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Schedule, contentDescription = null, tint = SciFiCyanGlow, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(routine.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                val time = if (routine.timed) "रोज़ ${hhmm(routine)}" else "सिर्फ़ आवाज़ से"
                Text("${routine.steps.size} काम · $time", color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
                Text(routine.steps.joinToString(" → "), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, maxLines = 2)
            }
            IconButton(onClick = onRun) { Icon(Icons.Filled.PlayArrow, contentDescription = "Run ${routine.name}", tint = SciFiCyanGlow) }
            Switch(
                checked = routine.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3AA8BC),
                    uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color(0xFF132036),
                ),
            )
        }
    }
}

@Composable
private fun RoutineEditor(
    routine: Routine?,
    existing: List<Routine>,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    var name by remember { mutableStateOf(routine?.name.orEmpty()) }
    var steps by remember { mutableStateOf(routine?.steps?.joinToString("\n").orEmpty()) }
    var time by remember { mutableStateOf(routine?.takeIf { it.timed }?.let { hhmm(it) }.orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF071224),
        title = { Text(if (routine == null) "नई Routine" else "Routine बदलें", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; problem = null }, singleLine = true,
                    label = { Text("नाम (जैसे Good Night)") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = steps, onValueChange = { steps = it; problem = null },
                    label = { Text("काम — हर लाइन में एक") },
                    placeholder = { Text("torch band karo\nvolume 20 karo\naaj ki briefing do", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                OutlinedTextField(
                    value = time, onValueChange = { time = it; problem = null }, singleLine = true,
                    label = { Text("रोज़ का समय (07:30) — खाली = सिर्फ़ आवाज़ से") }, modifier = Modifier.fillMaxWidth(),
                )
                Text("कॉल और मैसेज वाले काम चलते समय भी पहले आपसे पूछेंगे।", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                problem?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
                if (routine != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("यह routine हटाएँ", color = Color(0xFFFF8A80)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val check = checkRoutine(
                    id = routine?.id ?: System.currentTimeMillis(),
                    name = name, stepsText = steps, timeText = time,
                    enabled = routine?.enabled ?: true, existing = existing,
                    understood = { runCatching { LocalCommands.parse(it) != null }.getOrDefault(false) },
                )
                when (check) {
                    is RoutineCheck.Ok -> onSave(check.routine)
                    is RoutineCheck.Problem -> problem = check.message
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )

    if (confirmDelete && routine != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color(0xFF071224),
            title = { Text("\"${routine.name}\" हटाएँ?", color = Color.White) },
            text = { Text("इसका समय वाला alarm भी हट जाएगा।", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = { Button(onClick = { confirmDelete = false; onDelete(routine) }) { Text("हटाएँ") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("रहने दें", color = Color.White) } },
        )
    }
}

private fun hhmm(r: Routine): String =
    "${(r.hour ?: 0).toString().padStart(2, '0')}:${(r.minute ?: 0).toString().padStart(2, '0')}"
