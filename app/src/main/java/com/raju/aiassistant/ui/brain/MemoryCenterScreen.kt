package com.raju.aiassistant.ui.brain

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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.ui.components.SciFiBackgroundBottom
import com.raju.aiassistant.ui.components.SciFiBackgroundTop
import com.raju.aiassistant.ui.components.SciFiCard
import com.raju.aiassistant.ui.components.SciFiCyanGlow
import com.raju.aiassistant.ui.components.SciFiListCard
import com.raju.aiassistant.ui.components.SciFiProgressRing
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

/**
 * Screen: Memory Center Screen
 * Exact 1:1 match to User Reference Image (Image 3):
 * - Header: < [Brain] Memory Center + 3 Dots Menu
 * - Top Card: Memory Status (Active, Used: 42 MB / 1 GB) + Circular Progress Ring (4%)
 * - 3 Cut-Corner Beveled Cards:
 *     [Circuit] View Memories (Browse AI Memories) >
 *     [Data] Manage Memory (Clear, Export, Backup) >
 *     [Gear] Memory Settings (Auto-Learn, Retention) >
 * - Bottom Special Card: Glowing Holographic Brain Icon + 'Your memories make SEEMA smarter! ❤️'
 */
@Composable
fun MemoryCenterScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val scope = rememberCoroutineScope()

    val settings by container.currentSettings.collectAsStateWithLifecycle()
    val memories by container.memoryRepository.memories.collectAsStateWithLifecycle(initialValue = emptyList())

    var showViewDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }

    val usedMb = (memories.size * 0.8f + 12f).toInt()
    val percentage = ((memories.size / 50f).coerceIn(0.04f, 1f) * 100).toInt()

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
            // 1. Header: < [Brain] Memory Center ...
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
                        "Memory Center",
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

            // 2. Top Card: Memory Status & Circular Progress Ring (4%)
            SciFiCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Memory Status",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (settings.memoryEnabled) SuccessGreen else Color.Gray)
                            )
                            Text(
                                if (settings.memoryEnabled) "Active" else "Paused",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (settings.memoryEnabled) SuccessGreen else Color.Gray,
                                    fontSize = 16.5.sp
                                )
                            )
                        }
                        Text(
                            "Used: $usedMb MB / 1 GB  (${memories.size} facts)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 15.sp
                            )
                        )
                    }

                    // Circular Progress Ring
                    SciFiProgressRing(
                        percentage = percentage,
                        size = 72.dp,
                        ringColor = SuccessGreen,
                        strokeWidth = 7.dp
                    )
                }
            }

            // 3. Three Beveled Action Cards
            SciFiListCard(
                title = "View Memories",
                subtitle = "Browse AI Memories (${memories.size} stored)",
                icon = Icons.Filled.Visibility,
                onClick = { showViewDialog = true }
            )

            SciFiListCard(
                title = "Manage Memory",
                subtitle = "Clear, Export, Backup",
                icon = Icons.Filled.Storage,
                onClick = { showManageDialog = true }
            )

            SciFiListCard(
                title = "Memory Settings",
                subtitle = "Auto-Learn, Retention",
                icon = Icons.Filled.Settings,
                onClick = onOpenSettings
            )

            Spacer(Modifier.height(8.dp))

            // 4. Bottom Special Card: Brain Graphic + 'Your memories make SEEMA smarter! ❤️'
            SciFiCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Color(0xFF0077FF)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CutCornerShape(10.dp))
                            .background(Color(0x2B00E5FF))
                            .border(1.2.dp, SciFiCyanGlow, CutCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Your memories make SEEMA smarter! ❤️",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 17.sp,
                                lineHeight = 19.sp
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Long-term facts persist across sessions and power intelligent contextual responses.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 14.5.sp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // View Memories Dialog
        if (showViewDialog) {
            AlertDialog(
                onDismissRequest = { showViewDialog = false },
                confirmButton = {
                    TextButton(onClick = { showViewDialog = false }) {
                        Text("Close", color = NeonCyan)
                    }
                },
                title = { Text("AI Memories (${memories.size})", color = Color.White) },
                text = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (memories.isEmpty()) {
                            Text("No memories saved yet. Talk with SEEMA to build memories!", color = Color.Gray)
                        } else {
                            memories.forEach { mem ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x2600E5FF))
                                        .padding(8.dp)
                                ) {
                                    Text(mem.text, color = Color.White, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                },
                containerColor = Color(0xFF071224)
            )
        }

        // Manage Memory Dialog
        if (showManageDialog) {
            AlertDialog(
                onDismissRequest = { showManageDialog = false },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                container.memoryRepository.deleteAll()
                                showManageDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("Clear All", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManageDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                title = { Text("Manage Memory", color = Color.White) },
                text = {
                    Text(
                        "Are you sure you want to clear AI memory facts? This cannot be undone.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                },
                containerColor = Color(0xFF071224)
            )
        }
    }
}
