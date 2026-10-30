package com.raju.aiassistant.ui.home

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.raju.aiassistant.data.weather.WeatherState
import com.raju.aiassistant.data.weather.weatherLabel
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
fun HoloClock(color: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
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
    val glow = MaterialTheme.typography.headlineMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = if (compact) 26.sp else 34.sp,
        shadow = Shadow(color.copy(alpha = 0.9f), blurRadius = 24f),
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.graphicsLayer {
                rotationX = 18f // tilted back: a real 3D layer, not a flat label
                cameraDistance = 8f * density
            },
        ) {
            // Each digit rolls up on its own when it changes (only the seconds move every second).
            time.forEachIndexed { i, ch ->
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "digit$i",
                ) { c -> Text(c.toString(), Modifier.padding(horizontal = 1.dp), style = glow, color = color) }
            }
        }
        Text(date, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
    }
}

/** Battery, RAM, storage and CPU in glass panels, refreshed every 15 s while visible. CPU load is "N/A" (not available to apps). */
@Composable
fun DeviceStatsRow(accent: Color, weather: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier, fill: Boolean = true) {
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
    // fill = share the screen width; otherwise fixed-width tiles (for a scrolling strip on small screens).
    Row(if (fill) modifier.fillMaxWidth() else modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        fun item(weight: Float = 1f, width: Int = 72) = if (fill) Modifier.weight(weight) else Modifier.width(width.dp)
        weather(item(1.2f, 92))
        StatTile("BATTERY", s?.batteryPercent?.let { "$it%" + if (s.charging == true) " ⚡" else "" } ?: "N/A", accent, item())
        StatTile("RAM", s?.ramUsedGb?.let { "%.1f/%.0fG".format(it, s.ramTotalGb ?: 0f) } ?: "N/A", accent, item())
        StatTile("FREE", s?.storageFreeGb?.let { "%.0f GB".format(it) } ?: "N/A", accent, item())
        StatTile("CPU", s?.let { "${it.cpuCores} core" } ?: "N/A", accent, item())
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier
            .hudFrame(accent, cut = 6.dp, fillAlpha = 0.10f)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
        // Values slide in when they change (an animated info card, not a static label).
        AnimatedContent(value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = label) { v ->
            Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Real weather (Open-Meteo, city-level location). Loads when the dashboard is visible and every
 * 30 minutes; tap to retry, or to allow location the first time. Never shows made-up values.
 */
@Composable
fun WeatherTile(accent: Color, load: suspend (force: Boolean) -> WeatherState, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf<WeatherState>(WeatherState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, attempt) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var force = attempt > 0
            while (true) {
                if (state !is WeatherState.Ready) state = WeatherState.Loading
                state = runCatching { load(force) }.getOrElse { WeatherState.Unavailable("weather service unreachable") }
                force = false
                delay(30 * 60_000L)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { attempt++ }
    val (label, value) = when (val s = state) {
        WeatherState.Loading -> "WEATHER" to "…"
        WeatherState.NeedsPermission -> "WEATHER" to "Tap: allow"
        is WeatherState.Unavailable -> "WEATHER" to when {
            s.reason == "offline" -> "Offline"
            s.reason.startsWith("location") -> "No location"
            else -> "N/A ↻"
        }
        is WeatherState.Ready -> weatherLabel(s.weather.code, s.weather.isDay).let { (text, emoji) ->
            text.uppercase() to "$emoji ${s.weather.temperatureC.roundToInt()}°C"
        }
    }
    StatTile(
        label, value, accent,
        modifier
            .clickable(onClickLabel = "Weather") {
                if (state == WeatherState.NeedsPermission) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else attempt++
            }
            .semantics { contentDescription = "Weather: $label $value" + ((state as? WeatherState.Unavailable)?.let { ", ${it.reason}" } ?: "") },
    )
}

/**
 * Quick controls with real status: Wi-Fi and Bluetooth (Android lets apps only open their
 * settings, not switch them), the torch (switched directly), camera and phone settings.
 */
@Composable
fun QuickControlsRow(accent: Color, modifier: Modifier = Modifier, fill: Boolean = true) {
    val context = LocalContext.current
    var wifiOn by remember { mutableStateOf<Boolean?>(null) }
    var btOn by remember { mutableStateOf<Boolean?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val cameraManager = remember { context.getSystemService(CameraManager::class.java) }
    val torchId = remember {
        runCatching { cameraManager?.cameraIdList?.firstOrNull { cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull()
    }
    // The torch's real state (also when switched from the notification shade).
    DisposableEffect(cameraManager, torchId) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) torchOn = enabled
            }
        }
        if (torchId != null) runCatching { cameraManager?.registerTorchCallback(cb, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { cameraManager?.unregisterTorchCallback(cb) } }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                wifiOn = runCatching { context.getSystemService(WifiManager::class.java)?.isWifiEnabled }.getOrNull()
                btOn = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull()
                delay(5_000)
            }
        }
    }
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "Not available on this phone", Toast.LENGTH_SHORT).show() }
    }
    Row(if (fill) modifier.fillMaxWidth() else modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        fun item() = if (fill) Modifier.weight(1f) else Modifier.width(68.dp)
        QuickControl("Wi-Fi", if (wifiOn == false) Icons.Filled.WifiOff else Icons.Filled.Wifi, wifiOn, accent, item()) {
            open(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        QuickControl("Bluetooth", if (btOn == false) Icons.Filled.BluetoothDisabled else Icons.Filled.Bluetooth, btOn, accent, item()) {
            open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        QuickControl("Torch", if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, if (torchId == null) null else torchOn, accent, item()) {
            if (torchId == null) Toast.makeText(context, "No flashlight on this phone", Toast.LENGTH_SHORT).show()
            else runCatching { cameraManager?.setTorchMode(torchId, !torchOn) }
                .onFailure { Toast.makeText(context, "Flashlight is busy (camera in use?)", Toast.LENGTH_SHORT).show() }
        }
        QuickControl("Camera", Icons.Filled.PhotoCamera, null, accent, item()) {
            open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        }
        QuickControl("Phone", Icons.Filled.Tune, null, accent, item()) {
            open(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

/** A glass quick-control button; [on] lights it up (null = no on/off state, or unknown). */
@Composable
private fun QuickControl(label: String, icon: ImageVector, on: Boolean?, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val lit = on == true
    val stateText = when (on) { true -> "on"; false -> "off"; null -> "" }
    Column(
        modifier
            .hudFrame(accent, cut = 6.dp, fillAlpha = if (lit) 0.30f else 0.08f)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = "$label $stateText".trim() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (lit) Color.White else accent, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (lit) Color.White else TextSecondary, maxLines = 1)
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

/**
 * The 3D dashboard's control dock: a slim glass capsule with Chat, Stop and Settings as small
 * glowing buttons and the microphone in the middle inside a turning holographic ring
 * (it spins faster and glows while the mic is on). Same actions as the classic buttons.
 */
@Composable
fun HoloDock(
    accent: Color,
    micActive: Boolean,
    micLabel: String,
    stopEnabled: Boolean,
    time: () -> Float,
    onChat: () -> Unit,
    onMic: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), Color(0xCC050B18))))
            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.15f), accent.copy(alpha = 0.8f), accent.copy(alpha = 0.15f))), shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DockButton("Chat", Icons.AutoMirrored.Filled.Chat, accent, true, onChat)
        DockMic(micActive, micLabel, accent, time, onMic)
        DockButton("Stop", Icons.Filled.Stop, accent, stopEnabled, onStop)
        DockButton("Settings", Icons.Filled.Settings, accent, true, onSettings)
    }
}

@Composable
private fun DockButton(label: String, icon: ImageVector, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) accent else accent.copy(alpha = 0.3f)
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.28f), Color.Transparent)), CircleShape)
                .border(1.dp, tint.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp)) }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp), color = tint, maxLines = 1)
    }
}

@Composable
private fun DockMic(active: Boolean, label: String, accent: Color, time: () -> Float, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time()
            val spin = (t * (if (active) 220f else 60f)) % 360f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (active) 0.55f else 0.28f), Color.Transparent)), radius = size.minDimension / 2f)
            val stroke = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round)
            val inset = 3.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(accent, spin, 100f, false, topLeft, arcSize, style = stroke)
            drawArc(accent.copy(alpha = 0.6f), spin + 180f, 60f, false, topLeft, arcSize, style = stroke)
            drawCircle(accent.copy(alpha = 0.35f), radius = size.minDimension / 2f - 9.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
        Icon(if (active) Icons.Filled.Mic else Icons.Filled.MicNone, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}
