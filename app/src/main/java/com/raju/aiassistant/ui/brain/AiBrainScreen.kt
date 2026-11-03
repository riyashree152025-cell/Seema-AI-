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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * 1:1 match to Official Reference Image Screen 4:
 * - Top Split Layout:
 *   - Left: Cylindrical Holographic Containment Chamber with glowing 3D anatomical brain
 *   - Right: Sleek Status Card (Brain Status Active, Learning Mode On, Memory Usage 68% with progress bar, Knowledge Base Updated Today)
 * - Bottom Section:
 *   - Brain Capabilities Header + 4 Glowing Tiles (Fast Thinking, Deep Analysis, Smart Decision, Continuous Learning)
 */
@Composable
fun AiBrainScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = container.currentSettings.value)

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040714), Color(0xFF02040A))))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Header: < AI Brain
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Psychology, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "AI Brain",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
            )
        }

        // 2. Reference Screen 4 Split Upper Layout (Left Chamber + Right Status Card)
        Row(
            Modifier
                .fillMaxWidth()
                .height(230.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Cylindrical Holographic Glass Chamber
            Box(
                Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xD9081429), Color(0xF2040B17))))
                    .border(1.2.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                HoloBrainChamberCanvas(Modifier.fillMaxSize())
            }

            // Right: Brain Status Readout Panel
            Box(
                Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xD9081429), Color(0xF2040B17))))
                    .border(1.2.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Row 1: Brain Status
                    Column {
                        Text("Brain Status", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp))
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(SuccessGreen))
                            Text("Active", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = SuccessGreen, fontSize = 13.sp))
                        }
                    }

                    // Row 2: Learning Mode
                    Column {
                        Text("Learning Mode", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (settings.memoryEnabled) "On" else "Off",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (settings.memoryEnabled) SuccessGreen else Color.Gray,
                                fontSize = 13.sp,
                            )
                        )
                    }

                    // Row 3: Memory Usage
                    Column {
                        Text("Memory Usage", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp))
                        Spacer(Modifier.height(2.dp))
                        Text("68%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp))
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { 0.68f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NeonCyan,
                            trackColor = Color(0x3300E5FF),
                        )
                    }

                    // Row 4: Knowledge Base
                    Column {
                        Text("Knowledge Base", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp))
                        Spacer(Modifier.height(2.dp))
                        Text("Updated Today", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = SuccessGreen, fontSize = 12.sp))
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 3. Lower Section: Brain Capabilities Header & 4 Glowing Chips
        Text(
            "Brain Capabilities",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BrainCapabilityCard(
                title = "Fast\nThinking",
                icon = Icons.Filled.Lightbulb,
                accentColor = GoldAccent,
                onClick = onOpenSettings,
            )
            BrainCapabilityCard(
                title = "Deep\nAnalysis",
                icon = Icons.Filled.AutoAwesome,
                accentColor = NeonCyan,
                onClick = onOpenSettings,
            )
            BrainCapabilityCard(
                title = "Smart\nDecision",
                icon = Icons.Filled.Security,
                accentColor = Color(0xFF0077FF),
                onClick = onOpenSettings,
            )
            BrainCapabilityCard(
                title = "Continuous\nLearning",
                icon = Icons.Filled.AllInclusive,
                accentColor = NeonCyan,
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun HoloBrainChamberCanvas(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "holoBrain")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "float"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f + floatY

        val topY = h * 0.12f
        val botY = h * 0.88f
        val chamberW = w * 0.80f

        // 1. Vertical blue containment light beams
        val beams = 14
        for (i in 0..beams) {
            val bx = cx - chamberW / 2f + (chamberW / beams) * i
            drawLine(
                color = NeonCyan.copy(alpha = 0.12f * pulse),
                start = Offset(bx, topY + 10f),
                end = Offset(bx, botY - 10f),
                strokeWidth = 1.5f
            )
        }

        // 2. Base Pedestal: Tiered Concentric Metallic & Golden Rings
        drawOval(
            color = Color(0xFF0F1B2B),
            topLeft = Offset(cx - chamberW * 0.52f, botY - 16f),
            size = Size(chamberW * 1.04f, 32f),
            style = Stroke(width = 4f)
        )
        drawOval(
            brush = Brush.horizontalGradient(listOf(GoldAccent, Color(0xFFFFD54F), GoldAccent)),
            topLeft = Offset(cx - chamberW * 0.46f, botY - 12f),
            size = Size(chamberW * 0.92f, 24f),
            style = Stroke(width = 3.5f)
        )
        drawOval(
            color = NeonCyan.copy(alpha = 0.9f * pulse),
            topLeft = Offset(cx - chamberW * 0.38f, botY - 8f),
            size = Size(chamberW * 0.76f, 16f),
            style = Stroke(width = 2.5f)
        )

        // 3. Top Metallic Ring with Cyan Emitter Notches
        drawOval(
            color = Color(0xFF1E2F45),
            topLeft = Offset(cx - chamberW * 0.48f, topY - 12f),
            size = Size(chamberW * 0.96f, 24f),
            style = Stroke(width = 4f)
        )
        drawOval(
            color = NeonCyan.copy(alpha = 0.9f),
            topLeft = Offset(cx - chamberW * 0.42f, topY - 8f),
            size = Size(chamberW * 0.84f, 16f),
            style = Stroke(width = 2.5f)
        )

        // 4. Central Glowing Anatomical 3D Brain
        val brX = cx
        val brY = cy - 2f
        val scale = (chamberW * 0.35f) / 75f

        // Ambient radial glow behind the brain
        drawCircle(
            brush = Brush.radialGradient(
                listOf(NeonCyan.copy(alpha = 0.35f * pulse), Color(0x180055FF), Color.Transparent),
                center = Offset(brX, brY),
                radius = 65f * scale * pulse
            ),
            radius = 65f * scale * pulse,
            center = Offset(brX, brY)
        )

        // Anatomical Sulci & Gyri Curves
        val lobes = listOf(
            listOf(Offset(-55f, -15f), Offset(-40f, -40f), Offset(-10f, -48f), Offset(20f, -45f), Offset(45f, -30f), Offset(60f, -10f)),
            listOf(Offset(60f, -10f), Offset(65f, 12f), Offset(50f, 30f), Offset(25f, 38f), Offset(8f, 32f)),
            listOf(Offset(-55f, -15f), Offset(-60f, 8f), Offset(-40f, 24f), Offset(-15f, 24f), Offset(0f, 16f)),
            listOf(Offset(-25f, -30f), Offset(-12f, -16f), Offset(6f, -30f), Offset(24f, -16f), Offset(42f, -22f)),
            listOf(Offset(-36f, -12f), Offset(-18f, 0f), Offset(8f, -8f), Offset(28f, 4f), Offset(42f, -2f)),
            listOf(Offset(-22f, 8f), Offset(0f, 4f), Offset(22f, 18f)),
            listOf(Offset(18f, 32f), Offset(32f, 42f), Offset(50f, 36f), Offset(54f, 22f)),
            listOf(Offset(-4f, 22f), Offset(-4f, 48f), Offset(4f, 48f), Offset(4f, 22f))
        )

        for (lobe in lobes) {
            val path = Path()
            lobe.forEachIndexed { idx, pt ->
                val px = brX + pt.x * scale
                val py = brY + pt.y * scale
                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            // Glow layer
            drawPath(path, color = NeonCyan.copy(alpha = 0.55f * pulse), style = Stroke(width = 4.5f * scale, cap = StrokeCap.Round))
            // Crisp core
            drawPath(path, color = Color.White.copy(alpha = 0.95f), style = Stroke(width = 1.8f * scale, cap = StrokeCap.Round))
        }

        // Glowing Synaptic Nodes
        val synapses = listOf(
            Offset(-32f, -36f), Offset(10f, -40f), Offset(36f, -26f),
            Offset(-45f, -8f), Offset(-16f, -12f), Offset(14f, -4f),
            Offset(46f, 8f), Offset(-30f, 14f), Offset(6f, 10f), Offset(36f, 30f)
        )
        for (syn in synapses) {
            val sx = brX + syn.x * scale
            val sy = brY + syn.y * scale
            drawCircle(color = GoldAccent.copy(alpha = 0.95f * pulse), radius = 3.2f * scale, center = Offset(sx, sy))
            drawCircle(color = Color(0xFFFFD54F).copy(alpha = 0.5f), radius = 6.0f * scale, center = Offset(sx, sy), style = Stroke(width = 1.2f))
        }
    }
}

@Composable
private fun BrainCapabilityCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xD9081224))
            .border(1.2.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
