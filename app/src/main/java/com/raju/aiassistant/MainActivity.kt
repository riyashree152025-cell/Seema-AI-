package com.raju.aiassistant

import android.content.Intent
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.domain.model.CoreAppearance
import com.raju.aiassistant.data.tts.local.runOfflineVoiceTest
import kotlinx.coroutines.launch
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.raju.aiassistant.ui.LaunchRequest
import com.raju.aiassistant.ui.navigation.RajuNavHost
import com.raju.aiassistant.ui.theme.RajuTheme
import com.raju.aiassistant.voice.SeemaWakeService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Start continuous background microphone service
        SeemaWakeService.start(this)

        // Auto-heal provider selection to guaranteed-working Gemini if current provider is broken or unconfigured
        val appContainer = (application as RajuApp).container
        appContainer.appScope.launch {
            val configured = appContainer.secureKeyStore.configured.value
            val s = appContainer.currentSettings.value
            if (s.defaultProvider == com.raju.aiassistant.domain.ai.ProviderId.OLLAMA || s.defaultProvider.secret !in configured) {
                if (com.raju.aiassistant.domain.model.SecretId.GEMINI_API_KEY in configured) {
                    appContainer.settingsRepository.setDefaultProvider(com.raju.aiassistant.domain.ai.ProviderId.GEMINI)
                }
            }
            val geminiModel = s.modelFor(com.raju.aiassistant.domain.ai.ProviderId.GEMINI)
            if (geminiModel != null && (geminiModel.contains("omni", ignoreCase = true) || geminiModel.trim().isEmpty())) {
                appContainer.settingsRepository.setModel(com.raju.aiassistant.domain.ai.ProviderId.GEMINI, "gemini-2.0-flash")
            }
        }

        // Only a fresh launch carries a new request; after rotation the same intent must not run again.
        if (savedInstanceState == null) handle(intent)
        publishShortcuts()
        setContent {
            RajuTheme {
                RajuNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        LaunchRequest.from(intent)?.let { (application as RajuApp).container.launchRequests.value = it }
        intent?.getStringExtra("seema_cmd")?.let { cmd ->
            val c = (application as RajuApp).container
            c.appScope.launch {
                c.engine.submit(cmd, fromVoice = true)
            }
        }
        // Debug builds only: lets the emulator test choose an AI core (`--es seema_core MODERN`), like tapping Apply.
        if (BuildConfig.DEBUG) intent?.getStringExtra("seema_core")?.let { name ->
            CoreAppearance.entries.firstOrNull { it.name == name }?.let { core ->
                val c = (application as RajuApp).container
                c.appScope.launch { c.settingsRepository.setCoreAppearance(core) }
            }
        }
        // Debug builds only: `--es seema_preview Listening` shows that state's visuals, NONE returns to the real state.
        if (BuildConfig.DEBUG) intent?.getStringExtra("seema_preview")?.let { name ->
            (application as RajuApp).container.debugPreview.value = AssistantState.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        // Debug builds only: `--es seema_voice_test PIPER` downloads that offline voice for real, speaks a Hindi
        // sentence through the normal speech path and logs the measured numbers (emulator proof of the engine).
        if (BuildConfig.DEBUG) intent?.getStringExtra("seema_voice_test")?.let { name ->
            val engine = com.raju.aiassistant.domain.model.TtsEngine.entries.firstOrNull { it.name == name } ?: return@let
            val c = (application as RajuApp).container
            c.appScope.launch { runOfflineVoiceTest(c, engine) }
        }
    }

    /** Long-press the app icon → "Voice" / "Chat". Dynamic, so they work for both debug and release package names. */
    private fun publishShortcuts() {
        runCatching {
            val icon = IconCompat.createWithResource(this, R.mipmap.ic_launcher)
            fun shortcut(id: String, label: String, action: String) = ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(label)
                .setIcon(icon)
                .setIntent(Intent(this, MainActivity::class.java).setAction(action))
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(
                this,
                listOf(
                    shortcut("voice", "Seema se bolo", LaunchRequest.ACTION_VOICE),
                    shortcut("chat", "Chat", LaunchRequest.ACTION_CHAT),
                ),
            )
        }
    }
}
