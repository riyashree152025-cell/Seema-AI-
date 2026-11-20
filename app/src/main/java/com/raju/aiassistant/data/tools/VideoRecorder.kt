package com.raju.aiassistant.data.tools

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * "Video recording chalu karo" → "band kar do": a video with sound from the phone's own camera and microphone
 * (Camera2 + MediaRecorder, no extra library), saved in the gallery under Movies/Seema. Android allows the camera
 * only while Seema is on screen. Seema keeps listening for "band kar do" at the same time: both recordings belong
 * to this app, and Android gives the microphone to both.
 */
object VideoRecorder {
    private class Active(
        val recorder: MediaRecorder,
        val device: CameraDevice,
        val session: CameraCaptureSession,
        val thread: HandlerThread,
        val pfd: ParcelFileDescriptor,
        val uri: Uri?,
        val file: java.io.File?,
        val name: String,
        val startedAt: Long,
    )

    @Volatile private var active: Active? = null

    fun recording(): Boolean = active != null

    /** The largest recordable size up to 1080p (wide first), else 720p. */
    private fun sizeFor(manager: CameraManager, id: String): Size {
        val sizes = manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(MediaRecorder::class.java).orEmpty()
        return sizes.filter { maxOf(it.width, it.height) <= 1920 && minOf(it.width, it.height) <= 1080 && it.width * 9 == it.height * 16 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.filter { maxOf(it.width, it.height) <= 1920 }.maxByOrNull { it.width * it.height }
            ?: Size(1280, 720)
    }

    /** Starts recording; throws when the camera or the file cannot be used. */
    @SuppressLint("MissingPermission") // the caller checks CAMERA and RECORD_AUDIO
    suspend fun start(context: Context, front: Boolean) {
        check(active == null) { "Already recording" }
        val manager = context.getSystemService(CameraManager::class.java) ?: error("No camera service")
        val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing }
            ?: manager.cameraIdList.firstOrNull() ?: error("No camera on this phone")
        val size = sizeFor(manager, id)
        val sensor = manager.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        val name = "Seema_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4"
        var uri: Uri? = null
        var file: java.io.File? = null
        val pfd: ParcelFileDescriptor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Seema")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Gallery is not available")
            context.contentResolver.openFileDescriptor(uri, "w") ?: error("Gallery file could not be opened")
        } else {
            // Android 8–9: the app's own Movies folder (no storage permission needed).
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            file = java.io.File(dir, name)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
        }

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        val thread = HandlerThread("seema-video").apply { start() }
        val handler = Handler(thread.looper)
        var device: CameraDevice? = null
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(pfd.fileDescriptor)
            recorder.setVideoEncodingBitRate(if (size.width >= 1920) 10_000_000 else 5_000_000)
            recorder.setVideoFrameRate(30)
            recorder.setVideoSize(size.width, size.height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            // Held upright: the sensor's own turn (90 back, 270 front on most phones).
            recorder.setOrientationHint(sensor)
            recorder.prepare()

            val session = withTimeout(10_000) {
                val cam = openCamera(manager, id, handler).also { device = it }
                val s = createSession(cam, listOf(recorder.surface), handler)
                val request = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply { addTarget(recorder.surface) }.build()
                s.setRepeatingRequest(request, null, handler)
                s
            }
            recorder.start()
            active = Active(recorder, device!!, session, thread, pfd, uri, file, name, System.currentTimeMillis())
        } catch (e: Throwable) {
            runCatching { recorder.release() }
            runCatching { device?.close() }
            runCatching { pfd.close() }
            thread.quitSafely()
            uri?.let { u -> runCatching { context.contentResolver.delete(u, null, null) } }
            file?.delete()
            throw e
        }
    }

    /** Stops and saves. Returns the file name and its length in seconds, or null when nothing was recording. */
    fun stop(context: Context): Pair<String, Int>? {
        val a = active ?: return null
        active = null
        val seconds = ((System.currentTimeMillis() - a.startedAt) / 1000).toInt()
        // stop() throws when no frame was written (stopped at once): the empty file is removed.
        val ok = runCatching { a.recorder.stop() }.isSuccess
        runCatching { a.recorder.release() }
        runCatching { a.session.close() }
        runCatching { a.device.close() }
        runCatching { a.pfd.close() }
        a.thread.quitSafely()
        if (!ok) {
            a.uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            a.file?.delete()
            error("The video was too short or the camera stopped")
        }
        if (a.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(a.uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }
        return a.name to seconds
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(manager: CameraManager, id: String, handler: Handler): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) { if (cont.isActive) cont.resume(camera) else camera.close() }
            override fun onDisconnected(camera: CameraDevice) { camera.close(); if (cont.isActive) cont.resumeWithException(IllegalStateException("Camera disconnected")) }
            override fun onError(camera: CameraDevice, error: Int) { camera.close(); if (cont.isActive) cont.resumeWithException(IllegalStateException("Camera error $error (in use by another app?)")) }
        }, handler)
    }

    @Suppress("DEPRECATION") // createCaptureSession(List<Surface>) is the simple form that works from API 26
    private suspend fun createSession(cam: CameraDevice, targets: List<Surface>, handler: Handler): CameraCaptureSession = suspendCancellableCoroutine { cont ->
        cam.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) { if (cont.isActive) cont.resume(session) }
            override fun onConfigureFailed(session: CameraCaptureSession) { if (cont.isActive) cont.resumeWithException(IllegalStateException("Camera could not start recording")) }
        }, handler)
    }
}
