package com.raju.aiassistant.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raju.aiassistant.domain.assistant.isSafeWebUrl
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.domain.model.ChatMessage
import com.raju.aiassistant.domain.model.ChatRole
import com.raju.aiassistant.ui.RajuViewModelFactory
import com.raju.aiassistant.ui.theme.ReactorBlue
import com.raju.aiassistant.ui.theme.TextSecondary
import com.raju.aiassistant.ui.theme.WarningAmber

@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = RajuViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val busy = state.state.isBusy

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoice()
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var searching by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        var results by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
        LaunchedEffect(query, searching) { results = if (searching) viewModel.search(query) else emptyList() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chat · ${state.providerName}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = if (searching) "Close search" else "Search chats")
            }
            IconButton(onClick = { viewModel.setSpeakReplies(!state.speakReplies) }) {
                Icon(
                    if (state.speakReplies) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (state.speakReplies) "Mute replies" else "Speak replies",
                )
            }
            IconButton(onClick = viewModel::newConversation) { Icon(Icons.Filled.AddComment, contentDescription = "New conversation") }
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search saved chats… / খুঁজুন…") },
                singleLine = true,
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (query.isNotBlank() && results.isEmpty()) item { Text("No matches.", color = TextSecondary) }
                items(results, key = { it.id }) { m ->
                    Column {
                        Text(
                            java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(m.createdAt)) +
                                " · " + if (m.role == ChatRole.USER) "You" else "Seema",
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                        )
                        MessageBubble(m, onRepeat = null)
                    }
                }
            }
        }
        if (!searching) {
        state.model?.let { m ->
            Text("Model: $m", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        state.modelWarning?.let { w ->
            Text("⚠ $w", style = MaterialTheme.typography.bodyMedium, color = WarningAmber)
        }
        if (!state.historyEnabled) {
            Text("History is off — this chat is not saved.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Text(
                        "Namaste! Kuch bhi poochhiye — हिन्दी, Hinglish ya বাংলা mein.\nTry: \"Mom ko call karo\", \"5 minute ka timer\", \"Kal 5 baje meeting add karo\", \"India Gate ka rasta\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            }
            items(state.messages, key = { it.id }) { MessageBubble(it, onRepeat = viewModel::repeat) }
            if (state.canRetry) {
                item {
                    OutlinedButton(onClick = viewModel::retry) { Text("Retry") }
                }
            }
            if (state.state == AssistantState.Thinking || state.state == AssistantState.Executing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(if (state.state == AssistantState.Thinking) "Thinking…" else "Working on it…", color = TextSecondary)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                maxLines = 4,
            )
            if (busy) {
                FilledTonalIconButton(onClick = viewModel::stop) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
            } else {
                FilledTonalIconButton(onClick = {
                    if (viewModel.hasMicPermission()) viewModel.startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Filled.Mic, contentDescription = "Speak") }
            }
            FilledIconButton(
                onClick = { viewModel.send(input); input = "" },
                // Disabled while a reply is being prepared, so one tap = one request.
                enabled = input.isNotBlank() && !busy,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
        }
    }
}

private val URL_REGEX = Regex("""https?://[^\s\]]+""")

/** Turns safe http(s) URLs into tappable links. */
private fun linkify(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in URL_REGEX.findAll(text)) {
        append(text.substring(last, m.range.first))
        var url = m.value.trimEnd('.', ',', ';', ':', '!', '?', '\u0964')
        while (url.endsWith(")") && url.count { it == ')' } > url.count { it == '(' }) url = url.dropLast(1)
        if (isSafeWebUrl(url)) {
            withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = ReactorBlue, textDecoration = TextDecoration.Underline)))) { append(url) }
            append(m.value.substring(url.length))
        } else {
            append(m.value)
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
private fun MessageBubble(message: ChatMessage, onRepeat: ((String) -> Unit)?) {
    if (message.role == ChatRole.SYSTEM) {
        Text(
            message.text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        return
    }
    val isUser = message.role == ChatRole.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        // Holographic message tags: user = cyan, Seema = emerald, each with cut corners on its own side.
        val accent = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        Surface(
            shape = if (isUser) CutCornerShape(topStart = 12.dp, bottomEnd = 2.dp, topEnd = 2.dp, bottomStart = 2.dp)
            else CutCornerShape(topEnd = 12.dp, bottomStart = 2.dp, topStart = 2.dp, bottomEnd = 2.dp),
            color = accent.copy(alpha = if (isUser) 0.14f else 0.08f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column {
                // Long-press to select part of it; Copy takes the whole message.
                SelectionContainer {
                    Text(linkify(message.text), Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (isUser) 12.dp else 0.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                if (!isUser) {
                    val clipboard = LocalClipboardManager.current
                    Row(Modifier.padding(horizontal = 4.dp)) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) { Text("Copy", style = MaterialTheme.typography.labelMedium) }
                        if (onRepeat != null) TextButton(onClick = { onRepeat(message.text) }) { Text("🔊 Repeat", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}
