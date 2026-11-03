package com.raju.aiassistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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

// Futuristic Sci-Fi HUD Colors matching user reference photos
val SciFiBackgroundTop = Color(0xFF030712)
val SciFiBackgroundBottom = Color(0xFF02040A)
val SciFiCardDark = Color(0xE6081426)
val SciFiCardDeep = Color(0xF2040915)
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
            .clip(SciFiCardShape)
            .background(
                Brush.verticalGradient(
                    listOf(SciFiCardDark, SciFiCardDeep)
                )
            )
            .border(
                1.2.dp,
                Brush.horizontalGradient(
                    listOf(
                        borderColor.copy(alpha = borderAlpha * 0.4f),
                        borderColor.copy(alpha = borderAlpha),
                        borderColor.copy(alpha = borderAlpha * 0.4f)
                    )
                ),
                SciFiCardShape
            )
            .then(clickModifier)
            .padding(14.dp),
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
    iconColor: Color = SciFiCyanGlow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(78.dp)
            .clip(SciFiCardShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xD90A1A33), Color(0xF2050D1D))
                )
            )
            .border(
                1.3.dp,
                Brush.verticalGradient(
                    listOf(
                        SciFiCyanGlow.copy(alpha = 0.85f),
                        SciFiBlueAccent.copy(alpha = 0.45f)
                    )
                ),
                SciFiCardShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * Full-width Settings & Menu Card with glowing Icon Emblem, Title, Subtitle and Chevron >
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
    SciFiCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
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
                // Emblem Icon Box with cut corners / hexagonal feel
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CutCornerShape(8.dp))
                        .background(Color(0x2B00E5FF))
                        .border(1.2.dp, emblemBorder.copy(alpha = 0.8f), CutCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SciFiCyanGlow.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
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
    gradient: Brush = Brush.horizontalGradient(listOf(Color(0xFF0052D4), Color(0xFF4364F7), Color(0xFF6FB1FC))),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(SciFiButtonShape)
            .background(gradient)
            .border(1.2.dp, SciFiCyanGlow.copy(alpha = 0.9f), SciFiButtonShape)
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
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = Color.White,
                    fontSize = 14.5.sp
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
                (sin(phase + i * 0.6f) * 0.45f + 0.55f).coerceIn(0.2f, 1f)
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
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(SciFiCardShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xD908172F), Color(0xF2040C1A))
                )
            )
            .border(1.dp, SciFiCyanGlow.copy(alpha = 0.55f), SciFiCardShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "$icon $label",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = valueColor,
                    fontSize = 13.5.sp
                ),
                maxLines = 1
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
                fontSize = 12.sp
            )
        )
    }
}
