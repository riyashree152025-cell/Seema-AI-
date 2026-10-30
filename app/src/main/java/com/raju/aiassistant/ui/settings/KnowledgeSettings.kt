package com.raju.aiassistant.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raju.aiassistant.ui.components.HudPanel
import com.raju.aiassistant.ui.theme.ErrorRed
import com.raju.aiassistant.ui.theme.TextSecondary

/** MIME types the Knowledge Base can read (PDF, Word .docx, plain text, Markdown). */
private val DOCUMENT_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain", "text/markdown", "text/x-markdown", "application/octet-stream",
)

/** Settings → Knowledge Base: import, list and remove documents Seema answers from (RAG). */
@Composable
fun KnowledgeBaseSettings(viewModel: SettingsViewModel) {
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val busy by viewModel.kbBusy.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importDocuments(uris)
    }
    HudPanel(title = "Knowledge Base (your documents)") {
        Text(
            "Add PDF, Word (.docx), TXT or Markdown files. Seema reads them on the phone, splits them into passages and, when you ask something, adds the matching passages to the question and names the document as the source. This is document search (RAG) — the AI model is not trained or changed. Only those passages are sent to your AI provider with the question; the files stay on the phone. Scanned (image-only) PDFs cannot be read.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(DOCUMENT_TYPES) }, enabled = !busy) { Text("Add documents") }
            OutlinedButton(onClick = viewModel::rebuildIndex, enabled = !busy && docs.isNotEmpty()) { Text("Rebuild index") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (docs.isEmpty()) Text("No documents yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        docs.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📄 ${d.name} · ${d.chunkCount} passages", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                TextButton(onClick = { viewModel.removeDocument(d.id) }) { Text("Remove", color = ErrorRed) }
            }
        }
    }
}

/** Export / import of user-approved memories as a JSON file (Settings → Memory & privacy). */
@Composable
fun MemoryFileButtons(viewModel: SettingsViewModel) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportMemories(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importMemories(uri)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { export.launch("seema-memories.json") }) { Text("Export memories") }
        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Import memories") }
    }
    Text(
        "Memories are stored only on this phone and are sent to your AI provider as part of the request so Seema can use them. Passwords, PINs, OTPs and keys are never saved as memories (also not when importing).",
        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
    )
}
