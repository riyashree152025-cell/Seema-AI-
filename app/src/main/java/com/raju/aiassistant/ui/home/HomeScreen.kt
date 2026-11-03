package com.raju.aiassistant.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.components.SciFiBackgroundBottom
import com.raju.aiassistant.ui.components.SciFiBackgroundTop
import com.raju.aiassistant.ui.components.SciFiCyanGlow
import com.raju.aiassistant.ui.components.SciFiDigitalClock
import com.raju.aiassistant.ui.components.SciFiHomeNavButton
import com.raju.aiassistant.ui.components.SciFiStateCapsule
import com.raju.aiassistant.ui.components.SciFiVoiceOrb
import com.raju.aiassistant.ui.components.SciFiWaveform
import com.raju.aiassistant.ui.home.visuals.ArcReactor
import com.raju.aiassistant.ui.home.visuals.motionProfileFor
import com.raju.aiassistant.ui.home.visuals.reactorStyleFor
import com.raju.aiassistant.ui.home.visuals.rememberAnimationTime
import com.raju.aiassistant.ui.theme.SuccessGreen

/**
 * Screen: SEEMA AI Master Sci-Fi Cockpit HUD Home Screen
 * - Top Row: Live Cybernetic Digital Clock (Left) + Live AI State Capsule Widget (Right)
 * - Upper Center: Majestic 3D Living Arc Reactor encased in Concentric Energy Tech Rings
 * - Center Greeting: "Hello Raju!" + "How can I help you today?"
 * - Sound Visualizer: Symmetric Audio Waveforms + Center ChatGPT-Style Living Fluid Voice Orb
 * - Bottom Anchored 6-Button HUD Dock with Glassy Sky-Blue Sheen:
 *     Row 1: [AI Brain] [Voice Center] [Memory]
 *     Row 2: [Automation] [Apps & Tools] [Settings]
 * - Bottom Armored Dock Status: 🟢 SEEMA CORE v2.5 | 100% ONLINE | READY
 */
@Composable
fun HomeScreen(
    onOpenVoiceControl: () -> Unit = {},
    onOpenControlPanel: () -> Unit = {},
    onOpenAiBrain: () -> Unit = {},
    onOpenMemoryCenter: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = RajuViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val assistantState = state.previewState ?: state.assistantState
    val speechLevel by viewModel.speechLevel.collectAsStateWithLifecycle(initialValue = 0f)

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startAlwaysListening() else viewModel.micDenied()
    }

    val profile = motionProfileFor(
        lowPowerMode = state.lowPowerMode || state.systemPowerSave,
        reducedMotion = state.reducedMotion,
        systemAnimationsEnabled = true,
        thermal = state.thermal,
    )
    val timeProvider = rememberAnimationTime(running = profile.animate, maxFps = profile.maxFps)
    val style = reactorStyleFor(assistantState)

    val isListening = assistantState == AssistantState.Listening || state.listening
    val isThinking = assistantState == AssistantState.Thinking
    val isSpeaking = assistantState == AssistantState.Speaking

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SciFiBackgroundTop, Color(0xFF040A18), SciFiBackgroundBottom)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Top HUD Row: Live Digital Clock (Left) <---> Live AI State Capsule (Right)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: Stylish Cybernetic Digital Clock (HH:mm:ss + Date)
                SciFiDigitalClock()

                // Center Title Banner
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "SEEMA AI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp,
                            fontSize = 18.sp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF38E1FF), Color(0xFF00F0FF), Color(0xFF7000FF).copy(alpha = 0.85f))
                            )
                        )
                    )
                    Text(
                        "Your Intelligent Companion",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    )
                }

                // Right: Live AI State Capsule Widget (Pulsing Dot + Mode + Tap-to-Sleep/Wake)
                SciFiStateCapsule(
                    state = assistantState,
                    isListening = isListening,
                    onToggle = { viewModel.tapReactor() }
                )
            }

            // 2. Central Upper Core: Majestic Living Arc Reactor in Concentric Housing
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(245.dp),
                contentAlignment = Alignment.Center,
            ) {
                val coreSize = minOf(maxWidth, maxHeight) * 0.96f

                // Outer Concentric Cybernetic Tech Ring Frame
                Canvas(Modifier.size(coreSize)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = size.width / 2f

                    // Outer Hex/Segmented Ring
                    drawCircle(
                        color = Color(0x3300E5FF),
                        radius = r - 4f,
                        style = Stroke(width = 2.2f)
                    )
                    // Inner Concentric Cyan Track
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(SciFiCyanGlow.copy(alpha = 0.25f), SciFiCyanGlow.copy(alpha = 0.95f), SciFiCyanGlow.copy(alpha = 0.25f))
                        ),
                        radius = r - 12f,
                        style = Stroke(width = 1.6f)
                    )
                    // Core Reticle Tick Marks
                    val tickCount = 24
                    for (i in 0 until tickCount) {
                        val angle = (i * (360f / tickCount)) * (Math.PI / 180.0)
                        val innerR = r - 22f
                        val outerR = r - 16f
                        val start = androidx.compose.ui.geometry.Offset(
                            (cx + innerR * kotlin.math.cos(angle)).toFloat(),
                            (cy + innerR * kotlin.math.sin(angle)).toFloat()
                        )
                        val end = androidx.compose.ui.geometry.Offset(
                            (cx + outerR * kotlin.math.cos(angle)).toFloat(),
                            (cy + outerR * kotlin.math.sin(angle)).toFloat()
                        )
                        drawLine(
                            color = if (i % 6 == 0) SciFiCyanGlow else SciFiCyanGlow.copy(alpha = 0.4f),
                            start = start,
                            end = end,
                            strokeWidth = if (i % 6 == 0) 2.2f else 1.2f
                        )
                    }
                }

                // Centered Living Arc Reactor with Smooth 3D Mechanics
                ArcReactor(
                    style = style,
                    particleCount = profile.particleCount,
                    richEffects = profile.richEffects,
                    time = { timeProvider.floatValue },
                    speechLevel = { speechLevel },
                    modifier = Modifier.size(coreSize * 0.82f)
                )
            }

            // 3. Central Greeting: "Hello Raju!"
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "Hello Raju!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 21.sp,
                        letterSpacing = 0.8.sp
                    )
                )
                Text(
                    if (isListening) "Listening to you... Speak now"
                    else if (isThinking) "Thinking... Finding best answer"
                    else if (isSpeaking) "Speaking response..."
                    else "How can I help you today?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isListening) SciFiCyanGlow else Color.White.copy(alpha = 0.75f),
                        fontSize = 13.5.sp,
                        fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }

            // 4. Center Audio HUD: Waveforms + ChatGPT-Style Living Fluid Voice Orb
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Audio Waveform Bars
                SciFiWaveform(
                    modifier = Modifier.weight(1f),
                    barCount = 14,
                    color = SciFiCyanGlow,
                    isListening = isListening,
                    speechLevel = speechLevel
                )

                Spacer(Modifier.width(10.dp))

                // Center ChatGPT-Style Living Voice Orb (Fluid Multi-Color Aurora Glow, No Old Mic Icon!)
                SciFiVoiceOrb(
                    isListening = isListening,
                    isThinking = isThinking,
                    isSpeaking = isSpeaking,
                    speechLevel = speechLevel,
                    onClick = {
                        if (viewModel.hasMicPermission()) {
                            if (isListening) viewModel.stop() else viewModel.startVoice()
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )

                Spacer(Modifier.width(10.dp))

                // Right Audio Waveform Bars
                SciFiWaveform(
                    modifier = Modifier.weight(1f),
                    barCount = 14,
                    color = SciFiCyanGlow,
                    isListening = isListening,
                    speechLevel = speechLevel
                )
            }

            // 5. Bottom Anchored 6 HUD Cut-Corner Navigation Buttons with Glassy Sky-Blue Sheen
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: AI Brain, Voice Center, Memory
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SciFiHomeNavButton(
                        title = "AI Brain",
                        icon = Icons.Filled.Psychology,
                        onClick = onOpenAiBrain,
                        modifier = Modifier.weight(1f)
                    )
                    SciFiHomeNavButton(
                        title = "Voice Center",
                        icon = Icons.Filled.GraphicEq,
                        onClick = onOpenVoiceControl,
                        modifier = Modifier.weight(1f)
                    )
                    SciFiHomeNavButton(
                        title = "Memory",
                        icon = Icons.Filled.Storage,
                        onClick = onOpenMemoryCenter,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Automation, Apps & Tools, Settings
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SciFiHomeNavButton(
                        title = "Automation",
                        icon = Icons.Filled.AutoAwesome,
                        onClick = onOpenControlPanel,
                        modifier = Modifier.weight(1f)
                    )
                    SciFiHomeNavButton(
                        title = "Apps & Tools",
                        icon = Icons.Filled.Apps,
                        onClick = onOpenApps,
                        modifier = Modifier.weight(1f)
                    )
                    SciFiHomeNavButton(
                        title = "Settings",
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 6. Armored Bottom Dock Status Bar (Clickable to open Chat)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp)
                    .clip(CutCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0x3800D0FF), Color(0x1F002244), Color(0x3800D0FF))
                        )
                    )
                    .border(1.2.dp, SciFiCyanGlow.copy(alpha = 0.5f), CutCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenChat
                    )
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Text(
                        "🟢 SEEMA CORE v2.5 | 100% ONLINE | TAP FOR CHAT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}
