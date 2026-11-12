package com.raju.aiassistant.ui.home



import com.raju.aiassistant.ui.components.crystalSurface
import android.Manifest

import android.os.SystemClock

import androidx.compose.runtime.mutableLongStateOf

import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned

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
    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle(initialValue = 0f)



    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->

        if (granted) viewModel.startAlwaysListening() else viewModel.micDenied()

    }

    var lastOrbTap by remember { mutableLongStateOf(0L) }
    // Where the eye area and the canvas are on screen: read only while drawing (no recomposition).
    val eyeBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val canvasOrigin = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // The last touch on the eye (canvas position, animation time): the eye looks towards it.
    val eyeTouch = remember { mutableStateOf<Pair<androidx.compose.ui.geometry.Offset, Float>?>(null) }

    LaunchedEffect(state.paused, assistantState) {
        when {
            // OFF: no standby, no wake word, no microphone — enforced again on every state change.
            state.paused -> if (assistantState != AssistantState.Idle) viewModel.enforceOff()
            viewModel.hasMicPermission() && assistantState == AssistantState.Idle -> viewModel.ensureStandby()
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

    // Home's two looks: the Living Eye (default) or Liquid Glass; the pill under the ON/OFF bar switches.
    val glass = state.homeStyle == "GLASS"



    Box(

        Modifier

            .fillMaxSize()

            .background(
                Brush.verticalGradient(
                    if (glass) com.raju.aiassistant.ui.home.visuals.GLASS_BACKGROUND
                    else listOf(SciFiBackgroundTop, Color(0xFF040A18), SciFiBackgroundBottom),
                ),
            )

    ) {

        if (glass) com.raju.aiassistant.ui.home.visuals.LiquidGlassVisualizer(
            time = { timeProvider.floatValue },
            speechLevel = { speechLevel },
            micLevel = { micLevel },
            state = assistantState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { canvasOrigin.value = it.positionInRoot() },
            richEffects = profile.richEffects,
            sphereArea = { eyeBounds.value?.translate(-canvasOrigin.value) },
        ) else
        // One canvas, one draw pass, edge to edge behind everything: the ambient waves first, then the
        // Living Eye over them, placed where the eye area (above the buttons) is laid out.
        com.raju.aiassistant.ui.home.visuals.LivingEyeVisualizer(
            time = { timeProvider.floatValue },
            speechLevel = { speechLevel },
            micLevel = { micLevel },
            state = assistantState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { canvasOrigin.value = it.positionInRoot() },
            richEffects = profile.richEffects,
            waveCenterY = 0.42f,
            // Tapori mode keeps its tone colours while she speaks (orange roast, pink joke).
            accentArgb = if (state.tapori && assistantState == com.raju.aiassistant.domain.model.AssistantState.Speaking) {
                com.raju.aiassistant.ui.home.visuals.taporiRimArgb(assistantState, com.raju.aiassistant.ui.home.visuals.taporiMoodFor(state.lastReply))
            } else {
                com.raju.aiassistant.ui.home.visuals.eyeAccentArgb(assistantState)
            },
            eyeArea = { eyeBounds.value?.translate(-canvasOrigin.value) },
            lastTouch = { eyeTouch.value },
        )

        Column(

            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp),

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



                // Center Title Banner: takes the room that is left, so the state capsule on the right is
                // never squeezed ("STANDBY" was cut to "STAN").
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {

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
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            letterSpacing = 0.3.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f),
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                    .crystalSurface(shape = RoundedCornerShape(16.dp), accent = Color(0xFF38C8FF))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (glass) GlassInfoLine() else
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

                        .crystalSurface(shape = RoundedCornerShape(20.dp), accent = if (isMuted) Color(0xFFFF1744) else Color(0xFF00E5FF))

                        .clickable { viewModel.toggleMute() }

                        .padding(horizontal = 14.dp, vertical = 5.dp),

                    contentAlignment = Alignment.Center,

                ) {

                    Text(

                        // OFF = Seema neither listens nor speaks ("Hey Seema", notifications, calls too); tap again for ON.
                        text = if (isMuted) "⛔ SEEMA OFF — कुछ नहीं सुनेगी (दबाकर चालू करें)" else "🟢 SEEMA ON — दबाकर बंद करें",

                        fontSize = 14.5.sp,

                        fontWeight = FontWeight.Bold,

                        color = if (isMuted) Color(0xFFFF5252) else Color(0xFF00E5FF),

                        letterSpacing = 0.8.sp,

                    )

                }

            }



            HomeStyleSwitch(glass = glass, onSelect = viewModel::setHomeStyle)

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




                    // The eye is drawn by the full-screen canvas behind; this box only marks where it goes
                    // (and stays the tap target).
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { eyeBounds.value = it.boundsInRoot() }
                            // Watch touches (without taking them: the tap still toggles listening) so the
                            // eye can turn towards the finger.
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull() ?: continue
                                        val box = eyeBounds.value ?: continue
                                        eyeTouch.value = (box.topLeft + change.position - canvasOrigin.value) to timeProvider.floatValue
                                    }
                                }
                            }
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

                            fontSize = 16.sp,

                            fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal

                        )

                    )

                }

            }



            // 3. Lower Cockpit Section: 6-Button Dock + Status Bar (Anchored to the very bottom!)

            Column(

                Modifier.fillMaxWidth(),

                verticalArrangement = Arrangement.spacedBy(8.dp),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {

                // The Living Eye above is the one voice control (tap: listen / wake); the old voice orb
                // and its two waveform bars are gone, which leaves room around the greeting.

                // Bottom Anchored 6 HUD Cut-Corner Navigation Buttons with Glassy Sky-Blue Sheen

                Column(

                    Modifier.fillMaxWidth(),

                    verticalArrangement = Arrangement.spacedBy(8.dp)

                ) {

                    // Row 1: AI Brain, Voice Center, Memory

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.spacedBy(12.dp)

                    ) {

                        SciFiHomeNavButton(

                            title = "AI Brain",

                            icon = Icons.Filled.Psychology,

                            iconColor = com.raju.aiassistant.ui.components.Premium.NeonBrain,

                            onClick = onOpenAiBrain,

                            modifier = Modifier.weight(1f)

                        )

                        SciFiHomeNavButton(

                            title = "Voice Center",

                            icon = Icons.Filled.GraphicEq,

                            iconColor = com.raju.aiassistant.ui.components.Premium.NeonVoice,

                            onClick = onOpenVoiceControl,

                            modifier = Modifier.weight(1f)

                        )

                        SciFiHomeNavButton(

                            title = "Memory",

                            icon = Icons.Filled.Storage,

                            iconColor = com.raju.aiassistant.ui.components.Premium.NeonMemory,

                            onClick = onOpenMemoryCenter,

                            modifier = Modifier.weight(1f)

                        )

                    }



                    // Row 2: Automation, Apps & Tools, Settings

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.spacedBy(12.dp)

                    ) {

                        SciFiHomeNavButton(

                            title = "Automation",

                            icon = Icons.Filled.AutoAwesome,

                            iconColor = com.raju.aiassistant.ui.components.Premium.NeonAutomation,

                            onClick = onOpenControlPanel,

                            modifier = Modifier.weight(1f)

                        )

                        SciFiHomeNavButton(

                            title = "Apps & Tools",

                            icon = Icons.Filled.Apps,

                            iconColor = com.raju.aiassistant.ui.components.Premium.NeonApps,

                            onClick = onOpenApps,

                            modifier = Modifier.weight(1f)

                        )

                        SciFiHomeNavButton(

                            title = "Settings",

                            icon = Icons.Filled.Settings,

                            iconColor = com.raju.aiassistant.ui.components.Premium.NeonSettings,

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

                        .crystalSurface(shape = CutCornerShape(8.dp), accent = SciFiCyanGlow)

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

                            "SEEMA ONLINE  ·  TAP FOR CHAT",

                            style = MaterialTheme.typography.labelSmall.copy(

                                fontWeight = FontWeight.Bold,

                                color = Color.White.copy(alpha = 0.9f),

                                fontSize = 14.sp, // one line on the phone,

                                letterSpacing = 1.sp

                            )

                        )

                    }

                }

            }

        }

    }

}



/** The small pill under the ON/OFF bar: "👁 आँख" or "◉ Liquid Glass" — Home's two looks. */
@Composable
private fun HomeStyleSwitch(glass: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("EYE" to "👁  आँख", "GLASS" to "◉  Liquid Glass").forEach { (key, label) ->
            val on = (key == "GLASS") == glass
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (on) Color(0xFFE0B061).copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { if (!on) onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(label, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color(0xFFF5C26B) else Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

/** Liquid Glass's card: today's date in gold, and the next Tatkal opening when it is within three hours. */
@Composable
private fun GlassInfoLine() {
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = java.time.LocalDateTime.now()
        }
    }
    val days = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")
    val months = listOf("जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
    val next = com.raju.aiassistant.domain.tatkal.tatkalWindows(now)
        .firstOrNull { java.time.Duration.between(now, it.opensAt).seconds in 0..3 * 3600 }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${days[now.dayOfWeek.value - 1]}, ${now.dayOfMonth} ${months[now.monthValue - 1]}",
            style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFFF5C26B), fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        )
        Text(
            next?.let { "${if (it.label == com.raju.aiassistant.domain.tatkal.TatkalAlarm.AC) "AC" else "Sleeper"} Tatkal: ${com.raju.aiassistant.domain.tatkal.tatkalCountdown(it, now)}" }
                ?: "Bolkar ya likhkar poochhiye",
            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.75f)),
        )
    }
}
