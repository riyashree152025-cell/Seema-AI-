package com.raju.aiassistant.data.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.raju.aiassistant.domain.rail.IrctcTicketParser
import com.raju.aiassistant.domain.tools.TicketPdf
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The folder where tickets are downloaded (usually Download), chosen once by the user in Apps & Tools.
 * Android lets an app read another app's PDFs only from a folder the user picked like this, so Seema
 * never scans storage on her own. The choice survives restarts; it can be changed any time.
 */
object TicketFolder {
    private const val PREFS = "seema_tickets"
    private const val KEY = "folder"
    /** How many of the newest PDFs are looked at to find the newest railway ticket among them. */
    private const val LOOK_AT = 6

    fun get(context: Context): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let(Uri::parse)

    /** Saves the folder picked with ACTION_OPEN_DOCUMENT_TREE and keeps the read permission. */
    fun set(context: Context, tree: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tree.toString()).apply()
    }

    /** The folder's name for the screen ("Download"), or null when none is chosen. */
    fun label(context: Context): String? = get(context)?.let { uri ->
        runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').ifBlank { "Download" } }.getOrNull()
    }

    /** The newest railway ticket among the newest PDFs in the folder (or the newest PDF, so its name can be said). */
    suspend fun latest(context: Context): TicketPdf = withContext(Dispatchers.IO) {
        val tree = get(context) ?: return@withContext TicketPdf.NoFolder
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        if (!granted) return@withContext TicketPdf.NoFolder
        val pdfs = runCatching { pdfsNewestFirst(context, tree) }.getOrDefault(emptyList())
        if (pdfs.isEmpty()) return@withContext TicketPdf.NoPdf
        var firstReadable: TicketPdf.Found? = null
        for ((docId, name) in pdfs.take(LOOK_AT)) {
            val text = textOf(context, DocumentsContract.buildDocumentUriUsingTree(tree, docId)) ?: continue
            val found = TicketPdf.Found(name, text)
            if (IrctcTicketParser.parse(text) != null) return@withContext found
            if (firstReadable == null) firstReadable = found
        }
        firstReadable ?: TicketPdf.Unreadable(pdfs.first().second)
    }

    private fun pdfsNewestFirst(context: Context, tree: Uri): List<Pair<String, String>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val found = mutableListOf<Triple<String, String, Long>>()
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2).orEmpty()
                if (mime != "application/pdf" && !name.endsWith(".pdf", ignoreCase = true)) continue
                found += Triple(id, name, if (c.isNull(3)) 0L else c.getLong(3))
            }
        }
        return found.sortedByDescending { it.third }.map { it.first to it.second }
    }

    private fun textOf(context: Context, doc: Uri): String? = runCatching {
        context.contentResolver.openInputStream(doc)?.use { input ->
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(input).use { d -> if (d.isEncrypted) null else PDFTextStripper().getText(d) }
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
