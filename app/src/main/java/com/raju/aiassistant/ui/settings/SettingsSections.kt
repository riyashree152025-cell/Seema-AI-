package com.raju.aiassistant.ui.settings

import com.raju.aiassistant.ui.theme.TextPrimary
import com.raju.aiassistant.ui.theme.HoloViolet
import com.raju.aiassistant.ui.theme.GlassPanelHigh
import com.raju.aiassistant.ui.theme.Emerald
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A group heading in Settings (also announced as a heading by screen readers). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp).semantics { heading() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(22.dp).background(Brush.verticalGradient(listOf(Emerald, HoloViolet)), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.6.sp), fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(listOf(Emerald.copy(alpha = 0.7f), HoloViolet.copy(alpha = 0.4f), Color.Transparent))),
        )
    }
}

/** Chips at the top of Settings that jump to a section. */
@Composable
fun SectionJumpRow(sections: List<Pair<String, String>>, onJump: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { (key, label) ->
            AssistChip(
                onClick = { onJump(key) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary) },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = AssistChipDefaults.assistChipColors(containerColor = GlassPanelHigh),
                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Emerald.copy(alpha = 0.7f), HoloViolet.copy(alpha = 0.5f)))),
            )
        }
    }
}
