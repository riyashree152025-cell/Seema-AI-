package com.raju.aiassistant.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.di.AppContainer
import com.raju.aiassistant.domain.assistant.Decision
import com.raju.aiassistant.domain.assistant.PendingRequest
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.domain.model.ChatRole
import com.raju.aiassistant.ui.home.visuals.LivingEyeVisualizer
import com.raju.aiassistant.ui.home.visuals.eyeAccentArgb
import com.raju.aiassistant.ui.theme.RajuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Glass: deep ink, a cool edge light, and the state colour as the only accent.
private val GLASS_TOP = Color(0xF2171D33)
private val GLASS_BOTTOM = Color(0xF50A0D19)
private val EDGE_CYAN = Color(0xFF00E5FF)
private val EDGE_VIOLET = Color(0xFFB388FF)
private val TEXT_MAIN = Color(0xFFF4F7FF)
private val TEXT_SOFT = Color(0xFFB9C3D8)

/** The whole popup: the glass card or the eye bubble, both driven by Seema's live state. */
@Composable
internal fun FloatingSeemaContent(
    container: AppContainer,
    mode: FloatMode,
    hideWords: Boolean,
    onFold: () -> Unit,
    onOpenCard: () -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val engine = container.engine
    val state by engine.state.collectAsStateWithLifecycle()
    val pending by engine.pending.collectAsStateWithLifecycle()
    val heard by engine.partialTranscript.collectAsStateWithLifecycle()
    val transcript by engine.transcript.collectAsStateWithLifecycle()
    val lastError by engine.lastError.collectAsStateWithLifecycle()
    val speech by engine.speechLevel.collectAsStateWithLifecycle()
    val mic by container.micHub.level.collectAsStateWithLifecycle()

    // One clock for the eye, read only while drawing (no recomposition per frame).
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }

    // Opens by itself while she works or asks; folds into the bubble after a quiet while.
    val hasPending = pending != null
    // Opening reacts to a change of her state only, so a card the user just folded stays folded until
    // something new happens (her next answer, "Hey Seema", a question).
    val currentMode by rememberUpdatedState(mode)
    LaunchedEffect(state, hasPending) {
        if (currentMode == FloatMode.BUBBLE && opensCard(state, hasPending)) onOpenCard()
    }
    LaunchedEffect(state, hasPending, mode) {
        if (mode == FloatMode.CARD) foldAfterMs(state, hasPending)?.let { delay(it); onFold() }
    }

    val lastReply = transcript.lastOrNull { it.role == ChatRole.ASSISTANT }?.text
    val line = floatingLine(state, heard, pending?.message, lastReply, lastError, hideWords)
    val accent = Color(eyeAccentArgb(state))

    val tapMic = {
        when {
            state == AssistantState.Sleeping -> engine.wakeUp(greet = false)
            state == AssistantState.Speaking -> engine.interrupt()
            state == AssistantState.Listening || engine.listening.value -> container.stopEverything()
            else -> engine.startVoice()
        }
    }

    RajuTheme {
        if (mode == FloatMode.CARD) {
            GlassCard(
                state = state, accent = accent, line = line, pending = pending.takeUnless { hideWords },
                time = { time.floatValue }, speech = { speech }, mic = { mic },
                onMic = tapMic, onStop = { container.stopEverything() }, onOpenApp = onOpenApp, onFold = onFold, onClose = onClose,
                onAnswer = { yes -> engine.resolve(if (yes) Decision.Approve(pending?.options?.singleOrNull()) else Decision.Deny) },
            )
        } else {
            EyeBubble(
                state = state, accent = accent,
                time = { time.floatValue }, speech = { speech }, mic = { mic },
                onTap = onOpenCard, onDrag = onDrag, onDragEnd = onDragEnd,
            )
        }
    }
}

@Composable
private fun GlassCard(
    state: AssistantState,
    accent: Color,
    line: String,
    pending: PendingRequest?,
    time: () -> Float,
    speech: () -> Float,
    mic: () -> Float,
    onMic: () -> Unit,
    onStop: () -> Unit,
    onOpenApp: () -> Unit,
    onFold: () -> Unit,
    onClose: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    // Rises with a soft spring; folds down before turning into the bubble.
    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
    val rise = with(LocalDensity.current) { 56.dp.toPx() }
    val fold: () -> Unit = { scope.launch { appear.animateTo(0f, tween(180)); onFold() } }
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(30.dp)

    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * rise
                    scaleX = 0.94f + 0.06f * p
                    scaleY = 0.94f + 0.06f * p
                }
                .shadow(22.dp, shape, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(GLASS_TOP, GLASS_BOTTOM)))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(EDGE_CYAN.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), EDGE_VIOLET.copy(alpha = 0.55f))),
                    shape,
                )
                // Opening the app from anywhere on the card except its buttons.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenApp() }
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 118.dp, height = 66.dp)) {
                    LivingEyeVisualizer(
                        time = time, speechLevel = speech, micLevel = mic, state = state,
                        modifier = Modifier.size(width = 118.dp, height = 66.dp),
                        richEffects = false, drawWaves = false,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("सीमा", color = TEXT_MAIN, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        StatusPill(state, accent)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(line, color = TEXT_SOFT, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SmallRoundButton(Icons.Rounded.Close, "बंद करें") { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClose() }
                    Spacer(Modifier.height(6.dp))
                    SmallRoundButton(Icons.Rounded.KeyboardArrowDown, "छोटा करें") { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); fold() }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (pending != null && pending.kind == PendingRequest.Kind.CONFIRM && pending.options.size <= 1) {
                // A call, a message or money waits for the user's word: answer it right here.
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("हाँ, कर दो", null, accent, primary = true, Modifier.weight(1f)) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAnswer(true) }
                    PillButton("नहीं", null, accent, primary = false, Modifier.weight(1f)) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAnswer(false) }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val listening = state == AssistantState.Listening
                    PillButton(if (listening) "सुन रही…" else "बोलो", Icons.Rounded.Mic, accent, primary = true, Modifier.weight(1.25f), pulsing = listening) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMic()
                    }
                    PillButton("रोको", Icons.Rounded.Stop, accent, primary = false, Modifier.weight(1f)) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onStop() }
                    PillButton("खोलो", Icons.AutoMirrored.Rounded.OpenInNew, accent, primary = false, Modifier.weight(1f)) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpenApp() }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: AssistantState, accent: Color) {
    val busy = state.isBusy
    val glow by rememberInfiniteTransition(label = "pill").animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pillGlow",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .border(0.8.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent.copy(alpha = if (busy) glow else 0.9f)))
        Spacer(Modifier.width(5.dp))
        Text(floatingStatus(state), color = TEXT_MAIN, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SmallRoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(0.8.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = TEXT_SOFT, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PillButton(
    label: String,
    icon: ImageVector?,
    accent: Color,
    primary: Boolean,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseK",
    )
    val glow = if (pulsing) 0.35f + 0.4f * pulse else 0.35f
    val bg = if (primary) {
        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.95f), EDGE_VIOLET.copy(alpha = 0.85f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))
    }
    Row(
        modifier
            .height(46.dp)
            .then(if (primary) Modifier.shadow(10.dp, shape, ambientColor = accent.copy(alpha = glow), spotColor = accent.copy(alpha = glow)) else Modifier)
            .clip(shape)
            .background(bg)
            .border(0.8.dp, if (primary) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.14f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (primary) Color(0xFF061019) else TEXT_MAIN, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (primary) Color(0xFF061019) else TEXT_MAIN,
            fontSize = 15.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun EyeBubble(
    state: AssistantState,
    accent: Color,
    time: () -> Float,
    speech: () -> Float,
    mic: () -> Float,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val appear = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
    val ring by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "ringK",
    )
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .padding(6.dp)
            .size(FloatingSeema.BUBBLE_DP.dp - 12.dp)
            .graphicsLayer { scaleX = appear.value; scaleY = appear.value; alpha = appear.value.coerceIn(0f, 1f) }
            .shadow(14.dp, CircleShape, ambientColor = accent, spotColor = accent)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(GLASS_TOP, GLASS_BOTTOM)))
            .border(1.5.dp, accent.copy(alpha = if (state.isBusy) ring else 0.45f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTap() }) }
            .semantics { contentDescription = "सीमा — खोलने के लिए छुएँ" },
        contentAlignment = Alignment.Center,
    ) {
        LivingEyeVisualizer(
            time = time, speechLevel = speech, micLevel = mic, state = state,
            modifier = Modifier.size(FloatingSeema.BUBBLE_DP.dp - 16.dp),
            richEffects = false, drawWaves = false,
        )
    }
}
