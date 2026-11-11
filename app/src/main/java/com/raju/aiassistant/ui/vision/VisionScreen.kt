package com.raju.aiassistant.ui.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.vision.VisionTask
import com.raju.aiassistant.domain.vision.visionPrompt
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_IMAGES = 4

/** One chosen picture: a small preview for the screen and the JPEG that is sent. */
private class Picked(val preview: Bitmap, val jpeg: ByteArray)

/**
 * Vision: the user picks up to four pictures with Android's Photo Picker (Seema never reads the gallery
 * by herself), then asks about them, takes the text out of them, or compares them. Only the chosen
 * pictures go to the vision model, and only when a button is pressed.
 */
@Composable
fun VisionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf(listOf<Picked>()) }
    var question by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            images = withContext(Dispatchers.IO) { uris.take(MAX_IMAGES).mapNotNull { load(context, it) } }
            answer = null
            error = if (images.isEmpty()) "Photo khul nahi payi." else null
        }
    }

    fun run(task: VisionTask) {
        if (busy || images.isEmpty()) return
        busy = true; answer = null; error = null
        scope.launch {
            val hindi = container.engine.language.value != com.raju.aiassistant.domain.language.SeemaLanguage.BENGALI
            val r = container.askAboutImages(
                images.map { it.jpeg }, visionPrompt(task, question, images.size, hindi),
                "You are Seema, the user's phone assistant, looking at pictures the user chose. Be accurate: say what is visible, mark guesses as guesses, never invent text or details.",
            )
            when (r) {
                is AiResult.Success -> answer = r.value
                is AiResult.Failure -> error = r.message
            }
            busy = false
        }
    }

    // Tatkal helper: names and birth years are read from the ID photo; the ages are the app's own sum.
    fun readPassengers() {
        if (busy || images.isEmpty()) return
        busy = true; answer = null; error = null
        scope.launch {
            val r = container.askAboutImages(
                images.map { it.jpeg }, com.raju.aiassistant.domain.vision.ID_PASSENGER_PROMPT,
                "You read identity cards for a railway ticket form. Copy only what is printed; never guess a name, a date or a gender.",
            )
            when (r) {
                is AiResult.Success -> {
                    val list = com.raju.aiassistant.domain.tatkal.parsePassengerLines(r.value, java.time.LocalDate.now())
                    if (list.isEmpty()) error = "इस photo से नाम और जन्म का साल साफ़ नहीं पढ़ा गया — साफ़ photo चुनिए, या नाम और साल बोल दीजिए।"
                    else container.engine.showPassengers(list)
                }
                is AiResult.Failure -> error = r.message
            }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Vision", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }

        HudPanel(title = "Photo चुनिए") {
            Text(
                "Gallery से 1 से $MAX_IMAGES photo चुनिए। सिर्फ़ चुनी हुई photo, और सिर्फ़ बटन दबाने पर, समझने के लिए AI (Gemini) को भेजी जाती है।",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
            )
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (images.isEmpty()) "Gallery से photo चुनें" else "दूसरी photo चुनें") }
            if (images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.forEach { p ->
                        Image(
                            p.preview.asImageBitmap(), contentDescription = "चुनी हुई photo",
                            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop,
                        )
                    }
                }
                TextButton(onClick = { images = emptyList(); answer = null; error = null }) { Text("हटाएँ") }
            }
        }

        HudPanel(title = "क्या करना है?") {
            OutlinedTextField(
                value = question, onValueChange = { question = it.take(300) },
                label = { Text("सवाल (खाली भी छोड़ सकते हैं)") }, modifier = Modifier.fillMaxWidth(),
            )
            val ready = images.isNotEmpty() && !busy
            Button(onClick = { run(VisionTask.ASK) }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("बताओ इसमें क्या है") }
            OutlinedButton(onClick = { run(VisionTask.READ_TEXT) }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("लिखा हुआ text निकालो") }
            OutlinedButton(onClick = { run(VisionTask.COMPARE) }, enabled = ready && images.size >= 2, modifier = Modifier.fillMaxWidth()) {
                Text(if (images.size >= 2) "Photo की तुलना करो" else "तुलना के लिए 2 photo चुनिए")
            }
            OutlinedButton(onClick = { readPassengers() }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("ID से यात्री कार्ड (नाम, उम्र)") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        error?.let { HudPanel(title = "नहीं हो पाया", accent = ErrorRed) { Text(it, color = ErrorRed) } }
        answer?.let { text ->
            HudPanel(title = "जवाब") {
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { scope.launch { runCatching { container.speaker.speak(text.take(900)) } } }) { Text("बोलकर सुनाओ") }
                    TextButton(onClick = { container.deviceTools.copyToClipboard(text) }) { Text("Copy") }
                }
            }
        }
    }
}

/** Reads the chosen picture: longest side 1280 px for sending, 200 px for the preview. */
private fun load(context: Context, uri: Uri): Picked? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / (sample * 2) >= 1280) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val side = maxOf(bmp.width, bmp.height)
    val send = if (side > 1280) Bitmap.createScaledBitmap(bmp, bmp.width * 1280 / side, bmp.height * 1280 / side, true) else bmp
    val out = ByteArrayOutputStream()
    send.compress(Bitmap.CompressFormat.JPEG, 82, out)
    val p = maxOf(send.width, send.height)
    val preview = Bitmap.createScaledBitmap(send, (send.width * 200 / p).coerceAtLeast(1), (send.height * 200 / p).coerceAtLeast(1), true)
    Picked(preview, out.toByteArray())
}.getOrNull()
