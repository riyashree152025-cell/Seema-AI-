package com.raju.aiassistant.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Neon blue of the 3D Holographic Core. */
private val HOLO_BLUE = Color(0xFF4DC8FF)

/**
 * Bottom navigation for the 3D Holographic design: a slim floating glass capsule. Icons only,
 * except the open page, which lights up as a glowing pill with its name.
 */
@Composable
fun HoloNavBar(current: Destination?, onSelect: (Destination) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE60A1A2E), Color(0xF2040A14))))
            .border(1.dp, Brush.horizontalGradient(listOf(HOLO_BLUE.copy(alpha = 0.1f), HOLO_BLUE.copy(alpha = 0.7f), HOLO_BLUE.copy(alpha = 0.1f))), shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Destination.entries.forEach { dest ->
            val selected = dest == current
            val tint by animateColorAsState(if (selected) Color.White else HOLO_BLUE.copy(alpha = 0.65f), tween(250), label = "navTint")
            val pill by animateColorAsState(if (selected) HOLO_BLUE.copy(alpha = 0.28f) else Color.Transparent, tween(250), label = "navPill")
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(pill)
                    .then(if (selected) Modifier.border(1.dp, HOLO_BLUE.copy(alpha = 0.8f), RoundedCornerShape(18.dp)) else Modifier)
                    .clickable(role = Role.Tab, onClickLabel = dest.label) { onSelect(dest) }
                    .semantics { contentDescription = dest.label; this.selected = selected }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(dest.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                AnimatedVisibility(selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Row {
                        Spacer(Modifier.width(6.dp))
                        Text(dest.label, color = tint, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                    }
                }
            }
        }
    }
}
