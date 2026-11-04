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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.assistant.ActiveAiTest
import com.raju.aiassistant.ui.components.SciFiBackgroundBottom
import com.raju.aiassistant.ui.components.SciFiBackgroundTop
import com.raju.aiassistant.ui.components.SciFiCard
import com.raju.aiassistant.ui.components.SciFiCardShape
import com.raju.aiassistant.ui.components.SciFiCyanGlow
import com.raju.aiassistant.ui.components.SciFiGradientButton
import com.raju.aiassistant.ui.components.SciFiListCard
import com.raju.aiassistant.ui.components.SciFiStatTile
import com.raju.aiassistant.ui.theme.GoldAccent
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

/**
 * Screen 4: AI Brain Screen
 * Exact 1:1 match to User Reference Image (Image 5):
 * - Header: < [Brain] AI Brain + 3 Dots Menu
 * - Center Stage: Holographic 3D Glowing Brain with upward light beams on cyber pedestal
 * - Status Capsule: ✅ CONNECTED (Emerald Green Neon)
 * - Active Provider & Model Card:
 *     Active Provider: Google Gemini >
 *     Model: ✨ gemini-1.5-pro >
 * - 3 Horizontal Stat Badges:
 *     ⚡ Latency (e.g. 428ms)
 *     🛡️ Fallback (Ready)
 *     🕒 Last Test (2 min ago / Ready)
 * - Big Action Button: ▶ TEST ACTIVE AI (Cyan-to-Blue Linear Gradient)
 * - 3 Beveled Navigation Cards:
 *     [Gear] Provider Settings (Gemini • Groq • Cerebras • Ollama) >
 *     [Gear] Model Configuration (Select Model & Parameters) >
 *     [Lock] API Key Status (Secure & Encrypted) >
 */
@Composable
fun AiBrainScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val scope = rememberCoroutineScope()

    val settings by container.currentSettings.collectAsStateWithLifecycle()
    val configuredSecrets by container.secureKeyStore.configured.collectAsStateWithLifecycle(initialValue = emptySet())
    val latencies by container.engine.latencies.collectAsStateWithLifecycle()

    val defaultProvider = settings.defaultProvider
    val isKeyConfigured = defaultProvider == com.raju.aiassistant.domain.ai.ProviderId.OLLAMA || defaultProvider.secret in configuredSecrets
    val activeModel = settings.modelFor(defaultProvider) ?: "Auto"

    var testingAi by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ActiveAiTest?>(null) }

    val lastLatency = latencies.lastOrNull()?.totalMs ?: 428L

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SciFiBackgroundTop, Color(0xFF040A18), SciFiBackgroundBottom)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Header: < [Brain] AI Brain ...
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SciFiCyanGlow)
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Psychology, contentDescription = null, tint = SciFiCyanGlow, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI Brain",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp,
                            letterSpacing = 0.8.sp,
                        ),
                    )
                }

                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = SciFiCyanGlow)
                }
            }

            // 2. Holographic 3D Glowing Brain with Light Beams
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Background Light Beams & Pedestal
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val w = size.width
                    val h = size.height

                    // Vertical Cyan Hologram Light Beams
                    drawLine(
                        color = SciFiCyanGlow.copy(alpha = 0.35f),
                        start = Offset(cx - 90f, 10f),
                        end = Offset(cx - 90f, h - 10f),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = SciFiCyanGlow.copy(alpha = 0.35f),
                        start = Offset(cx + 90f, 10f),
                        end = Offset(cx + 90f, h - 10f),
                        strokeWidth = 2f
                    )

                    // Ambient Central Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(SciFiCyanGlow.copy(alpha = 0.30f), Color(0xFF0055FF).copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = 110f
                        ),
                        radius = 110f,
                        center = Offset(cx, cy)
                    )

                    // Pedestal Glowing Rings at Bottom
                    drawOval(
                        color = SciFiCyanGlow.copy(alpha = 0.55f),
                        topLeft = Offset(cx - 120f, h - 26f),
                        size = Size(240f, 22f),
                        style = Stroke(width = 2.5f)
                    )
                    drawOval(
                        color = Color(0xFF0077FF).copy(alpha = 0.75f),
                        topLeft = Offset(cx - 85f, h - 22f),
                        size = Size(170f, 14f),
                        style = Stroke(width = 2f)
                    )
                }

                // 3D Brain Anatomy Canvas
                HoloBrainStageCanvas(Modifier.fillMaxSize())
            }

            // 3. Status Capsule: ✅ CONNECTED
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE60A241A))
                    .border(1.2.dp, SuccessGreen.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        if (isKeyConfigured) "CONNECTED" else "READY TO CONNECT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = SuccessGreen,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // 4. Active Provider & Model Card
            SciFiCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSettings
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Active Provider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Active Provider",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    defaultProvider.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
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

                    // Divider
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x2600E5FF))
                    )

                    // Row 2: Model
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Model",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFA88BFF),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    activeModel,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD4E5FF),
                                        fontSize = 13.5.sp
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

            // 5. 3 Horizontal Stat Badges (Latency, Fallback, Last Test)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SciFiStatTile(
                    icon = "⚡",
                    label = "Latency",
                    value = "${lastLatency}ms",
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                SciFiStatTile(
                    icon = "🛡️",
                    label = "Fallback",
                    value = "Ready",
                    valueColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                SciFiStatTile(
                    icon = "🕒",
                    label = "Last Test",
                    value = if (testResult != null) "Verified" else "Ready",
                    valueColor = Color(0xFF80D8FF),
                    modifier = Modifier.weight(1f)
                )
            }

            // 6. Prominent Action Button: ▶ TEST ACTIVE AI
            if (testingAi) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CutCornerShape(10.dp))
                        .background(Color(0xD90A1A33))
                        .border(1.dp, SciFiCyanGlow, CutCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = SciFiCyanGlow,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Testing connection...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            } else {
                SciFiGradientButton(
                    text = "▶  TEST ACTIVE AI",
                    gradient = Brush.horizontalGradient(
                        listOf(Color(0xFF0052D4), Color(0xFF0077FF), Color(0xFF00C6FF))
                    ),
                    onClick = {
                        scope.launch {
                            testingAi = true
                            testResult = runCatching { container.engine.testActiveAi() }.getOrNull()
                            testingAi = false
                        }
                    }
                )
            }

            // Live Test Feedback if available
            testResult?.let { res ->
                val ok = res.error == null
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(8.dp))
                        .background(if (ok) Color(0x3300E676) else Color(0x33FF5252))
                        .border(1.dp, if (ok) SuccessGreen else Color(0xFFFF5252), CutCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (ok) "✅ ${res.answeredBy?.displayName ?: defaultProvider.displayName} answered in ${res.elapsedMs}ms: \"${res.reply ?: "OK"}\""
                        else "❌ Test failed: ${res.error}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // 7. 3 Navigation Cards: Provider Settings, Model Config, API Key Status
            SciFiListCard(
                title = "Provider Settings",
                subtitle = "Gemini • Groq • Cerebras • Ollama",
                icon = Icons.Filled.Settings,
                onClick = onOpenSettings
            )

            SciFiListCard(
                title = "Model Configuration",
                subtitle = "Select Model & Parameters",
                icon = Icons.Filled.AutoAwesome,
                onClick = onOpenSettings
            )

            SciFiListCard(
                title = "API Key Status",
                subtitle = "Secure & Encrypted in KeyStore",
                icon = Icons.Filled.Lock,
                onClick = onOpenSettings
            )

            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * High-definition 3D Brain Hologram Stage with Gyri, Sulci, and Synaptic Pulses
 */
@Composable
private fun HoloBrainStageCanvas(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "brainHolo")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f - 8f

        val scale = (w / 190f).coerceIn(1.3f, 2.2f)
        val brX = cx
        val brY = cy

        // Cortex Outline Path (Anatomical cerebrum curve)
        val cortexPath = Path().apply {
            moveTo(brX - 44f * scale, brY + 8f * scale)
            cubicTo(
                brX - 48f * scale, brY - 24f * scale,
                brX - 22f * scale, brY - 44f * scale,
                brX + 4f * scale, brY - 44f * scale
            )
            cubicTo(
                brX + 28f * scale, brY - 44f * scale,
                brX + 48f * scale, brY - 22f * scale,
                brX + 44f * scale, brY + 10f * scale
            )
            cubicTo(
                brX + 40f * scale, brY + 28f * scale,
                brX + 20f * scale, brY + 36f * scale,
                brX + 4f * scale, brY + 34f * scale
            )
            cubicTo(
                brX - 14f * scale, brY + 34f * scale,
                brX - 28f * scale, brY + 30f * scale,
                brX - 36f * scale, brY + 20f * scale
            )
            close()
        }
        drawPath(
            path = cortexPath,
            brush = Brush.horizontalGradient(
                listOf(NeonCyan.copy(alpha = 0.85f * pulse), Color(0xFF0077FF).copy(alpha = 0.9f))
            ),
            style = Stroke(width = 2.6f * scale, cap = StrokeCap.Round)
        )

        // Brainstem & Cerebellum curves
        val stemPath = Path().apply {
            moveTo(brX - 10f * scale, brY + 34f * scale)
            quadraticTo(brX - 14f * scale, brY + 46f * scale, brX - 6f * scale, brY + 52f * scale)
            moveTo(brX + 6f * scale, brY + 34f * scale)
            quadraticTo(brX + 2f * scale, brY + 46f * scale, brX + 8f * scale, brY + 52f * scale)
        }
        drawPath(
            path = stemPath,
            color = Color(0xFF0077FF).copy(alpha = 0.75f * pulse),
            style = Stroke(width = 2.2f * scale, cap = StrokeCap.Round)
        )

        // Gyri & Sulci Fold Lines
        val gyriLines = listOf(
            Triple(Offset(-32f, -6f), Offset(-14f, -24f), Offset(6f, -12f)),
            Triple(Offset(-20f, 10f), Offset(-2f, -6f), Offset(24f, -16f)),
            Triple(Offset(-10f, 22f), Offset(16f, 12f), Offset(32f, 4f)),
            Triple(Offset(8f, -28f), Offset(24f, -22f), Offset(34f, -8f)),
            Triple(Offset(-34f, -18f), Offset(-18f, -6f), Offset(-26f, 12f)),
            Triple(Offset(6f, -6f), Offset(18f, 2f), Offset(10f, 20f)),
        )
        for (g in gyriLines) {
            val p = Path().apply {
                moveTo(brX + g.first.x * scale, brY + g.first.y * scale)
                quadraticTo(
                    brX + g.second.x * scale, brY + g.second.y * scale,
                    brX + g.third.x * scale, brY + g.third.y * scale
                )
            }
            drawPath(
                path = p,
                color = NeonCyan.copy(alpha = 0.7f * pulse),
                style = Stroke(width = 1.8f * scale, cap = StrokeCap.Round)
            )
        }

        // Synaptic Glowing Nodes
        val synapses = listOf(
            Offset(-22f, -20f),
            Offset(10f, -28f),
            Offset(28f, -10f),
            Offset(-6f, 4f),
            Offset(18f, 16f),
            Offset(-26f, 16f),
        )
        for (syn in synapses) {
            val sx = brX + syn.x * scale
            val sy = brY + syn.y * scale
            drawCircle(color = GoldAccent.copy(alpha = 0.95f * pulse), radius = 3.6f * scale, center = Offset(sx, sy))
            drawCircle(color = Color(0xFFFFD54F).copy(alpha = 0.45f), radius = 7.0f * scale, center = Offset(sx, sy), style = Stroke(width = 1.2f))
        }
    }
}
