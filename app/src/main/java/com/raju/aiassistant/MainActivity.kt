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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

        // Auto-heal provider selection to guaranteed-working Gemini if current provider is broken or unconfigured
        val appContainer = (application as RajuApp).container
        appContainer.appScope.launch {
            val configured = appContainer.secureKeyStore.configured.value
            val s = appContainer.currentSettings.value
            // Only a provider that cannot work is replaced (Ollama counts as set up once its PC address is saved).
            if (s.defaultProvider.secret !in configured) {
                if (com.raju.aiassistant.domain.model.SecretId.GEMINI_API_KEY in configured) {
                    appContainer.settingsRepository.setDefaultProvider(com.raju.aiassistant.domain.ai.ProviderId.GEMINI)
                }
            }
            // A saved Gemini model that Google has retired (or a broken value) goes back to "auto":
            // Seema then picks the newest Flash model the key lists (never a fixed, ageing name).
            val geminiModel = s.modelFor(com.raju.aiassistant.domain.ai.ProviderId.GEMINI)
            if (geminiModel != null && com.raju.aiassistant.domain.ai.ModelAdvice.isRetiredGemini(geminiModel)) {
                appContainer.settingsRepository.clearModel(com.raju.aiassistant.domain.ai.ProviderId.GEMINI)
            }
        }

        // Only a fresh launch carries a new request; after rotation the same intent must not run again.
        if (savedInstanceState == null) handle(intent)
        publishShortcuts()
        setContent {
            RajuTheme {
                val settings by (application as RajuApp).container.currentSettings.collectAsStateWithLifecycle()
                val locked = settings.appLock && !unlocked.value && deviceSecure()
                if (locked) {
                    // Nothing of the app is composed while locked: no chat, khata or settings behind the gate.
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF05070F)),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            androidx.compose.material3.Text("Seema lock है", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White)
                            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
                            androidx.compose.material3.Button(onClick = { askUnlock() }) { androidx.compose.material3.Text("खोलें") }
                        }
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) { askUnlock() }
                } else {
                    RajuNavHost()
                }
            }
        }
    }

    // ---- App lock (optional): the phone's own fingerprint / PIN / pattern, asked by Android itself.
    private val unlocked = androidx.compose.runtime.mutableStateOf(false)
    private var leftAt = 0L
    private val unlockLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) unlocked.value = true
    }

    private fun deviceSecure(): Boolean =
        (getSystemService(KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isDeviceSecure == true

    private fun askUnlock() {
        val km = getSystemService(KEYGUARD_SERVICE) as? android.app.KeyguardManager
        @Suppress("DEPRECATION")
        val intent = km?.createConfirmDeviceCredentialIntent("Seema AI", "Seema खोलने के लिए")
        if (intent == null) unlocked.value = true else runCatching { unlockLauncher.launch(intent) }
    }

    override fun onStop() {
        super.onStop()
        leftAt = android.os.SystemClock.elapsedRealtime()
        // Floating Seema over the other app (not for a rotation, and nothing while the app lock is closed).
        if (!isChangingConfigurations) {
            val s = (application as RajuApp).container.currentSettings.value
            com.raju.aiassistant.ui.overlay.FloatingSeema.onAppHidden(this, appLocked = s.appLock && !unlocked.value && deviceSecure())
        }
    }

    override fun onStart() {
        super.onStart()
        com.raju.aiassistant.ui.overlay.FloatingSeema.onAppShown()
        // Away for more than half a minute: lock again (a quick trip to a permission page does not).
        if (leftAt != 0L && android.os.SystemClock.elapsedRealtime() - leftAt > 30_000L) unlocked.value = false
    }

    override fun onResume() {
        super.onResume()
        // Background standby for "Hey Seema" (started while the app is on screen, as Android requires;
        // needs the microphone permission, so it also starts right after that is granted).
        // Switched OFF on Home: nothing listens in the background either.
        if (!(application as RajuApp).container.currentSettings.value.assistantPaused) SeemaWakeService.start(this)
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
