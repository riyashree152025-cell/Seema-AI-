package com.raju.aiassistant.data.booking

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.raju.aiassistant.domain.booking.DaySlipWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Saves each journey date's slips as "Download/Seema Bookings/Bookings_dd-MM-yyyy.txt", where any
 * file manager can open it. Android 10+ needs no storage permission for this (MediaStore); on
 * Android 8–9 the file goes to the app's own folder in Android/data instead.
 */
class DownloadsSlipWriter(private val context: Context) : DaySlipWriter {

    override suspend fun write(date: LocalDate, fileName: String, text: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeMediaStore(fileName, text) else writeLegacy(fileName, text)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun writeMediaStore(fileName: String, text: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val folder = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/"
        val existing = resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(fileName, folder), null,
        )?.use { c -> if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null }
        val uri = existing ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            },
        ) ?: error("MediaStore insert failed")
        // "wt" truncates: the file always holds the whole day, never old and new text mixed.
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no output stream")
    }

    private fun writeLegacy(fileName: String, text: String) {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, FOLDER).apply { mkdirs() }
        File(dir, fileName).writeText(text)
    }

    companion object {
        const val FOLDER = "Seema Bookings"
    }
}
