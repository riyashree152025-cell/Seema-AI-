package com.raju.aiassistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.BuildConfig
import androidx.compose.material.icons.filled.Home
import com.raju.aiassistant.ui.components.SciFiPill
import com.raju.aiassistant.ui.components.SciFiListCard
import com.raju.aiassistant.ui.components.SciFiCyanGlow
import com.raju.aiassistant.domain.ai.ModelAdvice
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.ai.providerStatus
import com.raju.aiassistant.domain.language.LANGUAGE_CHOICES
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.VoiceCatalog
import com.raju.aiassistant.domain.voice.CloudVoice
import com.raju.aiassistant.domain.voice.VoiceModelOption
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.components.StatusChip
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.SuccessGreen
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen 7: Settings Screen
 * 1:1 match to Official Reference Image Screen 7:
 * - Split Layout:
 *   - Left: 6 Enclosed Category Cards (AI Assistant, Voice & Language, Privacy & Security, Notifications, Appearance, About)
 *   - Right: 3D Animated Metallic Mechanical Gear System (12-Teeth Gear + Counter-rotating Cog + Blue Glowing Power Core)
 * - Deep Category Screens with full, working API key configuration, model selection, live test connection, and voice controls.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = RajuViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val voiceTest by viewModel.voiceTest.collectAsStateWithLifecycle()
    val elevenModels by viewModel.elevenModels.collectAsStateWithLifecycle()
    val elevenVoices by viewModel.elevenVoices.collectAsStateWithLifecycle()
    val cartesiaVoices by viewModel.cartesiaVoices.collectAsStateWithLifecycle()
    val previewStatus by viewModel.previewStatus.collectAsStateWithLifecycle()
    val s = state.settings
    val context = LocalContext.current

    var activeDialog by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    val filterTabs = listOf("All", "AI", "Voice", "System", "Security")

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF030712), Color(0xFF040A18), Color(0xFF02040A))))
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Header: < Settings [Home Icon]
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
                Spacer(Modifier.width(6.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 22.sp,
                        letterSpacing = 0.8.sp,
                    ),
                )
            }

            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Home, contentDescription = "Home", tint = SciFiCyanGlow)
            }
        }

        // 2. Filter Pills (All, AI, Voice, System, Security)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterTabs.forEach { tab ->
                SciFiPill(
                    label = tab,
                    selected = selectedFilter == tab,
                    onClick = { selectedFilter = tab }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 3. Exact 1:1 Sci-Fi List Cards Matching User Reference Image 1
        if (selectedFilter in listOf("All", "AI")) {
            SciFiListCard(
                title = "AI & Brain",
                subtitle = "Providers, Models, API Keys",
                icon = Icons.Filled.Psychology,
                iconTint = SciFiCyanGlow,
                emblemBorder = SciFiCyanGlow,
                onClick = { activeDialog = "ai" }
            )
        }

        if (selectedFilter in listOf("All", "Voice")) {
            SciFiListCard(
                title = "Voice & Speech",
                subtitle = "Engine, Speed, Recognition",
                icon = Icons.Filled.Mic,
                iconTint = Color(0xFF40C4FF),
                emblemBorder = Color(0xFF40C4FF),
                onClick = { activeDialog = "voice" }
            )
        }

        if (selectedFilter in listOf("All", "System")) {
            SciFiListCard(
                title = "Appearance & Performance",
                subtitle = "Theme, Animations, Performance",
                icon = Icons.Filled.Palette,
                iconTint = Color(0xFFFFB800),
                emblemBorder = Color(0xFFFFB800),
                onClick = { activeDialog = "appearance" }
            )
        }

        if (selectedFilter in listOf("All", "Security")) {
            SciFiListCard(
                title = "Permissions & Status",
                subtitle = "Permissions, System Status",
                icon = Icons.Filled.Lock,
                iconTint = Color(0xFF00E676),
                emblemBorder = Color(0xFF00E676),
                onClick = { activeDialog = "privacy" }
            )
        }

        if (selectedFilter in listOf("All", "System")) {
            SciFiListCard(
                title = "Automation",
                subtitle = "Routines, Triggers, Tasks",
                icon = Icons.Filled.Notifications,
                iconTint = SciFiCyanGlow,
                emblemBorder = SciFiCyanGlow,
                onClick = { activeDialog = "notifications" }
            )
        }

        if (selectedFilter in listOf("All", "System")) {
            SciFiListCard(
                title = "System Integration",
                subtitle = "Apps, Shortcuts, Windows",
                icon = Icons.Filled.Settings,
                iconTint = Color(0xFF7C4DFF),
                emblemBorder = Color(0xFF7C4DFF),
                onClick = { activeDialog = "about" }
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    // Detail Dialogs for Categories (Preserving all actual working backend settings)
    when (activeDialog) {
        "ai" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                containerColor = Color(0xFF071120),
                title = { Text("AI Assistant Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Active AI Panel with live test button
                        ActiveAiPanel(
                            s = s,
                            keySaved = { p -> state.secrets[p.secret]?.configured == true },
                            testing = { p -> state.secrets[p.secret]?.busy == true },
                            viewModel = viewModel,
                        )

                        Text("Select Primary Provider", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                        ProviderId.entries.forEach { p ->
                            RadioRow(
                                label = p.shortName + if (p == s.defaultProvider) " (Active Primary)" else "",
                                selected = p == s.defaultProvider,
                                onClick = { viewModel.setDefaultProvider(p) },
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Configure API Keys & Models", style = MaterialTheme.typography.titleSmall, color = NeonCyan)

                        // Google Gemini
                        val geminiSecret = state.secrets[SecretId.GEMINI_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.GEMINI_API_KEY,
                            status = geminiSecret,
                            viewModel = viewModel,
                            title = "Google Gemini Key",
                            providerStatus = providerStatus(geminiSecret.configured, geminiSecret.busy, s.lastTests[ProviderId.GEMINI]),
                        ) {
                            ModelPicker(
                                provider = ProviderId.GEMINI,
                                selected = s.models[ProviderId.GEMINI],
                                options = (models[ProviderId.GEMINI] ?: emptyList()).map { it.id },
                                onLoad = { viewModel.loadModels(ProviderId.GEMINI) },
                                onSelect = { viewModel.selectModel(ProviderId.GEMINI, it) },
                                keySaved = geminiSecret.configured,
                                loading = geminiSecret.busy,
                            )
                        }

                        // Groq
                        val groqSecret = state.secrets[SecretId.GROQ_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.GROQ_API_KEY,
                            status = groqSecret,
                            viewModel = viewModel,
                            title = "Groq Key",
                            providerStatus = providerStatus(groqSecret.configured, groqSecret.busy, s.lastTests[ProviderId.GROQ]),
                        ) {
                            ModelPicker(
                                provider = ProviderId.GROQ,
                                selected = s.models[ProviderId.GROQ],
                                options = (models[ProviderId.GROQ] ?: emptyList()).map { it.id },
                                onLoad = { viewModel.loadModels(ProviderId.GROQ) },
                                onSelect = { viewModel.selectModel(ProviderId.GROQ, it) },
                                keySaved = groqSecret.configured,
                                loading = groqSecret.busy,
                            )
                        }

                        // Cerebras
                        val cerebrasSecret = state.secrets[SecretId.CEREBRAS_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.CEREBRAS_API_KEY,
                            status = cerebrasSecret,
                            viewModel = viewModel,
                            title = "Cerebras Key",
                            providerStatus = providerStatus(cerebrasSecret.configured, cerebrasSecret.busy, s.lastTests[ProviderId.CEREBRAS]),
                        ) {
                            ModelPicker(
                                provider = ProviderId.CEREBRAS,
                                selected = s.models[ProviderId.CEREBRAS],
                                options = (models[ProviderId.CEREBRAS] ?: emptyList()).map { it.id },
                                onLoad = { viewModel.loadModels(ProviderId.CEREBRAS) },
                                onSelect = { viewModel.selectModel(ProviderId.CEREBRAS, it) },
                                keySaved = cerebrasSecret.configured,
                                loading = cerebrasSecret.busy,
                            )
                        }

                        // OpenRouter
                        val openRouterSecret = state.secrets[SecretId.OPENROUTER_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.OPENROUTER_API_KEY,
                            status = openRouterSecret,
                            viewModel = viewModel,
                            title = "OpenRouter Key",
                            providerStatus = providerStatus(openRouterSecret.configured, openRouterSecret.busy, s.lastTests[ProviderId.OPENROUTER]),
                        ) {
                            ModelPicker(
                                provider = ProviderId.OPENROUTER,
                                selected = s.models[ProviderId.OPENROUTER],
                                options = (models[ProviderId.OPENROUTER] ?: emptyList()).map { it.id },
                                onLoad = { viewModel.loadModels(ProviderId.OPENROUTER) },
                                onSelect = { viewModel.selectModel(ProviderId.OPENROUTER, it) },
                                keySaved = openRouterSecret.configured,
                                loading = openRouterSecret.busy,
                            )
                        }

                        // DeepSeek
                        val deepseekSecret = state.secrets[SecretId.DEEPSEEK_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.DEEPSEEK_API_KEY,
                            status = deepseekSecret,
                            viewModel = viewModel,
                            title = "DeepSeek Key",
                            providerStatus = providerStatus(deepseekSecret.configured, deepseekSecret.busy, s.lastTests[ProviderId.DEEPSEEK]),
                        ) {
                            ModelPicker(
                                provider = ProviderId.DEEPSEEK,
                                selected = s.models[ProviderId.DEEPSEEK],
                                options = (models[ProviderId.DEEPSEEK] ?: emptyList()).map { it.id },
                                onLoad = { viewModel.loadModels(ProviderId.DEEPSEEK) },
                                onSelect = { viewModel.selectModel(ProviderId.DEEPSEEK, it) },
                                keySaved = deepseekSecret.configured,
                                loading = deepseekSecret.busy,
                            )
                        }

                        // Ollama (PC Local AI)
                        val ollamaSecret = state.secrets[SecretId.OLLAMA_ENDPOINT] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.OLLAMA_ENDPOINT,
                            status = ollamaSecret,
                            viewModel = viewModel,
                            title = "Ollama Host (e.g. 192.168.31.55:11434)",
                            providerStatus = providerStatus(ollamaSecret.configured, ollamaSecret.busy, s.lastTests[ProviderId.OLLAMA]),
                        ) {
                            ModelPicker(
                                provider = ProviderId.OLLAMA,
                                selected = s.models[ProviderId.OLLAMA],
                                options = (models[ProviderId.OLLAMA] ?: emptyList()).map { it.id },
                                onLoad = { viewModel.loadModels(ProviderId.OLLAMA) },
                                onSelect = { viewModel.selectModel(ProviderId.OLLAMA, it) },
                                keySaved = ollamaSecret.configured,
                                loading = ollamaSecret.busy,
                            )
                        }

                        // PC Companion & Memory Sync
                        val pcSyncSecret = state.secrets[SecretId.PC_SYNC_ENDPOINT] ?: SecretStatus()
                        val pcTokenSecret = state.secrets[SecretId.PC_SYNC_TOKEN] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.PC_SYNC_ENDPOINT,
                            status = pcSyncSecret,
                            viewModel = viewModel,
                            title = "PC Companion Host (e.g. 192.168.31.55:11435)",
                        )
                        SecretCard(
                            id = SecretId.PC_SYNC_TOKEN,
                            status = pcTokenSecret,
                            viewModel = viewModel,
                            title = "PC Companion Auth Token",
                        )

                        // Web Search (Tavily)
                        SecretCard(
                            id = SecretId.TAVILY_API_KEY,
                            status = state.secrets[SecretId.TAVILY_API_KEY] ?: SecretStatus(),
                            viewModel = viewModel,
                            title = "Web Search Key (Tavily)",
                        )

                        Spacer(Modifier.height(16.dp))
                        Text("Voice API Keys (Ultra-Realistic Speech)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)

                        // Sarvam AI (Bulbul / Priya)
                        val sarvamSecret = state.secrets[SecretId.SARVAM_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.SARVAM_API_KEY,
                            status = sarvamSecret,
                            viewModel = viewModel,
                            title = "Sarvam AI (Bulbul / Priya) Voice Key",
                        ) {
                            SarvamVoiceControls(s, viewModel, previewStatus)
                        }

                        // ElevenLabs
                        val elevenSecret = state.secrets[SecretId.ELEVENLABS_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.ELEVENLABS_API_KEY,
                            status = elevenSecret,
                            viewModel = viewModel,
                            title = "ElevenLabs Voice Key",
                        ) {
                            ElevenLabsVoiceControls(s, viewModel, elevenModels, elevenVoices, previewStatus)
                        }

                        // Cartesia Sonic
                        val cartesiaSecret = state.secrets[SecretId.CARTESIA_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.CARTESIA_API_KEY,
                            status = cartesiaSecret,
                            viewModel = viewModel,
                            title = "Cartesia Voice Key",
                        ) {
                            CartesiaVoiceControls(s, viewModel, cartesiaVoices, previewStatus)
                        }

                        // Deepgram Aura
                        val deepgramSecret = state.secrets[SecretId.DEEPGRAM_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.DEEPGRAM_API_KEY,
                            status = deepgramSecret,
                            viewModel = viewModel,
                            title = "Deepgram Voice Key",
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Done", color = NeonCyan)
                    }
                },
            )
        }
        "voice" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                containerColor = Color(0xFF071120),
                title = { Text("Voice & Language", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Language (भाषा)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                        ChipRow(
                            options = listOf("auto", "hi-IN", "bn-IN"),
                            selected = s.speechLanguage,
                            label = { when (it) { "auto" -> "Auto (हिन्दी/বাংলা)"; "hi-IN" -> "हिन्दी (Hindi)"; else -> "বাংলা (Bengali)" } },
                            onSelect = viewModel::setSpeechLanguage,
                        )

                        Text("Primary Speaking Voice Engine (सीमा की मुख्य आवाज़)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                        Text(
                            "सक्रिय आवाज़: " + when {
                                s.voiceEngineAuto -> "ऑटोमैटिक (Kokoro/Piper Offline अगर उपलब्ध है, अन्यथा ऑनलाइन)"
                                s.ttsEngine == TtsEngine.CARTESIA -> "Cartesia Sonic (अत्यंत भावपूर्ण)"
                                s.ttsEngine == TtsEngine.SARVAM -> "Sarvam AI Bulbul (भारतीय आवाज़ - ${s.sarvamSpeaker})"
                                s.ttsEngine == TtsEngine.ELEVENLABS -> "ElevenLabs"
                                s.ttsEngine == TtsEngine.ANDROID -> "Android TTS (फ़ोन डिफ़ॉल्ट)"
                                else -> s.ttsEngine.label
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            RadioRow("Cartesia Sonic (★ मूड्स एवं भावपूर्ण आवाज़)", s.ttsEngine == TtsEngine.CARTESIA && !s.voiceEngineAuto) {
                                viewModel.setActiveVoiceEngine(TtsEngine.CARTESIA)
                            }
                            RadioRow("Sarvam AI Bulbul (★ भारतीय आवाज़ें - प्रिया, काव्या)", s.ttsEngine == TtsEngine.SARVAM && !s.voiceEngineAuto) {
                                viewModel.setActiveVoiceEngine(TtsEngine.SARVAM)
                            }
                            RadioRow("ElevenLabs (अत्यंत मधुर एवं प्राकृतिक आवाज़)", s.ttsEngine == TtsEngine.ELEVENLABS && !s.voiceEngineAuto) {
                                viewModel.setActiveVoiceEngine(TtsEngine.ELEVENLABS)
                            }
                            RadioRow("Offline Neural Speech (Kokoro / Piper)", s.voiceEngineAuto) {
                                viewModel.setVoiceEngineAuto(true)
                            }
                            RadioRow("Android TTS (फ़ोन की इनबिल्ट ऑफलाइन आवाज़)", s.ttsEngine == TtsEngine.ANDROID && !s.voiceEngineAuto) {
                                viewModel.setActiveVoiceEngine(TtsEngine.ANDROID)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Voice API Keys & Models (आवाज़ मॉडल एवं कुंजियाँ)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)

                        // Cartesia Sonic
                        val cartesiaVoiceSec = state.secrets[SecretId.CARTESIA_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.CARTESIA_API_KEY,
                            status = cartesiaVoiceSec,
                            viewModel = viewModel,
                            title = "Cartesia Voice Key",
                        ) {
                            CartesiaVoiceControls(s, viewModel, cartesiaVoices, previewStatus)
                        }

                        // Sarvam AI (Bulbul / Priya)
                        val sarvamVoiceSec = state.secrets[SecretId.SARVAM_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.SARVAM_API_KEY,
                            status = sarvamVoiceSec,
                            viewModel = viewModel,
                            title = "Sarvam AI (Bulbul / Priya) Voice Key",
                        ) {
                            SarvamVoiceControls(s, viewModel, previewStatus)
                        }

                        // ElevenLabs
                        val elevenVoiceSec = state.secrets[SecretId.ELEVENLABS_API_KEY] ?: SecretStatus()
                        SecretCard(
                            id = SecretId.ELEVENLABS_API_KEY,
                            status = elevenVoiceSec,
                            viewModel = viewModel,
                            title = "ElevenLabs Voice Key",
                        ) {
                            ElevenLabsVoiceControls(s, viewModel, elevenModels, elevenVoices, previewStatus)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Offline Voice Engine Manager", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                        VoiceEngineManager(s, viewModel)

                        Text("Speech Rate (${String.format("%.1f", s.speechRate)}x)", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.speechRate,
                            onValueChange = viewModel::setSpeechRate,
                            valueRange = 0.5f..2.0f,
                        )

                        Text("Speech Pitch (${String.format("%.1f", s.speechPitch)}x)", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.speechPitch,
                            onValueChange = viewModel::setSpeechPitch,
                            valueRange = 0.5f..1.5f,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::testVoice) {
                                Text("Test Active Voice")
                            }
                            OutlinedButton(onClick = viewModel::stopVoice) {
                                Text("Stop")
                            }
                        }
                        voiceTest?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Done", color = NeonCyan)
                    }
                },
            )
        }
        "privacy" -> {
            var confirmClearChat by remember { mutableStateOf(false) }
            var confirmClearMem by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                containerColor = Color(0xFF071120),
                title = { Text("Privacy & Security", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🛡 Android Keystore AES-GCM: Active", color = SuccessGreen, fontWeight = FontWeight.Bold)
                        Text("• API keys are encrypted hardware-level and never stored in plain text.", color = Color(0xFFC0D5F0), fontSize = 13.sp)
                        Text("• Microphone is active ONLY when you tap or enable hands-free.", color = Color(0xFFC0D5F0), fontSize = 13.sp)
                        Text("• Conversation memories are stored locally in Room SQLite database.", color = Color(0xFFC0D5F0), fontSize = 13.sp)

                        Spacer(Modifier.height(4.dp))
                        ToggleRow(
                            label = "Save Conversation Memory",
                            checked = s.memoryEnabled,
                            onChange = viewModel::setMemoryEnabled,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { confirmClearChat = true }) {
                                Text("Clear Chats", color = ErrorRed)
                            }
                            OutlinedButton(onClick = { confirmClearMem = true }) {
                                Text("Clear Memory", color = ErrorRed)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Done", color = NeonCyan)
                    }
                },
            )

            if (confirmClearChat) {
                AlertDialog(
                    onDismissRequest = { confirmClearChat = false },
                    title = { Text("Clear Chat History?") },
                    text = { Text("This will permanently delete all messages stored on this phone.") },
                    confirmButton = { TextButton(onClick = { confirmClearChat = false; viewModel.clearChatHistory() }) { Text("Clear", color = ErrorRed) } },
                    dismissButton = { TextButton(onClick = { confirmClearChat = false }) { Text("Cancel") } },
                )
            }
            if (confirmClearMem) {
                AlertDialog(
                    onDismissRequest = { confirmClearMem = false },
                    title = { Text("Clear Memories?") },
                    text = { Text("This will permanently delete all learned user facts stored on this phone.") },
                    confirmButton = { TextButton(onClick = { confirmClearMem = false; viewModel.clearMemories() }) { Text("Clear", color = ErrorRed) } },
                    dismissButton = { TextButton(onClick = { confirmClearMem = false }) { Text("Cancel") } },
                )
            }
        }
        "notifications" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                containerColor = Color(0xFF071120),
                title = { Text("Notifications", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToggleRow(
                            label = "Announce WhatsApp / SMS",
                            checked = s.announceNotifications,
                            onChange = viewModel::setAnnounce,
                        )
                        ToggleRow(
                            label = "Announce Phone Calls",
                            checked = s.announceCalls,
                            onChange = viewModel::setAnnounceCalls,
                        )
                        ToggleRow(
                            label = "Speak Caller Name / Number",
                            checked = s.speakCallerNumber,
                            onChange = viewModel::setSpeakCallerNumber,
                        )
                        ToggleRow(
                            label = "Ask Before Reading Messages",
                            checked = s.askBeforeReading,
                            onChange = viewModel::setAskBeforeReading,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Android Notification Access", color = NeonCyan)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Done", color = NeonCyan)
                    }
                },
            )
        }
        "appearance" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                containerColor = Color(0xFF071120),
                title = { Text("Appearance", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToggleRow(
                            label = "Reduced Motion (Save Battery)",
                            checked = s.reducedMotion,
                            onChange = viewModel::setReducedMotion,
                        )
                        ToggleRow(
                            label = "Auto Speak Replies",
                            checked = s.autoSpeakReplies,
                            onChange = viewModel::setAutoSpeak,
                        )
                        ToggleRow(
                            label = "Voice Barge-in (Interruption)",
                            checked = s.bargeIn,
                            onChange = viewModel::setBargeIn,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Done", color = NeonCyan)
                    }
                },
            )
        }
        "about" -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                containerColor = Color(0xFF071120),
                title = { Text("About SEEMA AI", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SEEMA AI — Advanced Agentic Companion", fontWeight = FontWeight.Bold, color = NeonCyan)
                        Text("Version: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", color = Color(0xFFC0D5F0), fontSize = 13.sp)
                        Text("User: ${s.userName} (${s.userEmail})", color = Color(0xFFC0D5F0), fontSize = 13.sp)
                        Text("Architecture: Jetpack Compose + Room + ONNX + Multi-LLM", color = Color(0xFFC0D5F0), fontSize = 13.sp)
                        Text("GitHub: riyashree152025-cell/Seema-AI-", color = Color(0xFFC0D5F0), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/riyashree152025-cell/Seema-AI-")))
                                }
                            }
                        ) {
                            Text("Open GitHub Repository", color = NeonCyan)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", color = NeonCyan)
                    }
                },
            )
        }
    }
}

@Composable
private fun SecretCard(
    id: SecretId,
    status: SecretStatus,
    viewModel: SettingsViewModel,
    title: String = id.displayName,
    providerStatus: com.raju.aiassistant.domain.ai.ProviderStatus? = null,
    extra: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    HudPanel(title = title) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                label = if (status.configured) "Key saved ${status.maskedValue}" else "No key",
                color = if (status.configured) SuccessGreen else WarningAmber,
            )
            providerStatus?.let { ProviderStatusChip(it) }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (status.configured) "Replace key" else "Paste key") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (visible) "Hide key" else "Show key")
                }
            },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.saveSecret(id, input); input = ""; visible = false }, enabled = input.isNotBlank()) {
                Text(if (status.configured) "Update" else "Save")
            }
            OutlinedButton(onClick = { viewModel.testSecret(id) }, enabled = status.configured && !status.busy) {
                Text(if (status.busy) "Testing…" else "Test connection")
            }
            OutlinedButton(onClick = { confirmRemove = true }, enabled = status.configured) {
                Text("Remove", color = if (status.configured) ErrorRed else Color.Unspecified)
            }
        }
        if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = if (status.isError) ErrorRed else TextSecondary)
        }
        providerStatus?.takeIf { it.isError && status.message == null }?.let {
            Text(it.help, style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
        }
        extra()
        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(id.helpUrl))) } }) {
            Text("Get key: ${id.helpUrl}", fontSize = 11.sp, color = NeonCyan)
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove $title?") },
            text = { Text("The saved key is deleted from this phone. Seema cannot use this service until you add a key again.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.removeSecret(id) }) { Text("Remove", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ModelPicker(
    provider: ProviderId,
    selected: String?,
    options: List<String>,
    onLoad: () -> Unit,
    onSelect: (String) -> Unit,
    keySaved: Boolean = true,
    loading: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open && options.isEmpty() && keySaved) onLoad() }
    ModelAdvice.warning(selected)?.let { Text("⚠ $it", style = MaterialTheme.typography.bodyMedium, color = WarningAmber) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Model: ${selected ?: "auto"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        TextButton(onClick = onLoad, enabled = keySaved && !loading) { Text("Refresh", color = NeonCyan) }
        Box {
            TextButton(onClick = { open = true }, enabled = keySaved) { Text("Change", color = NeonCyan) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (options.isEmpty()) {
                    DropdownMenuItem(text = { Text(if (loading) "Loading models…" else "No models loaded — tap Refresh") }, onClick = { open = false })
                }
                options.forEach { m ->
                    val tag = when {
                        ModelAdvice.isRecommended(provider, m) -> "  ★ recommended"
                        ModelAdvice.warning(m) != null -> "  ⚠ not for Hindi"
                        else -> ""
                    }
                    DropdownMenuItem(text = { Text(m + tag) }, onClick = { onSelect(m); open = false })
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xD9081426))
            .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x3300E5FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 12.5.sp,
                    ),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = NeonCyan.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MetallicGearCanvas(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "gearSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot",
    )
    val counterRot by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "counterRot",
    )

    Canvas(modifier = modifier) {
        val c = center
        val r = (size.minDimension / 2f) * 0.92f

        val teeth = 12
        val toothDepth = r * 0.12f
        val innerR = r - toothDepth
        val toothAngle = 360f / teeth

        rotate(rotation, c) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF2E3E5C), Color(0xFF131E33), Color(0xFF090E1A)),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )

            repeat(teeth) { i ->
                val a = Math.toRadians((i * toothAngle).toDouble())
                val x = c.x + (r - toothDepth * 0.5f) * cos(a).toFloat()
                val y = c.y + (r - toothDepth * 0.5f) * sin(a).toFloat()
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.85f),
                    radius = toothDepth * 0.38f,
                    center = Offset(x, y),
                )
            }

            drawCircle(
                color = NeonCyan.copy(alpha = 0.6f),
                radius = innerR,
                center = c,
                style = Stroke(width = 2.5f),
            )
        }

        rotate(counterRot, c) {
            val cogR = r * 0.62f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF0077FF), NeonCyan, Color(0xFF0077FF)),
                    center = c,
                ),
                radius = cogR,
                center = c,
                style = Stroke(width = 2.5f),
            )
            repeat(6) { i ->
                val a = Math.toRadians((i * 60f).toDouble())
                drawLine(
                    color = NeonCyan.copy(alpha = 0.7f),
                    start = c,
                    end = Offset(c.x + cogR * cos(a).toFloat(), c.y + cogR * sin(a).toFloat()),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }

        val coreR = r * 0.32f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, NeonCyan, Color(0xFF0055CC), Color.Transparent),
                center = c,
                radius = coreR * 1.4f,
            ),
            radius = coreR * 1.4f,
            center = c,
        )
        drawCircle(
            color = Color.White,
            radius = coreR * 0.45f,
            center = c,
        )
    }
}

@Composable
internal fun ChipRow(options: List<String>, selected: String, label: (String) -> String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), color = Color.White)
    }
}

@Composable
internal fun StatusRow(label: String, status: String, ok: Boolean, action: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = if (ok) SuccessGreen else WarningAmber)
        }
        action?.let { (text, onClick) -> TextButton(onClick = onClick) { Text(text, color = NeonCyan) } }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CartesiaVoiceControls(
    s: UserSettings,
    viewModel: SettingsViewModel,
    cartesiaVoices: List<CloudVoice>,
    previewStatus: String? = null,
) {
    val isActive = s.ttsEngine == TtsEngine.CARTESIA && !s.voiceEngineAuto
    var modelMenuOpen by remember { mutableStateOf(false) }
    var voiceMenuOpen by remember { mutableStateOf(false) }
    var moodMenuOpen by remember { mutableStateOf(false) }
    val mergedVoices = remember(cartesiaVoices) { VoiceCatalog.mergeCartesiaVoices(cartesiaVoices) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Active indicator / Activate button
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActive) {
                StatusChip(label = "★ ACTIVE SEEMA VOICE", color = SuccessGreen)
            } else {
                Button(
                    onClick = { viewModel.setActiveVoiceEngine(TtsEngine.CARTESIA) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("सीमा की आवाज़ बनाएं", fontSize = 12.sp, maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = { viewModel.previewEngine(TtsEngine.CARTESIA) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("▶️ टेस्ट सुनें", fontSize = 12.sp, color = NeonCyan, maxLines = 1)
            }
        }

        if (previewStatus != null) {
            Text(previewStatus, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
        }

        // 1. Model Dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            val modelTitle = VoiceCatalog.cartesiaModels.firstOrNull { it.id == s.cartesiaModel }?.title ?: s.cartesiaModel
            Text("Model: $modelTitle", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Box {
                TextButton(onClick = { modelMenuOpen = true }) { Text("Change", color = NeonCyan) }
                DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                    VoiceCatalog.cartesiaModels.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text((if (opt.recommended) "★ " else "") + opt.title, fontWeight = FontWeight.Bold)
                                    Text(opt.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            },
                            onClick = {
                                viewModel.setCartesiaModel(opt.id)
                                modelMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        // 2. Voice Dropdown (loaded voices)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val currentVoiceName = s.cartesiaVoiceName ?: mergedVoices.firstOrNull { it.id == s.cartesiaVoiceId }?.name ?: "Ananya (Hindi Female)"
            Text("Voice: $currentVoiceName", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            TextButton(onClick = { viewModel.loadCartesia() }) { Text("Refresh", color = NeonCyan) }
            Box {
                TextButton(onClick = { voiceMenuOpen = true }) { Text("Select", color = NeonCyan) }
                DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                    mergedVoices.forEach { v ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text((if (v.hindi) "🇮🇳 " else "") + v.name + (if (v.female) " (Female)" else ""), fontWeight = FontWeight.Bold)
                                    if (v.detail.isNotBlank()) Text(v.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            },
                            onClick = {
                                viewModel.setCartesiaVoice(v)
                                voiceMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        // 3. Mood / Emotion Dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            val moodLabel = VoiceCatalog.cartesiaMoods.firstOrNull { it.first == s.cartesiaMood }?.second ?: "Natural (सामान्य)"
            Text("Mood (अंदाज़): $moodLabel", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Box {
                TextButton(onClick = { moodMenuOpen = true }) { Text("Change", color = NeonCyan) }
                DropdownMenu(expanded = moodMenuOpen, onDismissRequest = { moodMenuOpen = false }) {
                    VoiceCatalog.cartesiaMoods.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = if (key == s.cartesiaMood) NeonCyan else Color.White) },
                            onClick = {
                                viewModel.setCartesiaMood(key)
                                moodMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        Text(
            "सुझाव: Sonic 3 + Ananya (Hindi) + 'Pyaar bhari' या 'Khush' मूड चुनने पर सीमा बेहद मीठी व सजीव हिन्दी बोलती है।",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SarvamVoiceControls(
    s: UserSettings,
    viewModel: SettingsViewModel,
    previewStatus: String? = null,
) {
    val isActive = s.ttsEngine == TtsEngine.SARVAM && !s.voiceEngineAuto
    var speakerMenuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Active indicator / Activate button
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActive) {
                StatusChip(label = "★ ACTIVE SEEMA VOICE", color = SuccessGreen)
            } else {
                Button(
                    onClick = { viewModel.setActiveVoiceEngine(TtsEngine.SARVAM) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("सीमा की आवाज़ बनाएं", fontSize = 12.sp, maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = { viewModel.previewEngine(TtsEngine.SARVAM) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("▶️ टेस्ट सुनें", fontSize = 12.sp, color = NeonCyan, maxLines = 1)
            }
        }

        if (previewStatus != null) {
            Text(previewStatus, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
        }

        Text("Model: Bulbul v3 (उच्च-गुणवत्ता भारतीय भाषा मॉडल)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        // Speaker Dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            val currentSpeaker = VoiceCatalog.sarvamSpeakers.firstOrNull { it.id == s.sarvamSpeaker }?.name ?: s.sarvamSpeaker
            Text("Voice: $currentSpeaker", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Box {
                TextButton(onClick = { speakerMenuOpen = true }) { Text("Change", color = NeonCyan) }
                DropdownMenu(expanded = speakerMenuOpen, onDismissRequest = { speakerMenuOpen = false }) {
                    VoiceCatalog.sarvamSpeakers.forEach { sp ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text((if (sp.recommended) "★ " else "") + sp.name, fontWeight = FontWeight.Bold)
                                    Text(sp.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            },
                            onClick = {
                                viewModel.setSarvamSpeaker(sp.id)
                                speakerMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        Text(
            "सुझाव: 'Priya' (प्रिया) सबसे लोकप्रिय व मधुर हिन्दी आवाज़ है। आप 'Kavya' या 'Ritu' भी चुन सकते हैं।",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun ElevenLabsVoiceControls(
    s: UserSettings,
    viewModel: SettingsViewModel,
    elevenModels: List<VoiceModelOption>,
    elevenVoices: List<CloudVoice>,
    previewStatus: String? = null,
) {
    val isActive = s.ttsEngine == TtsEngine.ELEVENLABS && !s.voiceEngineAuto
    var modelMenuOpen by remember { mutableStateOf(false) }
    var voiceMenuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActive) {
                StatusChip(label = "★ ACTIVE SEEMA VOICE", color = SuccessGreen)
            } else {
                Button(
                    onClick = { viewModel.setActiveVoiceEngine(TtsEngine.ELEVENLABS) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("सीमा की आवाज़ बनाएं", fontSize = 12.sp, maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = { viewModel.previewEngine(TtsEngine.ELEVENLABS) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("▶️ टेस्ट सुनें", fontSize = 12.sp, color = NeonCyan, maxLines = 1)
            }
        }

        if (previewStatus != null) {
            Text(previewStatus, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
        }

        // Model Dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            val modelTitle = elevenModels.firstOrNull { it.id == s.elevenModel }?.title ?: s.elevenModel
            Text("Model: $modelTitle", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Box {
                TextButton(onClick = { modelMenuOpen = true }) { Text("Change", color = NeonCyan) }
                DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                    elevenModels.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text((if (opt.recommended) "★ " else "") + opt.title, fontWeight = FontWeight.Bold)
                                    Text(opt.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            },
                            onClick = {
                                viewModel.setElevenModel(opt.id)
                                modelMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        // Voice Dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            val currentVoiceName = s.elevenVoiceName ?: elevenVoices.firstOrNull { it.id == s.elevenVoiceId }?.name ?: "Sarah (Default)"
            Text("Voice: $currentVoiceName", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            TextButton(onClick = { viewModel.loadElevenLabs() }) { Text("Refresh", color = NeonCyan) }
            Box {
                TextButton(onClick = { voiceMenuOpen = true }) { Text("Select", color = NeonCyan) }
                DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                    if (elevenVoices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No voices loaded — tap Refresh") },
                            onClick = { voiceMenuOpen = false },
                        )
                    } else {
                        elevenVoices.forEach { v ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text((if (v.hindi) "🇮🇳 " else "") + v.label, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    viewModel.setElevenVoice(v)
                                    voiceMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
