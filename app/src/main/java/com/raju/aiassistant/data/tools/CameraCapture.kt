package com.raju.aiassistant.data.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Takes one JPEG with the front or back camera, without opening the camera app (Camera2, no extra
 * library). The camera runs ~0.8 s so exposure and white balance settle, then one still is taken.
 * Android allows this only while Seema is on screen (camera is a "while in use" permission).
 */
object CameraCapture {
    /** Longest side of the photo: enough for the AI to read a scene, small enough to send quickly. */
    private const val MAX_SIDE = 1600
    private const val SETTLE_MS = 800L

    @SuppressLint("MissingPermission") // the caller checks CAMERA
    suspend fun capture(context: Context, front: Boolean): ByteArray {
        val manager = context.getSystemService(CameraManager::class.java) ?: error("No camera service")
        val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing }
            ?: manager.cameraIdList.firstOrNull()
            ?: error("No camera on this phone")
        val chars = manager.getCameraCharacteristics(id)
        val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        val size = sizes.filter { maxOf(it.width, it.height) <= MAX_SIDE }.maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height } ?: Size(1280, 720)
        val sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        val thread = HandlerThread("seema-camera").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        // A preview target nobody draws: it only lets auto-exposure run before the still.
        val texture = SurfaceTexture(0).apply { setDefaultBufferSize(640, 480) }
        val preview = Surface(texture)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        try {
            return withTimeout(10_000) {
                val cam = openCamera(manager, id, handler).also { device = it }
                val s = createSession(cam, listOf(preview, reader.surface), handler).also { session = it }
                val warm = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(preview) }.build()
                s.setRepeatingRequest(warm, null, handler)
                delay(SETTLE_MS)
                val photo = CompletableDeferred<ByteArray>()
                reader.setOnImageAvailableListener({ r ->
                    r.acquireLatestImage()?.use { img ->
                        val buf = img.planes[0].buffer
                        photo.complete(ByteArray(buf.remaining()).also { buf.get(it) })
                    }
                }, handler)
                val still = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.JPEG_ORIENTATION, sensor)
                    set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                }.build()
                s.stopRepeating()
                s.capture(still, null, handler)
                photo.await()
            }
        } finally {
            runCatching { session?.close() }
            runCatching { device?.close() }
            reader.close()
            preview.release()
            texture.release()
            thread.quitSafely()
        }
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
            override fun onConfigureFailed(session: CameraCaptureSession) { if (cont.isActive) cont.resumeWithException(IllegalStateException("Camera could not start")) }
        }, handler)
    }
}
