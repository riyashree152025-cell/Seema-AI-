package com.raju.aiassistant.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.ui.LaunchRequest
import com.raju.aiassistant.ui.assistant.AnnouncementPrompt
import com.raju.aiassistant.ui.assistant.AssistantDialogs
import com.raju.aiassistant.ui.chat.ChatScreen
import com.raju.aiassistant.ui.home.HomeScreen
import com.raju.aiassistant.ui.settings.SettingsScreen
import com.raju.aiassistant.ui.tools.ToolsScreen
import com.raju.aiassistant.ui.control.ControlPanelScreen
import com.raju.aiassistant.ui.brain.AiBrainScreen
import com.raju.aiassistant.ui.voice.VoiceControlScreen
import com.raju.aiassistant.ui.apps.AppsScreen
import com.raju.aiassistant.ui.profile.ProfileScreen
import com.raju.aiassistant.ui.pro.SeemaProScreen
import com.raju.aiassistant.ui.components.HudEffects
import com.raju.aiassistant.ui.components.LocalHudEffects
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
fun RajuNavHost(navController: NavHostController = rememberNavController()) {
    val container = (LocalContext.current.applicationContext as RajuApp).container
    AssistantDialogs(container.engine)
    AnnouncementPrompt(container.announcements)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(backStackEntry?.destination?.route)

    val navigateTo: (Destination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Hands-free: listen as soon as the app is on screen, release the microphone when it is not.
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var askedMic by rememberSaveable { mutableStateOf(false) }
    // Caller name for "kiska call hai": Android gives it to the call-screening role at ring time,
    // without it only a call-log scan after the ring. Asked once, during setup (Settings → Setup
    // Doctor can ask again).
    val appContext = LocalContext.current.applicationContext
    val callScreeningRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val askCallScreeningOnce: () -> Unit = {
        val prefs = appContext.getSharedPreferences("seema_setup", android.content.Context.MODE_PRIVATE)
        if (android.os.Build.VERSION.SDK_INT >= 29 && !prefs.getBoolean("asked_call_screening", false)) {
            val rm = appContext.getSystemService(android.app.role.RoleManager::class.java)
            val role = android.app.role.RoleManager.ROLE_CALL_SCREENING
            val intent = runCatching {
                if (rm != null && rm.isRoleAvailable(role) && !rm.isRoleHeld(role)) rm.createRequestRoleIntent(role) else null
            }.getOrNull()
            if (intent != null) {
                prefs.edit().putBoolean("asked_call_screening", true).apply()
                runCatching { callScreeningRole.launch(intent) }
            }
        }
    }
    val handsFreeMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.engine.startHandsFree()
        askCallScreeningOnce()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> scope.launch {
                    container.engine.onAppForeground()
                    val s = container.settingsRepository.settings.first() // stored values, not the startup default
                    if (!s.assistantPaused) {
                        if (container.voiceInput.hasPermission()) {
                            if (s.autoListen) container.engine.startHandsFree()
                            else if (s.wakeWord) container.engine.startStandby()
                            if (s.announceCalls) askCallScreeningOnce()
                        } else if (!askedMic) {
                            askedMic = true
                            handsFreeMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    android.util.Log.i("SeemaWake", "app in background, standby service running=${com.raju.aiassistant.voice.SeemaWakeService.running}")
                    container.engine.suspendListening()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Home-screen shortcuts and text shared from other apps (see MainActivity).
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.engine.startHandsFree()
    }
    val launch by container.launchRequests.collectAsStateWithLifecycle()
    LaunchedEffect(launch) {
        val request = launch ?: return@LaunchedEffect
        container.launchRequests.value = null
        runCatching {
            when (request) {
                LaunchRequest.Voice -> {
                    navigateTo(Destination.Home)
                    if (container.voiceInput.hasPermission()) container.engine.startHandsFree()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
                LaunchRequest.Chat -> navigateTo(Destination.Chat)
                is LaunchRequest.Ask -> {
                    navigateTo(Destination.Chat)
                    container.engine.submit(request.text)
                }
            }
        }
    }

    // Decorative HUD motion follows the user's motion settings (and the system "Remove animations").
    val settings by container.currentSettings.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val systemAnimations = remember(resolver, settings.reducedMotion) {
        android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val hudEffects = HudEffects.forMotion(animate = !settings.reducedMotion && systemAnimations, lowPower = settings.lowPowerMode)

    CompositionLocalProvider(LocalHudEffects provides hudEffects) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Hide bottom bar on Home screen to allow full Sci-Fi Cockpit immersion; show on all other screens
            if (current != Destination.Home) {
                HoloNavBar(current, navigateTo)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.start.route,
            modifier = Modifier
                // A quiet navy → violet depth behind every screen (Home draws its own holographic background on top).
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface, Color(0xFF110D26))))
                .padding(padding),
            // Short holographic fade between screens (the default is a slow 700 ms crossfade).
            enterTransition = { fadeIn(tween(if (hudEffects.pulses) 240 else 0)) + scaleIn(tween(240), initialScale = if (hudEffects.pulses) 0.985f else 1f) },
            exitTransition = { fadeOut(tween(if (hudEffects.pulses) 140 else 0)) },
            popEnterTransition = { fadeIn(tween(if (hudEffects.pulses) 240 else 0)) },
            popExitTransition = { fadeOut(tween(if (hudEffects.pulses) 140 else 0)) },
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    onOpenChat = { navigateTo(Destination.Chat) },
                    onOpenSettings = { navigateTo(Destination.Settings) },
                    onOpenApps = { navigateTo(Destination.Apps) },
                    onOpenControlPanel = { navController.navigate(SubRoutes.CONTROL_PANEL) },
                    onOpenVoiceControl = { navController.navigate(SubRoutes.VOICE_CONTROL) },
                    onOpenAiBrain = { navController.navigate(SubRoutes.AI_BRAIN) },
                    onOpenMemoryCenter = { navController.navigate(SubRoutes.MEMORY_CENTER) },
                )
            }
            composable(Destination.Chat.route) {
                ChatScreen(
                    onOpenSettings = { navigateTo(Destination.Settings) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.Apps.route) {
                AppsScreen(
                    onOpenTools = { navController.navigate(SubRoutes.TOOLS) },
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.Profile.route) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPro = { navController.navigate(SubRoutes.SEEMA_PRO) },
                )
            }
            composable(SubRoutes.CONTROL_PANEL) {
                ControlPanelScreen(
                    onBack = { navController.popBackStack() },
                    onOpenApps = { navigateTo(Destination.Apps) },
                )
            }
            composable(SubRoutes.AI_BRAIN) {
                AiBrainScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navigateTo(Destination.Settings) },
                )
            }
            composable(SubRoutes.MEMORY_CENTER) {
                com.raju.aiassistant.ui.brain.MemoryCenterScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navigateTo(Destination.Settings) },
                )
            }
            composable(SubRoutes.VOICE_CONTROL) {
                VoiceControlScreen(
                    onBack = { navController.popBackStack() },
                    onOpenVoiceSettings = { navigateTo(Destination.Settings) },
                )
            }
            composable(SubRoutes.SEEMA_PRO) {
                SeemaProScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(SubRoutes.TOOLS) {
                ToolsScreen(
                    onOpenChat = { navigateTo(Destination.Chat) },
                )
            }
        }
    }
    }
}
