package com.raju.aiassistant.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.raju.aiassistant.ui.components.SciFiHomeNavButton
import com.raju.aiassistant.ui.components.SciFiWaveform
import com.raju.aiassistant.ui.home.visuals.ArcReactor
import com.raju.aiassistant.ui.home.visuals.motionProfileFor
import com.raju.aiassistant.ui.home.visuals.reactorStyleFor
import com.raju.aiassistant.ui.home.visuals.rememberAnimationTime
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen

/**
 * Screen 1: Home Screen
 * Exact 1:1 match to User Reference Image (Image 4):
 * - Header: Glowing Cyan SEEMA AI + 'Your Intelligent Companion' + Settings gear on top-right
 * - Central Core: Concentric Cybernetic Rings enclosing the living, reactive ARC REACTOR (in place of robot face)
 * - Greeting: 'Hello Raju! / How can I help you today?'
 * - Sound Visualizer: Symmetric Audio Waveform Bars + Center Glowing Mic Button
 * - 6 Beveled HUD Navigation Buttons in a 2x3 Grid:
 *     [AI Brain] [Voice Center] [Memory]
 *     [Automation] [Settings] [More]
 * - Bottom Armored Dock: 🟢 System Online | Ready
 */
@Composable
fun HomeScreen(
    onOpenVoiceControl: () -> Unit = {},
    onOpenControlPanel: () -> Unit = {},
    onOpenAiBrain: () -> Unit = {},
    onOpenMemoryCenter: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = RajuViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val assistantState = state.previewState ?: state.assistantState

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

    val isListening = assistantState == AssistantState.Listening

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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Top Bar: SEEMA AI Title, Subtitle and Top-Right Settings Gear
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "SEEMA AI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.8.sp,
                            fontSize = 24.sp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF38E1FF), Color(0xFF00F0FF), Color(0xFF7000FF).copy(alpha = 0.8f))
                            )
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Your Intelligent Companion",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.80f),
                        )
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(38.dp)
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = SciFiCyanGlow,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 2. Central Core: Living Arc Reactor encased in Concentric Futuristic Housing Rings
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                contentAlignment = Alignment.Center,
            ) {
                val coreSize = minOf(maxWidth, maxHeight) * 0.95f

                // Outer Concentric Cybernetic Tech Ring Frame
                Canvas(Modifier.size(coreSize)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = size.width / 2f

                    // Outer Hex/Segmented Ring
                    drawCircle(
                        color = Color(0x3300E5FF),
                        radius = r - 4f,
                        style = Stroke(width = 2f)
                    )
                    // Inner Concentric Cyan Track
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(SciFiCyanGlow.copy(alpha = 0.2f), SciFiCyanGlow.copy(alpha = 0.9f), SciFiCyanGlow.copy(alpha = 0.2f))
                        ),
                        radius = r - 14f,
                        style = Stroke(width = 2.5f)
                    )
                    // Angular Tech Ticks at 4 Cardinal Points
                    val tickLen = 10f
                    drawLine(SciFiCyanGlow, Offset(cx, cy - r + 4f), Offset(cx, cy - r + 4f + tickLen), strokeWidth = 3f)
                    drawLine(SciFiCyanGlow, Offset(cx, cy + r - 4f), Offset(cx, cy + r - 4f - tickLen), strokeWidth = 3f)
                    drawLine(SciFiCyanGlow, Offset(cx - r + 4f, cy), Offset(cx - r + 4f + tickLen, cy), strokeWidth = 3f)
                    drawLine(SciFiCyanGlow, Offset(cx + r - 4f, cy), Offset(cx + r - 4f - tickLen, cy), strokeWidth = 3f)
                }

                // Interactive Living Arc Reactor Inside the Ring
                Box(
                    Modifier
                        .size(coreSize * 0.82f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (viewModel.hasMicPermission()) {
                                viewModel.tapReactor()
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ArcReactor(
                        style = style,
                        particleCount = profile.particleCount,
                        richEffects = profile.richEffects,
                        time = { timeProvider.floatValue },
                        speechLevel = { viewModel.speechLevel.value },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 3. User Greeting: 'Hello Raju!' / 'How can I help you today?'
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clickable { onOpenChat() }
            ) {
                Text(
                    "Hello Raju!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp,
                    )
                )
                Text(
                    "How can I help you today?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = SciFiCyanGlow.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                )
            }

            // 4. Audio Waveform Visualizer & Central Glowing Mic Button
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Left Audio Wave Bars
                SciFiWaveform(
                    modifier = Modifier.weight(1f),
                    barCount = 14,
                    color = SciFiCyanGlow,
                    isListening = isListening
                )

                Spacer(Modifier.width(10.dp))

                // Center Glowing Circular Mic Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF0072FF), Color(0xFF003882), Color(0xFF030D22))
                            )
                        )
                        .border(
                            1.8.dp,
                            Brush.sweepGradient(listOf(SciFiCyanGlow, Color(0xFF0055FF), SciFiCyanGlow)),
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (viewModel.hasMicPermission()) {
                                viewModel.tapReactor()
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Right Audio Wave Bars
                SciFiWaveform(
                    modifier = Modifier.weight(1f),
                    barCount = 14,
                    color = SciFiCyanGlow,
                    isListening = isListening
                )
            }

            // 5. 6 HUD Cut-Corner Navigation Buttons (3 columns x 2 rows or 2 columns x 3 rows)
            // As shown in reference image: 3 columns x 2 rows
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                        icon = Icons.Filled.Mic,
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
                        title = "Settings",
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                    SciFiHomeNavButton(
                        title = "More",
                        icon = Icons.Filled.GridView,
                        onClick = onOpenChat,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 6. Armored Bottom Dock Bar: 🟢 System Online | Ready
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(CutCornerShape(8.dp))
                    .background(Color(0xE6050E1F))
                    .border(1.dp, Color(0x3300E5FF), CutCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.online) SuccessGreen else Color(0xFFFFB800))
                    )
                    Text(
                        if (state.online) "System Online" else "Offline Mode",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp
                        )
                    )
                    Text(
                        "|",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    )
                    val statusText = when (assistantState) {
                        AssistantState.Idle -> "Ready"
                        AssistantState.Listening -> "Listening..."
                        AssistantState.Thinking -> "Thinking..."
                        AssistantState.Speaking -> "Speaking..."
                        AssistantState.Offline -> "Offline"
                        else -> "Ready"
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (assistantState) {
                                AssistantState.Listening -> NeonCyan
                                AssistantState.Thinking -> Color(0xFFA88BFF)
                                AssistantState.Speaking -> SuccessGreen
                                else -> SuccessGreen
                            },
                            fontSize = 11.5.sp
                        )
                    )
                }
            }
        }
    }
}
