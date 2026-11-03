package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.WarningAmber

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.BuildConfig
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.theme.NeonCyan
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen 7: Settings Screen
 * Matches Reference Image: Left Column with 6 Group Cards, Right Column with 3D Animated Metallic Gear Mechanism.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = RajuViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val s = state.settings
    val context = LocalContext.current

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.refreshDiagnostics()
    }
    val diag by viewModel.diagnostics.collectAsStateWithLifecycle()

    var activeDialog by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040714), Color(0xFF02040A))))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header: < Settings
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
            Icon(Icons.Filled.Settings, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
            )
        }

        // Split Layout: Left 6 Category Cards, Right 3D Metallic Mechanical Gear
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column (6 Category Cards)
            Column(
                Modifier.weight(1.35f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsCategoryCard(
                    title = "AI Assistant Settings",
                    icon = Icons.Filled.Psychology,
                    onClick = { activeDialog = "ai" },
                )
                SettingsCategoryCard(
                    title = "Voice & Language",
                    icon = Icons.Filled.Mic,
                    onClick = { activeDialog = "voice" },
                )
                SettingsCategoryCard(
                    title = "Privacy & Security",
                    icon = Icons.Filled.Lock,
                    onClick = { activeDialog = "privacy" },
                )
                SettingsCategoryCard(
                    title = "Notifications",
                    icon = Icons.Filled.Notifications,
                    onClick = { activeDialog = "notifications" },
                )
                SettingsCategoryCard(
                    title = "Appearance",
                    icon = Icons.Filled.Palette,
                    onClick = { activeDialog = "appearance" },
                )
                SettingsCategoryCard(
                    title = "About SEEMA AI",
                    icon = Icons.Filled.Info,
                    onClick = { activeDialog = "about" },
                )
            }

            // Right Column (Animated 3D Metallic Mechanical Gear)
            Box(
                Modifier
                    .weight(0.95f)
                    .height(290.dp),
                contentAlignment = Alignment.Center,
            ) {
                MetallicGearCanvas(Modifier.fillMaxSize())
            }
        }
    }

    // Detail Dialogs for Categories (Preserving all actual backend settings)
    when (activeDialog) {
        "ai" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("AI Assistant Settings", color = Color.White) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Active Provider: ${s.defaultProvider.name}", color = NeonCyan, fontWeight = FontWeight.Bold)
                        ProviderId.entries.forEach { p ->
                            Button(
                                onClick = { viewModel.setDefaultProvider(p) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(p.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", color = NeonCyan)
                    }
                },
            )
        }
        "voice" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Voice & Language", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active Engine: ${s.ttsEngine.label}", color = NeonCyan)
                        Text("Language: ${s.speechLanguage}", color = Color(0xFFC0D5F0))
                        Text("Speech Rate: ${s.speechRate}x", color = Color(0xFFC0D5F0))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", color = NeonCyan)
                    }
                },
            )
        }
        "privacy" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Privacy & Security", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("• Android Keystore AES-GCM Encryption: Active", color = NeonCyan)
                        Text("• No API keys stored in plain text.", color = Color(0xFFC0D5F0))
                        Text("• Microphone used only with user consent.", color = Color(0xFFC0D5F0))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", color = NeonCyan)
                    }
                },
            )
        }
        "notifications" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Notifications", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Listener Service: ${if (diag.listenerEnabled) "Connected" else "Disabled"}", color = NeonCyan)
                        Text("WhatsApp Announcements: ${if (s.announceNotifications) "Active" else "Off"}", color = Color(0xFFC0D5F0))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", color = NeonCyan)
                    }
                },
            )
        }
        "appearance" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Appearance", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Theme: Midnight Holo (Glossy Black & Cyan)", color = NeonCyan)
                        Text("Active Core: ${s.coreAppearance.label}", color = Color(0xFFC0D5F0))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", color = NeonCyan)
                    }
                },
            )
        }
        "about" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("About SEEMA AI", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SEEMA AI — Personal Assistant", fontWeight = FontWeight.Bold, color = NeonCyan)
                        Text("Version: ${BuildConfig.VERSION_NAME}", color = Color(0xFFC0D5F0))
                        Text("Package: ${context.packageName}", color = Color(0xFF8BA5CC), fontSize = 12.sp)
                        Text("Running on Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", color = Color(0xFF8BA5CC), fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", color = NeonCyan)
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xD90A152C), Color(0xF2060D1C))))
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                    ),
                    maxLines = 1,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = NeonCyan.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun MetallicGearCanvas(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gearAnim")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Restart),
        label = "gearRot",
    )
    val counterRot by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "counterRot",
    )

    Canvas(modifier = modifier) {
        val c = center
        val r = (size.minDimension / 2f) * 0.92f

        // Outer Gear Teeth & Ring
        val teeth = 12
        val toothDepth = r * 0.12f
        val innerR = r - toothDepth
        val toothAngle = 360f / teeth

        rotate(rotation, c) {
            // Metallic Outer Rim
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF2E3E5C), Color(0xFF131E33), Color(0xFF090E1A)),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )

            // Gear Teeth
            repeat(teeth) { i ->
                val a = Math.toRadians((i * toothAngle).toDouble())
                val x = c.x + (r - toothDepth * 0.5f) * cos(a).toFloat()
                val y = c.y + (r - toothDepth * 0.5f) * sin(a).toFloat()
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.85f),
                    radius = toothDepth * 0.38f,
                    center = Offset(x, y),
                )
            }

            drawCircle(
                color = NeonCyan.copy(alpha = 0.6f),
                radius = innerR,
                center = c,
                style = Stroke(width = 2.5f),
            )
        }

        // Inner Counter-rotating Cog Ring
        rotate(counterRot, c) {
            val cogR = r * 0.62f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF0077FF), NeonCyan, Color(0xFF0077FF)),
                    center = c,
                ),
                radius = cogR,
                center = c,
                style = Stroke(width = 2.5f),
            )
            repeat(6) { i ->
                val a = Math.toRadians((i * 60f).toDouble())
                drawLine(
                    color = NeonCyan.copy(alpha = 0.7f),
                    start = c,
                    end = Offset(c.x + cogR * cos(a).toFloat(), c.y + cogR * sin(a).toFloat()),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Core Glowing Blue Power Hub
        val coreR = r * 0.32f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, NeonCyan, Color(0xFF0055CC), Color.Transparent),
                center = c,
                radius = coreR * 1.4f,
            ),
            radius = coreR * 1.4f,
            center = c,
        )
        drawCircle(
            color = Color.White,
            radius = coreR * 0.45f,
            center = c,
        )
    }
}


@Composable
internal fun ChipRow(options: List<String>, selected: String, label: (String) -> String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@Composable
internal fun StatusRow(label: String, status: String, ok: Boolean, action: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = if (ok) SuccessGreen else WarningAmber)
        }
        action?.let { (text, onClick) -> TextButton(onClick = onClick) { Text(text) } }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
