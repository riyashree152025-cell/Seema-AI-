package com.raju.aiassistant.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.BuildConfig
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.components.StatusChip
import com.raju.aiassistant.ui.home.visuals.ArcReactor
import com.raju.aiassistant.ui.home.visuals.CyberBackground
import com.raju.aiassistant.ui.home.visuals.motionProfileFor
import com.raju.aiassistant.ui.home.visuals.reactorStyleFor
import com.raju.aiassistant.ui.home.visuals.rememberAnimationTime
import com.raju.aiassistant.ui.home.visuals.statusHintFor
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.EmeraldBright
import com.raju.aiassistant.ui.theme.HoloCyan
import com.raju.aiassistant.ui.components.DecodingText
import com.raju.aiassistant.ui.components.VoiceWaveform
import com.raju.aiassistant.ui.components.hudFrame
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber
import kotlin.math.abs
import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.raju.aiassistant.domain.power.ActivityPhase
import com.raju.aiassistant.domain.power.IDLE_AFTER_MS
import com.raju.aiassistant.domain.power.SLEEP_AFTER_MS
import com.raju.aiassistant.domain.power.ThermalLevel
import com.raju.aiassistant.domain.power.activityPhaseFor
import kotlinx.coroutines.delay
import com.raju.aiassistant.domain.model.VoiceMode
import kotlin.math.sin
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import com.raju.aiassistant.domain.model.CoreAppearance
import com.raju.aiassistant.ui.home.visuals.HoloCore
import com.raju.aiassistant.ui.home.visuals.HoloCodeStream
import com.raju.aiassistant.ui.home.visuals.holoCoreStyleFor

@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = RajuViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val assistantState = state.assistantState
    // Only the chosen AI core runs; both follow the same assistant state.
    val modern = state.core == CoreAppearance.MODERN
    val style = if (modern) holoCoreStyleFor(assistantState) else reactorStyleFor(assistantState)

    // Smart power: when nobody touches the screen and Seema is not busy, the visuals slow down
    // (idle) and then stop drawing altogether (sleep) until the next touch or event.
    var lastTouch by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var phase by remember { mutableStateOf(ActivityPhase.ACTIVE) }
    val engaged = state.previewState != null || state.listening ||
        assistantState !in setOf(AssistantState.Idle, AssistantState.Offline, AssistantState.Sleeping)
    LaunchedEffect(engaged, assistantState, lastTouch, state.smartPower) {
        phase = activityPhaseFor(state.smartPower, engaged, 0)
        if (engaged || !state.smartPower) return@LaunchedEffect
        delay(IDLE_AFTER_MS)
        phase = ActivityPhase.IDLE
        delay(SLEEP_AFTER_MS - IDLE_AFTER_MS)
        phase = ActivityPhase.SLEEP
    }
    val thermal = if (state.thermalProtection) state.thermal else ThermalLevel.NORMAL
    val batterySaver = state.lowPowerMode || (state.smartPower && state.systemPowerSave)
    val profile = motionProfileFor(batterySaver, state.reducedMotion, rememberSystemAnimationsEnabled(), thermal, phase)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = profile.animate && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val time = rememberAnimationTime(running, profile.maxFps)
    val timeProvider = { time.floatValue }

    // Real speech level comes from the audio being played. In the "Speaking" preview a synthetic
    // level is animated purely so the speech-reactive visuals can be checked.
    val realLevel = viewModel.speechLevel
    val speechLevel = {
        if (state.previewState == AssistantState.Speaking) {
            val t = time.floatValue
            abs(sin(t * 9f) * (0.6f + 0.4f * sin(t * 2.3f)))
        } else realLevel.value
    }

    val handsFreeMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleHandsFree() else viewModel.micDenied()
    }
    val onVoice = {
        if (viewModel.hasMicPermission()) viewModel.toggleHandsFree() else handsFreeMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    val telemetry = {
        listOf(
            "SEEMA//CORE ${BuildConfig.VERSION_NAME.substringBefore('-')}",
            "STATE  ${assistantState.label.uppercase()}",
            "NET    ${if (state.online) "ONLINE" else "OFFLINE"}",
            "AI     ${state.defaultProvider.shortName.uppercase()}${if (state.providerConnected) " OK" else ""}",
            "VOICE  ${state.voiceEngine}",
            "MIC    ${if (state.handsFree) "HANDS-FREE" else "STANDBY"} · ${state.listenEngine}",
        )
    }

    // Small screens (e.g. 640 dp tall): the 3D dashboard packs its tiles into one scrolling strip
    // so the core always keeps a large share of the screen.
    val compact = LocalConfiguration.current.screenHeightDp < 760

    Box(
        Modifier
            .fillMaxSize()
            // Any touch counts as activity (observed only, never consumed: buttons work as before).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = SystemClock.uptimeMillis()
                    }
                }
            },
    ) {
        CyberBackground(
            color = Color(style.primaryArgb),
            accent = Color(style.paletteArgb[2]),
            time = timeProvider,
            // The 3D dashboard's own code stream is the star of its backdrop: lighter rain there.
            density = if (modern) profile.backgroundDensity * 0.45f else profile.backgroundDensity,
            // The 3D dashboard shows the same facts in its own panels (and the clock sits there).
            // Hidden too while a banner (paused, Safety Mode, heat) pushes the tags down, so texts never overlap.
            telemetry = if (modern || state.paused || state.safetyMode || thermal >= ThermalLevel.HOT) ({ emptyList() }) else telemetry,
            modifier = Modifier.fillMaxSize(),
            liveClock = running,
        )
        // 3D dashboard only: JARVIS-style terminals of code and system logs scrolling behind the core.
        if (modern) HoloCodeStream(Color(style.primaryArgb), Color(style.paletteArgb[1]), timeProvider, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (modern && compact) 4.dp else 8.dp),
        ) {
            HoloTitle("SEEMA AI", Color(style.primaryArgb))

            if (state.paused) {
                // Paused is easy to forget: show it clearly, with a one-tap way back.
                Row(
                    Modifier.fillMaxWidth().hudFrame(WarningAmber, cut = 8.dp, fillAlpha = 0.08f).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "⏸ Assistant paused — not listening, no announcements",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningAmber,
                    )
                    FilledTonalButton(onClick = viewModel::resume) { Text("Resume") }
                }
            }

            if (thermal >= ThermalLevel.HOT) {
                // Android reports real heat: say why the visuals went quiet.
                Text(
                    "🌡 फ़ोन गरम है (${state.thermal.label}) — animations कम कर दी हैं; ठंडा होते ही अपने-आप वापस।",
                    Modifier.fillMaxWidth().hudFrame(ErrorRed, cut = 8.dp, fillAlpha = 0.08f).padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                )
            }

            if (state.safetyMode) {
                // Safety Mode silently blocks calls, WhatsApp, SMS, apps and music: say so where the user looks.
                Row(
                    Modifier.fillMaxWidth().hudFrame(WarningAmber, cut = 8.dp, fillAlpha = 0.08f).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "🛡️ Safety Mode on — calls, WhatsApp, SMS, apps & music are blocked",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningAmber,
                    )
                    FilledTonalButton(onClick = viewModel::disableSafetyMode) { Text("Turn off") }
                }
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The state tag breathes with the reactor's own (frame-capped) clock.
                StatusChip(assistantState.label, Color(style.primaryArgb), pulse = if (running) ({ 0.65f + 0.35f * sin(timeProvider() * 3f) }) else null)
                // One tap switches the design (also in Settings → AI core appearance).
                StatusChip(
                    if (modern) "🎨 3D Core ⇄" else "🎨 Arc Reactor ⇄",
                    Color(style.paletteArgb[1]),
                    Modifier.clickable(onClickLabel = "Switch AI core design") { viewModel.toggleCore() },
                )
                StatusChip(if (state.online) "Online" else "Offline", if (state.online) SuccessGreen else ErrorRed)
                // One tap: always listening (app open) ⇄ tap-to-talk.
                StatusChip(
                    "Mode: ${state.listenMode} ⇄",
                    MaterialTheme.colorScheme.secondary,
                    Modifier.clickable(onClickLabel = "Switch listening mode") {
                        if (viewModel.toggleAlwaysListening()) {
                            if (viewModel.hasMicPermission()) viewModel.startAlwaysListening() else handsFreeMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
                StatusChip("🌐 ${state.language.label.substringBefore(' ')}", MaterialTheme.colorScheme.secondary)
                StatusChip("🗣 Voice: ${state.voiceEngine}", MaterialTheme.colorScheme.secondary)
                StatusChip(
                    when {
                        state.listening -> "Mic: listening"
                        assistantState == AssistantState.Sleeping -> "Mic: sleep — say \"Seema\""
                        state.handsFree -> "Mic: hands-free"
                        else -> "Mic: off"
                    },
                    if (state.listening || state.handsFree) SuccessGreen else TextSecondary,
                )
                val provider = state.defaultProvider.shortName
                when {
                    state.providerConnected && state.providerKeySaved -> StatusChip("$provider: connected", SuccessGreen)
                    state.providerKeySaved -> StatusChip("$provider: key saved", WarningAmber)
                    else -> StatusChip("$provider: no key", ErrorRed)
                }
                if (state.safetyMode) StatusChip("Safety Mode", WarningAmber)
                if (state.paused) StatusChip("Paused", WarningAmber)
            }

            if (modern) HoloClock(Color(style.primaryArgb), compact = compact)

            // Stage: one large, centred core — the Legacy Arc Reactor or the 3D Holographic Core.
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = state.previewState == null && (assistantState == AssistantState.Speaking || assistantState == AssistantState.Sleeping),
                        onClickLabel = if (assistantState == AssistantState.Sleeping) "Wake up" else "Stop talking",
                    ) { viewModel.tapReactor() }
                    .semantics { contentDescription = "Assistant status: ${assistantState.label}" },
                contentAlignment = Alignment.Center,
            ) {
                // The 3D core fills all the free space; the Arc Reactor keeps its original size.
                val coreSize = minOf(maxWidth, maxHeight) * (if (modern) 1f else 0.96f)
                Crossfade(targetState = modern, animationSpec = tween(400), label = "core") { showModern ->
                    if (showModern) HoloCore(style, profile.particleCount, profile.richEffects, timeProvider, speechLevel, Modifier.size(coreSize))
                    else ArcReactor(style, profile.particleCount, profile.richEffects, timeProvider, speechLevel, Modifier.size(coreSize))
                }
                val waveWidth = maxWidth * 0.55f
                if (modern) {
                    // The state name sits on the hologram itself, so the core gets the space.
                    val stateColor = Color(style.primaryArgb)
                    Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                        DecodingText(
                            assistantState.label.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 5.sp, shadow = Shadow(stateColor.copy(alpha = 0.9f), blurRadius = 18f)),
                            color = stateColor,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        VoiceWaveform(assistantState, stateColor, timeProvider, speechLevel, Modifier.width(waveWidth).height(18.dp))
                    }
                }
            }

            val stateColor = Color(style.primaryArgb)
            if (!modern) DecodingText(
                assistantState.label.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp, shadow = Shadow(stateColor.copy(alpha = 0.8f), blurRadius = 16f)),
                color = stateColor,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (!modern) VoiceWaveform(
                assistantState, stateColor, timeProvider, speechLevel,
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(26.dp),
            )
            val caption = when {
                state.previewState != null -> "Visual preview only — nothing is running."
                state.listening && state.partialTranscript.isNotBlank() -> "“${state.partialTranscript}”"
                // "Listening" is shown only once the microphone is really open, never as proof of anything.
                assistantState == AssistantState.Listening && !state.listening -> "🎙 माइक शुरू हो रहा है… Starting the microphone"
                state.heard != null && (assistantState == AssistantState.Thinking || assistantState == AssistantState.Executing) ->
                    "✓ सुना (Recognized): “${state.heard}” — सोच रही हूँ…"
                assistantState == AssistantState.Idle && state.hint != null -> state.hint!!
                assistantState == AssistantState.Idle && state.lastReply != null -> state.lastReply!!
                assistantState == AssistantState.Idle && !state.providerKeySaved -> "Add your ${state.defaultProvider.shortName} API key in Settings to start."
                else -> statusHintFor(assistantState)
            }
            Text(
                caption,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = if (modern) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )

            val busy = assistantState.isBusy || state.listening
            val voiceLabel = when {
                state.handsFree -> "Mic ON"
                state.voiceMode == VoiceMode.PUSH_TO_TALK -> "Talk"
                else -> "Voice"
            }
            if (modern) {
                // 3D dashboard: weather, phone status and quick controls in one sliding strip (swipe
                // sideways for more), so the core keeps the space.
                val weatherTile: @Composable (Modifier) -> Unit = { m -> WeatherTile(Color(style.primaryArgb), viewModel::loadWeather, m) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceStatsRow(Color(style.primaryArgb), weatherTile, fill = false)
                    QuickControlsRow(Color(style.paletteArgb[1]), fill = false)
                }
            }
            // The holographic control dock on both designs (Arc Reactor and 3D core).
            HoloDock(
                accent = Color(style.primaryArgb),
                micActive = state.handsFree || state.listening,
                micLabel = voiceLabel,
                stopEnabled = busy || state.handsFree || state.previewState != null,
                time = timeProvider,
                onChat = onOpenChat,
                onMic = onVoice,
                onStop = viewModel::stop,
                onSettings = onOpenSettings,
            )
        }
    }
}

/** The "SEEMA AI" wordmark: wide-spaced holographic letters with a soft glow. */
@Composable
private fun HoloTitle(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium.copy(
            letterSpacing = 8.sp,
            fontWeight = FontWeight.Bold,
            brush = Brush.horizontalGradient(listOf(EmeraldBright, color, HoloCyan)),
            shadow = Shadow(color.copy(alpha = 0.7f), blurRadius = 22f),
        ),
    )
}

/** False when the user disabled animations in Developer options / Accessibility ("Remove animations"). */
@Composable
private fun rememberSystemAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return remember(resolver, lifecycleState) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
