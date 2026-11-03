package com.raju.aiassistant.ui.voice

import android.Manifest
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.language.LANGUAGE_AUTO
import com.raju.aiassistant.domain.language.SeemaLanguage
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.ui.theme.GoldAccent
import com.raju.aiassistant.ui.theme.NeonCyan
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen 5: Voice Control
 * Matches Reference Image: Large Voice Orb with Gold Ring, Horizontal Audio Waves, Voice Quick Chips.
 */
@Composable
fun VoiceControlScreen(
    onBack: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = container.currentSettings.value)
    val assistantState by container.engine.state.collectAsStateWithLifecycle()
    val isListening by container.engine.listening.collectAsStateWithLifecycle()
    val speechLevel by container.engine.speechLevel.collectAsStateWithLifecycle()

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.engine.startVoice()
    }

    val scope = rememberCoroutineScope()
    val onToggleMic = {
        if (container.voiceInput.hasPermission()) {
            if (isListening) container.engine.stop() else container.engine.startVoice()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040714), Color(0xFF02040A))))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: < Voice Control
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
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Voice Control",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Center Voice Orb with Horizontal Visualizer Waves
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            VoiceOrbVisualizer(
                speechLevel = speechLevel,
                isListening = isListening,
                modifier = Modifier.fillMaxSize(),
            )

            // Center Interactive Mic Button
            Box(
                Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0x6600E5FF), Color(0x33002244))))
                    .border(2.dp, NeonCyan, CircleShape)
                    .clickable { onToggleMic() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isListening) NeonCyan else Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        // Voice Readout Text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                when (assistantState) {
                    AssistantState.Listening -> "Listening..."
                    AssistantState.Speaking -> "Speaking..."
                    AssistantState.Thinking -> "Thinking..."
                    else -> "Ready"
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.sp,
                ),
            )
            Text(
                when (assistantState) {
                    AssistantState.Listening -> "Speak now, I'm ready!"
                    AssistantState.Speaking -> "Responding aloud..."
                    AssistantState.Thinking -> "Processing your request..."
                    else -> "Tap the microphone to speak"
                },
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFC0D5F0)),
            )
        }

        // Horizontal Equalizer Bars (below status text)
        HorizontalEqualizer(
            active = isListening || assistantState == AssistantState.Speaking,
            level = speechLevel,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(12.dp))

        // Quick Mode Chips (Language, Voice, TTS Settings)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            VoiceChip(
                label = "Language\n(Hindi/English)",
                onClick = {
                    val next = if (settings.speechLanguage == SeemaLanguage.HINDI.tag) LANGUAGE_AUTO else SeemaLanguage.HINDI.tag
                    scope.launch { container.settingsRepository.setSpeechLanguage(next) }
                },
            )
            VoiceChip(
                label = "Voice\n(British Male)",
                onClick = onOpenVoiceSettings,
            )
            VoiceChip(
                label = "TTS\nSettings",
                onClick = onOpenVoiceSettings,
            )
        }
    }
}

@Composable
private fun VoiceChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(102.dp, 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xDD0B162C))
            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                lineHeight = 13.sp,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun VoiceOrbVisualizer(
    speechLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "voiceOrb")
    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "waveOffset",
    )

    Canvas(modifier = modifier) {
        val c = center
        val orbR = 56.dp.toPx()
        val energy = if (isListening) (0.4f + speechLevel * 0.6f) else 0.25f

        // Outer Metallic Gold Segmented Ring
        val gold = GoldAccent
        val goldR = orbR + 10.dp.toPx()
        val segments = 8
        val sweep = 360f / segments - 12f
        repeat(segments) { i ->
            drawArc(
                color = gold,
                startAngle = i * (360f / segments) + 6f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(c.x - goldR, c.y - goldR),
                size = androidx.compose.ui.geometry.Size(goldR * 2, goldR * 2),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                alpha = 0.9f,
            )
        }

        // Inner glowing cyan ring
        drawCircle(
            color = NeonCyan,
            radius = orbR,
            center = c,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Horizontal audio visualizer waves extending left and right from the orb
        val leftStart = c.x - goldR - 6.dp.toPx()
        val rightStart = c.x + goldR + 6.dp.toPx()
        val waveBars = 16
        val barGap = 6.dp.toPx()

        for (i in 0 until waveBars) {
            val dist = i.toFloat() / waveBars
            val decay = 1f - dist * 0.7f
            val h = (24.dp.toPx() * energy * abs(sin(waveOffset + i * 0.5f)) * decay).coerceAtLeast(4f)

            val xLeft = leftStart - i * barGap
            val xRight = rightStart + i * barGap

            if (xLeft > 10f) {
                drawLine(
                    color = NeonCyan.copy(alpha = 0.85f * decay),
                    start = Offset(xLeft, c.y - h),
                    end = Offset(xLeft, c.y + h),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (xRight < size.width - 10f) {
                drawLine(
                    color = NeonCyan.copy(alpha = 0.85f * decay),
                    start = Offset(xRight, c.y - h),
                    end = Offset(xRight, c.y + h),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun HorizontalEqualizer(
    active: Boolean,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eqAnim")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "eqPhase",
    )

    Canvas(modifier = modifier) {
        val bars = 32
        val barWidth = size.width / (bars * 1.5f)
        val hMax = size.height / 2f
        val energy = if (active) (0.35f + level * 0.65f) else 0.15f

        for (i in 0 until bars) {
            val x = i * (barWidth * 1.5f) + barWidth / 2f
            val wave = abs(sin(phase + i * 0.4f))
            val barH = (hMax * (0.2f + 0.8f * wave) * energy).coerceAtLeast(3f)

            drawLine(
                brush = Brush.verticalGradient(
                    listOf(NeonCyan.copy(alpha = 0.9f), Color(0xFF0077FF).copy(alpha = 0.7f)),
                ),
                start = Offset(x, size.height / 2f - barH),
                end = Offset(x, size.height / 2f + barH),
                strokeWidth = barWidth * 0.85f,
                cap = StrokeCap.Round,
            )
        }
    }
}
