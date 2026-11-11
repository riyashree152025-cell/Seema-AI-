package com.raju.aiassistant.ui.components

import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raju.aiassistant.domain.model.AssistantState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import kotlin.math.sin

// Futuristic Sci-Fi HUD Colors matching user reference photos with Glassy Sky-Blue Sheen
val SciFiBackgroundTop = Color(0xFF030712)
val SciFiBackgroundBottom = Color(0xFF02040A)
val SciFiCardDark = Color(0x3800C8FF)      // Frosted Glassy Sky-Blue Top Glow
val SciFiCardDeep = Color(0x22051E3D)      // Translucent Glass Sky-Blue Depth
val SciFiButtonGlassTop = Color(0x4000E5FF) // Light Sky-Blue Glass Top Highlight
val SciFiButtonGlassMid = Color(0x280066CC) // Glassy Sky-Blue Azure Sheen
val SciFiButtonGlassDeep = Color(0x1F002244)// Deep Sky-Blue Glass Core
val SciFiCyanGlow = Color(0xFF00E5FF)
val SciFiBlueAccent = Color(0xFF0077FF)
val SciFiPurpleGradientStart = Color(0xFF8A2BE2)
val SciFiPurpleGradientEnd = Color(0xFF4B0082)

val SciFiCardShape = CutCornerShape(12.dp)
val SciFiButtonShape = CutCornerShape(10.dp)

/**
 * Cut-Corner Sci-Fi HUD Card Container
 * Used for Settings items, Navigation tiles, Routine cards, and Brain panels.
 */
@Composable
fun SciFiCard(
    modifier: Modifier = Modifier,
    borderColor: Color = SciFiCyanGlow,
    borderAlpha: Float = 0.75f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .premiumSurface(shape = RoundedCornerShape(20.dp), accent = borderColor, lit = (borderAlpha - 0.75f).coerceIn(0f, 1f))
            .then(clickModifier)
            .padding(16.dp),
        content = content
    )
}

/**
 * 2x3 Grid Item Navigation Button on Home Screen
 */
@Composable
fun SciFiHomeNavButton(
    title: String,
    icon: ImageVector,
    iconColor: Color = Premium.Aqua,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shows a small status dot: green = on/working, null = nothing to show. */
    active: Boolean? = null,
) {
    // Premium tile: ink glass, a hairline from champagne gold to the tile's own colour, the icon in a
    // softly lit glass disc, and a large clear label. Pressed: a gentle dip, a brighter edge, a light tick.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.94f else 1f, tween(130), label = "tileScale")
    val lit by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f, tween(160), label = "tileLit")
    Box(
        modifier = modifier
            .height(104.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            // Crystal glass tile with a neon rim in the tile's own colour; pressed, it dips and glows.
            .crystalSurface(shape = RoundedCornerShape(22.dp), accent = iconColor, lit = lit)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        // A floating glass orb with its own halo, not a flat circle.
                        .glowingOrb(iconColor, lit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                }
                if (active != null) {
                    // Status dot in the corner: green when on, dim grey when off.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .background(if (active) Color(0xFF5EF2A8) else Color(0xFF5D7083), CircleShape)
                            .border(1.5.dp, Color(0xFF060B15), CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Premium.TextHigh,
                    fontSize = 14.5.sp,
                    lineHeight = 17.sp,
                    letterSpacing = 0.1.sp,
                ),
                // "Voice Center" on a narrow phone: a second line rather than "Voice Ce…".
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Full-width Settings & Menu Card with glowing Icon Emblem, Title, Subtitle and Chevron >
 * Exactly matching Panel 1 in User Reference Image (Glassy Rounded Navy/Cyan Glow)
 */
@Composable
fun SciFiListCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = SciFiCyanGlow,
    emblemBorder: Color = SciFiCyanGlow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .premiumSurface(accent = emblemBorder)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular Glowing Emblem Matching Reference Image Panel 1
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(iconTint.copy(alpha = 0.24f), iconTint.copy(alpha = 0.06f))))
                        .border(1.dp, Brush.linearGradient(listOf(Premium.Gold.copy(alpha = 0.55f), iconTint.copy(alpha = 0.5f))), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Premium.TextHigh,
                            fontSize = 18.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Premium.TextLow,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Premium.Gold.copy(alpha = 0.85f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Glowing Circular Progress Ring (e.g. Memory Center 4%)
 */
@Composable
fun SciFiProgressRing(
    percentage: Int,
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    ringColor: Color = SuccessGreen,
    strokeWidth: Dp = 6.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(size.toPx() - strokePx, size.toPx() - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // Background Track
            drawArc(
                color = Color(0x3300E5FF),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx * 0.7f, cap = StrokeCap.Round)
            )

            // Active Glowing Arc
            val sweep = (percentage.coerceIn(0, 100) / 100f) * 360f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(ringColor.copy(alpha = 0.6f), ringColor, SciFiCyanGlow)
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Text(
            "$percentage%",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 15.sp
            )
        )
    }
}

/**
 * Prominent Sci-Fi Action Button with gradient and glowing cut corners
 */
@Composable
fun SciFiGradientButton(
    text: String,
    icon: ImageVector? = null,
    gradient: Brush = Brush.horizontalGradient(listOf(Color(0xFF16324F), Color(0xFF1E4A6E), Color(0xFF16324F))),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .border(1.dp, Brush.linearGradient(listOf(Premium.Gold.copy(alpha = 0.7f), Premium.Aqua.copy(alpha = 0.45f))), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                    color = Premium.TextHigh,
                    fontSize = 17.sp
                )
            )
        }
    }
}

/**
 * Futuristic Audio Waveform Visualizer
 */
@Composable
fun SciFiWaveform(
    modifier: Modifier = Modifier,
    barCount: Int = 12,
    color: Color = SciFiCyanGlow,
    isListening: Boolean = false,
    speechLevel: Float = 0f,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.height(36.dp)) {
        val totalWidth = size.width
        val barWidth = (totalWidth / barCount) * 0.45f
        val spacing = (totalWidth / barCount) * 0.55f
        val midY = size.height / 2f

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing) + barWidth / 2f
            val factor = if (isListening) {
                ((sin(phase + i * 0.6f) * 0.4f + 0.6f) * (1f + speechLevel * 0.8f)).coerceIn(0.2f, 1f)
            } else {
                (sin(i * 0.5f) * 0.25f + 0.35f).coerceIn(0.15f, 0.6f)
            }
            val barHeight = size.height * factor

            drawLine(
                color = color.copy(alpha = 0.85f),
                start = Offset(x, midY - barHeight / 2f),
                end = Offset(x, midY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Stat Tile for AI Brain & Diagnostics (e.g. Latency 428ms)
 */
@Composable
fun SciFiStatTile(
    icon: String,
    label: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    // Grows with its text (a fixed 72 dp cut the larger values off), premium glass like the rest.
    Box(
        modifier = modifier
            .heightIn(min = 84.dp)
            .premiumSurface(shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "$icon $label",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Premium.TextLow,
                    fontSize = 13.5.sp
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                    fontSize = 17.sp
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Filter Chip Pill for Settings & Lists (All, AI, Voice, System, Security)
 */
@Composable
fun SciFiPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(Color(0xFF0072FF), Color(0xFF00C6FF)))
                else Brush.horizontalGradient(listOf(Color(0x2600E5FF), Color(0x1A081426)))
            )
            .border(
                1.dp,
                if (selected) SciFiCyanGlow else SciFiCyanGlow.copy(alpha = 0.35f),
                shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp
            )
        )
    }
}


/**
 * ChatGPT / Siri Style Living Fluid Voice Orb
 * Replaces the static microphone button with an iridescent, morphing, pulsating cybernetic voice core.
 * Fluidly shifts between Cyan, Cobalt Blue, Electric Violet, and Soft Magenta.
 * Dynamically reacts to speech level, listening, thinking, and speaking states.
 */
@Composable
fun SciFiVoiceOrb(
    isListening: Boolean,
    isThinking: Boolean = false,
    isSpeaking: Boolean = false,
    speechLevel: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceOrbTransition")

    // Rotation speed changes dynamically: faster when thinking or listening
    val durationMs = if (isThinking) 2200 else if (isListening) 3400 else 6000
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    // Gentle breathing pulse
    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbBreathing"
    )

    val audioScale = if (isListening) (1f + speechLevel * 0.45f) else if (isSpeaking) 1.08f else breathing
    val orbSize = 72.dp

    Box(
        modifier = modifier
            .size(orbSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = center
            val r = (size.minDimension / 2f) * audioScale

            // 1. Outer ambient acoustic halo
            val haloAlpha = if (isListening) (0.35f + speechLevel * 0.4f).coerceIn(0f, 0.85f) else 0.25f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF00E5FF).copy(alpha = haloAlpha),
                        Color(0xFF7C4DFF).copy(alpha = haloAlpha * 0.6f),
                        Color.Transparent
                    ),
                    center = c,
                    radius = r * 1.45f
                ),
                radius = r * 1.45f,
                center = c
            )

            // 2. Rotating Iridescent Aurora Border Ring
            rotate(rotation, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF00E5FF),
                            Color(0xFF0077FF),
                            Color(0xFF7C4DFF),
                            Color(0xFFE040FB),
                            Color(0xFF00E5FF)
                        ),
                        center = c
                    ),
                    radius = r * 0.95f,
                    style = Stroke(width = if (isListening) 4f else 2.5f)
                )
            }

            // 3. Counter-rotating Inner Accent Ring
            rotate(-rotation * 0.7f, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0x8000E5FF),
                            Color(0x30E040FB),
                            Color(0x900077FF),
                            Color(0x8000E5FF)
                        ),
                        center = c
                    ),
                    radius = r * 0.78f,
                    style = Stroke(width = 1.8f)
                )
            }

            // 4. Glowing 3D Glassy Liquid Spherical Core
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFFE0F7FA),
                        Color(0xFF00E5FF),
                        Color(0xFF0052CC),
                        Color(0xFF140533)
                    ),
                    center = Offset(c.x - r * 0.15f, c.y - r * 0.15f),
                    radius = r * 0.75f
                ),
                radius = r * 0.72f,
                center = c
            )

            // 5. Living Center Soundwave Aperture (No generic mic symbol!)
            val waveWidth = r * 0.65f
            val segments = 8
            for (i in 0 until segments) {
                val frac = (i - segments / 2f) / (segments / 2f)
                val x = c.x + frac * (waveWidth / 2f)
                val waveAmp = if (isListening) (speechLevel * 18f + 4f) else if (isSpeaking) 10f else (sin(rotation * 0.1f + i) * 5f + 5f)
                drawLine(
                    color = Color.White.copy(alpha = 0.95f),
                    start = Offset(x, c.y - waveAmp),
                    end = Offset(x, c.y + waveAmp),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Top-Left Sci-Fi Cybernetic Digital Clock
 * Shows HH:mm:ss and Day/Date with glowing neon cyan styling and glassy sky-blue fill.
 */
@Composable
fun SciFiDigitalClock(
    modifier: Modifier = Modifier,
) {
    var timeText by remember { mutableStateOf("00:00:00") }
    var dateText by remember { mutableStateOf("LIVE HUD") }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        while (isActive) {
            val now = Date()
            timeText = timeFmt.format(now)
            dateText = dateFmt.format(now).uppercase(Locale.getDefault())
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .crystalSurface(shape = CutCornerShape(8.dp), accent = SciFiCyanGlow)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                timeText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 15.sp,
                    letterSpacing = 1.2.sp
                )
            )
            Text(
                dateText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = SciFiCyanGlow,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.8.sp
                )
            )
        }
    }
}

/**
 * Top-Right Live AI State Capsule Widget
 * Displays exact live status (READY, LISTENING, THINKING, SPEAKING, SLEEPING) with pulsing dot.
 * Tapping it toggles sleep/wake mode.
 */
@Composable
fun SciFiStateCapsule(
    state: AssistantState,
    isListening: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CapsuleDotTransition")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotPulse"
    )

    val (statusLabel, statusColor, subtitle) = when {
        isListening || state == AssistantState.Listening -> Triple("LISTENING", Color(0xFF00E5FF), "Voice Active")
        state == AssistantState.Thinking -> Triple("THINKING", Color(0xFFB388FF), "AI Processing")
        state == AssistantState.Speaking -> Triple("SPEAKING", Color(0xFF0077FF), "Voice Output")
        state == AssistantState.Sleeping -> Triple("STANDBY", Color(0xFF00E5FF), "Say 'हे सीमा' or Tap")
        else -> Triple("READY", Color(0xFF00E676), "Say 'हे सीमा' or Tap")
    }

    Box(
        modifier = modifier
            .crystalSurface(shape = RoundedCornerShape(14.dp), accent = statusColor, lit = 0.25f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = dotAlpha))
            )
            Column {
                // One line each: in the narrow header the label broke into "STA / NDB / Y".
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 13.sp,
                        letterSpacing = 0.6.sp
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = Premium.TextLow,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Settings home (reference look): one glass panel holding compact rows, with a large faint gear
 * drawn behind them as decoration.
 */
@Composable
fun SciFiSettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0C1F42), Color(0xFF050E22))))
            .border(1.5.dp, Brush.linearGradient(listOf(Color(0xCC00E5FF), Color(0x557C4DFF), Color(0xCC00E5FF))), shape),
    ) {
        // Decoration only: a big gear glowing faintly at the right edge.
        Icon(
            Icons.Filled.Settings,
            contentDescription = null,
            tint = Color(0xFF00E5FF).copy(alpha = 0.07f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(190.dp)
                .offset(x = 60.dp),
        )
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), content = content)
    }
}

/** One compact row of [SciFiSettingsPanel]: small glowing icon, title, short subtitle and ">". */
@Composable
fun SciFiSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = title, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(iconTint.copy(alpha = 0.24f), iconTint.copy(alpha = 0.05f))))
                    .border(1.dp, Brush.linearGradient(listOf(Premium.Gold.copy(alpha = 0.5f), iconTint.copy(alpha = 0.5f))), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(23.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = Premium.TextHigh, fontSize = 18.sp))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Premium.TextLow, fontSize = 15.sp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Premium.Gold.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 78.dp, end = 18.dp)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Premium.Gold.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))),
            )
        }
    }
}
