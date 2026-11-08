package com.raju.aiassistant.data.voice

import com.raju.aiassistant.domain.assistant.WakeListener

/**
 * Turns 20 ms frames into a wake decision. A non-null result ends the listen: an utterance to check,
 * or an empty array for "her name was spotted on the phone" (offline wake word).
 */
interface FrameDetector {
    fun feed(frame: ByteArray): ByteArray?

    /** Frees what the detector holds (a native stream). Called once, never while [feed] runs. */
    fun close() {}
}

/**
 * Sleep-mode listener on the shared microphone: holds a WAKE lease on [hub] and runs a
 * [FrameDetector] over the frames ([WakeUtteranceDetector] by default, the offline keyword spotter
 * for KWS). Same contract as [MicWakeListener.start]: one result (or onError), then the listener is
 * done until started again. The lease is released as soon as there is a result, and the shared
 * recorder is closed for the recognizer that follows.
 */
class HubWakeListener(
    private val hub: MicHub,
    private val newDetector: () -> FrameDetector = { WakeUtteranceDetector() },
) : WakeListener {
    private inner class Session(val onUtterance: (ByteArray) -> Unit, val onError: () -> Unit) {
        val detector = newDetector()
        var lease: MicLease? = null // guarded by this@HubWakeListener
        var finished = false // guarded by this@HubWakeListener
    }

    private var session: Session? = null // guarded by this

    override fun start(onUtterance: (ByteArray) -> Unit, onError: () -> Unit) {
        stop()
        val s = Session(onUtterance, onError)
        synchronized(this) { session = s }
        val lease = hub.lease(
            MicPurpose.WAKE,
            onFrame = { frame -> onFrame(s, frame) },
            onError = { _ -> fail(s) },
        )
        // A frame or an error can arrive before lease() returned: then the lease is released here.
        val keep = synchronized(this) {
            if (session === s && !s.finished) {
                s.lease = lease
                true
            } else {
                false
            }
        }
        if (!keep) lease.release()
    }

    override fun stop() {
        val old = synchronized(this) {
            val cur = session ?: return
            session = null
            cur.finished = true
            cur.detector.close() // under the lock: never while feed() runs
            cur
        }
        val lease = synchronized(this) { old.lease }
        lease?.release()
        hub.closeIfIdle()
    }

    private fun onFrame(s: Session, frame: ByteArray) {
        val utterance = synchronized(this) { if (s.finished) null else s.detector.feed(frame) } ?: return
        val lease = finish(s) ?: return
        lease.release()
        // The recognizer that follows opens its own AudioRecord: the shared one must be closed first.
        hub.closeIfIdle()
        s.onUtterance(utterance)
    }

    private fun fail(s: Session) {
        val lease = finish(s) ?: return
        lease.release()
        hub.closeIfIdle()
        s.onError()
    }

    /** Marks the session done and returns its lease, or null if it was already done (only the first wins). */
    private fun finish(s: Session): MicLease? = synchronized(this) {
        if (s.finished) return null
        s.finished = true
        s.detector.close() // under the lock: never while feed() runs
        if (session === s) session = null
        s.lease ?: NO_LEASE_YET
    }

    private companion object {
        /** Placeholder: the lease is released by start() once it is returned. */
        val NO_LEASE_YET = object : MicLease {
            override val purpose = MicPurpose.WAKE
            override fun release() = Unit
        }
    }
}
