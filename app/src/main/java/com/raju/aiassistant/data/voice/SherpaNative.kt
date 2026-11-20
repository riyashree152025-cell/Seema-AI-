package com.raju.aiassistant.data.voice

/** The sherpa-onnx native library that runs the offline "Hey Seema" detector (it is no longer used for voices). */
object SherpaNative {
    /** False when the native library cannot run on this phone (unsupported CPU); then the offline wake word is off. */
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("sherpa-onnx-jni"); true }.getOrDefault(false)
    }
}
