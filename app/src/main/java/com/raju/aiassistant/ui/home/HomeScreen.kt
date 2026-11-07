package com.raju.aiassistant.ui.home



import android.Manifest

import android.os.SystemClock

import androidx.compose.runtime.mutableLongStateOf

import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box


import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.CutCornerShape

import androidx.compose.foundation.shape.RoundedCornerShape

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


import com.raju.aiassistant.ui.home.visuals.motionProfileFor


import com.raju.aiassistant.ui.home.visuals.rememberAnimationTime

import com.raju.aiassistant.ui.theme.SuccessGreen



/**

 * Screen: SEEMA AI Master Sci-Fi Cockpit HUD Home Screen

 * Perfectly anchored edge-to-edge layout with ZERO dead space:

 * - Top Bar: Live Cybernetic Digital Clock (Left) + Live AI State Capsule Widget (Right)

 * - Upper Center (weight 1f): Majestic 3D Living Arc Reactor dynamically expanding to fill space

 * - Center Greeting: "Hello Raju!" + Live conversational status

 * - Sound Visualizer: Symmetric Audio Waveforms + Center ChatGPT-Style Living Fluid Voice Orb

 * - Bottom Anchored 6-Button HUD Dock with Glassy Sky-Blue Sheen (Flush with bottom):

 *     Row 1: [AI Brain] [Voice Center] [Memory]

 *     Row 2: [Automation] [Apps & Tools] [Settings]

 * - Bottom Dock Status Bar: 🟢 SEEMA CORE v2.5 | 100% ONLINE | TAP FOR CHAT

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

    var lastOrbTap by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.paused, assistantState) {
        if (!state.paused && viewModel.hasMicPermission() && assistantState == AssistantState.Idle) {
            viewModel.ensureStandby()
        }
    }



    val profile = motionProfileFor(

        lowPowerMode = state.lowPowerMode || state.systemPowerSave,

        reducedMotion = state.reducedMotion,

        systemAnimationsEnabled = true,

        thermal = state.thermal,

    )

    val timeProvider = rememberAnimationTime(running = profile.animate, maxFps = profile.maxFps)




    val isListening = assistantState == AssistantState.Listening || state.listening

    val isThinking = assistantState == AssistantState.Thinking

    val isSpeaking = assistantState == AssistantState.Speaking



    Box(

        Modifier

            .fillMaxSize()

            .background(Brush.verticalGradient(listOf(SciFiBackgroundTop, Color(0xFF040A18), SciFiBackgroundBottom)))

            .navigationBarsPadding()

            .padding(horizontal = 14.dp, vertical = 6.dp)

    ) {

        Column(

            Modifier.fillMaxSize(),

            horizontalAlignment = Alignment.CenterHorizontally,

            verticalArrangement = Arrangement.SpaceBetween,

        ) {

            // 1. Top HUD Row: Live Digital Clock (Left) <---> Live AI State Capsule (Right)

            Row(

                Modifier

                    .fillMaxWidth()

                    .padding(top = 2.dp, bottom = 4.dp),

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

                            letterSpacing = 2.4.sp,

                            fontSize = 17.5.sp,

                            brush = Brush.horizontalGradient(

                                listOf(Color(0xFF38E1FF), Color(0xFF00F0FF), Color(0xFF7000FF).copy(alpha = 0.85f))

                            )

                        )

                    )

                    Text(

                        "Your Intelligent Companion",

                        style = MaterialTheme.typography.labelSmall.copy(

                            fontSize = 10.sp,

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



            // Greeting card (reference look): a neon panel with a fixed line; it changes nothing.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xCC0B1C3A), Color(0xCC061026))))
                    .border(1.dp, Brush.horizontalGradient(listOf(Color(0x6638E1FF), Color(0xB300F0FF), Color(0x667000FF))), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "How can I help you today?",
                        style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        "Bolkar ya likhkar poochhiye",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f)),
                    )
                }
            }
            // Emergency Silent / Mute Quick Switch Bar

            val isMuted = state.paused

            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(horizontal = 16.dp, vertical = 2.dp),

                horizontalArrangement = Arrangement.Center,

                verticalAlignment = Alignment.CenterVertically,

            ) {

                Box(

                    modifier = Modifier

                        .clip(RoundedCornerShape(20.dp))

                        .background(if (isMuted) Color(0x33FF1744) else Color(0x1F00E5FF))

                        .border(

                            width = 1.2.dp,

                            color = if (isMuted) Color(0xFFFF1744) else Color(0xFF00E5FF).copy(alpha = 0.6f),

                            shape = RoundedCornerShape(20.dp)

                        )

                        .clickable { viewModel.toggleMute() }

                        .padding(horizontal = 14.dp, vertical = 5.dp),

                    contentAlignment = Alignment.Center,

                ) {

                    Text(

                        // OFF = Seema neither listens nor speaks ("Hey Seema", notifications, calls too); tap again for ON.
                        text = if (isMuted) "⛔ SEEMA OFF — कुछ नहीं सुनेगी (दबाकर चालू करें)" else "🟢 SEEMA ON — दबाकर बंद करें",

                        fontSize = 11.5.sp,

                        fontWeight = FontWeight.Bold,

                        color = if (isMuted) Color(0xFFFF5252) else Color(0xFF00E5FF),

                        letterSpacing = 0.8.sp,

                    )

                }

            }



            // 2. Central Section (weight 1f): Takes ALL available upper vertical space!

            Column(

                modifier = Modifier

                    .fillMaxWidth()

                    .weight(1f),

                horizontalAlignment = Alignment.CenterHorizontally,

                verticalArrangement = Arrangement.Center

            ) {

                // Majestic Living Arc Reactor scaled dynamically to fill the upper viewport

                Box(

                    modifier = Modifier

                        .fillMaxWidth()

                        .weight(1f)

                        .clickable(

                            interactionSource = remember { MutableInteractionSource() },

                            indication = null,

                            onClick = {

                                val now = SystemClock.elapsedRealtime()

                                if (now - lastOrbTap >= 600L) {

                                    lastOrbTap = now

                                    if (viewModel.hasMicPermission()) {

                                        viewModel.tapReactor()

                                    } else {

                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)

                                    }

                                }

                            }

                        ),

                    contentAlignment = Alignment.Center,

                ) {




                    // Seema's face (all modes): the dotted ball with eyes and mouth fills the whole area above the buttons.
                    com.raju.aiassistant.ui.home.visuals.TaporiFace(
                        time = { timeProvider.floatValue },
                        speechLevel = { speechLevel },
                        state = assistantState,
                        modifier = Modifier.fillMaxSize(),
                        richEffects = profile.richEffects,
                        mood = if (state.tapori) com.raju.aiassistant.ui.home.visuals.taporiMoodFor(state.lastReply) else com.raju.aiassistant.ui.home.visuals.TaporiMood.NORMAL,
                    )

                }



                // Central Greeting: "Hello Raju!"

                Column(

                    Modifier

                        .fillMaxWidth()

                        .padding(top = 4.dp, bottom = 4.dp),

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

            }



            // 3. Lower Cockpit Section: Audio HUD + 6-Button Dock + Status Bar (Anchored to the very bottom!)

            Column(

                Modifier.fillMaxWidth(),

                verticalArrangement = Arrangement.spacedBy(8.dp),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {

                // Audio HUD: Waveforms + Center ChatGPT-Style Living Fluid Voice Orb

                Row(

                    Modifier

                        .fillMaxWidth()

                        .padding(vertical = 2.dp),

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

                            val now = SystemClock.elapsedRealtime()

                            if (now - lastOrbTap >= 600L) {

                                lastOrbTap = now

                                if (viewModel.hasMicPermission()) {

                                    viewModel.tapReactor()

                                } else {

                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)

                                }

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



                // Bottom Anchored 6 HUD Cut-Corner Navigation Buttons with Glassy Sky-Blue Sheen

                Column(

                    Modifier.fillMaxWidth(),

                    verticalArrangement = Arrangement.spacedBy(8.dp)

                ) {

                    // Row 1: AI Brain, Voice Center, Memory

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        SciFiHomeNavButton(

                            title = "AI Brain",

                            icon = Icons.Filled.Psychology,

                            iconColor = Color(0xFF00E5FF),

                            onClick = onOpenAiBrain,

                            modifier = Modifier.weight(1f)

                        )

                        SciFiHomeNavButton(

                            title = "Voice Center",

                            icon = Icons.Filled.GraphicEq,

                            iconColor = Color(0xFF40C4FF),

                            onClick = onOpenVoiceControl,

                            modifier = Modifier.weight(1f)

                        )

                        SciFiHomeNavButton(

                            title = "Memory",

                            icon = Icons.Filled.Storage,

                            iconColor = Color(0xFFB388FF),

                            onClick = onOpenMemoryCenter,

                            modifier = Modifier.weight(1f)

                        )

                    }



                    // Row 2: Automation, Apps & Tools, Settings

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        SciFiHomeNavButton(

                            title = "Automation",

                            icon = Icons.Filled.AutoAwesome,

                            iconColor = Color(0xFFFFB300),

                            onClick = onOpenControlPanel,

                            modifier = Modifier.weight(1f)

                        )

                        SciFiHomeNavButton(

                            title = "Apps & Tools",

                            icon = Icons.Filled.Apps,

                            iconColor = Color(0xFF00E676),

                            onClick = onOpenApps,

                            modifier = Modifier.weight(1f)

                        )

                        SciFiHomeNavButton(

                            title = "Settings",

                            icon = Icons.Filled.Settings,

                            iconColor = Color(0xFF00E5FF),

                            onClick = onOpenSettings,

                            modifier = Modifier.weight(1f)

                        )

                    }

                }



                // Armored Bottom Dock Status Bar (Flush with bottom edge, clickable to open Chat)

                Box(

                    Modifier

                        .fillMaxWidth()

                        .padding(top = 2.dp)

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

}

