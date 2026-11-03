package com.raju.aiassistant.ui.brain

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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val GoldAccent = Color(0xFFFFB800)
private val DOCUMENT_MIMES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain", "text/markdown", "text/x-markdown", "application/octet-stream"
)

/**
 * Screen 4: AI Brain
 * 1:1 match to Official Reference Image Screen 4 with Genuine Live Data:
 * - Top Split Layout:
 *   - Left: Cylindrical Holographic Containment Chamber with glowing 3D anatomical brain
 *   - Right: Sleek Status Card:
 *       • Brain Status (Active / Ready / Setup Needed with provider and model name)
 *       • Learning Mode (Active / Disabled based on real settings.memoryEnabled)
 *       • Memory Usage (actual saved items count & working context capacity %)
 *       • Knowledge Base (real document count and chunk summary)
 * - Bottom Section:
 *   - Brain Capabilities Header + 4 Functional Glowing Tiles:
 *       • Fast Thinking: Toggles Quick / Standard response mode
 *       • Deep Analysis: Opens RAG Knowledge Base document manager & importer
 *       • Smart Decision: Toggles Safety Mode
 *       • Continuous Learning: Toggles Memory Enabled directly
 */
@Composable
fun AiBrainScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val scope = rememberCoroutineScope()

    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = container.currentSettings.value)
    val memories by container.memoryRepository.memories.collectAsStateWithLifecycle(initialValue = emptyList())
    val docs by container.knowledgeRepository.documents.collectAsStateWithLifecycle(initialValue = emptyList())
    val configuredSecrets by container.secureKeyStore.configured.collectAsStateWithLifecycle(initialValue = emptySet())

    val defaultProvider = settings.defaultProvider
    val isKeyConfigured = defaultProvider.secret in configuredSecrets
    val activeModel = settings.modelFor(defaultProvider) ?: "Auto"

    var showKnowledgeDialog by remember { mutableStateOf(false) }

    val docPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { container.knowledgeRepository.import(it) }
            }
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
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Row 1: Brain Status (Real Provider & Key State)
                    Column {
                        Text("Brain Status", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp))
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            val dotColor = if (isKeyConfigured) SuccessGreen else WarningAmber
                            val statusLabel = if (isKeyConfigured) "Active" else "Setup Needed"
                            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                            Text(statusLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = dotColor, fontSize = 13.sp))
                        }
                        Text(
                            text = if (isKeyConfigured) "${defaultProvider.shortName} • $activeModel" else "Tap to add key",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp),
                            maxLines = 1,
                        )
                    }

                    // Row 2: Learning Mode (Real settings.memoryEnabled)
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

                    // Row 3: Memory Usage (Real Memory Database Usage)
                    Column {
                        val memoryCapRatio = (memories.size / 50f).coerceIn(0.04f, 1f)
                        val memoryPercent = (memoryCapRatio * 100).toInt()
                        Text("Memory Usage", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp))
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("$memoryPercent%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp))
                            Text("${memories.size} facts", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp))
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { memoryCapRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NeonCyan,
                            trackColor = Color(0x3300E5FF),
                        )
                    }

                    // Row 4: Knowledge Base (Real Document Summary)
                    Column {
                        Text("Knowledge Base", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (docs.isEmpty()) "No Docs Added" else "${docs.size} Docs (${docs.sumOf { it.chunkCount }} Chunks)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (docs.isEmpty()) Color.Gray else SuccessGreen,
                                fontSize = 12.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 3. Lower Section: Brain Capabilities Header & 4 Functional Tiles
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
                badge = if (settings.lowPowerMode) "TURBO" else "STD",
                onClick = {
                    scope.launch { container.settingsRepository.setLowPowerMode(!settings.lowPowerMode) }
                },
            )
            BrainCapabilityCard(
                title = "Deep\nAnalysis",
                icon = Icons.Filled.AutoAwesome,
                accentColor = NeonCyan,
                badge = "${docs.size} DOCS",
                onClick = { showKnowledgeDialog = true },
            )
            BrainCapabilityCard(
                title = "Smart\nDecision",
                icon = Icons.Filled.Security,
                accentColor = Color(0xFF0077FF),
                badge = if (settings.safetyMode) "SAFE" else "DIRECT",
                onClick = {
                    scope.launch { container.settingsRepository.setSafetyMode(!settings.safetyMode) }
                },
            )
            BrainCapabilityCard(
                title = "Continuous\nLearning",
                icon = Icons.Filled.AllInclusive,
                accentColor = NeonCyan,
                badge = if (settings.memoryEnabled) "ON" else "OFF",
                onClick = {
                    scope.launch { container.settingsRepository.setMemoryEnabled(!settings.memoryEnabled) }
                },
            )
        }
    }

    // Knowledge Base Dialog for Deep Analysis capability
    if (showKnowledgeDialog) {
        AlertDialog(
            onDismissRequest = { showKnowledgeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = NeonCyan)
                    Text("Knowledge Base (RAG)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Import PDF, Word (.docx), TXT or Markdown documents. Seema indexes them locally and adds relevant passages when you ask questions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        "Currently Imported: ${docs.size} document(s) with ${docs.sumOf { it.chunkCount }} passages indexed.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NeonCyan,
                    )
                    docs.take(3).forEach { d ->
                        Text("• ${d.name} (${d.chunkCount} chunks)", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        docPickerLauncher.launch(DOCUMENT_MIMES)
                        showKnowledgeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                ) {
                    Text("Add Documents", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKnowledgeDialog = false }) {
                    Text("Close", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF0A1124),
        )
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

        // 1. Draw Glass Cylinder Frame & Metallic Top/Bottom Caps
        val capH = 14f
        val capW = w * 0.76f
        val capLeft = cx - capW / 2f

        // Top Cap
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFF0F2644), NeonCyan, Color(0xFF0F2644))),
            topLeft = Offset(capLeft, 12f),
            size = Size(capW, capH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
        // Bottom Base Cap
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFF0F2644), NeonCyan, Color(0xFF0F2644))),
            topLeft = Offset(capLeft, h - 26f),
            size = Size(capW, capH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        // Glass containment glow lines
        drawLine(
            color = NeonCyan.copy(alpha = 0.45f),
            start = Offset(capLeft + 8f, 26f),
            end = Offset(capLeft + 8f, h - 26f),
            strokeWidth = 1.5f
        )
        drawLine(
            color = NeonCyan.copy(alpha = 0.45f),
            start = Offset(capLeft + capW - 8f, 26f),
            end = Offset(capLeft + capW - 8f, h - 26f),
            strokeWidth = 1.5f
        )

        // 2. Base Energy Platform (Dual glowing cyan concentric rings)
        drawOval(
            color = NeonCyan.copy(alpha = 0.35f * pulse),
            topLeft = Offset(cx - capW * 0.42f, h - 38f),
            size = Size(capW * 0.84f, 16f),
            style = Stroke(width = 2.5f)
        )
        drawOval(
            color = Color(0xFF0077FF).copy(alpha = 0.45f * pulse),
            topLeft = Offset(cx - capW * 0.28f, h - 34f),
            size = Size(capW * 0.56f, 10f),
            style = Stroke(width = 2f)
        )

        // 3. Ambient blue/cyan glow around central brain
        drawCircle(
            brush = Brush.radialGradient(
                listOf(NeonCyan.copy(alpha = 0.32f * pulse), Color(0xFF0055FF).copy(alpha = 0.12f), Color.Transparent),
                center = Offset(cx, cy),
                radius = 65f
            ),
            radius = 65f,
            center = Offset(cx, cy)
        )

        // 4. Stylized 3D Anatomical Brain Profile & Neural Gyri Curves
        val scale = (w / 180f).coerceIn(0.7f, 1.25f)
        val brX = cx
        val brY = cy

        fun dpToP(x: Float, y: Float) = Offset(brX + x * scale, brY + y * scale)

        // Cortex Outline Path (Cerebrum + Cerebellum)
        val cortexPath = Path().apply {
            moveTo(brX - 34f * scale, brY + 6f * scale)
            cubicTo(
                brX - 38f * scale, brY - 18f * scale,
                brX - 18f * scale, brY - 34f * scale,
                brX + 2f * scale, brY - 34f * scale
            )
            cubicTo(
                brX + 22f * scale, brY - 34f * scale,
                brX + 38f * scale, brY - 16f * scale,
                brX + 34f * scale, brY + 8f * scale
            )
            cubicTo(
                brX + 32f * scale, brY + 22f * scale,
                brX + 16f * scale, brY + 28f * scale,
                brX + 2f * scale, brY + 26f * scale
            )
            cubicTo(
                brX - 10f * scale, brY + 26f * scale,
                brX - 22f * scale, brY + 24f * scale,
                brX - 28f * scale, brY + 16f * scale
            )
            close()
        }
        drawPath(
            path = cortexPath,
            color = NeonCyan.copy(alpha = 0.85f * pulse),
            style = Stroke(width = 2.4f * scale, cap = StrokeCap.Round)
        )

        // Brainstem & Cerebellum curves
        val stemPath = Path().apply {
            moveTo(brX - 8f * scale, brY + 26f * scale)
            quadraticTo(brX - 12f * scale, brY + 36f * scale, brX - 6f * scale, brY + 40f * scale)
            moveTo(brX + 4f * scale, brY + 26f * scale)
            quadraticTo(brX + 2f * scale, brY + 36f * scale, brX + 6f * scale, brY + 40f * scale)
        }
        drawPath(
            path = stemPath,
            color = Color(0xFF0077FF).copy(alpha = 0.75f * pulse),
            style = Stroke(width = 2f * scale, cap = StrokeCap.Round)
        )

        // Detailed Gyri & Sulci Fold Lines (Reference exact convolutions)
        val gyriLines = listOf(
            Triple(Offset(-24f, -4f), Offset(-10f, -18f), Offset(4f, -8f)),
            Triple(Offset(-16f, 8f), Offset(-2f, -4f), Offset(18f, -12f)),
            Triple(Offset(-8f, 18f), Offset(12f, 10f), Offset(24f, 4f)),
            Triple(Offset(6f, -22f), Offset(18f, -18f), Offset(26f, -6f)),
            Triple(Offset(-26f, -14f), Offset(-14f, -4f), Offset(-20f, 10f)),
            Triple(Offset(4f, -4f), Offset(14f, 2f), Offset(8f, 16f)),
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
                color = NeonCyan.copy(alpha = 0.65f * pulse),
                style = Stroke(width = 1.6f * scale, cap = StrokeCap.Round)
            )
        }

        // Golden Glowing Synaptic Node Highlights (Reference Screen 4 synaptic nodes)
        val synapses = listOf(
            Offset(-18f, -16f),
            Offset(8f, -22f),
            Offset(22f, -8f),
            Offset(-4f, 2f),
            Offset(14f, 12f),
            Offset(-20f, 12f),
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
    badge: String? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xD9081224))
            .border(1.2.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
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
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                ),
                textAlign = TextAlign.Center,
            )
            if (badge != null) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                        ),
                    )
                }
            }
        }
    }
}
