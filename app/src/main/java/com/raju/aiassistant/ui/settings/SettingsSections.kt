package com.raju.aiassistant.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raju.aiassistant.ui.theme.HoloCyan

/** A group heading in Settings (also announced as a heading by screen readers). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() }) {
        Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HoloCyan)
        HorizontalDivider(Modifier.padding(top = 6.dp), thickness = 1.dp, color = HoloCyan.copy(alpha = 0.5f))
    }
}

/** Chips at the top of Settings that jump to a section. */
@Composable
fun SectionJumpRow(sections: List<Pair<String, String>>, onJump: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { (key, label) ->
            AssistChip(onClick = { onJump(key) }, label = { Text(label, style = MaterialTheme.typography.labelLarge) }, modifier = Modifier.height(48.dp))
        }
    }
}
