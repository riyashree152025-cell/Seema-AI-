package com.raju.aiassistant.ui.control

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.ui.components.SciFiBackgroundBottom
import com.raju.aiassistant.ui.components.SciFiBackgroundTop
import com.raju.aiassistant.ui.components.SciFiButtonShape
import com.raju.aiassistant.ui.components.SciFiCard
import com.raju.aiassistant.ui.components.SciFiCardShape
import com.raju.aiassistant.ui.components.SciFiCyanGlow
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

/**
 * Screen 3: Automation Screen
 * Exact 1:1 match to User Reference Image (Image 2):
 * - Header: < [Robot] Automation + 3 Dots Menu
 * - Top Card: Automation Service (Green glowing gear) + 'Run Routines Automatically' + Toggle
 * - Section: 'Your Routines' (View All >)
 *     1. [Sun] Good Morning (Start with greetings) + Toggle
 *     2. [Clock] Night Mode (Reduce activity) + Toggle
 *     3. [Home] Smart Home (Control devices) + Toggle
 * - Big Action Button: + Add New Routine (Purple/Magenta Gradient Button)
 * - Footer: ⚡ Automation is Active (Emerald Green Lightning)
 * - Quick System Telemetry & Hardware Tools integrated below
 */
@Composable
fun ControlPanelScreen(
    onBack: () -> Unit,
    onOpenApps: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val settings by container.currentSettings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var automationEnabled by remember { mutableStateOf(!settings.assistantPaused) }
    var goodMorningActive by remember { mutableStateOf(true) }
    var nightModeActive by remember { mutableStateOf(settings.lowPowerMode) }
    var smartHomeActive by remember { mutableStateOf(true) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newRoutineName by remember { mutableStateOf("") }

    var batteryPct by remember { mutableIntStateOf(92) }
    var ramPct by remember { mutableIntStateOf(62) }

    LaunchedEffect(Unit) {
        val bIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) batteryPct = (level * 100 / scale)

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        if (memInfo.totalMem > 0) {
            val used = memInfo.totalMem - memInfo.availMem
            ramPct = ((used.toDouble() / memInfo.totalMem) * 100).toInt()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SciFiBackgroundTop, Color(0xFF040A18), SciFiBackgroundBottom)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Header: < [Robot] Automation ...
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SciFiCyanGlow)
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = SciFiCyanGlow, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Automation",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp,
                            letterSpacing = 0.8.sp,
                        ),
                    )
                }

                IconButton(onClick = onOpenApps, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = SciFiCyanGlow)
                }
            }

            // 2. Top Card: Automation Service (Green gear + Toggle)
            SciFiCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Color(0xFF00E676)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CutCornerShape(8.dp))
                                .background(Color(0x3300E676))
                                .border(1.2.dp, SuccessGreen, CutCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Text(
                                "Automation Service",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Run Routines Automatically",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = automationEnabled,
                        onCheckedChange = { checked ->
                            automationEnabled = checked
                            scope.launch {
                                container.settingsRepository.setAssistantPaused(!checked)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0072FF),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF132036)
                        )
                    )
                }
            }

            // 3. Section Title: Your Routines (View All >)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Your Routines",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenApps() }
                ) {
                    Text(
                        "View All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = SciFiCyanGlow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = SciFiCyanGlow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 4. Three Routines Cards
            RoutineItemCard(
                title = "Good Morning",
                subtitle = "Start with greetings",
                icon = Icons.Filled.WbSunny,
                iconTint = Color(0xFFFFD600),
                active = goodMorningActive,
                onToggle = {
                    goodMorningActive = it
                    if (it) {
                        scope.launch { container.engine.submit("Good morning SEEMA") }
                    }
                }
            )

            RoutineItemCard(
                title = "Night Mode",
                subtitle = "Reduce activity",
                icon = Icons.Filled.Bedtime,
                iconTint = SciFiCyanGlow,
                active = nightModeActive,
                onToggle = {
                    nightModeActive = it
                    scope.launch {
                        container.settingsRepository.setLowPowerMode(it)
                    }
                }
            )

            RoutineItemCard(
                title = "Smart Home",
                subtitle = "Control devices",
                icon = Icons.Filled.Home,
                iconTint = Color(0xFF40C4FF),
                active = smartHomeActive,
                onToggle = { smartHomeActive = it }
            )

            Spacer(Modifier.height(4.dp))

            // 5. Big Action Button: + Add New Routine (Purple Gradient)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(SciFiButtonShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF8A2BE2), Color(0xFF6A0DAD), Color(0xFF9370DB))
                        )
                    )
                    .border(1.2.dp, Color(0xFFBA68C8), SciFiButtonShape)
                    .clickable { showAddDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "+  Add New Routine",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            letterSpacing = 0.8.sp
                        )
                    )
                }
            }

            // 6. Bottom Indicator: ⚡ Automation is Active
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.FlashOn,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (automationEnabled) "Automation is Active" else "Automation is Paused",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (automationEnabled) SuccessGreen else Color.Gray,
                        fontSize = 13.sp
                    )
                )
            }

            // 7. System Tools & Telemetry (Real Battery & RAM status preserved)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SciFiCardShape)
                        .background(Color(0xD9081224))
                        .border(1.dp, Color(0x3300E5FF), SciFiCardShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Battery: $batteryPct%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SciFiCardShape)
                        .background(Color(0xD9081224))
                        .border(1.dp, Color(0x3300E5FF), SciFiCardShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("RAM: $ramPct%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Add Routine Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newRoutineName.isNotBlank()) {
                                scope.launch { container.engine.submit("Create routine: $newRoutineName") }
                            }
                            showAddDialog = false
                            newRoutineName = ""
                        }
                    ) {
                        Text("Add", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                title = { Text("Add New Routine", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter the phrase or command for this routine:", color = Color.White.copy(alpha = 0.8f))
                        OutlinedTextField(
                            value = newRoutineName,
                            onValueChange = { newRoutineName = it },
                            placeholder = { Text("e.g. Leave Home, Meeting Mode", color = Color.Gray) }
                        )
                    }
                },
                containerColor = Color(0xFF071224)
            )
        }
    }
}

@Composable
private fun RoutineItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    active: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SciFiCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CutCornerShape(8.dp))
                        .background(Color(0x2600E5FF))
                        .border(1.2.dp, iconTint.copy(alpha = 0.8f), CutCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            Switch(
                checked = active,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF0072FF),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF132036)
                )
            )
        }
    }
}
