package com.raju.aiassistant.ui.kids

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.domain.model.ChatRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Riya mode: one bright, simple screen for a three-year-old. A big face that listens and speaks, one big
 * button to talk, nothing else to press. Back does nothing; the screen is pinned when the phone allows it.
 * Only a parent leaves: hold the lock for 3 seconds, then answer a small sum.
 */
@Composable
fun KidModeScreen() {
    val container = (LocalContext.current.applicationContext as RajuApp).container
    val engine = container.engine
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    val state by engine.state.collectAsStateWithLifecycle()
    val transcript by engine.transcript.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }

    // Pinned to this app while Riya plays (Android asks once); unpinned when a parent leaves the mode.
    // The screen stays on (no auto-lock mid-play) and the system bars hide (a swipe shows them briefly).
    DisposableEffect(Unit) {
        runCatching { activity?.startLockTask() }
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insets = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(enabled = true) { /* Riya cannot leave with Back */ }

    // Hands-free: the microphone opens by itself when the mode starts and whenever the screen comes back
    // (she will not press a button first). After each answer the engine listens again on its own.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) scope.launch { delay(START_LISTEN_DELAY_MS); engine.startKidListening() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val level by container.speaker.level.collectAsStateWithLifecycle()
    val lastLine = transcript.lastOrNull { it.role == ChatRole.ASSISTANT }?.text ?: "রিয়া সোনা, কথা বলো!"
    val cyan = Color(0xFF4DF0FF)
    val gold = Color(0xFFFFD27D)

    Box(Modifier.fillMaxSize().background(Color(0xFF081018))) {
        RiyaAvatarFace(
            state = state,
            level = level,
            onGiggle = { line -> scope.launch { runCatching { container.speaker.speak(line) } } },
            modifier = Modifier.fillMaxSize(),
        )

        // "RIYA AI" glass badge, top left.
        Box(
            Modifier.align(Alignment.TopStart).padding(16.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0x33121F2C)).border(1.dp, cyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("RIYA AI", color = cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp) }

        // The lock: a parent holds it for 3 seconds.
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val released = withTimeoutOrNull(HOLD_TO_EXIT_MS) {
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.changes.none { it.pressed }) break
                            }
                            true
                        }
                        if (released == null) asking = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text("🔒", fontSize = 20.sp) }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                lastLine, fontSize = 19.sp, lineHeight = 26.sp, textAlign = TextAlign.Center, color = Color(0xFFE6FDFF),
                maxLines = 3, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x66101C28)).padding(10.dp),
            )
            // The dark-glass dock: mic (also an instant tap override) and what Seema is doing.
            val listening = state == AssistantState.Listening
            val glow = rememberInfiniteTransition(label = "mic")
            val ring by glow.animateFloat(1f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ring")
            Row(
                Modifier.clip(RoundedCornerShape(40.dp)).background(Color(0xCC0B1622))
                    .border(1.dp, (if (listening) cyan else gold).copy(alpha = 0.5f), RoundedCornerShape(40.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(64.dp).scale(if (listening) ring else 1f).clip(CircleShape)
                        .background(Brush.radialGradient(listOf((if (listening) cyan else gold).copy(alpha = 0.9f), Color(0xFF0B1622))))
                        .clickable {
                            when (state) {
                                AssistantState.Speaking -> engine.interrupt()
                                AssistantState.Listening -> Unit
                                else -> engine.startVoice()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("🎤", fontSize = 28.sp) }
                Text(
                    when (state) {
                        AssistantState.Listening -> "শুনছি…"
                        AssistantState.Thinking, AssistantState.Executing -> "ভাবছি…"
                        AssistantState.Speaking -> "বলছি…"
                        else -> "ছুঁয়ে দেখো!"
                    },
                    color = Color(0xFFE6FDFF), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 14.dp),
                )
            }
        }
    }

    if (asking) {
        var a by remember { mutableIntStateOf((4..9).random()) }
        var b by remember { mutableIntStateOf((3..9).random()) }
        var answer by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Riya mode बंद करें?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$a + $b = ?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = answer, onValueChange = { answer = it.filter(Char::isDigit).take(3); wrong = false },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (wrong) Text("गलत जवाब", color = Color(0xFFE03131))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (answer.toIntOrNull() == a + b) {
                        asking = false
                        runCatching { activity?.stopLockTask() }
                        engine.stop()
                        engine.onKidModeExit()
                        scope.launch { container.settingsRepository.setKidMode(false) }
                    } else {
                        wrong = true
                        answer = ""
                        a = (4..9).random()
                        b = (3..9).random()
                    }
                }) { Text("बंद करें") }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("रहने दें") } },
        )
    }
}

private const val HOLD_TO_EXIT_MS = 3_000L
/** The microphone opens this long after the screen appears (the screen settles first). */
private const val START_LISTEN_DELAY_MS = 700L
