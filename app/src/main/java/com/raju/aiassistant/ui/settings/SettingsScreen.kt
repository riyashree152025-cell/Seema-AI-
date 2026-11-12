package com.raju.aiassistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.domain.ai.AiModel
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.CloudVoice
import com.raju.aiassistant.domain.voice.VoiceCatalog
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.components.SciFiCyanGlow
import com.raju.aiassistant.ui.components.SciFiSettingsPanel
import com.raju.aiassistant.ui.components.SciFiSettingsRow
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber
import kotlin.math.cos
import kotlin.math.sin

/**
 * Clean, Modular, Futuristic Glassy Settings Screen matching User Reference Image (6-Panel Architecture)
 * 1. Screen 1: Settings Menu with 6 Glassy Cards & Robot Avatar Header
 * 2. Screen 2: Brain & AI 2-Column Provider Grid with official Logos & Connected Badges
 * 3. Screen 3: AI Provider Settings Detail with Cosmic Portal, API Key, Test, Save, Model Dropdown, Status Box
 * 4. Screen 4: Voice & Speech with Glowing Mic Banner & Engine Selection (Cartesia, Sarvam, Android)
 * 5. Screen 5: Voice Settings Fine-Tune with Presets, Speed, Pitch, Toggles & Test Voice
 * 6. Screen 6: Appearance & Performance
 * 7. Screen 7, 8, 9: Privacy, Automation, System Integration
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = RajuViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val cartesiaVoices by viewModel.cartesiaVoices.collectAsStateWithLifecycle()
    val previewStatus by viewModel.previewStatus.collectAsStateWithLifecycle()
    val voiceTest by viewModel.voiceTest.collectAsStateWithLifecycle()
    val s = state.settings

    // Navigation state inside Settings
    var currentScreen by remember { mutableStateOf("menu") }
    var selectedAiProvider by remember { mutableStateOf(ProviderId.GEMINI) }

    // Intercept hardware Android back button to navigate back gracefully through the hierarchy
    BackHandler(enabled = currentScreen != "menu") {
        currentScreen = when (currentScreen) {
            "ai_detail", "tavily_detail" -> "brain_ai"
            "voice_finetune", "voice_catalog" -> "voice_speech"
            else -> "menu"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020614),
                        Color(0xFF06142E),
                        Color(0xFF020614)
                    )
                )
            )
    ) {
        when (currentScreen) {
            "menu" -> {
                SettingsMenuScreen(
                    onBack = onBack,
                    onOpenBrainAi = { currentScreen = "brain_ai" },
                    onOpenVoice = { currentScreen = "voice_speech" },
                    onOpenAppearance = { currentScreen = "appearance" },
                    onOpenPrivacy = { currentScreen = "privacy" },
                    onOpenAutomation = { currentScreen = "automation" },
                    onOpenSystem = { currentScreen = "system" }
                )
            }
            "brain_ai" -> {
                BrainAiGridScreen(
                    s = s,
                    secrets = state.secrets,
                    onBack = { currentScreen = "menu" },
                    onSelectProvider = { provider ->
                        selectedAiProvider = provider
                        currentScreen = "ai_detail"
                    },
                    onSelectTavily = { currentScreen = "tavily_detail" },
                    personalityPanel = { PersonalityPanel(s, viewModel) },
                    // More AI backups: the app could already use these keys but had no place to enter them.
                    moreKeys = {
                        // Which AI and model really answer now, the last reply's route, and a real test.
                        ActiveAiPanel(
                            s,
                            keySaved = { state.secrets[it.secret]?.configured == true },
                            testing = { state.secrets[it.secret]?.busy == true },
                            viewModel = viewModel,
                        )
                        VoiceEngineSecretCard(
                            secretId = SecretId.OPENROUTER_API_KEY,
                            title = "OpenRouter API Key (backup AI, free models)",
                            secretStatus = state.secrets[SecretId.OPENROUTER_API_KEY] ?: SecretStatus(),
                            viewModel = viewModel
                        )
                        VoiceEngineSecretCard(
                            secretId = SecretId.DEEPSEEK_API_KEY,
                            title = "DeepSeek API Key (backup AI)",
                            secretStatus = state.secrets[SecretId.DEEPSEEK_API_KEY] ?: SecretStatus(),
                            viewModel = viewModel
                        )
                    }
                )
            }
            "ai_detail" -> {
                AiProviderDetailScreen(
                    provider = selectedAiProvider,
                    s = s,
                    secretStatus = state.secrets[selectedAiProvider.secret] ?: SecretStatus(),
                    models = models[selectedAiProvider] ?: emptyList(),
                    viewModel = viewModel,
                    onBack = { currentScreen = "brain_ai" }
                )
            }
            "tavily_detail" -> {
                TavilyDetailScreen(
                    secretStatus = state.secrets[SecretId.TAVILY_API_KEY] ?: SecretStatus(),
                    viewModel = viewModel,
                    onBack = { currentScreen = "brain_ai" },
                    extra = {
                        Spacer(Modifier.height(16.dp))
                        Text("🚆 RailKit (PNR, ट्रेनें, लाइव स्टेटस, सीट, किराया)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF69F0AE)))
                        Text("railkit.in dashboard की API key यहाँ डालिए (Advance plan)। Key सिर्फ़ इस फ़ोन में encrypted रहती है।", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                        VoiceEngineSecretCard(
                            secretId = SecretId.RAILKIT_API_KEY,
                            title = "RailKit API key",
                            secretStatus = state.secrets[SecretId.RAILKIT_API_KEY] ?: SecretStatus(),
                            viewModel = viewModel,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("🚆 PNR status service (background PNR alerts)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF69F0AE)))
                        Text("Aapki apni PNR service ka URL — {pnr} ki jagah PNR aata hai. Official free API nahi hai.", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                        VoiceEngineSecretCard(
                            secretId = SecretId.PNR_STATUS_ENDPOINT,
                            title = "PNR service URL",
                            secretStatus = state.secrets[SecretId.PNR_STATUS_ENDPOINT] ?: SecretStatus(),
                            viewModel = viewModel,
                        )
                        VoiceEngineSecretCard(
                            secretId = SecretId.PNR_STATUS_TOKEN,
                            title = "PNR service token (optional)",
                            secretStatus = state.secrets[SecretId.PNR_STATUS_TOKEN] ?: SecretStatus(),
                            viewModel = viewModel,
                        )
                    }
                )
            }
            "voice_speech" -> {
                VoiceSpeechScreen(
                    s = s,
                    secrets = state.secrets,
                    viewModel = viewModel,
                    onBack = { currentScreen = "menu" },
                    onOpenFineTune = { currentScreen = "voice_finetune" },
                    onOpenVoiceCatalog = { currentScreen = "voice_catalog" }
                )
            }
            "voice_finetune" -> {
                VoiceFineTuneScreen(
                    s = s,
                    viewModel = viewModel,
                    voiceTest = voiceTest,
                    previewStatus = previewStatus,
                    onBack = { currentScreen = "voice_speech" }
                )
            }
            "voice_catalog" -> {
                VoiceCatalogScreen(
                    s = s,
                    secrets = state.secrets,
                    viewModel = viewModel,
                    cartesiaVoices = cartesiaVoices,
                    previewStatus = previewStatus,
                    onBack = { currentScreen = "voice_speech" }
                )
            }
            "appearance" -> {
                AppearanceScreen(
                    s = s,
                    viewModel = viewModel,
                    onBack = { currentScreen = "menu" }
                )
            }
            "privacy" -> {
                PrivacyScreen(
                    s = s,
                    viewModel = viewModel,
                    onBack = { currentScreen = "menu" }
                )
            }
            "automation" -> {
                AutomationScreen(
                    s = s,
                    viewModel = viewModel,
                    onBack = { currentScreen = "menu" }
                )
            }
            "system" -> {
                SystemScreen(
                    s = s,
                    viewModel = viewModel,
                    onBack = { currentScreen = "menu" },
                    secrets = state.secrets,
                )
            }
        }
    }
}

// =========================================================================================
// VECTOR OFFICIAL LOGOS (Gemini, Groq, Cerebras, Tavily)
// =========================================================================================

@Composable
fun GeminiLogo(modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.48f
        val path = Path().apply {
            moveTo(cx, cy - r)
            quadraticBezierTo(cx + r * 0.14f, cy - r * 0.14f, cx + r, cy)
            quadraticBezierTo(cx + r * 0.14f, cy + r * 0.14f, cx, cy + r)
            quadraticBezierTo(cx - r * 0.14f, cy + r * 0.14f, cx - r, cy)
            quadraticBezierTo(cx - r * 0.14f, cy - r * 0.14f, cx, cy - r)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF4285F4), // Google Blue
                    Color(0xFF9B51E0), // Purple
                    Color(0xFFFF4081), // Pink
                    Color(0xFFFFB300)  // Amber
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
    }
}

@Composable
fun GroqLogo(modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.44f
        val numRays = 12
        for (i in 0 until numRays) {
            val angle = (i * 360f / numRays) * (Math.PI.toFloat() / 180f)
            val x1 = cx + (r * 0.40f) * cos(angle)
            val y1 = cy + (r * 0.40f) * sin(angle)
            val x2 = cx + r * cos(angle)
            val y2 = cy + r * sin(angle)
            drawLine(
                brush = Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF3D00))),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = w * 0.12f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun CerebrasLogo(modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.38f
        drawArc(
            brush = Brush.linearGradient(listOf(Color(0xFFFF6E40), Color(0xFFFF1744), Color(0xFFFF9100))),
            startAngle = 45f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = w * 0.20f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun TavilyLogo(modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF00E5FF), Color(0xFF0077FF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = "Tavily",
            tint = Color.White,
            modifier = Modifier.size((size.value * 0.6f).dp)
        )
    }
}

// =========================================================================================
// SCREEN 1: SETTINGS MENU SCREEN (Matching Panel 1 in User Reference Image)
// =========================================================================================

@Composable
fun SettingsMenuScreen(
    onBack: () -> Unit,
    onOpenBrainAi: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenSystem: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Bar Header: Back arrow + Seema AI Robot Avatar + Settings Cog
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00E5FF)
                    )
                }
                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF00E5FF).copy(alpha = 0.35f), Color(0xFF002244))
                            )
                        )
                        .border(1.4.dp, Color(0xFF00E5FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        "Seema AI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        "Smart • Personal • Always with you",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                            fontSize = 14.sp
                        )
                    )
                }
            }

            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF00E5FF)
                )
            }
        }

        // Title Block
        Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 24.sp,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Customize your AI Assistant",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 15.5.sp
                )
            )
        }

        // 2. The six categories in one glass panel (reference look); each row opens the same page as before.
        SciFiSettingsPanel {
            SciFiSettingsRow(title = "Brain & AI", subtitle = "Providers, Models, API Keys", icon = Icons.Filled.Psychology, iconTint = Color(0xFF00E5FF), onClick = onOpenBrainAi)
            SciFiSettingsRow(title = "Voice & Speech", subtitle = "Engines, Settings, Test", icon = Icons.Filled.GraphicEq, iconTint = Color(0xFF40C4FF), onClick = onOpenVoice)
            SciFiSettingsRow(title = "Appearance & Performance", subtitle = "Theme, Animations, UI Style", icon = Icons.Filled.Palette, iconTint = Color(0xFFFFB300), onClick = onOpenAppearance)
            SciFiSettingsRow(title = "Permissions & Privacy", subtitle = "Permissions, Security, Data", icon = Icons.Filled.Lock, iconTint = Color(0xFF00E676), onClick = onOpenPrivacy)
            SciFiSettingsRow(title = "Automation", subtitle = "Routines, Triggers, Tasks", icon = Icons.Filled.AutoAwesome, iconTint = Color(0xFF00D0FF), onClick = onOpenAutomation)
            SciFiSettingsRow(title = "System Integration", subtitle = "Apps, Shortcuts, Windows", icon = Icons.Filled.Extension, iconTint = Color(0xFFB388FF), onClick = onOpenSystem, showDivider = false)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// =========================================================================================
// SCREEN 2: BRAIN & AI GRID SCREEN (Matching Panel 2 in User Reference Image)
// =========================================================================================

@Composable
fun BrainAiGridScreen(
    s: UserSettings,
    secrets: Map<SecretId, SecretStatus>,
    onBack: () -> Unit,
    onSelectProvider: (ProviderId) -> Unit,
    onSelectTavily: () -> Unit,
    personalityPanel: @Composable () -> Unit = {},
    moreKeys: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00E5FF)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        "Brain & AI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        "Choose your AI Provider",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x2B00E5FF))
                    .border(1.2.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Memory,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Active Primary AI Status Glow Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0A2244), Color(0xFF051329))
                    )
                )
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "★ CURRENT PRIMARY AI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        s.defaultProvider.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        "सीमा सोचने व उत्तर देने के लिए इसी AI का उपयोग कर रही है।",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.5.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2400E676))
                        .border(1.dp, SuccessGreen, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "ACTIVE",
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Text(
            "उपलब्ध AI इंजन (Tap to Configure):",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                fontSize = 17.sp
            )
        )

        // 2-Column Grid of Providers (Gemini, Groq, Cerebras, Tavily)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderGridCard(
                    title = "Google Gemini",
                    isConnected = secrets[SecretId.GEMINI_API_KEY]?.configured == true,
                    onClick = { onSelectProvider(ProviderId.GEMINI) },
                    modifier = Modifier.weight(1f)
                ) {
                    GeminiLogo(size = 46.dp)
                }

                ProviderGridCard(
                    title = "Groq (Llama 3.3)",
                    isConnected = secrets[SecretId.GROQ_API_KEY]?.configured == true,
                    onClick = { onSelectProvider(ProviderId.GROQ) },
                    modifier = Modifier.weight(1f)
                ) {
                    GroqLogo(size = 46.dp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderGridCard(
                    title = "Cerebras Fast",
                    isConnected = secrets[SecretId.CEREBRAS_API_KEY]?.configured == true,
                    onClick = { onSelectProvider(ProviderId.CEREBRAS) },
                    modifier = Modifier.weight(1f)
                ) {
                    CerebrasLogo(size = 46.dp)
                }

                ProviderGridCard(
                    title = "Tavily Search",
                    isConnected = secrets[SecretId.TAVILY_API_KEY]?.configured == true,
                    onClick = onSelectTavily,
                    modifier = Modifier.weight(1f)
                ) {
                    TavilyLogo(size = 46.dp)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF06142A))
                .border(1.2.dp, Color(0xFF14305A), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x2B00E5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Multiple AI providers for the best experience. Groq & Cerebras provide instant responses (<500ms), while Google Gemini provides extensive reasoning and knowledge.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp
                    )
                )
            }
        }

        moreKeys()

        personalityPanel()

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ProviderGridCard(
    title: String,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    logo: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C1F42), Color(0xFF051229))
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.7f),
                        Color(0xFF0066CC).copy(alpha = 0.4f),
                        Color(0xFF00E5FF).copy(alpha = 0.7f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                logo()
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF).copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp
                    ),
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) SuccessGreen else Color.Gray)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (isConnected) "Connected" else "Key Needed",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isConnected) SuccessGreen else Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}

// =========================================================================================
// SCREEN 3: AI PROVIDER DETAIL SCREEN (Matching Panel 3 in User Reference Image)
// =========================================================================================

@Composable
fun AiProviderDetailScreen(
    provider: ProviderId,
    s: UserSettings,
    secretStatus: SecretStatus,
    models: List<AiModel>,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isPrimary = s.defaultProvider == provider
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00E5FF)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "${provider.displayName} Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 19.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x2B00E5FF))
                    .border(1.2.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Hero Cosmic Swirling Portal Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0E2855), Color(0xFF061530), Color(0xFF020917))
                    )
                )
                .border(
                    1.6.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0xFF00E5FF), Color(0xFF9B51E0), Color(0xFF00E5FF))
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(vertical = 20.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0x1F00E5FF))
                        .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (provider) {
                        ProviderId.GEMINI -> GeminiLogo(size = 54.dp)
                        ProviderId.GROQ -> GroqLogo(size = 54.dp)
                        ProviderId.CEREBRAS -> CerebrasLogo(size = 54.dp)
                        else -> Icon(Icons.Filled.Psychology, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(44.dp))
                    }
                }

                Text(
                    provider.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                )

                Text(
                    "Powerful AI for your ideas",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                )

                if (isPrimary) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x2600E676))
                            .border(1.2.dp, SuccessGreen, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "★ ACTIVE PRIMARY AI (सक्रिय मुख्य AI)",
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.setDefaultProvider(provider) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("⭐ इसे सीमा का मुख्य AI बनाएं (Set Primary)", fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // API Key Card Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "API Key",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    if (secretStatus.configured) {
                        Text(
                            secretStatus.maskedValue,
                            color = Color(0xFF00E5FF),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (secretStatus.configured) "Replace key" else "Paste key") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF)
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.testSecret(provider.secret) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        enabled = secretStatus.configured && !secretStatus.busy,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(if (secretStatus.busy) "Testing…" else "Test Connection", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveSecret(provider.secret, keyInput)
                            keyInput = ""
                            keyVisible = false
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        enabled = keyInput.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Save", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (secretStatus.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                if (secretStatus.configured) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.secret.helpUrl))) }
                        }) {
                            Text("Get API Key", fontSize = 14.5.sp, color = Color(0xFF00E5FF))
                        }
                        TextButton(onClick = { confirmRemove = true }) {
                            Text("Remove", fontSize = 14.5.sp, color = ErrorRed)
                        }
                    }
                }
            }
        }

        // Model Selection Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Model Selection",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    )
                }

                val currentModel = s.models[provider] ?: "(Latest & Recommended)"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF051022))
                        .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(10.dp))
                        .clickable { modelMenuOpen = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currentModel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF00E5FF))
                    }

                    DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                        if (models.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Tap Refresh Models to load") },
                                onClick = { modelMenuOpen = false }
                            )
                        } else {
                            models.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.displayName.ifBlank { m.id }) },
                                    onClick = {
                                        viewModel.selectModel(provider, m.id)
                                        modelMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.loadModels(provider) },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Text("Refresh Models", color = Color(0xFF00E5FF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Connection Status Box (Matching Reference Image Panel 3)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (secretStatus.configured && !secretStatus.isError) Color(0x1F00E676)
                    else Color(0x1FFFB300)
                )
                .border(
                    1.4.dp,
                    if (secretStatus.configured && !secretStatus.isError) SuccessGreen
                    else WarningAmber,
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (secretStatus.configured && !secretStatus.isError) SuccessGreen else WarningAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (secretStatus.configured && !secretStatus.isError) Icons.Filled.Check else Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        "Connection Status",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.5.sp
                        )
                    )
                    Text(
                        if (secretStatus.configured && !secretStatus.isError) "Connected Successfully"
                        else if (secretStatus.isError) secretStatus.message ?: "Connection Failed"
                        else "API Key Required",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (secretStatus.configured && !secretStatus.isError) SuccessGreen else WarningAmber,
                            fontSize = 17.sp
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove Key?") },
            text = { Text("Are you sure you want to remove the API key for ${provider.displayName}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSecret(provider.secret)
                    confirmRemove = false
                }) { Text("Remove", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            }
        )
    }
}

// =========================================================================================
// SCREEN: TAVILY DETAIL SCREEN
// =========================================================================================

@Composable
fun TavilyDetailScreen(
    secretStatus: SecretStatus,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    /** More data services shown under web search (the PNR status service). */
    extra: @Composable () -> Unit = {},
) {
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF00E5FF))
            }
            Spacer(Modifier.width(6.dp))
            Text("Tavily Web Search Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 19.sp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0E2855), Color(0xFF061530))))
                .border(1.6.dp, Color(0xFF00E5FF), RoundedCornerShape(22.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TavilyLogo(size = 64.dp)
                Text("Tavily AI Web Search", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                Text("Real-time web search for up-to-date answers", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tavily API Key", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste Tavily Key") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.testSecret(SecretId.TAVILY_API_KEY) },
                        modifier = Modifier.weight(1f),
                        enabled = secretStatus.configured && !secretStatus.busy
                    ) { Text("Test Connection") }
                    Button(
                        onClick = {
                            viewModel.saveSecret(SecretId.TAVILY_API_KEY, keyInput)
                            keyInput = ""
                        },
                        modifier = Modifier.weight(1f),
                        enabled = keyInput.isNotBlank()
                    ) { Text("Save") }
                }
                // What really happened: saved (masked key), testing, connected or the error.
                Text(
                    if (secretStatus.configured) "✓ Key saved ${secretStatus.maskedValue}" else "Key abhi save nahi hai",
                    color = if (secretStatus.configured) Color(0xFF69F0AE) else Color(0xFFFFC857),
                    fontSize = 15.5.sp,
                )
                secretStatus.message?.let {
                    Text(it, color = if (secretStatus.isError) Color(0xFFFF6B8A) else Color.White.copy(alpha = 0.8f), fontSize = 15.5.sp)
                }
            }
        }
        extra()
    }
}

// =========================================================================================
// SCREEN 4: VOICE & SPEECH SCREEN (Matching Panel 4 in User Reference Image)
// =========================================================================================

@Composable
fun VoiceSpeechScreen(
    s: UserSettings,
    secrets: Map<SecretId, SecretStatus>,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenFineTune: () -> Unit,
    onOpenVoiceCatalog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00E5FF)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        "Voice & Speech",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        "Choose your Voice Engine",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x2B00E5FF))
                    .border(1.2.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Hero Glowing Microphone Banner with Sound Waves (Matching Panel 4)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0E2855), Color(0xFF06142E), Color(0xFF020714))
                    )
                )
                .border(
                    1.6.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0xFF00E5FF), Color(0xFF40C4FF), Color(0xFF00E5FF))
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(vertical = 20.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF00E5FF).copy(alpha = 0.35f), Color(0xFF001F3F))
                            )
                        )
                        .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    "Real-time Neural Speech Engine",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                )

                Text(
                    "सीमा की बोलने वाली आवाज़ चुनें (Tap on an engine to activate):",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 15.sp
                    )
                )
            }
        }

        // Engine Cards List Matching Reference Image Panel 4
        val isCartesiaActive = s.ttsEngine == TtsEngine.CARTESIA && !s.voiceEngineAuto
        VoiceEngineCard(
            engineName = "Voice Engine One: Cartesia Sonic",
            subtitle = "High Quality • Moods & Companion (Meera / Ananya)",
            isActive = isCartesiaActive,
            iconTint = Color(0xFF00E5FF),
            onClick = { viewModel.setActiveVoiceEngine(TtsEngine.CARTESIA) }
        )

        val isSarvamActive = s.ttsEngine == TtsEngine.SARVAM && !s.voiceEngineAuto
        VoiceEngineCard(
            engineName = "Voice Engine Two: Sarvam AI Bulbul v4",
            subtitle = "Indian Hindi • Companion (Roopa / Suman)",
            isActive = isSarvamActive,
            iconTint = Color(0xFFFF4081),
            onClick = { viewModel.setActiveVoiceEngine(TtsEngine.SARVAM) }
        )

        val isAndroidActive = s.ttsEngine == TtsEngine.ANDROID && !s.voiceEngineAuto
        VoiceEngineCard(
            engineName = "Voice Engine Three: Android TTS",
            subtitle = "Offline System Speech • Always Available",
            isActive = isAndroidActive,
            iconTint = Color(0xFF40C4FF),
            onClick = { viewModel.setActiveVoiceEngine(TtsEngine.ANDROID) }
        )

        // Active Engine API Key & Live Connection Test Card
        if (isSarvamActive) {
            VoiceEngineSecretCard(
                secretId = SecretId.SARVAM_API_KEY,
                title = "Sarvam AI Voice API Key (कुंजी)",
                secretStatus = secrets[SecretId.SARVAM_API_KEY] ?: SecretStatus(),
                viewModel = viewModel
            )
        } else if (isCartesiaActive) {
            VoiceEngineSecretCard(
                secretId = SecretId.CARTESIA_API_KEY,
                title = "Cartesia Sonic API Key (कुंजी)",
                secretStatus = secrets[SecretId.CARTESIA_API_KEY] ?: SecretStatus(),
                viewModel = viewModel
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOpenFineTune,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("⚙️ Voice Settings & Fine-Tune ➔", fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                }
            }

            OutlinedButton(
                onClick = onOpenVoiceCatalog,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                    Text("🎙️ आवाज़ चुनें (Meera, Roopa, Suman) ➔", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // Below your voice choices (which stay where they were): listening / wake word and offline voices.
        // Check-up first: what is missing (permissions, Accessibility, battery, keys) with buttons to fix it.
        DiagnosePanel(secrets, wakeWord = s.wakeWord)
        ListeningPanel(s, viewModel)
        // Deepgram: the key box (save / test / remove) and the choice of listening engine.
        VoiceEngineSecretCard(
            secretId = SecretId.DEEPGRAM_API_KEY,
            title = "Deepgram API Key (सुनने के लिए / listening)",
            secretStatus = secrets[SecretId.DEEPGRAM_API_KEY] ?: SecretStatus(),
            viewModel = viewModel
        )
        com.raju.aiassistant.ui.components.HudPanel(title = "Speech input (listening engine)") {
            SpeechInputSettings(s, viewModel, secrets[SecretId.DEEPGRAM_API_KEY]?.configured == true)
        }
        VoiceEngineManager(s, viewModel)

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun VoiceEngineCard(
    engineName: String,
    subtitle: String,
    isActive: Boolean,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isActive) Color(0xFF0E2855) else Color(0xFF081730),
                        Color(0xFF051022)
                    )
                )
            )
            .border(
                1.5.dp,
                if (isActive) Color(0xFF00E5FF) else Color(0xFF16345C),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.2f))
                        .border(1.2.dp, iconTint.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        engineName,
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
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.5.sp
                        )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x2400E676))
                            .border(1.dp, SuccessGreen, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("Active", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                RadioButton(
                    selected = isActive,
                    onClick = onClick
                )
            }
        }
    }
}

// =========================================================================================
// SCREEN 5: VOICE SETTINGS FINE-TUNE (Matching Panel 5 in User Reference Image)
// =========================================================================================

@Composable
fun VoiceFineTuneScreen(
    s: UserSettings,
    viewModel: SettingsViewModel,
    voiceTest: String?,
    previewStatus: String?,
    onBack: () -> Unit,
) {
    val presets = listOf("Normal", "Pyaar bhari", "Khush", "Calm")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00E5FF)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        "Voice Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        "Fine-tune your voice",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x2B00E5FF))
                    .border(1.2.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Voice Presets Row (Matching Reference Image Panel 5)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Voice Preset (अंदाज़ / मूड):",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    fontSize = 17.sp
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = when (preset) {
                        "Normal" -> VoiceCatalog.cartesiaEmotion(s.cartesiaMood) == null
                        "Pyaar bhari" -> VoiceCatalog.cartesiaEmotion(s.cartesiaMood) == "affectionate"
                        "Khush" -> VoiceCatalog.cartesiaEmotion(s.cartesiaMood) == "content"
                        "Calm" -> VoiceCatalog.cartesiaEmotion(s.cartesiaMood) == "calm"
                        else -> false
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Color(0xFF00E5FF) else Color(0xFF081A38)
                            )
                            .border(
                                1.2.dp,
                                if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E3A5F),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                when (preset) {
                                    "Normal" -> viewModel.setCartesiaMood("")
                                    "Pyaar bhari" -> viewModel.setCartesiaMood("affectionate")
                                    "Khush" -> viewModel.setCartesiaMood("content")
                                    "Calm" -> viewModel.setCartesiaMood("calm")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            preset,
                            color = if (isSelected) Color(0xFF030712) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Sliders Section (Speech Speed & Pitch)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Speech Speed Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                            Text("Speech Speed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                        }
                        Text(
                            "${String.format("%.1f", s.speechRate)}x",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        )
                    }
                    Slider(
                        value = s.speechRate,
                        onValueChange = viewModel::setSpeechRate,
                        valueRange = 0.5f..2.0f
                    )
                }

                // Pitch Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                            Text("Pitch (टोन)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                        }
                        Text(
                            "${String.format("%.1f", s.speechPitch)}x",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        )
                    }
                    Slider(
                        value = s.speechPitch,
                        onValueChange = viewModel::setSpeechPitch,
                        valueRange = 0.5f..1.5f
                    )
                }
            }
        }

        // Toggles Section (Direct Read, Auto Detect Language, Auto Speak)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Direct Message Reading", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                        Text("मैसेज आने पर सीधा पढ़ना (बिना अनुमति पॉप-अप)", color = Color.White.copy(alpha = 0.65f), fontSize = 14.5.sp)
                    }
                    Switch(
                        checked = !s.askBeforeReading,
                        onCheckedChange = { viewModel.setAskBeforeReading(!it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Speak Replies", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                        Text("AI उत्तर स्वतः बोलकर सुनाए", color = Color.White.copy(alpha = 0.65f), fontSize = 14.5.sp)
                    }
                    Switch(
                        checked = s.autoSpeakReplies,
                        onCheckedChange = viewModel::setAutoSpeak
                    )
                }
            }
        }

        // Test Voice & Stop Buttons (Matching Reference Image Panel 5)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = viewModel::testVoice,
                modifier = Modifier.weight(1.5f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Test Voice", fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                }
            }

            OutlinedButton(
                onClick = viewModel::stopVoice,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    Text("Stop", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                }
            }
        }

        (voiceTest ?: previewStatus)?.let {
            Text("🔊 $it", color = Color(0xFF00E5FF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// =========================================================================================
// SCREEN: VOICE CATALOG SCREEN (Favorite Companion Voices: Meera, Roopa, Suman, Ananya)
// =========================================================================================

@Composable
fun VoiceCatalogScreen(
    s: UserSettings,
    secrets: Map<SecretId, SecretStatus>,
    viewModel: SettingsViewModel,
    cartesiaVoices: List<CloudVoice>,
    previewStatus: String?,
    onBack: () -> Unit,
) {
    val mergedCartesia = remember(cartesiaVoices) { VoiceCatalog.mergeCartesiaVoices(cartesiaVoices) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF00E5FF))
            }
            Spacer(Modifier.width(6.dp))
            Text("पसंदीदा आवाज़ों की सूची (Voices)", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 19.sp))
        }

        Text("🌟 Cartesia Sonic Voices:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontSize = 17.sp))

        VoiceEngineSecretCard(
            secretId = SecretId.CARTESIA_API_KEY,
            title = "Cartesia API Key (कुंजी)",
            secretStatus = secrets[SecretId.CARTESIA_API_KEY] ?: SecretStatus(),
            viewModel = viewModel
        )

        mergedCartesia.filter { it.name in listOf("Meera", "Ananya") || it.hindi }.forEach { voice ->
            val isSelected = s.cartesiaVoiceId == voice.id || s.cartesiaVoiceName == voice.name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color(0xFF0C244C) else Color(0xFF07142A))
                    .border(1.3.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF16345C), RoundedCornerShape(14.dp))
                    .clickable { viewModel.setCartesiaVoice(voice) }
                    .padding(14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("★ " + voice.name + (if (voice.name.contains("Meera")) " (Bright Companion - पसंदीदा)" else " (Paced Helper)"), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                        Text(voice.detail, style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 14.5.sp))
                    }
                    RadioButton(selected = isSelected, onClick = { viewModel.setCartesiaVoice(voice) })
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text("🌸 Sarvam AI Indian Voices (Bulbul v3 / v4):", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFFF4081), fontSize = 17.sp))

        VoiceEngineSecretCard(
            secretId = SecretId.SARVAM_API_KEY,
            title = "Sarvam AI Voice API Key (कुंजी)",
            secretStatus = secrets[SecretId.SARVAM_API_KEY] ?: SecretStatus(),
            viewModel = viewModel
        )

        VoiceCatalog.sarvamSpeakers.forEach { speaker ->
            val isSelected = s.sarvamSpeaker.equals(speaker.id, ignoreCase = true)
            val isFavorite = speaker.id in listOf("roopa", "suman")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color(0xFF2B0A20) else Color(0xFF07142A))
                    .border(1.3.dp, if (isSelected) Color(0xFFFF4081) else Color(0xFF16345C), RoundedCornerShape(14.dp))
                    .clickable { viewModel.setSarvamSpeaker(speaker.id) }
                    .padding(14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text((if (isFavorite) "★ " else "") + speaker.name + (if (isFavorite) " (राजू जी की पसंद)" else ""), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                        Text(speaker.note, style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 14.5.sp))
                    }
                    RadioButton(selected = isSelected, onClick = { viewModel.setSarvamSpeaker(speaker.id) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// =========================================================================================
// =========================================================================================
// VOICE ENGINE SECRET & LIVE TEST CARD
// =========================================================================================

@Composable
fun VoiceEngineSecretCard(
    secretId: SecretId,
    title: String,
    secretStatus: SecretStatus,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF081730))
            .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VpnKey, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                )
                Spacer(Modifier.weight(1f))
                if (secretStatus.configured) {
                    Text(
                        secretStatus.maskedValue,
                        color = Color(0xFF00E5FF),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (secretStatus.configured) "Replace key" else "Paste key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.testSecret(secretId) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    enabled = secretStatus.configured && !secretStatus.busy,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(if (secretStatus.busy) "Testing…" else "Test Connection", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        viewModel.saveSecret(secretId, keyInput)
                        keyInput = ""
                        keyVisible = false
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    enabled = keyInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Save", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (secretStatus.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (secretStatus.configured) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        // Each key's own site (the Deepgram card used to point to Cartesia).
                        if (secretId.helpUrl.startsWith("https://")) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(secretId.helpUrl))) }
                        }
                    }) {
                        Text(
                            if (secretId.helpUrl.startsWith("https://")) "Get key: " + secretId.helpUrl.removePrefix("https://").substringBefore('/') else "",
                            color = Color(0xFF00E5FF),
                            fontSize = 14.5.sp
                        )
                    }

                    TextButton(onClick = { confirmRemove = true }) {
                        Text("Remove key", color = ErrorRed, fontSize = 14.5.sp)
                    }
                }
            }

            // Status feedback banner
            if (secretStatus.message != null) {
                val isSuccess = !secretStatus.isError
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSuccess) Color(0x1F00E676) else Color(0x26FF5252))
                        .border(1.2.dp, if (isSuccess) SuccessGreen else ErrorRed, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSuccess) Color(0x3300E676) else Color(0x33FF5252)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                contentDescription = null,
                                tint = if (isSuccess) SuccessGreen else ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isSuccess) "Connection Status" else "Error",
                                fontWeight = FontWeight.Bold,
                                color = if (isSuccess) SuccessGreen else ErrorRed,
                                fontSize = 15.5.sp
                            )
                            Text(
                                secretStatus.message,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.5.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove Key?") },
            text = { Text("Are you sure you want to remove this API key?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSecret(secretId)
                    confirmRemove = false
                }) {
                    Text("Remove", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// SCREEN 6: APPEARANCE & PERFORMANCE SCREEN (Matching Panel 6 in User Reference Image)
// =========================================================================================

@Composable
fun AppearanceScreen(
    s: UserSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF00E5FF))
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Text("Appearance & Performance", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 19.sp))
                Text("Personalize your experience", color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
            }
        }

        // Phone Illustration Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0E2855), Color(0xFF06142E))))
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(46.dp))
                Text("UI Style & Core Aesthetics", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                Text("Futuristic Glassy Cyan & Deep Navy Glow", color = Color(0xFF00E5FF), fontSize = 15.5.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Smooth Animations", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                        Text("तरल और आकर्षक विज़ुअल्स", color = Color.White.copy(alpha = 0.65f), fontSize = 14.5.sp)
                    }
                    Switch(checked = !s.reducedMotion, onCheckedChange = { viewModel.setReducedMotion(!it) })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery Saver Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                        Text("बैटरी बचाने के लिए ऑप्टिमाइज़ेशन", color = Color.White.copy(alpha = 0.65f), fontSize = 14.5.sp)
                    }
                    Switch(checked = s.lowPowerMode, onCheckedChange = viewModel::setLowPowerMode)
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Apply Changes", fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// =========================================================================================
// SCREEN 7, 8, 9: PRIVACY, AUTOMATION, SYSTEM
// =========================================================================================

@Composable
fun PrivacyScreen(
    s: UserSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var confirmClearChat by remember { mutableStateOf(false) }
    var confirmClearMem by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF00E5FF))
            }
            Spacer(Modifier.width(6.dp))
            Text("Permissions & Privacy", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 19.sp))
        }

        // App lock and a plain statement of what leaves the phone.
        com.raju.aiassistant.ui.components.HudPanel(title = "आवाज़ से फ़ोन लॉक") {
            var adminOn by remember { mutableStateOf(com.raju.aiassistant.notifications.DeviceLock.isActive(context)) }
            androidx.compose.runtime.LaunchedEffect(Unit) { adminOn = com.raju.aiassistant.notifications.DeviceLock.isActive(context) }
            ToggleRow("\"screen lock karo\" से फ़ोन लॉक (बैकग्राउंड में भी)", adminOn) { want ->
                if (want) runCatching { context.startActivity(com.raju.aiassistant.notifications.DeviceLock.requestIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                else runCatching {
                    (context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager)
                        ?.removeActiveAdmin(android.content.ComponentName(context, com.raju.aiassistant.notifications.SeemaDeviceAdmin::class.java))
                    adminOn = false
                }
            }
            Text(
                "चालू होने पर Seema आपके कहने पर फ़ोन लॉक कर देगी, भले दूसरी app खुली हो। फ़ोन खोलने के लिए आपका अपना fingerprint / PIN / pattern ही चलेगा — Seema उसे कभी सेव या दर्ज नहीं करती, और खोल नहीं सकती।",
                style = MaterialTheme.typography.bodySmall, color = com.raju.aiassistant.ui.theme.TextSecondary,
            )
        }
        com.raju.aiassistant.ui.components.HudPanel(title = "App lock") {
            val secure = remember { (context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isDeviceSecure == true }
            ToggleRow("Seema खोलने पर फ़ोन का lock माँगे", s.appLock && secure) { if (secure) viewModel.setAppLock(it) }
            Text(
                if (secure) "चालू होने पर chat, खाता, memory और settings फ़ोन के fingerprint / PIN / pattern के बाद ही खुलते हैं। बोलकर काम करवाना चलता रहता है।"
                else "पहले फ़ोन में screen lock (PIN, pattern या fingerprint) लगाइए, तभी यह चालू होगा।",
                style = MaterialTheme.typography.bodySmall, color = com.raju.aiassistant.ui.theme.TextSecondary,
            )
        }
        com.raju.aiassistant.ui.components.HudPanel(title = "फ़ोन से बाहर क्या जाता है") {
            listOf(
                "आपका सवाल और जवाब के लिए ज़रूरी बातें: चुने हुए AI provider को।",
                "आवाज़: Deepgram को (अगर उसकी key लगी है), वरना फ़ोन के recogniser को।",
                "Photo: सिर्फ़ वही जो आप Vision में चुनें या कैमरे से खिंचवाएँ।",
                "Screen की तस्वीर: हर बार पूछकर, आपके हाँ कहने पर ही।",
                "कभी नहीं जाते: contacts, call log, खाता, reminders, OTP और API keys।",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Color.White) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("System Permissions Status", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Notification Access", color = Color.White, fontSize = 16.sp)
                    val hasNotif = viewModel.notificationAccessEnabled()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hasNotif) Color(0x2400E676) else Color(0x24FFB300))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(if (hasNotif) "Granted" else "Needed", color = if (hasNotif) SuccessGreen else WarningAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open Notification Settings", color = Color(0xFF00E5FF)) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chat & Memory Data", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Button(
                    onClick = { confirmClearChat = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Clear Chat History", color = ErrorRed) }
                Button(
                    onClick = { confirmClearMem = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Clear All Memories", color = ErrorRed) }
            }
        }
    }

    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text("Clear Chat History?") },
            text = { Text("This permanently deletes all saved messages.") },
            confirmButton = { TextButton(onClick = { viewModel.clearChatHistory(); confirmClearChat = false }) { Text("Clear", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { confirmClearChat = false }) { Text("Cancel") } }
        )
    }

    if (confirmClearMem) {
        AlertDialog(
            onDismissRequest = { confirmClearMem = false },
            title = { Text("Clear Memories?") },
            text = { Text("This removes everything Seema remembers about you.") },
            confirmButton = { TextButton(onClick = { viewModel.clearMemories(); confirmClearMem = false }) { Text("Clear", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { confirmClearMem = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AutomationScreen(
    s: UserSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF00E5FF))
            }
            Spacer(Modifier.width(6.dp))
            Text("Automation & Routines", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 19.sp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF081730))
                .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Announce WhatsApp & Messages", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                        Text("मैसेज आने पर बोलकर सुनाए", color = Color.White.copy(alpha = 0.65f), fontSize = 14.5.sp)
                    }
                    Switch(checked = s.announceNotifications, onCheckedChange = viewModel::setAnnounce)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Announce Calls", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                        Text("फ़ोन आने पर कॉलर का नाम बोले", color = Color.White.copy(alpha = 0.65f), fontSize = 14.5.sp)
                    }
                    Switch(checked = s.announceCalls, onCheckedChange = viewModel::setAnnounceCalls)
                }
            }
        }
    }
}

@Composable
fun SystemScreen(
    s: UserSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    secrets: Map<SecretId, SecretStatus> = emptyMap(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF00E5FF))
            }
            Spacer(Modifier.width(6.dp))
            Text("System Integration", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 19.sp))
        }

        // The real check-up (was a fixed "100% Online" card that checked nothing): every permission,
        // role, service and key Seema needs, with a button to fix each, plus mic and voice tests.
        DiagnosePanel(secrets, wakeWord = s.wakeWord)
    }
}
