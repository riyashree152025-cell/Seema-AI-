package com.raju.aiassistant.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.home.visuals.ArcReactor
import com.raju.aiassistant.ui.home.visuals.motionProfileFor
import com.raju.aiassistant.ui.home.visuals.reactorStyleFor
import com.raju.aiassistant.ui.home.visuals.rememberAnimationTime
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.WarningAmber

/**
 * Screen 1: Home Screen
 * 1:1 match to Official Reference Image Screen 1:
 * - Top Bar: Voice Waveform Button (left), SEEMA AI Title & Subtitle (center), Online Capsule (right)
 * - Center Stage: 3D Metallic Arc Reactor (Struts, Titanium Collar, Gold Flanges, Oscillating Cyan Soundwave)
 * - Bottom Greeting Card: 'Hello Dinesh! / How can I help you today?'
 */
@Composable
fun HomeScreen(
    onOpenVoiceControl: () -> Unit = {},
    onOpenControlPanel: () -> Unit = {},
    onOpenAiBrain: () -> Unit = {},
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF030712), Color(0xFF020409), Color(0xFF010205))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 1. Reference Screen 1 Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: Voice Wave Button
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x3300E5FF))
                        .border(1.5.dp, NeonCyan.copy(alpha = 0.8f), CircleShape)
                        .clickable { onOpenVoiceControl() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = "Voice Control",
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Center: Title & Subtitle (Clickable to open AI Brain)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onOpenAiBrain() }
                ) {
                    Text(
                        "SEEMA AI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "YOUR INTELLIGENT COMPANION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    )
                }

                // Right: Online / Offline Status Capsule (Clickable to open Control Panel)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x3300E5FF))
                        .border(
                            1.2.dp,
                            if (state.online) SuccessGreen.copy(alpha = 0.9f) else WarningAmber.copy(alpha = 0.9f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onOpenControlPanel() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.online) SuccessGreen else WarningAmber)
                    )
                    Text(
                        if (state.online) "Online" else "Offline",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (state.online) SuccessGreen else WarningAmber
                        )
                    )
                }
            }

            // 2. Center Stage: Official 3D Metallic Arc Reactor
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val reactorSize = minOf(maxWidth, maxHeight) * 0.95f
                Box(
                    Modifier
                        .size(reactorSize)
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

            // 3. Reference Screen 1 Chamfered Greeting Glass Card
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xD9081426), Color(0xF2040915))
                        )
                    )
                    .border(
                        1.2.dp,
                        Brush.horizontalGradient(
                            listOf(
                                NeonCyan.copy(alpha = 0.25f),
                                NeonCyan.copy(alpha = 0.90f),
                                NeonCyan.copy(alpha = 0.25f)
                            )
                        ),
                        RoundedCornerShape(18.dp),
                    )
                    .clickable { onOpenChat() }
                    .padding(vertical = 12.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Hello Dinesh!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp,
                        )
                    )
                    Text(
                        "How can I help you today?",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NeonCyan.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                        )
                    )
                }
            }
        }
    }
}
