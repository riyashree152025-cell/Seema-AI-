package com.raju.aiassistant.data.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/** 16 kHz mono PCM16 in 20 ms frames: the format every microphone consumer already uses. */
const val MIC_SAMPLE_RATE = 16_000
const val MIC_FRAME_MS = 20
const val MIC_FRAME_BYTES = MIC_SAMPLE_RATE / 1000 * MIC_FRAME_MS * 2

/** After the last lease ends, the recorder stays open this long, so a quick re-listen does not leave a gap. */
const val MIC_IDLE_GRACE_MS = 1_500L

/** The frame ring holds at least this many times the Android minimum buffer, so a CPU spike does not underrun. */
const val MIC_RING_MULTIPLIER = 4
private const val MIN_RING_FRAMES = 8

/** Who wants the microphone. A higher rank gets the frames while it is active; lower ranks wait. */
enum class MicPurpose(val rank: Int) {
    WAKE(0),
    BARGE_IN(1),
    STT(2),
}

/** Number of ring frames for an AudioRecord whose minimum buffer is [minBufferBytes]: at least 4x it, never fewer than 8. */
fun ringFramesFor(minBufferBytes: Int): Int =
    maxOf(MIN_RING_FRAMES, ceil(MIC_RING_MULTIPLIER * minBufferBytes.toDouble() / MIC_FRAME_BYTES).toInt())

/**
 * Where raw PCM comes from. The Android implementation wraps AudioRecord; tests use a scripted source.
 * [read] fills `buf[offset until offset+length]` and returns the bytes read, or a value <= 0 when the
 * microphone failed. It may return fewer bytes than asked; the hub keeps asking until a frame is full.
 */
interface FrameSource {
    /** Opens the microphone. False when it cannot be used right now. */
    fun open(): Boolean

    suspend fun read(buf: ByteArray, offset: Int, length: Int): Int

    fun close()
}

/** A consumer's claim on the microphone. [release] is idempotent. */
interface MicLease {
    val purpose: MicPurpose

    fun release()
}

/** The microphone handed to a recorder outside the hub (barge-in). [release] is idempotent. */
interface MicExclusive {
    fun release()
}

/** How long [MicHub.takeExclusive] waits for the hub's recorder to stop (a blocked read ends within 20 ms). */
const val MIC_EXCLUSIVE_WAIT_MS = 1_000L

/** Counters for the phone log (what the hub did), read with [MicHub.stats]. */
data class MicHubStats(
    val opens: Int,
    val framesRead: Long,
    val framesDelivered: Long,
    val droppedNoBuffer: Long,
    val droppedSlow: Long,
    val consumerErrors: Long,
    val leases: Int,
    val recording: Boolean,
    /** Someone outside the hub (barge-in) has the microphone: the hub does not record. */
    val exclusive: Boolean = false,
)

/**
 * One microphone reader for the whole app. Consumers take a [MicLease] and receive frames while they
 * hold it; only the highest-ranked active purpose gets frames at any moment ([MicPurpose]), so a
 * "Hey Seema" wake listener is quiet while speech recognition runs, with no second AudioRecord.
 *
 * Guarantees:
 * - The reader never waits for a consumer. Each frame is copied into a pooled buffer and handed to a
 *   per-lease channel with a non-blocking send. A slow callback loses its own frames (counted), it
 *   never stalls the reader and never affects the other leases.
 * - The reader always drains the microphone, even when the pool is empty, so the AudioRecord buffer
 *   does not overflow into a glitch; such frames are counted as [MicHubStats.droppedNoBuffer].
 * - When the last lease ends the recorder closes after [idleGraceMs]; a lease started inside that
 *   window reuses it.
 * - [pauseForCall] closes the recorder at once; [resume] reopens it if anyone still holds a lease.
 *
 * Callbacks receive a buffer that is reused after they return: copy it if you keep it.
 */
class MicHub(
    private val scope: CoroutineScope,
    private val sourceFactory: () -> FrameSource,
    private val ringFrames: Int = ringFramesFor(0),
    private val idleGraceMs: Long = MIC_IDLE_GRACE_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val consumerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = {},
) {
    private class Frame(val bytes: ByteArray) {
        val refs = AtomicInteger(0)
    }

    private inner class LeaseImpl(
        override val purpose: MicPurpose,
        val onFrame: (ByteArray) -> Unit,
        val onError: (String) -> Unit,
    ) : MicLease {
        val channel = Channel<Frame>(ringFrames)
        var consumer: Job? = null
        var released = false // guarded by lock

        override fun release() = releaseLease(this)
    }

    private val lock = Any()
    private val leases = mutableListOf<LeaseImpl>() // guarded by lock
    private val pool = ArrayDeque<Frame>().apply { repeat(ringFrames) { add(Frame(ByteArray(MIC_FRAME_BYTES))) } } // guarded by lock
    private var reader: Job? = null // guarded by lock; non-null while the recorder is running
    private val stopping = mutableListOf<Job>() // guarded by lock: cancelled readers that may still be inside a read
    private var closeJob: Job? = null // guarded by lock
    private var suspended = false // guarded by lock: a phone call holds the microphone
    private var exclusiveHolders = 0 // guarded by lock: recorders outside the hub (barge-in) holding the mic

    private var opens = 0 // guarded by lock
    private var framesRead = 0L
    private var framesDelivered = 0L
    private var droppedNoBuffer = 0L
    private var droppedSlow = 0L
    private var consumerErrors = 0L

    /** Takes the microphone for [purpose]. [onFrame] runs on [consumerDispatcher]; [onError] when the microphone cannot be used. */
    fun lease(purpose: MicPurpose, onFrame: (ByteArray) -> Unit, onError: (String) -> Unit = {}): MicLease {
        val lease = LeaseImpl(purpose, onFrame, onError)
        lease.consumer = scope.launch(consumerDispatcher) {
            for (frame in lease.channel) {
                try {
                    lease.onFrame(frame.bytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    synchronized(lock) { consumerErrors++ }
                } finally {
                    releaseFrame(frame)
                }
            }
        }
        val count = synchronized(lock) {
            leases += lease
            closeJob?.cancel()
            closeJob = null
            startRecorderLocked()
            leases.size
        }
        log("mic: lease ${purpose.name} (holders now $count)")
        return lease
    }

    /**
     * Closes the recorder right now when nobody holds a lease. For a consumer whose hand-over must not
     * overlap another AudioRecord (a speech recognizer that opens its own): no idle grace.
     */
    fun closeIfIdle() {
        synchronized(lock) {
            if (leases.isEmpty()) {
                closeJob?.cancel()
                closeJob = null
                closeRecorderLocked()
            }
        }
    }

    /**
     * Hands the microphone to a recorder outside the hub (barge-in with its own call-style recorder and
     * echo canceller). The hub's recorder is closed and this returns only once it has really stopped,
     * so the two never record at the same time. Until [MicExclusive.release] no lease opens a recorder;
     * leases keep their place and get frames again afterwards. Cancelling the caller while it waits
     * gives the microphone back.
     */
    suspend fun takeExclusive(waitMs: Long = MIC_EXCLUSIVE_WAIT_MS): MicExclusive {
        val running = synchronized(lock) {
            exclusiveHolders++
            closeJob?.cancel()
            closeJob = null
            closeRecorderLocked()
            stopping.filter { !it.isCompleted } // a cancelled reader is not "active" but may still be reading
        }
        val hold = ExclusiveImpl()
        try {
            if (running.isNotEmpty() && withTimeoutOrNull(waitMs) { running.forEach { it.join() } } == null) {
                log("mic: shared recorder did not stop within $waitMs ms")
            }
        } catch (e: CancellationException) {
            hold.release()
            throw e
        }
        log("mic: exclusive on (shared recorder closed)")
        return hold
    }

    private inner class ExclusiveImpl : MicExclusive {
        private val released = AtomicBoolean(false)

        override fun release() {
            if (!released.compareAndSet(false, true)) return
            synchronized(lock) {
                exclusiveHolders--
                startRecorderLocked()
            }
            log("mic: exclusive off")
        }
    }

    /** A phone call is using the microphone: stop reading now. Leases stay, they just get no frames. */
    fun pauseForCall() {
        synchronized(lock) {
            suspended = true
            closeJob?.cancel()
            closeJob = null
            closeRecorderLocked()
        }
        log("mic: paused for call")
    }

    /** The call ended: reopen the recorder if anyone still holds a lease. */
    fun resumeAfterCall() {
        synchronized(lock) {
            suspended = false
            startRecorderLocked()
        }
        log("mic: resumed after call")
    }

    fun stats(): MicHubStats = synchronized(lock) {
        MicHubStats(
            opens = opens,
            framesRead = framesRead,
            framesDelivered = framesDelivered,
            droppedNoBuffer = droppedNoBuffer,
            droppedSlow = droppedSlow,
            consumerErrors = consumerErrors,
            leases = leases.size,
            recording = reader != null,
            exclusive = exclusiveHolders > 0,
        )
    }

    private fun releaseLease(lease: LeaseImpl) {
        synchronized(lock) {
            if (lease.released) return
            lease.released = true
            leases.remove(lease)
            if (leases.isEmpty()) scheduleCloseLocked()
        }
        lease.channel.close()
        lease.consumer?.cancel()
        // Frames still queued for this lease go back to the pool; the consumer will not see them.
        while (true) {
            val frame = lease.channel.tryReceive().getOrNull() ?: break
            releaseFrame(frame)
        }
    }

    /** Sends a frame to the active purpose only. Caller holds [lock]. */
    private fun dispatchLocked(frame: Frame) {
        val top = leases.maxOfOrNull { it.purpose.rank }
        val targets = if (top == null) emptyList() else leases.filter { it.purpose.rank == top }
        if (targets.isEmpty()) {
            releaseFrameLocked(frame)
            return
        }
        frame.refs.set(targets.size)
        for (target in targets) {
            if (target.channel.trySend(frame).isSuccess) {
                framesDelivered++
            } else {
                droppedSlow++
                releaseFrameLocked(frame)
            }
        }
    }

    private fun releaseFrame(frame: Frame) = synchronized(lock) { releaseFrameLocked(frame) }

    /** Caller holds [lock]. The buffer returns to the pool after its last consumer. */
    private fun releaseFrameLocked(frame: Frame) {
        if (frame.refs.decrementAndGet() <= 0) {
            frame.refs.set(0)
            pool.addLast(frame)
        }
    }

    /** Starts the reader when someone wants the microphone and neither a call nor barge-in holds it. Caller holds [lock]. */
    private fun startRecorderLocked() {
        if (reader != null || suspended || exclusiveHolders > 0 || leases.isEmpty()) return
        // A cancelled reader can still be inside a blocking read (up to a frame): the new recorder opens
        // only after every earlier one has closed, so two never record at the same time.
        stopping.retainAll { !it.isCompleted }
        val earlier = stopping.toList()
        val job = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
            earlier.forEach { it.join() }
            record()
        }
        reader = job
        job.start()
    }

    private fun closeRecorderLocked() {
        reader?.let {
            it.cancel()
            stopping += it
        }
        reader = null
    }

    private fun scheduleCloseLocked() {
        closeJob?.cancel()
        closeJob = scope.launch {
            delay(idleGraceMs)
            synchronized(lock) {
                if (leases.isEmpty()) {
                    closeRecorderLocked()
                    log("mic: closed after idle grace")
                }
                closeJob = null
            }
        }
    }

    private suspend fun record() {
        val me = currentCoroutineContext()[Job]
        val src = try {
            sourceFactory()
        } catch (e: Exception) {
            recorderFailed("microphone unavailable", me)
            return
        }
        if (!src.open()) {
            src.close()
            recorderFailed("microphone unavailable", me)
            return
        }
        synchronized(lock) { opens++ }
        log("mic: open")
        var stoppedBy: String? = null
        val scratch = ByteArray(MIC_FRAME_BYTES)
        try {
            recordLoop@ while (true) {
                // AudioRecord.read blocks without suspending, so cancellation is never noticed by itself:
                // without these checks every closed recorder kept reading, and on the phone each turn
                // added one more stream (45, 97, 149 … 1000 frames/s) mixed into the next one.
                currentCoroutineContext().ensureActive()
                var got = 0
                while (got < MIC_FRAME_BYTES) {
                    val n = src.read(scratch, got, MIC_FRAME_BYTES - got)
                    currentCoroutineContext().ensureActive()
                    if (n <= 0) {
                        stoppedBy = "microphone read failed"
                        break@recordLoop
                    }
                    got += n
                }
                synchronized(lock) {
                    // Only the current reader may hand out frames, never one that was replaced.
                    if (reader !== me) return
                    framesRead++
                    val frame = pool.removeLastOrNull()
                    if (frame == null) {
                        droppedNoBuffer++
                    } else {
                        scratch.copyInto(frame.bytes)
                        dispatchLocked(frame)
                    }
                }
            }
        } finally {
            src.close()
        }
        stoppedBy?.let { recorderFailed(it, me) }
    }

    /** The recorder could not run: tell every holder once, and let the next lease try again. */
    private fun recorderFailed(reason: String, me: Job?) {
        val victims = synchronized(lock) {
            // A reader that was already replaced must not take the new one down with it.
            if (reader !== me) return
            reader = null
            leases.toList()
        }
        log("mic: $reason")
        for (victim in victims) victim.onError(reason)
    }
}
