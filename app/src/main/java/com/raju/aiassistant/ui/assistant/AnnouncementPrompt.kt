package com.raju.aiassistant.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.domain.notify.AnnouncementCoordinator
import com.raju.aiassistant.domain.notify.whoText

/**
 * "Riya ka message aaya hai. Padhkar sunaun?" as a compact pill floating at the top: it does not
 * take focus (her listening for "haan / rehne do" carries on) and never covers the Living Eye.
 */
@Composable
fun AnnouncementPrompt(coordinator: AnnouncementCoordinator) {
    val pending by coordinator.pending.collectAsStateWithLifecycle()
    val p = pending ?: return
    val who = whoText(p.item)
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, 0),
        onDismissRequest = {},
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(top = 8.dp)
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .background(Color(0xF0081A33), RoundedCornerShape(26.dp))
                .border(1.dp, Color(0x8800E5FF), RoundedCornerShape(26.dp))
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.item.appLabel, color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    if (p.item.count > 1) "$who ke ${p.item.count} messages. Padhkar sunaun?" else "$who ka message. Padhkar sunaun?",
                    color = Color.White, fontSize = 16.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { coordinator.skip(p.id) }) { Text("Skip", color = Color.White.copy(alpha = 0.7f)) }
            TextButton(onClick = { coordinator.read(p.id) }) { Text("Read", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold) }
        }
    }
}
