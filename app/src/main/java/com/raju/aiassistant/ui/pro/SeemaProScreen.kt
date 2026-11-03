package com.raju.aiassistant.ui.pro

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raju.aiassistant.ui.theme.GoldAccent
import com.raju.aiassistant.ui.theme.GoldGlow
import com.raju.aiassistant.ui.theme.GoldLight
import com.raju.aiassistant.ui.theme.NeonCyan
import kotlin.math.sin

/**
 * Screen 9: SEEMA AI Pro
 * Matches Reference Image: Gold Crown Header, 5 Features Checklist, Golden Energy Pedestal, Gold "Upgrade Now" Button.
 */
@Composable
fun SeemaProScreen(
    onBack: () -> Unit,
) {
    var showStatusDialog by remember { mutableStateOf(false) }

    val proFeatures = listOf(
        "Advanced AI Features",
        "Unlimited Voice Commands",
        "Pro Smart Automation",
        "Priority Updates",
        "Ad-Free Experience",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070B16), Color(0xFF03050B))))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header with Back Arrow
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldAccent)
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👑", fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        "SEEMA AI Pro",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = GoldLight,
                            letterSpacing = 1.sp,
                        ),
                    )
                    Text(
                        "Unlock More Power",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NeonCyan,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Split Layout: Left Checklist, Right Glowing Pedestal / Arrow
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xD90D172E), Color(0xF2070D1A))))
                .border(1.dp, GoldAccent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Checklist Column
            Column(
                Modifier.weight(1.3f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                proFeatures.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFA000))
                                .border(1.dp, GoldAccent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            feature,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }

            // Right Visual: Glowing Futuristic Pedestal with Upward Energy Arrow
            Box(
                Modifier
                    .weight(0.9f)
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                GoldenPillarCanvas(Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.height(16.dp))

        // Bottom CTA: Gold Gradient "Upgrade Now" Button
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFFFB800), Color(0xFFFFD54F), Color(0xFFFFA000)),
                    ),
                )
                .clickable { showStatusDialog = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("👑", fontSize = 18.sp)
                Text(
                    "Upgrade Now",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1000),
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }

    // Honest Status Dialog (Strict compliance with condition 3)
    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👑", fontSize = 20.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("SEEMA AI Pro Status", color = GoldLight)
                }
            },
            text = {
                Text(
                    "SEEMA AI Pro is currently unlocked in Personal Edition on this device.\n\n" +
                        "All Pro capabilities (Advanced AI, Multi-Provider Brain, Unlimited Voice, and Automation) " +
                        "are fully active with your configured API keys. Commercial in-app billing is not required.",
                    color = Color(0xFFC0D5F0),
                )
            },
            confirmButton = {
                Button(
                    onClick = { showStatusDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun GoldenPillarCanvas(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "goldAnim")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "goldWave",
    )

    Canvas(modifier = modifier) {
        val c = center
        val w = size.width
        val h = size.height

        // Tiered metallic gold base rings
        val baseW = w * 0.75f
        drawOval(
            color = GoldGlow.copy(alpha = 0.8f),
            topLeft = Offset(c.x - baseW / 2f, h - 28f),
            size = androidx.compose.ui.geometry.Size(baseW, 18f),
            style = Stroke(width = 3.5f),
        )
        drawOval(
            color = GoldLight,
            topLeft = Offset(c.x - baseW * 0.35f, h - 38f),
            size = androidx.compose.ui.geometry.Size(baseW * 0.7f, 14f),
            style = Stroke(width = 2.5f),
        )

        // Upward glowing energy beam
        val beamW = baseW * 0.45f
        val pulse = 0.8f + 0.2f * sin(wave)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, GoldLight.copy(alpha = 0.5f * pulse), GoldGlow.copy(alpha = 0.8f)),
            ),
            topLeft = Offset(c.x - beamW / 2f, 20f),
            size = androidx.compose.ui.geometry.Size(beamW, h - 55f),
        )

        // Giant Golden Arrow pointing upwards
        val arrowY = 32f - 8f * sin(wave)
        val arrowW = beamW * 0.75f
        val arrowPath = Path().apply {
            moveTo(c.x, arrowY) // Arrow Tip
            lineTo(c.x + arrowW / 2f, arrowY + arrowW * 0.8f) // Right wing
            lineTo(c.x + arrowW * 0.22f, arrowY + arrowW * 0.8f) // Right notch
            lineTo(c.x + arrowW * 0.22f, arrowY + arrowW * 1.5f) // Right stem
            lineTo(c.x - arrowW * 0.22f, arrowY + arrowW * 1.5f) // Left stem
            lineTo(c.x - arrowW * 0.22f, arrowY + arrowW * 0.8f) // Left notch
            lineTo(c.x - arrowW / 2f, arrowY + arrowW * 0.8f) // Left wing
            close()
        }

        drawPath(
            path = arrowPath,
            brush = Brush.verticalGradient(listOf(Color.White, GoldLight, GoldAccent)),
        )
    }
}
