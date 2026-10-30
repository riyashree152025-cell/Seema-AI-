package com.raju.aiassistant.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.raju.aiassistant.core.power.DeviceStats
import com.raju.aiassistant.ui.components.hudFrame
import com.raju.aiassistant.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The 3D core's digital clock: the time tilted back in real 3D (perspective) with a neon glow,
 * and the date under it. Ticks once a second, only while the screen is visible.
 */
@Composable
fun HoloClock(color: Color, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000 - now % 1_000) // tick exactly on the second
            }
        }
    }
    val time = remember(now / 1000) { SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date(now)) }
    val date = remember(now / 60_000) { SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(Date(now)).uppercase() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            time,
            modifier = Modifier.graphicsLayer {
                rotationX = 18f // tilted back: a real 3D layer, not a flat label
                cameraDistance = 8f * density
            },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = 4.sp,
                shadow = Shadow(color.copy(alpha = 0.9f), blurRadius = 24f),
            ),
            color = color,
        )
        Text(date, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
    }
}

/** Battery, RAM, storage and CPU in glass panels, refreshed every 15 s while visible. CPU load is "N/A" (not available to apps). */
@Composable
fun DeviceStatsRow(accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<DeviceStats?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = runCatching { DeviceStats.read(context) }.getOrNull()
                delay(15_000)
            }
        }
    }
    val s = stats
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatTile("BATTERY", s?.batteryPercent?.let { "$it%" + if (s.charging == true) " ⚡" else "" } ?: "N/A", accent, Modifier.weight(1f))
        StatTile("RAM", s?.ramUsedGb?.let { "%.1f/%.0fG".format(it, s.ramTotalGb ?: 0f) } ?: "N/A", accent, Modifier.weight(1f))
        StatTile("STORAGE", s?.storageFreeGb?.let { "%.0fG free".format(it) } ?: "N/A", accent, Modifier.weight(1f))
        StatTile("CPU", s?.let { "${it.cpuCores} cores · N/A" } ?: "N/A", accent, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier
            .hudFrame(accent, cut = 6.dp, fillAlpha = 0.10f)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** The 3D core's large round voice button (same action as the Voice/Talk button). */
@Composable
fun BigVoiceButton(active: Boolean, label: String, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent.copy(alpha = if (active) 0.45f else 0.18f), contentColor = Color.White),
        ) {
            Icon(if (active) Icons.Filled.Mic else Icons.Filled.MicNone, contentDescription = label, modifier = Modifier.size(38.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    }
}
