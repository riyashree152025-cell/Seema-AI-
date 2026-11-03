package com.raju.aiassistant.ui.brain

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
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.ui.theme.GoldAccent
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen 4: AI Brain
 * Matches Reference Image: 3D Holographic Chamber, Brain Status Readouts, 4 Capabilities Chips.
 */
@Composable
fun AiBrainScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = container.currentSettings.value)
    val assistantState by container.engine.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040714), Color(0xFF02040A))))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header: < AI Brain
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
            Icon(Icons.Filled.Psychology, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "AI Brain",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
            )
        }

        // Top Section: Cylindrical Holo Chamber with 3D Holographic Brain Display
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xD9081429), Color(0xF2040B17))))
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            HoloBrainCanvas(Modifier.fillMaxSize())
        }

        // Brain Status Readout Panel
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xDD09152B))
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRow(
                    label = "Brain Status",
                    value = "Active",
                    badgeColor = SuccessGreen,
                )
                StatusRow(
                    label = "Learning Mode",
                    value = if (settings.memoryEnabled) "On" else "Off",
                    badgeColor = if (settings.memoryEnabled) NeonCyan else Color.Gray,
                )
                StatusRow(
                    label = "Memory Usage",
                    value = "68%",
                    badgeColor = Color(0xFF88AAFF),
                )
                StatusRow(
                    label = "Knowledge Base",
                    value = "Updated Today",
                    badgeColor = SuccessGreen,
                )
            }
        }

        // Brain Capabilities Section
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Brain Capabilities",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp,
                ),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CapabilityChip(
                    title = "Fast\nThinking",
                    icon = Icons.Filled.Lightbulb,
                    accentColor = GoldAccent,
                    onClick = onOpenSettings,
                )
                CapabilityChip(
                    title = "Deep\nAnalysis",
                    icon = Icons.Filled.AutoAwesome,
                    accentColor = NeonCyan,
                    onClick = onOpenSettings,
                )
                CapabilityChip(
                    title = "Smart\nDecision",
                    icon = Icons.Filled.Shield,
                    accentColor = Color(0xFF0077FF),
                    onClick = onOpenSettings,
                )
                CapabilityChip(
                    title = "Continuous\nLearning",
                    icon = Icons.Filled.AllInclusive,
                    accentColor = NeonCyan,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, badgeColor: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFC0D5F0)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                ),
            )
        }
    }
}

@Composable
private fun CapabilityChip(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(74.dp, 82.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xDD0B162C))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(1.dp, accentColor.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                ),
                maxLines = 2,
            )
        }
    }
}

/**
 * Animated 3D Cylindrical Chamber with floating holographic neural nodes and brain silhouette.
 */
@Composable
private fun HoloBrainCanvas(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "brainAnim")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "brainRot",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "brainPulse",
    )

    Canvas(modifier = modifier) {
        val c = center
        val w = size.width
        val h = size.height

        // Upper & Lower Chamber Containment Rings
        val chamberR = (w * 0.32f).coerceAtMost(h * 0.45f)
        drawOval(
            color = NeonCyan.copy(alpha = 0.65f),
            topLeft = Offset(c.x - chamberR, 14f),
            size = androidx.compose.ui.geometry.Size(chamberR * 2, 22f),
            style = Stroke(width = 2.5f),
        )
        drawOval(
            color = NeonCyan.copy(alpha = 0.65f),
            topLeft = Offset(c.x - chamberR, h - 36f),
            size = androidx.compose.ui.geometry.Size(chamberR * 2, 22f),
            style = Stroke(width = 2.5f),
        )

        // Light beam gradient inside cylinder
        drawRect(
            brush = Brush.verticalGradient(
                listOf(NeonCyan.copy(alpha = 0.15f), NeonCyan.copy(alpha = 0.05f), NeonCyan.copy(alpha = 0.18f)),
            ),
            topLeft = Offset(c.x - chamberR, 25f),
            size = androidx.compose.ui.geometry.Size(chamberR * 2, h - 50f),
        )

        // Central floating brain neural sphere
        val brainR = chamberR * 0.55f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                listOf(NeonCyan.copy(alpha = 0.45f), Color(0xFF0077FF).copy(alpha = 0.2f), Color.Transparent),
                center = c,
                radius = brainR * 1.5f,
            ),
            radius = brainR * 1.5f,
            center = c,
        )

        // Brain Hemisphere Arcs & Nodes
        val nodeCount = 18
        for (i in 0 until nodeCount) {
            val a = Math.toRadians((i * (360f / nodeCount) + rotation).toDouble())
            val nr = brainR * (0.6f + 0.38f * sin(i * 1.7))
            val nx = c.x + (nr * cos(a) * 0.9f).toFloat()
            val ny = c.y + (nr * sin(a) * 0.7f).toFloat()

            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 3.5f,
                center = Offset(nx, ny),
            )

            // Neural synaptic lines connecting to center and neighbor
            if (i % 2 == 0) {
                drawLine(
                    color = NeonCyan.copy(alpha = 0.6f),
                    start = c,
                    end = Offset(nx, ny),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
