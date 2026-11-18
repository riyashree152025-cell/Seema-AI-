package com.raju.aiassistant.ui.kids

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    DisposableEffect(Unit) {
        runCatching { activity?.startLockTask() }
        onDispose { }
    }
    BackHandler(enabled = true) { /* Riya cannot leave with Back */ }

    val pulse = rememberInfiniteTransition(label = "riya")
    val beat by pulse.animateFloat(
        initialValue = 1f, targetValue = if (state == AssistantState.Listening || state == AssistantState.Speaking) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "beat",
    )
    val face = when (state) {
        AssistantState.Listening -> "👂"
        AssistantState.Thinking, AssistantState.Executing -> "🤔"
        AssistantState.Speaking -> "😄"
        AssistantState.Error -> "🙈"
        else -> "😊"
    }
    val lastLine = transcript.lastOrNull { it.role == ChatRole.ASSISTANT }?.text ?: "রিয়া সোনা, কথা বলো!"

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFE6F0), Color(0xFFFFF4D6), Color(0xFFDFF5FF)))),
    ) {
        // The lock: a parent holds it for 3 seconds.
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp).clip(CircleShape).background(Color(0x22000000))
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
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text("রিয়ার সীমা দিদি", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD6336C))
            Box(
                Modifier.size(220.dp).scale(beat).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFC2D9)))),
                contentAlignment = Alignment.Center,
            ) { Text(face, fontSize = 110.sp) }
            Text(
                lastLine, fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center, color = Color(0xFF5A3E5D),
                maxLines = 4, modifier = Modifier.fillMaxWidth(),
            )
            // The one button: talk (or stop her talking).
            Box(
                Modifier.size(130.dp).clip(CircleShape)
                    .background(if (state == AssistantState.Listening) Color(0xFF51CF66) else Color(0xFFFF6B9A))
                    .clickable {
                        when (state) {
                            AssistantState.Speaking -> engine.interrupt()
                            AssistantState.Listening -> Unit
                            else -> engine.startVoice()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(if (state == AssistantState.Listening) "👂" else "🎤", fontSize = 56.sp) }
            Spacer(Modifier.height(8.dp))
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
