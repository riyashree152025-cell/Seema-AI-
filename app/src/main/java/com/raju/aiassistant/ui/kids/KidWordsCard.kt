package com.raju.aiassistant.ui.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.kids.KidWord
import kotlinx.coroutines.launch

/**
 * Memory Center → "रिया के शब्द": every word Seema learned for Riya (and how: taught by a parent, her own
 * "হ্যাঁ", or guessed from the talk). A wrong one is deleted with ✕; a new one is added here or by saying
 * "रिया हुसू बोले तो मतलब पेशाब".
 */
@Composable
fun KidWordsCard() {
    val container = (LocalContext.current.applicationContext as RajuApp).container
    val words by container.kidWords.words.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var heard by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF081730))
            .border(1.2.dp, Color(0xFF16345C), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("👧 रिया के शब्द (${words.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
        Text(
            "रिया जो बोलती है → उसका मतलब। Seema रिया मोड में ये शब्द समझती है, और रिया की \"हाँ\" से नए शब्द ख़ुद सीखती है। गलत शब्द ✕ से हटाइए।",
            color = Color.White.copy(alpha = 0.65f), fontSize = 13.5.sp,
        )
        if (words.isEmpty()) Text("अभी कोई शब्द नहीं।", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        words.take(200).forEach { w ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${w.heard} → ${w.meaning}", color = Color.White, fontSize = 15.sp)
                    Text(
                        when (w.source) {
                            KidWord.Source.TAUGHT -> "आपने सिखाया"
                            KidWord.Source.CONFIRMED -> "रिया ने \"हाँ\" कहकर सिखाया"
                            KidWord.Source.GUESSED -> "बात से अंदाज़ा (जाँच लीजिए)"
                        } + if (w.uses > 0) " · ${w.uses} बार" else "",
                        color = if (w.source == KidWord.Source.GUESSED) Color(0xFFFFC078) else Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    )
                }
                TextButton(onClick = { scope.launch { container.kidWords.remove(w.heard) } }) { Text("✕", color = Color(0xFFFF8787), fontSize = 18.sp) }
            }
        }
        OutlinedTextField(value = heard, onValueChange = { heard = it.take(30) }, label = { Text("रिया बोलती है (जैसे: মালু)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = meaning, onValueChange = { meaning = it.take(40) }, label = { Text("मतलब (जैसे: মালাই)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(
            enabled = heard.isNotBlank() && meaning.isNotBlank(),
            onClick = {
                val h = heard.trim()
                val m = meaning.trim()
                scope.launch { container.kidWords.put(KidWord(h, m, KidWord.Source.TAUGHT, System.currentTimeMillis())) }
                heard = ""
                meaning = ""
            },
        ) { Text("+ शब्द जोड़ें", color = Color(0xFF00E5FF)) }
    }
}
