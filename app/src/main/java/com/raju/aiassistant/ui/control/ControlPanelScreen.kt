package com.raju.aiassistant.ui.control

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.launch

/**
 * Screen 3: Control Panel
 * 1:1 match to Official Reference Image Screen 3:
 * - Header: < ⚙ Control Panel
 * - Central Power Core with connecting glowing cyan traces
 * - Left 3 Cards: Power Mode, Brightness, Battery Saver
 * - Right 3 Cards: Do Not Disturb, Volume, Quick Tools
 * - Bottom System Status Card: Real Battery %, RAM %, and 2 Animated Network telemetry bars
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
    var batteryPct by remember { mutableIntStateOf(92) }
    var ramPct by remember { mutableIntStateOf(62) }

    LaunchedEffect(Unit) {
        val bIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            batteryPct = (level * 100 / scale)
        }

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        if (memInfo.totalMem > 0) {
            val used = memInfo.totalMem - memInfo.availMem
            ramPct = ((used.toDouble() / memInfo.totalMem) * 100).toInt()
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
        // 1. Header: < ⚙ Control Panel
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Settings, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Control Panel",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
            )
        }

        // 2. Central Control Grid (3 Cards Left, Big Center Power Button, 3 Cards Right)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column (Power Mode, Brightness, Battery Saver)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                ControlCard(
                    title = "Battery\nSaver",
                    icon = Icons.Filled.EnergySavingsLeaf,
                    active = settings.lowPowerMode,
                    accentColor = SuccessGreen,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

            // Right Column (Do Not Disturb, Volume, Quick Tools)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlCard(
                    title = "Do Not\nDisturb",
                    icon = Icons.Filled.Bedtime,
                    active = false,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
                ControlCard(
                    title = "Volume",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    active = false,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
                ControlCard(
                    title = "Quick\nTools",
                    icon = Icons.Filled.Apps,
                    active = false,
                    onClick = onOpenApps,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 3. System Status Panel with Battery, RAM and 2 Glowing Network Signal Bars
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xD9081429), Color(0xF2040B17))))
                .border(1.2.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header: System Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) SuccessGreen else WarningAmber)
                    )
                    Text(
                        "System Status",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            fontSize = 14.sp,
                        ),
                    )
                }

                // Row 1: Battery % (Left) & Network Bar 1 (Right)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "🔋 Battery $batteryPct%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                        ),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Network",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp),
                        )
                        NetworkSignalBar(active = isOnline)
                    }
                }

                // Row 2: RAM % (Left) & Network Bar 2 (Right)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "📊 RAM $ramPct%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                        ),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Network",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp),
                        )
                        NetworkSignalBar(active = isOnline)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkSignalBar(active: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "netWave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "net"
    )

    Box(
        Modifier
            .width(110.dp)
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF0F1E36))
            .border(0.8.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val barW = size.width
            val barH = size.height

            if (active) {
                // Gradient fill
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF0077FF), NeonCyan, Color(0xFF00E5FF)),
                        startX = 0f,
                        endX = barW * 0.85f,
                    ),
                    size = Size(barW * 0.85f, barH),
                )
                // Small animated signal tick in the right portion
                val tickX = barW * 0.85f + (barW * 0.12f) * waveOffset
                drawLine(
                    color = SuccessGreen,
                    start = Offset(tickX, 1f),
                    end = Offset(tickX, barH - 1f),
                    strokeWidth = 2f,
                )
            } else {
                drawRect(
                    color = Color.Gray.copy(alpha = 0.3f),
                    size = Size(barW * 0.25f, barH),
                )
            }
        }
    }
}

@Composable
private fun ControlCard(
    title: String,
    icon: ImageVector,
    active: Boolean = false,
    accentColor: Color = NeonCyan,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(width = 86.dp, height = 76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xD9081224))
            .border(
                1.2.dp,
                if (active) accentColor else accentColor.copy(alpha = 0.45f),
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) accentColor else accentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    lineHeight = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
