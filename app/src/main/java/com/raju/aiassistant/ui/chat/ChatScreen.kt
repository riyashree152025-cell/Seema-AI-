package com.raju.aiassistant.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.domain.model.ChatMessage
import com.raju.aiassistant.domain.model.ChatRole
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.theme.NeonCyan
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber

/**
 * Screen 2: Chat Screen
 * Matches Reference Image: Header `< Chat ⋮`, Avatar messages, Input bar with circular mic & send button.
 */
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ChatViewModel = viewModel(factory = RajuViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val busy = state.state.isBusy
    var showMenu by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoice()
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040714), Color(0xFF02040A))))
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var searching by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        var results by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
        LaunchedEffect(query, searching) {
            results = if (searching) viewModel.search(query) else emptyList()
        }

        // Header: < Chat ⋮
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Chat",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
                modifier = Modifier.weight(1f),
            )

            // 3-Dot Overflow Menu
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = NeonCyan)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (searching) "Close search" else "Search chats") },
                        onClick = {
                            showMenu = false
                            searching = !searching
                            if (!searching) query = ""
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.speakReplies) "Mute replies" else "Speak replies") },
                        onClick = {
                            showMenu = false
                            viewModel.setSpeakReplies(!state.speakReplies)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("New conversation") },
                        onClick = {
                            showMenu = false
                            viewModel.newConversation()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("AI Settings") },
                        onClick = {
                            showMenu = false
                            onOpenSettings()
                        },
                    )
                }
            }
        }

        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search saved messages...") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color(0x3300E5FF),
                ),
            )
        }

        // Messages Area
        val displayMessages = if (searching && query.isNotBlank()) results else state.messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (displayMessages.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (searching) "No messages found" else "Hello! How can I help you today?",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF8BA5CC)),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(displayMessages, key = { it.id }) { msg ->
                ReferenceMessageRow(message = msg, onRepeat = viewModel::repeat)
            }

            if (busy) {
                item {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonCyan)
                        Text(
                            if (state.state == AssistantState.Thinking) "Thinking..." else "Working on it...",
                            color = Color(0xFF8BA5CC),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // Bottom Input Row: Mic Circle + Glass Input Field + Send Button
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Left: Glowing circular mic button
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x3300E5FF))
                    .border(1.5.dp, NeonCyan, CircleShape)
                    .clickable {
                        if (busy) {
                            viewModel.stop()
                        } else {
                            if (viewModel.hasMicPermission()) viewModel.startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (busy) Icons.Filled.Stop else Icons.Filled.GraphicEq,
                    contentDescription = "Voice Input",
                    tint = if (busy) WarningAmber else NeonCyan,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Center: Glass Input Field
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = { Text("Type or Speak...", color = Color(0xFF7088AA), fontSize = 14.sp) },
                maxLines = 3,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color(0x3300E5FF),
                    focusedContainerColor = Color(0xDD081226),
                    unfocusedContainerColor = Color(0xCC050B18),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            // Right: Glowing Send Button
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank()) NeonCyan else Color(0x3300E5FF))
                    .border(1.5.dp, NeonCyan, CircleShape)
                    .clickable {
                        val text = input.trim()
                        if (text.isNotEmpty() && !busy) {
                            viewModel.send(text)
                            input = ""
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank()) Color.Black else NeonCyan,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ReferenceMessageRow(message: ChatMessage, onRepeat: ((String) -> Unit)? = null) {
    val isUser = message.role == ChatRole.USER
    val clipboard = LocalClipboardManager.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // Assistant Avatar on Left
        if (!isUser) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x3300E5FF))
                    .border(1.dp, NeonCyan, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        // Message Bubble
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) Color(0xFF0077FF) else Color(0xDD0B162C),
            border = if (isUser) null else BorderStroke(1.dp, Color(0x3300E5FF)),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                SelectionContainer {
                    Text(
                        message.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    )
                }

                // Copy / Repeat row for assistant messages
                if (!isUser) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            "Copy",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { clipboard.setText(AnnotatedString(message.text)) }
                                .padding(4.dp),
                        )
                        if (onRepeat != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Repeat",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable { onRepeat(message.text) }
                                    .padding(4.dp),
                            )
                        }
                    }
                }
            }
        }

        // User Avatar on Right
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x3300E5FF))
                    .border(1.dp, NeonCyan, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            }
        }
    }
}
