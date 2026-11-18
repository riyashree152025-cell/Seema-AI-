package com.raju.aiassistant.data.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * The one saved payment QR photo (Paytm / any UPI QR), kept in the app's private files and shared only on a
 * request ("mera QR bhej do Rizwan ko"). Saved as PNG, at most 1600 px on its longest side.
 */
object PaymentQr {
    fun file(context: Context): File = File(File(context.filesDir, "payment"), "payment_qr.png")

    /** Copies the picked picture in; false when it could not be read as an image. */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return false
        val scale = minOf(1f, MAX_SIDE / maxOf(bmp.width, bmp.height).toFloat())
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val f = file(context)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "payment_qr.tmp")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(f) || run { f.delete(); tmp.renameTo(f) }
    }.getOrDefault(false)

    fun delete(context: Context): Boolean = file(context).delete()

    /** A small preview for the card, or null. */
    fun thumbnail(context: Context, side: Int = 240): Bitmap? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= side) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private const val MAX_SIDE = 1600f
}
