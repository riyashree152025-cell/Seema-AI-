package com.raju.aiassistant.ui.control

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.WarningAmber

/**
 * Screen 3: Control Panel
 * Matches Reference Image: Power Switch, Controls Grid, System Status Telemetry.
 */
@Composable
fun ControlPanelScreen(
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = container.currentSettings.value)
    val isOnline by container.connectivityObserver.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val isPaused = settings.assistantPaused
    val scope = rememberCoroutineScope()

    // Real system statistics (no fake data)
    var batteryPct by remember { mutableIntStateOf(90) }
    var ramPct by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        val bIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            batteryPct = (level * 100) / scale
        }

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        if (memInfo.totalMem > 0) {
            ramPct = (((memInfo.totalMem - memInfo.availMem) * 100) / memInfo.totalMem).toInt()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040714), Color(0xFF02040A))))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header: < Control Panel
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Settings, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Control Panel",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
            )
        }

        // Main Controls Section: Left Column, Center Power, Right Column
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column (Power Mode, Brightness)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ControlCard(
                    title = "Power\nMode",
                    icon = Icons.Filled.FlashOn,
                    active = settings.lowPowerMode,
                    onClick = { scope.launch { container.settingsRepository.setLowPowerMode(!settings.lowPowerMode) } },
                )
                ControlCard(
                    title = "Brightness",
                    icon = Icons.Filled.WbSunny,
                    active = false,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
            }

            // Center Big Power Button
            Box(
                Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0x3300E5FF), Color(0x0A00E5FF), Color.Transparent)))
                    .border(2.dp, NeonCyan.copy(alpha = if (isPaused) 0.35f else 0.85f), CircleShape)
                    .clickable { scope.launch { container.settingsRepository.setAssistantPaused(!settings.assistantPaused) } },
                contentAlignment = Alignment.Center,
            ) {
                // Outer ring
                Box(
                    Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFF0077FF).copy(alpha = 0.6f), CircleShape)
                        .background(Color(0x2200E5FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Inner ring + power icon
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0x6600E5FF), Color(0x22002A55))))
                            .border(1.5.dp, NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PowerSettingsNew,
                            contentDescription = "Master Power",
                            tint = if (isPaused) WarningAmber else NeonCyan,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            // Right Column (Do Not Disturb, Volume)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ControlCard(
                    title = "Do Not\nDisturb",
                    icon = Icons.Filled.DoNotDisturbOn,
                    active = false,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
                ControlCard(
                    title = "Volume",
                    icon = Icons.Filled.VolumeUp,
                    active = false,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
            }
        }

        // Secondary Row (Battery Saver, Quick Tools)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ControlCardWide(
                title = "Battery\nSaver",
                icon = Icons.Filled.BatterySaver,
                active = settings.lowPowerMode,
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )
            ControlCardWide(
                title = "Quick\nTools",
                icon = Icons.Filled.GridView,
                active = true,
                onClick = onOpenApps,
            )
        }

        // System Status Telemetry Card
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xD90A1428), Color(0xF2050B17))))
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "System Status",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                }

                // Battery Row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Battery $batteryPct%",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFC0D5F0)),
                    )
                    MetricBar(pct = batteryPct / 100f, color = SuccessGreen)
                }

                // RAM Row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "RAM $ramPct%",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFC0D5F0)),
                    )
                    MetricBar(pct = ramPct / 100f, color = NeonCyan)
                }

                // Network Row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isOnline) "Network Active" else "Network Offline",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isOnline) NeonCyan else WarningAmber,
                        ),
                    )
                    MetricBar(pct = if (isOnline) 0.85f else 0.1f, color = if (isOnline) NeonCyan else WarningAmber)
                }
            }
        }
    }
}

@Composable
private fun ControlCard(
    title: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(76.dp, 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xDD0B162C))
            .border(1.dp, if (active) NeonCyan else Color(0x3300E5FF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) NeonCyan else Color(0xFF8BA5CC),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                ),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ControlCardWide(
    title: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(108.dp, 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xDD0B162C))
            .border(1.dp, if (active) NeonCyan else Color(0x3300E5FF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) NeonCyan else Color(0xFF8BA5CC),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun MetricBar(pct: Float, color: Color) {
    val fill by animateFloatAsState(targetValue = pct.coerceIn(0.05f, 1f), tween(500), label = "metricFill")
    Box(
        Modifier
            .width(140.dp)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0F1E38)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color))),
        )
    }
}
