package com.raju.aiassistant.ui.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.ui.theme.NeonCyan

private data class AppItem(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val iconColor: Color,
    val category: String, // Tools, System, Utilities
    val action: (Context) -> Unit,
)

/**
 * Screen 6: Apps Launcher
 * Matches Reference Image: 4 Category Tabs, 4x3 Grid of 12 real phone apps.
 */
@Composable
fun AppsScreen(
    onOpenTools: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RajuApp).container
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Tools", "System", "Utilities")

    val appList = remember {
        listOf(
            // Row 1
            AppItem(
                id = "camera",
                name = "Camera",
                icon = Icons.Filled.CameraAlt,
                iconColor = NeonCyan,
                category = "System",
                action = { ctx ->
                    runCatching {
                        ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { container.deviceTools.openApp("Camera") }
                },
            ),
            AppItem(
                id = "gallery",
                name = "Gallery",
                icon = Icons.Filled.PhotoLibrary,
                iconColor = Color(0xFFD946EF),
                category = "System",
                action = { ctx ->
                    runCatching {
                        val i = Intent(Intent.ACTION_VIEW).apply {
                            type = "image/*"
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        ctx.startActivity(i)
                    }.onFailure { container.deviceTools.openApp("Gallery") }
                },
            ),
            AppItem(
                id = "music",
                name = "Music",
                icon = Icons.Filled.MusicNote,
                iconColor = Color(0xFFEC4899),
                category = "Utilities",
                action = { ctx ->
                    val intent = ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
                        ?: ctx.packageManager.getLaunchIntentForPackage("com.spotify.music")
                        ?: Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)
                    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { container.deviceTools.openApp("Music") }
                },
            ),
            AppItem(
                id = "youtube",
                name = "YouTube",
                icon = Icons.Filled.PlayArrow,
                iconColor = Color(0xFFEF4444),
                category = "Utilities",
                action = { ctx ->
                    val intent = ctx.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com"))
                    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
            ),
            // Row 2
            AppItem(
                id = "maps",
                name = "Maps",
                icon = Icons.Filled.Place,
                iconColor = Color(0xFF38BDF8),
                category = "Tools",
                action = { ctx ->
                    val intent = ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=maps"))
                    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
            ),
            AppItem(
                id = "playstore",
                name = "Play Store",
                icon = Icons.Filled.Shop,
                iconColor = Color(0xFF10B981),
                category = "System",
                action = { ctx ->
                    val intent = ctx.packageManager.getLaunchIntentForPackage("com.android.vending")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store"))
                    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
            ),
            AppItem(
                id = "calculator",
                name = "Calculator",
                icon = Icons.Filled.Calculate,
                iconColor = Color(0xFFF59E0B),
                category = "Utilities",
                action = { ctx ->
                    container.deviceTools.openApp("Calculator")
                },
            ),
            AppItem(
                id = "calendar",
                name = "Calendar",
                icon = Icons.Filled.CalendarMonth,
                iconColor = Color(0xFF06B6D4),
                category = "Utilities",
                action = { ctx ->
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
                    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { container.deviceTools.openApp("Calendar") }
                },
            ),
            // Row 3
            AppItem(
                id = "files",
                name = "Files",
                icon = Icons.Filled.Folder,
                iconColor = Color(0xFF3B82F6),
                category = "System",
                action = { ctx ->
                    runCatching {
                        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        ctx.startActivity(i)
                    }.onFailure { container.deviceTools.openApp("Files") }
                },
            ),
            AppItem(
                id = "contacts",
                name = "Contacts",
                icon = Icons.Filled.Contacts,
                iconColor = Color(0xFF60A5FA),
                category = "Tools",
                action = { ctx ->
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { container.deviceTools.openApp("Contacts") }
                },
            ),
            AppItem(
                id = "browser",
                name = "Browser",
                icon = Icons.Filled.Language,
                iconColor = NeonCyan,
                category = "Tools",
                action = { ctx ->
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            ),
            AppItem(
                id = "more",
                name = "More",
                icon = Icons.Filled.MoreHoriz,
                iconColor = Color.White,
                category = "All",
                action = { onOpenTools() },
            ),
        )
    }

    val filteredApps = remember(selectedCategory) {
        if (selectedCategory == "All") {
            appList
        } else {
            appList.filter { it.category == selectedCategory || it.id == "more" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040714), Color(0xFF02040A))))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: 9-dots + Apps
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.GridView, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Apps",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
            )
        }

        // Category Filter Tabs (All, Tools, System, Utilities)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            categories.forEach { cat ->
                val selected = cat == selectedCategory
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color(0xFF0077FF) else Color(0x330A1428))
                        .border(
                            1.dp,
                            if (selected) NeonCyan else Color(0x2200E5FF),
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else Color(0xFF8BA5CC),
                        ),
                    )
                }
            }
        }

        // 4x3 App Launcher Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(filteredApps) { item ->
                AppGridCard(item = item, onClick = { item.action(context) })
            }
        }
    }
}

@Composable
private fun AppGridCard(item: AppItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xD90B1832), Color(0xF2050D1C))))
                .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = item.name,
                tint = item.iconColor,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            item.name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
