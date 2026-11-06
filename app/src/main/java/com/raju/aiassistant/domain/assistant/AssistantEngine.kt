package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.tools.PhotoResult
import com.raju.aiassistant.domain.notify.callCommand
import com.raju.aiassistant.domain.notify.CallCommand
import com.raju.aiassistant.domain.notify.readReply
import com.raju.aiassistant.data.search.WebSearch
import com.raju.aiassistant.domain.ai.AiProvider
import com.raju.aiassistant.domain.ai.Turn
import com.raju.aiassistant.domain.ai.TurnRole
import com.raju.aiassistant.domain.knowledge.KnowledgeSearch
import com.raju.aiassistant.domain.knowledge.NoKnowledge
import com.raju.aiassistant.domain.language.Detection
import com.raju.aiassistant.domain.language.LANGUAGE_AUTO
import com.raju.aiassistant.domain.language.SeemaLanguage
import com.raju.aiassistant.domain.language.detectScript
import com.raju.aiassistant.domain.language.nextConversationLanguage
import com.raju.aiassistant.domain.language.phrase
import kotlinx.coroutines.flow.first
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.ai.ModelAdvice
import com.raju.aiassistant.domain.ai.pickDefaultModel
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.domain.model.ChatMessage
import com.raju.aiassistant.domain.model.ChatRole
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.model.VoiceMode
import com.raju.aiassistant.domain.tools.Contact
import com.raju.aiassistant.domain.tools.ContactLookup
import com.raju.aiassistant.domain.tools.DeviceTools
import com.raju.aiassistant.domain.tools.ToolOutcome
import com.raju.aiassistant.domain.tools.distinctContacts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.raju.aiassistant.domain.voice.afterWakeWord
import com.raju.aiassistant.domain.voice.isStopCommand
import com.raju.aiassistant.domain.voice.stripEcho
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.raju.aiassistant.domain.business.BusinessStore
import com.raju.aiassistant.domain.business.NoBusinessStore
import com.raju.aiassistant.domain.business.Period
import com.raju.aiassistant.domain.business.businessContext
import com.raju.aiassistant.domain.business.describeJourney
import com.raju.aiassistant.domain.business.formatRupees
import com.raju.aiassistant.domain.business.periodRange
import com.raju.aiassistant.domain.business.summarizeExpenses
import com.raju.aiassistant.domain.business.summarizeJourneys
import com.raju.aiassistant.domain.booking.Booking
import com.raju.aiassistant.domain.booking.BookingStatus
import com.raju.aiassistant.domain.booking.BookingStore
import com.raju.aiassistant.domain.booking.BOOKING_FOLDER
import com.raju.aiassistant.domain.booking.EditResult
import com.raju.aiassistant.domain.booking.NoBookingStore
import com.raju.aiassistant.domain.booking.applyEdit
import com.raju.aiassistant.domain.booking.bookingContext
import com.raju.aiassistant.domain.booking.daySlipFileName
import com.raju.aiassistant.domain.booking.formatSlip
import com.raju.aiassistant.domain.booking.lastTouched
import com.raju.aiassistant.domain.booking.summarizeBookings
import com.raju.aiassistant.domain.model.Personality

/** A question the user must answer before the assistant continues. */
data class PendingRequest(
    val kind: Kind,
    val title: String,
    val message: String,
    val options: List<Contact> = emptyList(),
    val permission: String? = null,
) {
    enum class Kind { PERMISSION, CONFIRM }
}

private const val DAY_MS = 24 * 60 * 60_000L
/** Official Indian Railways PNR enquiry (needs a captcha, so the user finishes it there). */
private const val PNR_ENQUIRY_URL = "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html?locale=en"
private val DEVANAGARI = Regex("[\\u0900-\\u097F]")
private val BENGALI_SCRIPT = Regex("[\\u0980-\\u09FF]")
/** "No time recorded yet" (0 is a valid clock reading in tests). */
private const val NOT_YET = -1L
/** The longest Seema waits for an AI reply before showing a timeout (she never hangs in "Thinking"). */
private const val AI_TIMEOUT_MS = 45_000L
/** Exchanges of recent conversation sent with each AI request, and their size limit. */
private const val RECENT_EXCHANGES = 6
private const val HISTORY_CHARS_PER_REQUEST = 4_000
/** A streaming AI that has not written a single word by now is given up on when another AI can answer. */
private const val FIRST_TEXT_TIMEOUT_MS = 15_000L
/** Failures where another provider may still answer (not a blocked or empty request). */
private const val TEST_SYSTEM_PROMPT = "You are a connection test. Reply with the single word OK."
private val FALLBACK_KINDS = setOf(FailureKind.RATE_LIMITED, FailureKind.QUOTA_EXCEEDED, FailureKind.TIMEOUT, FailureKind.SERVER, FailureKind.INVALID_KEY, FailureKind.UNKNOWN)
/** Consecutive recogniser errors (not silence) tolerated in hands-free mode before it stops. */
/** How long after a caller announcement "haan / utha lo" or "kaat do" is listened for. */
private const val CALL_COMMAND_WINDOW_MS = 25_000L
/** How long she listens for "haan / rehne do" after "Padh ke sunau kya?". */
private const val READ_REPLY_WINDOW_MS = 5_000L
/** "This model is no longer available" and similar replies: the model was retired, not a passing error. */
internal fun isModelGone(message: String): Boolean {
    val m = message.lowercase()
    return "no longer available" in m || "is not found for api version" in m || "not supported for generatecontent" in m ||
        ("http 404" in m && "model" in m)
}

/**
 * How long a provider rests after a failure before it is asked again. A busy server (503) or a slow
 * answer is over in a minute; a used-up limit or a bad key is not.
 */
internal fun cooldownMsFor(kind: FailureKind): Long = when (kind) {
    FailureKind.RATE_LIMITED, FailureKind.QUOTA_EXCEEDED, FailureKind.INVALID_KEY -> 15 * 60_000L
    else -> 60_000L
}

private const val MAX_LISTEN_FAILURES = 3
/** Chat messages kept in memory (older ones stay in the database). */
private const val MAX_TRANSCRIPT_IN_MEMORY = 300
/** A voice message ending in one of these was cut off by a pause: wait for the rest. */
private val CONTINUATION = Regex("(^|\\s)(aur|lekin|par|ki|ke|to|toh|matlab|jaise|and|but|or|so|because|और|लेकिन|पर|कि|के|तो|मतलब|जैसे|क्योंकि)[\\s,.…]*$", RegexOption.IGNORE_CASE)
/** How long to wait for the rest of a sentence that ended in "aur…" / "lekin…". */
private const val CONTINUATION_WAIT_MS = 6_000L

sealed interface Decision {
    /** Approve; [option] is the chosen contact when [PendingRequest.options] is not empty. */
    data class Approve(val option: Contact? = null) : Decision
    data object Deny : Decision
    data class PermissionResult(val granted: Boolean) : Decision
}

/**
 * Orchestrates: user text/voice → AI provider → reply (+ speech) → requested tool actions
 * (with confirmation and permissions) → honest result reports. Single source of truth for the
 * assistant's visible state and the chat transcript.
 */
class AssistantEngine(
    private val scope: CoroutineScope,
    private val settings: StateFlow<UserSettings>,
    private val secret: suspend (SecretId) -> String?,
    private val providerFor: (ProviderId, String) -> AiProvider,
    private val searchFor: (String) -> WebSearch,
    private val history: ChatHistory,
    private val memories: MemoryStore,
    private val actionLog: ActionLog,
    private val tools: DeviceTools,
    private val speaker: Speaker,
    private val voice: VoiceInput,
    private val isOnline: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val nowDescription: () -> String = { java.util.Date(clock()).toString() },
    private val bargeIn: BargeIn = NoBargeIn,
    private val transcriber: Transcriber? = null,
    private val conversationMode: ConversationMode = NoConversationMode,
    private val wakeListener: WakeListener = NoWakeListener,
    private val hourOfDay: () -> Int = { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) },
    private val business: BusinessStore = NoBusinessStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Privacy-safe diagnostics (stages, error codes, lengths — never what the user said). */
    private val log: (String) -> Unit = {},
    /** Hindi or Bengali, shared with speech output so the voice speaks the conversation's language. */
    private val conversationLanguage: MutableStateFlow<SeemaLanguage> = MutableStateFlow(SeemaLanguage.HINDI),
    /** The user's imported documents (RAG); empty when none were imported. */
    private val knowledge: KnowledgeSearch = NoKnowledge,
    /** Other AI providers to try when the chosen one fails (auto fallback). */
    private val fallbackProviders: () -> List<ProviderId> = { emptyList() },
    private val latestNotification: () -> String? = { null },
    /** Called when she really wakes up (her name was recognised): the app turns the screen on. */
    private val onWake: () -> Unit = {},
    /** Answers a question about a photo (Gemini); used for "dekho kya dikh raha hai". */
    private val describeImage: suspend (jpeg: ByteArray, question: String) -> AiResult<String> =
        { _, _ -> AiResult.Failure(FailureKind.NOT_CONFIGURED, "Image understanding is not available.") },
    /** A saved model was retired by the provider and replaced by a working one: remember it in Settings. */
    private val onModelReplaced: (ProviderId, String) -> Unit = { _, _ -> },
    /**
     * Headphones (wired or Bluetooth) are connected. Talking over her (barge-in) is only used then:
     * on the loudspeaker her own voice reached the microphone and cut her replies after a word.
     */
    private val headphones: () -> Boolean = { true },
    /** Train booking requests and their slips (see domain/booking). */
    private val bookings: BookingStore = NoBookingStore,
    /** "टपोरी मोड ऑन करो": saves the new personality in Settings. */
    private val onPersonality: (Personality) -> Unit = {},
) {
    /** The language Seema is speaking now (Settings choice, or detected from the user in Auto). */
    val language: StateFlow<SeemaLanguage> = conversationLanguage.asStateFlow()
    private fun lang(): SeemaLanguage = SeemaLanguage.fromTag(settings.value.speechLanguage) ?: conversationLanguage.value
    /** The last message mixed Hindi and Bengali evenly: ask which language instead of guessing. */
    private var languageUnclear = false

    private val _state = MutableStateFlow(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    private val _pending = MutableStateFlow<PendingRequest?>(null)
    val pending: StateFlow<PendingRequest?> = _pending.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _voiceSession = MutableStateFlow(false)
    /** True while a hands-free voice session is running. */
    val voiceSession: StateFlow<Boolean> = _voiceSession.asStateFlow()

    val speechLevel: StateFlow<Float> get() = speaker.level
    val listening: StateFlow<Boolean> get() = voice.listening
    val partialTranscript: StateFlow<String> get() = voice.partial

    private var job: Job? = null
    private val submitGuard = SubmitGuard()
    private var lastUserText: String? = null
    private val warnedModels = mutableSetOf<String>()
    private var decision: CompletableDeferred<Decision>? = null
    private var localId = -1L
    private var lastVoiceNote: String? = null

    /** Hands-free mode: keep listening (also after silence) until stop() is called. */
    private var handsFree = false
    private var listenFailures = 0
    private val _handsFreeActive = MutableStateFlow(false)
    /** True while the assistant is in hands-free (always listening) mode. */
    val handsFreeActive: StateFlow<Boolean> = _handsFreeActive.asStateFlow()

    private val _heard = MutableStateFlow<String?>(null)
    /** The last thing speech recognition really understood ("Recognized" stage); null while listening. */
    val heard: StateFlow<String?> = _heard.asStateFlow()

    /** Cooldown timestamps for providers experiencing quota or rate-limit issues, enabling instantaneous fallback. */
    private val providerCooldown = java.util.concurrent.ConcurrentHashMap<ProviderId, Long>()

    private val _hint = MutableStateFlow<String?>(null)
    /** A short status for the user that is not an error (e.g. "nothing was heard"); cleared on the next step. */
    val hint: StateFlow<String?> = _hint.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
    /** Last voice-pipeline events, for Tools → Voice diagnostics. No user words or replies are kept. */
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    private fun trace(event: String) {
        log(event)
        val t = clock()
        val stamp = "%02d:%02d:%02d".format((t / 3_600_000) % 24, (t / 60_000) % 60, (t / 1000) % 60)
        _diagnostics.value = (_diagnostics.value + "$stamp $event").takeLast(40)
    }

    /** Each listen() gets a number; a delayed re-listen only runs if no newer listen/stop happened. */
    private var listenGen = 0
    /** Default model found per provider and key (when none is chosen in Settings). */
    private val discoveredModels = mutableMapOf<Pair<ProviderId, Int>, String>()
    /** True while the current turn came from the user's voice (errors are then also spoken). */
    private var voiceTurn = false

    /** Sleep mode (hands-free only): the recogniser is off and a light on-phone detector waits for her name. */
    private var asleep = false
    /** Last time the user spoke or Seema finished a turn; the sleep timer counts from here. */
    private var lastActivityAt = 0L
    private var lastSpokeAt = 0L
    private var lastWakeAttemptAt = 0L
    /** Low-confidence recognitions in a row; she asks to repeat only for the first. */
    private var unclearInARow = 0
    /** First half of a sentence the user paused in ("… aur"), waiting for the rest. */
    private var heldVoiceText: String? = null
    private var heldJob: Job? = null
    /** The model's reply text for this turn was spoken (so a search needs no "one second…"). */
    private var replySpoken = false

    /** Loads saved chat history into the transcript (call once at startup). */
    suspend fun load() {
        if (!settings.value.saveHistory) return
        runCatching { history.recent(100) }.onSuccess { saved ->
            _transcript.value = saved + _transcript.value
        }
    }

    // ---------------------------------------------------------------- input

    fun submit(text: String, fromVoice: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // One user submission → one request: ignore an identical message sent again within a moment.
        if (!submitGuard.accept(trimmed, clock())) return
        if (!fromVoice) voice.stop() // typed while listening: don't also take a voice result
        if (fromVoice) { listenFailures = 0; unclearInARow = 0 }
        leaveSleep()
        lastActivityAt = clock()
        cancelWork()
        lastUserText = trimmed
        conversationLanguage.value = nextConversationLanguage(settings.value.speechLanguage, conversationLanguage.value, trimmed)
        languageUnclear = settings.value.speechLanguage == LANGUAGE_AUTO && detectScript(trimmed) == Detection.MIXED
        trace("submit ${if (fromVoice) "voice" else "typed"} (${trimmed.length} chars)")
        job = scope.launch { handleUserMessage(trimmed, fromVoice) }
    }

    /**
     * A voice result. People pause mid-sentence ("Mummy ko call karo aur…"): when the words end in a
     * joining word, the assistant keeps listening a few seconds and answers the whole sentence.
     */
    private fun onHeard(heard: String) {
        trace("recognized (${heard.length} chars)")
        if (handleReadReply(heard)) return
        if (handleCallCommand(heard)) return
        val text = heldVoiceText?.let { "$it $heard" } ?: heard
        _heard.value = text
        heldVoiceText = null
        heldJob?.cancel()
        if (CONTINUATION.containsMatchIn(text.trim()) && text.trim().split(Regex("\\s+")).size >= 2) {
            heldVoiceText = text
            lastActivityAt = clock()
            heldJob = scope.launch {
                delay(CONTINUATION_WAIT_MS)
                // Nothing more came: answer what was said.
                heldVoiceText?.let { heldVoiceText = null; submit(it, fromVoice = true) }
            }
            listen()
            return
        }
        val requestAfterWake = if (settings.value.wakeWord) afterWakeWord(text) else null
        if (requestAfterWake != null) {
            if (requestAfterWake.isBlank()) {
                // The user only called her name ("हे सीमा" / "सीमा" / "Hey Seema") to wake/greet her:
                // Instantly reply "हाँ जी, बोलिए?" (or Bengali "হ্যাঁ, বলুন?") and keep listening!
                scope.launch {
                    say(phrase(lang(), "हाँ जी, बोलिए?", "হ্যাঁ, বলুন?"), allowBargeIn = false)
                    _voiceSession.value = true
                    listen()
                }
                return
            } else {
                // The user said "हे सीमा, [कमांड]" -> run the clean command without wake prefix
                submit(requestAfterWake, fromVoice = true)
                return
            }
        }
        submit(text, fromVoice = true)
    }

    /** True when the last request failed and can be sent again. */
    val canRetry: Boolean get() = lastUserText != null && _lastError.value != null

    /** Re-sends the last message after a failure, without adding it to the chat again. */
    fun retry() {
        val text = lastUserText ?: return
        cancelWork()
        job = scope.launch { handleUserMessage(text, fromVoice = false, recordUser = false) }
    }

    /** Starts listening (tap-to-talk). In continuous mode, listening resumes after each reply. */
    fun startVoice() {
        if (!voice.isAvailable()) {
            failAsync("Speech recognition is not available on this phone. Install/enable Google speech services.")
            return
        }
        cancelWork()
        speaker.stop()
        leaveSleep()
        lastActivityAt = clock()
        _voiceSession.value = true
        listen()
    }

    /**
     * Hands-free mode: listen now and keep listening after every reply and after silence, until
     * [stop] is pressed (or the app goes to the background → [suspendListening]).
     */
    fun startHandsFree() {
        handsFree = true
        _handsFreeActive.value = true
        listenFailures = 0
        // Opening the app always finds her awake; the sleep timer starts again.
        lastActivityAt = clock()
        if (asleep) {
            leaveSleep()
            _state.value = AssistantState.Idle
        }
        if (settings.value.assistantPaused) return
        trace("hands-free on")
        // Call-style audio turns on the phone's echo canceller, so the user can talk over Seema —
        // but only when this app records the microphone itself (Deepgram). Android silences other
        // apps' microphones during call-style audio, so the Google recogniser would hear nothing.
        if (callStyleAllowed()) conversationMode.enter()
        val busy = _state.value == AssistantState.Thinking || _state.value == AssistantState.Speaking || _state.value == AssistantState.Executing
        if (!_voiceSession.value && !busy && _pending.value == null) startVoice()
    }

    /** Standby wake-word listening: listens silently for her name ("Seema" / "सीमा") while app is open. */
    fun startStandby() {
        if (settings.value.assistantPaused) return
        if (!asleep && _state.value == AssistantState.Idle) {
            goToSleep()
        }
    }

    private fun returnToStandby() {
        _voiceSession.value = false
        if (settings.value.wakeWord && !settings.value.assistantPaused && settings.value.voiceMode != VoiceMode.SESSION && wakeListener !== NoWakeListener) {
            goToSleep()
        } else {
            _state.value = AssistantState.Idle
        }
    }

    /** App went to the background: keep wake detector alive if wakeWord is on (supported by SeemaWakeService). */
    fun suspendListening() {
        conversationMode.exit()
        heldJob?.cancel()
        heldVoiceText = null
        if (settings.value.wakeWord && !settings.value.assistantPaused && wakeListener !== NoWakeListener) {
            // SeemaWakeService runs as a foreground service with microphone type, allowing continuous background wake word
            if (!asleep) {
                goToSleep()
            }
        } else {
            if (asleep) {
                leaveSleep()
            }
            _voiceSession.value = false
            listenGen++
            voice.stop()
            _state.value = AssistantState.Idle
        }
    }

    /**
     * Call-style audio only serves barge-in, which needs headphones: on the loudspeaker a Samsung
     * phone played her voice from the earpiece in call-style audio, like a normal phone call.
     */
    private fun callStyleAllowed() = settings.value.bargeIn && voice.capturesInApp() && headphones()

    private fun listen() {
        val gen = ++listenGen
        _state.value = AssistantState.Listening
        _heard.value = null
        _hint.value = null
        // The system recogniser (another app) gets only silence while call-style audio is on, and
        // without headphones call-style audio would play her voice from the earpiece.
        if (!callStyleAllowed()) conversationMode.exit()
        trace("listen ${if (handsFree) "hands-free" else "tap"} ${if (voice.capturesInApp()) "in-app" else "system"} recogniser")
        voice.start(
            lang().tag,
            onResult = { heard -> if (gen == listenGen) onHeard(heard) else trace("stale result dropped") },
            onError = { err ->
                if (gen != listenGen) { trace("stale error dropped"); return@start }
                trace("listen error: ${err.message}")
                // A short yes/no window (message or call) ends quietly on silence: no loop.
                if (replyHandler != null) { finishReadReply(null); return@start }
                if (clock() < callCommandUntil) {
                    callCommandUntil = 0
                    trace("call: no answer heard, stop listening")
                    returnToStandby()
                    return@start
                }
                val keepGoing = handsFree && _voiceSession.value && !settings.value.assistantPaused && !err.permissionDenied
                val held = heldVoiceText
                if (held != null && (err.noSpeech || err.unclear)) {
                    // The pause after "… aur" was the end after all: answer what was said.
                    heldVoiceText = null
                    heldJob?.cancel()
                    submit(held, fromVoice = true)
                } else if (err.unclear && _voiceSession.value && unclearInARow == 0) {
                    // Silent visual hint instead of unprompted speech loop in a quiet room
                    unclearInARow++
                    _hint.value = phrase(lang(), "साफ़ सुनाई नहीं दिया", "স্পষ্ট শোনা যায়নি")
                    if (keepGoing) relisten(500) else returnToStandby()
                } else if (keepGoing && (err.noSpeech || err.unclear)) {
                    // Silence is normal in hands-free mode: listen again, or doze off after a quiet while.
                    if (sleepDue()) {
                        // Always-listening sleeps and waits for her name; a session simply ends (mic off).
                        if (settings.value.voiceMode == VoiceMode.ALWAYS) goToSleep() else endSession()
                    } else relisten(300)
                } else if (err.transient && _voiceSession.value && listenFailures < MAX_LISTEN_FAILURES) {
                    // Recogniser busy/restarting: harmless, try again shortly (also in tap-to-talk).
                    listenFailures++
                    relisten(700)
                } else if (keepGoing && listenFailures < MAX_LISTEN_FAILURES) {
                    listenFailures++
                    relisten(2_000)
                } else {
                    _voiceSession.value = false
                    if (!err.noSpeech) {
                        // A real problem (no permission, recogniser keeps failing): leave hands-free mode.
                        handsFree = false
                        _handsFreeActive.value = false
                    }
                    if (err.noSpeech) {
                        // Tap-to-talk heard nothing: say so on screen instead of silently going idle.
                        _hint.value = phrase(lang(), "कुछ सुनाई नहीं दिया — Mic दबाकर फिर बोलिए।", "কিছু শুনতে পাইনি — Mic চেপে আবার বলুন।")
                        returnToStandby()
                    } else if (err.permissionDenied) permissionMissing(err.message) else failAsync(err.message)
                }
            },
        )
    }

    /** Listens again after [delayMs], unless something newer (a new listen, Stop, a reply) happened meanwhile. */
    private fun relisten(delayMs: Long) {
        val gen = listenGen
        scope.launch {
            delay(delayMs)
            if (gen == listenGen && _voiceSession.value && _state.value == AssistantState.Listening) listen()
        }
    }

    /** Stop button: immediately stops listening, speech, pending questions and any running request. */
    fun stop() {
        handsFree = false
        _handsFreeActive.value = false
        leaveSleep()
        heldJob?.cancel()
        heldVoiceText = null
        conversationMode.exit()
        _voiceSession.value = false
        listenGen++
        voice.stop()
        speaker.stop()
        cancelWork()
        _heard.value = null
        trace("stop")
        _state.value = AssistantState.Idle
    }

    /** A conversation session timed out: microphone off, nothing keeps running. */
    private fun endSession() {
        trace("session ended after silence")
        handsFree = false
        _handsFreeActive.value = false
        listenGen++
        voice.stop()
        conversationMode.exit()
        _voiceSession.value = false
        _hint.value = phrase(lang(), "बातचीत बंद — फिर बात करने के लिए Mic दबाइए।", "কথা শেষ — আবার কথা বলতে Mic চাপুন।")
        _state.value = AssistantState.Idle
    }

    // ---------------------------------------------------------------- sleep mode

    private fun sleepDue(): Boolean {
        val after = settings.value.sleepAfterSeconds
        return handsFree && after > 0 && _pending.value == null && clock() - lastActivityAt >= after * 1_000L
    }

    /** Quiet for a while: release the recogniser (no beeps, no data) and wait on the phone for her name. */
    private fun goToSleep() {
        voice.stop()
        _voiceSession.value = false
        conversationMode.exit()
        asleep = true
        _state.value = AssistantState.Sleeping
        armWakeListener()
    }

    private fun armWakeListener() {
        if (!asleep) return
        wakeListener.start(
            onUtterance = { pcm -> scope.launch { onWakeAudio(pcm) } },
            onError = { scope.launch { delay(3_000); armWakeListener() } },
        )
    }

    private suspend fun onWakeAudio(pcm: ByteArray) {
        if (!asleep) return
        if (wakeListener.spotsWakeWord) {
            // The offline detector heard "Hey Seema" on the phone itself: wake and listen.
            trace("wake word (offline)")
            onWake()
            wakeUp(greet = true)
            return
        }
        val now = clock()
        // Her own voice (still speaking, or its tail) must not wake her: the phone once sent her
        // "Contacts में … नहीं मिला" sentence back to the recogniser as a wake request.
        if (speaker.playing.value || now - lastSpokeAt < 4_000L) {
            armWakeListener()
            return
        }
        trace("wake audio: ${pcm.size} bytes")
        val t = transcriber
        if (t != null && t.isAvailable()) {
            val heard = runCatching { t.transcribe(pcm) }.getOrNull()?.trim().orEmpty()
            trace("wake heard: $heard")
            if (!asleep) return
            val request = if (settings.value.wakeWord) afterWakeWord(heard) else heard.takeIf { it.length >= 2 }
            trace("wake request: $request")
            when {
                request == null -> armWakeListener() // someone else talking, TV, noise: keep sleeping
                request.isBlank() -> { onWake(); wakeUp(greet = true) } // "Seema?" → "Haan ji, boliye?"
                else -> {
                    onWake()
                    // "Seema, kal ka mausam batao": wake and answer straight away.
                    leaveSleep()
                    _voiceSession.value = true
                    if (callStyleAllowed()) conversationMode.enter()
                    submit(request, fromVoice = true)
                }
            }
        } else if (settings.value.wakeWord) {
            // "Wake only by name" is on, but her name cannot be checked (no offline "Hey Seema" model,
            // no Deepgram key). A random sound — TV, talk, or her own voice — must not wake her: that
            // made her repeat "हाँ जी, बोलिए?" in a loop. She keeps sleeping; a tap on the reactor wakes her.
            trace("wake: name cannot be checked, staying asleep")
            _hint.value = phrase(
                lang(),
                "\"Hey Seema\" से जगाने के लिए Settings → Voice & Speech → \"Download\" (offline wake word) दबाइए, या रिएक्टर पर टैप कीजिए।",
                "\"Hey Seema\" দিয়ে জাগাতে Settings → Voice & Speech → Download চাপুন, বা রিঅ্যাক্টরে ট্যাপ করুন।",
            )
            armWakeListener()
        } else {
            // "Wake only by name" is off: any voice wakes her (her own voice is ignored above).
            trace("wake: transcriber unavailable, fallback")
            if (now - lastWakeAttemptAt < 3_000L) {
                armWakeListener()
                return
            }
            lastWakeAttemptAt = now
            wakeUp(greet = true)
        }
    }

    /** Wakes her from sleep mode (her name, or a tap on the reactor) and listens. */
    fun wakeUp(greet: Boolean = true) {
        if (!asleep) return
        leaveSleep()
        lastActivityAt = clock()
        unclearInARow = 0
        _voiceSession.value = true
        if (callStyleAllowed()) conversationMode.enter()
        if (!greet) { listen(); return }
        cancelWork()
        job = scope.launch {
            say(phrase(lang(), "हाँ जी, बोलिए?", "হ্যাঁ, বলুন?"), allowBargeIn = false)
            if (_voiceSession.value) listen()
        }
    }

    private fun leaveSleep() {
        if (!asleep) return
        asleep = false
        wakeListener.stop()
    }

    fun resolve(d: Decision) {
        decision?.complete(d)
    }

    fun clearTranscript() {
        _transcript.value = emptyList()
    }

    // ---------------------------------------------------------------- core flow

    private suspend fun handleUserMessage(text: String, fromVoice: Boolean, recordUser: Boolean = true) {
        turnStartedAt = clock()
        _lastError.value = null
        voiceTurn = fromVoice
        if (recordUser) record(ChatRole.USER, text)
        val s = settings.value

        // Simple phone commands ("torch on karo", "Mummy ko call karo", "5 minute ka timer") run on
        // the phone at once: no AI request, no tokens, and they also work without internet.
        LocalCommands.personalitySwitch(text, s.personality)?.let { p ->
            if (runLocalCommand(AssistantAction.SetPersonality(p), speak = fromVoice || s.autoSpeakReplies)) return
        }
        LocalCommands.parse(text)?.let { action ->
            if (runLocalCommand(action, speak = fromVoice || s.autoSpeakReplies)) return
        }
        // "Rizwan Travels को WhatsApp करो कि …" / "किसी को WhatsApp करना है": handled on the phone, with
        // the name matched against Contacts however it was spelled, and sent only after "हाँ".
        WhatsAppRequest.parse(text)?.let { request ->
            trace("offline command: whatsapp")
            whatsAppFlow(request, byVoice = fromVoice, speak = fromVoice || s.autoSpeakReplies)
            if (_state.value != AssistantState.Error) {
                _state.value = AssistantState.Success
                delay(800)
            }
            finishTurn()
            return
        }

        if (!isOnline() && s.defaultProvider != ProviderId.OLLAMA) {
            fail(
                "No internet connection. AI replies need internet; torch, volume, timer, alarm, calls, photos, battery and opening apps still work.",
                AssistantState.Offline,
                spoken = offlineSpoken(),
            )
            return
        }
        var providerId = s.defaultProvider
        val cooldownUntil = providerCooldown[providerId] ?: 0L
        var key = keyFor(providerId)
        val primaryBroken = cooldownUntil > clock() || key.isNullOrBlank() || (providerId == ProviderId.OLLAMA && cooldownUntil > 0L)
        if (primaryBroken) {
            val alternative = fallbackProviders().firstOrNull {
                it != providerId && (providerCooldown[it] ?: 0L) <= clock() && !keyFor(it).isNullOrBlank()
            }
            if (alternative != null) {
                trace("fast-routing away from ${providerId.shortName} to ${alternative.shortName}")
                providerId = alternative
                key = keyFor(providerId)
            }
        }
        if (key.isNullOrBlank()) {
            fail("${providerId.displayName} API key is not set. Add it in Settings.")
            return
        }
        val provider = providerFor(providerId, key)
        val model = when (val m = resolveModel(providerId, key, provider)) {
            is AiResult.Success -> m.value
            is AiResult.Failure -> {
                _lastRoute.value = AiRoute(providerId, null, null, clock(), m.message)
                fail(m.message)
                return
            }
        }

        ModelAdvice.warning(model)?.let { w ->
            if (warnedModels.add(model)) note("⚠️ ${providerId.shortName} model \"$model\": $w")
        }
        _state.value = AssistantState.Thinking
        val allMemories = if (s.memoryEnabled) runCatching { memories.all().map { it.text } }.getOrDefault(emptyList()) else emptyList()
        // Smart memory: with many memories, the ones related to this message are used.
        val memoryTexts = relevantMemories(allMemories, text)
        val recentOpenings = _transcript.value.asReversed().asSequence()
            .filter { it.role == ChatRole.ASSISTANT }.take(3).map { openingOf(it.text) }.filter { it.isNotBlank() }.toList()
        // Knowledge Base: passages of the user's documents that match this message (searched on the phone).
        val documents = runCatching { knowledge.search(text) }.getOrDefault(emptyList())
        if (documents.isNotEmpty()) trace("knowledge: ${documents.size} passage(s)")
        val context = PromptContext(
            voiceTurn = fromVoice, recentOpenings = recentOpenings, partOfDay = partOfDay(hourOfDay()), business = listOfNotNull(businessSnapshot(), bookingSnapshot()).joinToString("\n").ifEmpty { null },
            language = lang(), languageUnclear = languageUnclear, documents = documents,
        )
        val system = buildSystemPrompt(memoryTexts, nowDescription(), s.personality, context)
        val turns = historyTurns(model)

        trace("ai request ${providerId.shortName}")
        val speak = fromVoice || s.autoSpeakReplies
        // Streaming: the reply arrives piece by piece; as soon as its first sentence is complete
        // Seema starts speaking it while the rest is still being written.
        val buffer = StringBuilder()
        val firstTextAt = java.util.concurrent.atomic.AtomicLong(NOT_YET) // NOT_YET until the first text arrives
        val headReady = CompletableDeferred<String?>()
        var spokenHead: String? = null
        turnFirstAudioAt = null
        turnActive = true
        val streamed: AiResult<String> = try {
            coroutineScope {
                // A busy provider (e.g. Gemini under high demand) can stay silent for the whole 45 s.
                // When another AI with a saved key could answer, silence for 15 s is enough to switch.
                val now = clock()
                val canFallBack = provider.streams && fallbackProviders().any { it != providerId && (providerCooldown[it] ?: 0L) <= now }
                var silentTooLong = false
                val request = async {
                    val r = coroutineScope {
                        val call = async {
                            withTimeoutOrNull(AI_TIMEOUT_MS) {
                                provider.chatStream(model, system, turns) { delta ->
                                    firstTextAt.compareAndSet(NOT_YET, clock())
                                    val head = synchronized(buffer) {
                                        buffer.append(delta)
                                        if (speak && provider.streams && !headReady.isCompleted) speakableHead(buffer) else null
                                    }
                                    if (head != null) headReady.complete(head)
                                }
                            }
                        }
                        val watchdog = if (canFallBack) launch {
                            delay(FIRST_TEXT_TIMEOUT_MS)
                            if (firstTextAt.get() == NOT_YET) {
                                silentTooLong = true
                                call.cancel()
                            }
                        } else null
                        val result = try {
                            call.await()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            if (!silentTooLong) throw e
                            null
                        }
                        watchdog?.cancel()
                        result
                    }
                    headReady.complete(null)
                    r ?: synchronized(buffer) {
                        // No full reply in time: keep what arrived, otherwise it is a timeout (Seema never hangs in "Thinking").
                        if (buffer.isNotBlank()) AiResult.Success(buffer.toString().trim() + "\n\n(जवाब पूरा नहीं आया — फिर से पूछें।)")
                        else if (silentTooLong) AiResult.Failure(FailureKind.TIMEOUT, "${providerId.shortName} did not start answering within ${FIRST_TEXT_TIMEOUT_MS / 1000} seconds.")
                        else AiResult.Failure(FailureKind.TIMEOUT, "${providerId.shortName} did not answer within ${AI_TIMEOUT_MS / 1000} seconds.")
                    }
                }
                headReady.await()?.let { head ->
                    spokenHead = head
                    trace("speaking early (${head.length} chars)")
                    say(head)
                }
                request.await()
            }
        } finally {
            turnActive = false
        }
        var answeredBy = providerId
        var answeredModel = model
        var raw = when (streamed) {
            is AiResult.Success -> streamed.value
            is AiResult.Failure -> {
                trace("ai failed: ${streamed.kind} (${streamed.message})")
                // The provider retired this model (HTTP 404 "no longer available"): switch to a model it
                // still lists, on the same provider, before trying other providers.
                val replaced = if (spokenHead == null && isModelGone(streamed.message)) replaceGoneModel(providerId, key.orEmpty(), provider, model, system, turns) else null
                if (replaced != null) {
                    note("↪ ${providerId.shortName} ne model \"$model\" band kar diya hai — ab \"${replaced.model}\" use ho raha hai.")
                    answeredModel = replaced.model
                    replaced.text
                } else {
                providerCooldown[providerId] = clock() + cooldownMsFor(streamed.kind)
                recordLatency(providerId, model, firstTextAt.get(), ok = false)
                // Auto fallback: another AI whose key is saved answers instead (said openly in the chat).
                val backup = if (spokenHead == null) fallbackReply(providerId, system, turns) else null
                if (backup == null) {
                    _lastRoute.value = AiRoute(providerId, null, model, clock(), streamed.message)
                    val offline = streamed.kind == FailureKind.NETWORK && !isOnline()
                    fail(
                        streamed.message,
                        if (offline) AssistantState.Offline else AssistantState.Error,
                        spoken = when {
                            offline -> offlineSpoken()
                            streamed.kind == FailureKind.QUOTA_EXCEEDED || streamed.kind == FailureKind.RATE_LIMITED -> phrase(
                                lang(),
                                "अभी सभी AI की सीमा पूरी हो गई है, थोड़ी देर बाद पूछिए। टॉर्च, टाइमर, कॉल और फोटो जैसे फ़ोन के काम अभी भी चलेंगे।",
                                "এখন সব AI-এর সীমা শেষ, একটু পরে জিজ্ঞেস করুন। টর্চ, টাইমার, কল, ছবির মতো ফোনের কাজ এখনও চলবে।",
                            )
                            else -> null
                        },
                    )
                    return
                }
                note("↪ ${providerId.shortName}: ${streamed.message} — answered by ${backup.provider.shortName} instead.")
                answeredBy = backup.provider
                answeredModel = backup.model
                backup.text
                }
            }
        }
        trace("ai reply (${raw.length} chars)")
        var parsedRaw = parseReply(raw)
        if (parsedRaw.text.isBlank() && parsedRaw.actions.isEmpty() && spokenHead == null && answeredBy == providerId) {
            // An empty answer is a failure too: let another AI try before giving up.
            fallbackReply(providerId, system, turns)?.let { b ->
                note("↪ ${providerId.shortName} returned an empty reply — answered by ${b.provider.shortName} instead.")
                answeredBy = b.provider
                answeredModel = b.model
                raw = b.text
                parsedRaw = parseReply(b.text)
            }
        }
        val repetition = collapseRepetition(parsedRaw.text)
        val parsed = parsedRaw.copy(text = repetition.text)
        if (parsed.text.isBlank() && parsed.actions.isEmpty()) {
            recordLatency(providerId, model, firstTextAt.get(), ok = false)
            _lastRoute.value = AiRoute(providerId, null, model, clock(), "empty reply")
            fail("${providerId.shortName} returned an empty reply (model $model). Tap Retry or choose another model.")
            return
        }
        _lastRoute.value = AiRoute(providerId, answeredBy, answeredModel, clock(), null)
        if (parsed.text.isNotBlank()) record(ChatRole.ASSISTANT, parsed.text, via = "${answeredBy.shortName} · $answeredModel")
        if (repetition.degenerate) {
            note("⚠️ The reply from model \"$model\" was repeating itself, so the repeats were removed. A recommended model in Settings usually fixes this.")
        }

        replySpoken = speak && parsed.text.isNotBlank()
        if (replySpoken) {
            turnActive = true
            try {
                val rest = spokenHead?.let { remainderAfter(parsed.text, it) } ?: parsed.text
                if (rest.isNotBlank()) say(rest)
            } finally {
                turnActive = false
            }
        }
        recordLatency(answeredBy, answeredModel, firstTextAt.get(), ok = true)

        var hadActions = false
        for (action in parsed.actions) {
            hadActions = true
            val activeProvider = if (answeredBy == providerId) provider else keyFor(answeredBy)?.let { providerFor(answeredBy, it) } ?: provider
            execute(action, activeProvider, answeredModel, system, speak)
        }

        if (hadActions && _state.value != AssistantState.Error) {
            _state.value = AssistantState.Success
            delay(1_200)
        }
        finishTurn()
    }

    private val _latencies = MutableStateFlow<List<TurnLatency>>(emptyList())
    /** Response times of the last replies (Tools → Response time). */
    val latencies: StateFlow<List<TurnLatency>> = _latencies.asStateFlow()
    private var turnStartedAt = 0L
    @Volatile private var turnFirstAudioAt: Long? = null
    @Volatile private var turnActive = false

    private fun recordLatency(provider: ProviderId, model: String, firstTextAt: Long, ok: Boolean) {
        val now = clock()
        val entry = TurnLatency(
            at = now, provider = provider.shortName, model = model,
            firstTextMs = firstTextAt.takeIf { it != NOT_YET }?.let { it - turnStartedAt },
            firstAudioMs = turnFirstAudioAt?.let { it - turnStartedAt },
            totalMs = now - turnStartedAt, streamed = firstTextAt != NOT_YET, ok = ok,
        )
        _latencies.value = (_latencies.value + entry).takeLast(50)
        trace("latency: text ${entry.firstTextMs ?: "-"} ms, voice ${entry.firstAudioMs ?: "-"} ms, total ${entry.totalMs} ms" + if (ok) "" else " (failed)")
    }

    /**
     * The recent conversation sent with a request: the last few exchanges only. Free plans limit
     * tokens per minute (Groq: 8000), and 20 exchanges of history made every request ~5000 tokens.
     */
    private fun historyTurns(model: String): List<Turn> = toTurns(
        _transcript.value,
        minOf(settings.value.historyLimit, RECENT_EXCHANGES) * 2,
        minOf(ModelAdvice.historyCharBudget(model), HISTORY_CHARS_PER_REQUEST),
    )

    private class FallbackReply(val provider: ProviderId, val model: String, val text: String)

    private suspend fun keyFor(id: ProviderId): String? {
        val raw = secret(id.secret)?.takeIf { it.isNotBlank() }
        return if (id == ProviderId.OLLAMA) raw ?: "http://192.168.31.55:11434" else raw
    }

    /** Tries the other configured providers in turn; returns the first real reply, or null. */
    private suspend fun fallbackReply(failed: ProviderId, system: String, turns: List<Turn>): FallbackReply? {
        val now = clock()
        for (id in fallbackProviders().filter { it != failed && (providerCooldown[it] ?: 0L) <= now }) {
            val key = keyFor(id) ?: continue
            val p = providerFor(id, key)
            val model = (resolveModel(id, key, p) as? AiResult.Success)?.value ?: continue
            trace("ai fallback ${id.shortName}")
            val r = withTimeoutOrNull(AI_TIMEOUT_MS) { p.chat(model, system, turns) } ?: continue
            if (r is AiResult.Success && r.value.isNotBlank()) return FallbackReply(id, model, r.value)
            if (r is AiResult.Failure) {
                providerCooldown[id] = now + cooldownMsFor(r.kind)
            }
        }
        return null
    }

    /** Models a provider said are gone (HTTP 404); never asked for again in this run. */
    private val goneModels = mutableSetOf<Pair<ProviderId, String>>()

    private class Replacement(val model: String, val text: String)

    /** Marks [gone] as retired, picks the best model the provider still lists, and asks it once. */
    private suspend fun replaceGoneModel(id: ProviderId, key: String, provider: AiProvider, gone: String, system: String, turns: List<Turn>): Replacement? {
        goneModels += id to gone
        val next = (resolveModel(id, key, provider) as? AiResult.Success)?.value?.takeIf { it != gone } ?: return null
        trace("model $gone gone, trying $next")
        val r = withTimeoutOrNull(AI_TIMEOUT_MS) { provider.chat(next, system, turns) }
        if (r !is AiResult.Success || r.value.isBlank()) return null
        onModelReplaced(id, next)
        return Replacement(next, r.value)
    }

    /** The chosen model, or the best one the provider lists for this key (fetched once per key, not per request). */
    private suspend fun resolveModel(id: ProviderId, key: String, provider: AiProvider): AiResult<String> {
        settings.value.modelFor(id)?.takeIf { (id to it) !in goneModels }?.let { return AiResult.Success(it) }
        val cacheKey = id to key.hashCode()
        discoveredModels[cacheKey]?.takeIf { (id to it) !in goneModels }?.let { return AiResult.Success(it) }
        return when (val models = provider.listModels()) {
            is AiResult.Success -> pickDefaultModel(id, models.value.filter { (id to it.id) !in goneModels })?.id?.also { discoveredModels[cacheKey] = it }?.let { AiResult.Success(it) }
                ?: AiResult.Failure(FailureKind.UNKNOWN, "No chat model is available for this ${id.shortName} key.")
            is AiResult.Failure -> models
        }
    }

    private val _lastRoute = MutableStateFlow<AiRoute?>(null)
    /** Which provider and model really answered the last message (or why it failed). */
    val lastRoute: StateFlow<AiRoute?> = _lastRoute.asStateFlow()

    /**
     * Settings → AI & Brain → Test active AI: a tiny real request through the same path as a chat
     * message (chosen provider, chosen or discovered model, automatic fallback). Nothing is added
     * to the conversation.
     */
    suspend fun testActiveAi(): ActiveAiTest {
        val id = settings.value.defaultProvider
        val started = clock()
        fun result(answeredBy: ProviderId? = null, model: String? = null, reply: String? = null, error: String? = null, fallbackReason: String? = null) =
            ActiveAiTest(id, answeredBy, model, clock() - started, reply, error, fallbackReason)
        if (!isOnline() && id != ProviderId.OLLAMA) return result(error = "No internet connection.")
        val key = keyFor(id) ?: return result(error = "${id.displayName} API key is not set.")
        val provider = providerFor(id, key)
        val model = when (val m = resolveModel(id, key, provider)) {
            is AiResult.Success -> m.value
            is AiResult.Failure -> return result(error = m.message)
        }
        val turns = listOf(Turn(TurnRole.USER, "Connection test. Reply with one short word."))
        val r = withTimeoutOrNull(AI_TIMEOUT_MS) { provider.chat(model, TEST_SYSTEM_PROMPT, turns) }
            ?: AiResult.Failure(FailureKind.TIMEOUT, "${id.shortName} did not answer within ${AI_TIMEOUT_MS / 1000} seconds.")
        if (r is AiResult.Success && r.value.isNotBlank()) return result(id, model, r.value.trim().take(80))
        val error = (r as? AiResult.Failure)?.message ?: "empty reply"
        if (r !is AiResult.Failure || r.kind in FALLBACK_KINDS) {
            fallbackReply(id, TEST_SYSTEM_PROMPT, turns)?.let { b ->
                return result(b.provider, b.model, b.text.trim().take(80), fallbackReason = error)
            }
        }
        return result(model = model, error = error)
    }

    /**
     * Runs a command understood without AI ([LocalCommands]). Returns false to leave the message to
     * the AI after all: an app name the phone does not know, a contact name not found as written
     * ("मम्मी" saved as "Mummy"), or Safety Mode (the AI path explains what is blocked).
     */
    private suspend fun runLocalCommand(action: AssistantAction, speak: Boolean): Boolean {
        val s = settings.value
        if (s.safetyMode && (action is AssistantAction.Call || action is AssistantAction.OpenApp)) return false
        when (action) {
            is AssistantAction.OpenApp -> {
                val outcome = tools.openApp(action.name)
                if (outcome !is ToolOutcome.Done) return false
                trace("offline command: open app")
                report(action, outcome)
                if (speak) say(phrase(lang(), "${action.name} खोल दिया है।", "${action.name} খুলে দিয়েছি।"))
            }
            is AssistantAction.Call -> {
                val name = action.contact
                if (name != null) {
                    val found = tools.findContacts(name)
                    if (found is ContactLookup.Found && found.contacts.isEmpty()) return false
                }
                trace("offline command: call")
                _state.value = AssistantState.Executing
                callContact(action, speak)
            }
            is AssistantAction.Flashlight -> {
                trace("offline command: torch")
                val outcome = tools.setFlashlight(action.on)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) {
                    say(if (action.on) phrase(lang(), "टॉर्च जला दी है।", "টর্চ জ্বালিয়ে দিয়েছি।") else phrase(lang(), "टॉर्च बंद कर दी है।", "টর্চ বন্ধ করে দিয়েছি।"))
                }
            }
            is AssistantAction.Volume -> {
                trace("offline command: volume")
                val outcome = tools.setVolume(action.percent)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "आवाज़ ${action.percent} प्रतिशत कर दी है।", "ভলিউম ${action.percent} শতাংশ করে দিয়েছি।"))
            }
            is AssistantAction.SetTimer -> {
                trace("offline command: timer")
                val outcome = tools.setTimer(action.seconds, action.note)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) {
                    val sec = action.seconds
                    val (hi, bn) = when {
                        sec % 3600 == 0 -> "${sec / 3600} घंटे" to "${sec / 3600} ঘণ্টা"
                        sec % 60 == 0 -> "${sec / 60} मिनट" to "${sec / 60} মিনিট"
                        else -> "$sec सेकंड" to "$sec সেকেন্ড"
                    }
                    say(phrase(lang(), "$hi का टाइमर लगा दिया है।", "$bn এর টাইমার দিয়ে দিয়েছি।"))
                }
            }
            is AssistantAction.SetAlarm -> {
                trace("offline command: alarm")
                val outcome = tools.setAlarm(action.hour, action.minute, action.note)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) {
                    val time = "${action.hour}:${action.minute.toString().padStart(2, '0')}"
                    say(phrase(lang(), "$time बजे का अलार्म लगा दिया है।", "$time এর অ্যালার্ম দিয়ে দিয়েছি।"))
                }
            }
            is AssistantAction.TakePhoto -> {
                trace("offline command: photo")
                _state.value = AssistantState.Executing
                camera(action, action.front, question = null, speak = speak)
            }
            is AssistantAction.Battery -> {
                trace("offline command: battery")
                val outcome = tools.batteryStatus()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(outcome.message)
            }
            is AssistantAction.EndCall -> {
                trace("offline command: end call")
                val outcome = tools.endCall()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "कॉल कट कर दी गई है राजू।", "কল কেটে দেওয়া হয়েছে রাজু।"))
            }
            is AssistantAction.AnswerCall -> {
                trace("offline command: answer call")
                report(action, tools.answerCall())
            }
            is AssistantAction.GoHome -> {
                trace("offline command: home")
                val outcome = tools.goHome()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "लीजिए राजू, होम स्क्रीन पर आ गए।", "এই যে রাজু, হোম স্ক্রিনে চলে এসেছি।"))
            }
            is AssistantAction.SetPersonality -> {
                trace("offline command: mode")
                setPersonality(action, speak)
            }
            else -> return false
        }
        if (_state.value != AssistantState.Error) {
            _state.value = AssistantState.Success
            delay(1_200)
        }
        finishTurn()
        return true
    }

    private fun finishTurn() {
        val s = settings.value
        val wasVoice = voiceTurn
        voiceTurn = false
        lastActivityAt = clock() // the sleep timer counts from the end of her answer
        if (_voiceSession.value && (s.continuousConversation || handsFree) && !s.assistantPaused) {
            listen()
        } else {
            _voiceSession.value = false
            if (_state.value == AssistantState.Error) scheduleErrorReset()
            else if (_state.value != AssistantState.Offline) {
                if (s.wakeWord && !s.assistantPaused && wakeListener !== NoWakeListener) {
                    goToSleep()
                } else {
                    _state.value = AssistantState.Idle
                }
            }
        }
    }

    private suspend fun say(text: String, allowBargeIn: Boolean = true, depth: Int = 0) {
        // "Speaking" is shown only once audio really plays; while the voice is being prepared she is thinking.
        if (_state.value != AssistantState.Thinking && _state.value != AssistantState.Executing) _state.value = AssistantState.Thinking
        val spoken = speakableText(text)
        val showSpeaking = scope.launch {
            speaker.playing.first { it }
            if (turnActive && turnFirstAudioAt == null) turnFirstAudioAt = clock()
            if (_state.value == AssistantState.Thinking || _state.value == AssistantState.Executing) _state.value = AssistantState.Speaking
        }
        // In a voice conversation the user can talk over Seema, like interrupting a person — but only
        // when the phone's echo canceller is on (call-style audio) and what was heard can be checked
        // by speech-to-text. Without both, her own voice coming back from the speaker into the mic
        // would stop her after a few words (tested on a phone). Tapping the core always stops her.
        val canBargeIn = allowBargeIn && settings.value.bargeIn && (handsFree || _voiceSession.value) &&
            conversationMode.active && transcriber?.isAvailable() == true && headphones()
        val capture = canBargeIn
        val utterance = CompletableDeferred<ByteArray?>()
        // Set from the microphone thread: atomic, so the speaking coroutine always sees it.
        val interrupted = java.util.concurrent.atomic.AtomicBoolean(false)
        if (canBargeIn) {
            bargeIn.start(
                capture = capture,
                onInterrupt = { if (interrupted.compareAndSet(false, true)) speaker.stop() },
                onUtterance = { utterance.complete(it) },
            )
        }
        trace("speak (${spoken.length} chars)${if (canBargeIn) " barge-in on" else ""}")
        val result = try {
            speaker.speak(spoken)
        } finally {
            showSpeaking.cancel()
            if (!interrupted.get()) bargeIn.stop()
        }
        lastSpoken = text
        lastSpokeAt = clock()
        if (interrupted.get()) {
            trace("interrupted by the user after ${speaker.playedChunks} part(s)")
            onInterrupted(spoken, capture, utterance, depth)
            return
        }
        trace("spoke: ${result.engineLabel}, ${if (result.completed) "complete" else "NOT complete"}${result.error?.let { " — $it" } ?: ""}")
        when {
            // Nothing (or not all) was heard: always tell the user, never fail silently.
            !result.completed && result.error != null -> note("🔇 Voice failed: ${result.error}")
            // A fallback reason (e.g. "ElevenLabs failed … using Android") is shown once, not under every reply.
            result.error != null && result.error != lastVoiceNote -> note("Voice: ${result.error}")
        }
        lastVoiceNote = result.error
        _state.value = AssistantState.Thinking
    }

    /**
     * The user talked over Seema. Like a person she stops at once and listens, then:
     * - "ruko" / "bas" / "stop" → she stays quiet ("Haan, boliye?") and waits for the user;
     * - a new question or remark → she answers that instead (the old answer is dropped);
     * - nothing understandable (a cough, a noise) → she continues where she stopped.
     * Without a speech-to-text key the normal recogniser takes over right away.
     */
    private suspend fun onInterrupted(spoken: String, capture: Boolean, utterance: CompletableDeferred<ByteArray?>, depth: Int) {
        val played = speaker.playedChunks
        _state.value = AssistantState.Listening
        if (!capture) {
            bargeIn.stop()
            _voiceSession.value = true
            listen()
            throw CancellationException("Interrupted by the user")
        }
        val audio = withTimeoutOrNull(20_000) { utterance.await() }
        bargeIn.stop()
        _state.value = AssistantState.Thinking
        val raw = audio?.let { runCatching { transcriber?.transcribe(it) }.getOrNull() }?.trim().orEmpty()
        val heard = stripEcho(raw, spoken)
        when {
            heard.length < 2 -> {
                // False alarm: carry on from the sentence she was saying.
                val rest = chunkForSpeech(spoken).drop(played).joinToString(" ")
                if (rest.isNotBlank() && depth < 2) say(resumePrefix(rest) + rest, depth = depth + 1)
            }
            isStopCommand(heard) -> {
                record(ChatRole.USER, heard)
                say(if (lang() == SeemaLanguage.BENGALI) "হ্যাঁ, বলুন?" else if (DEVANAGARI.containsMatchIn(heard)) "हाँ, बोलिए?" else "Haan, boliye?", allowBargeIn = false)
                _voiceSession.value = true
                listen()
                throw CancellationException("Stopped by the user")
            }
            else -> {
                // A new question: answer it now (this cancels the rest of the current turn).
                submit(heard, fromVoice = true)
                throw CancellationException("Interrupted with a new question")
            }
        }
    }

    private fun resumePrefix(rest: String) = when {
        BENGALI_SCRIPT.containsMatchIn(rest) -> "হ্যাঁ, তো… "
        DEVANAGARI.containsMatchIn(rest) -> "हाँ, तो… "
        else -> "Haan, toh… "
    }

    private var lastSpoken: String? = null

    /** "Repeat": speaks Seema's last reply again (nothing is sent to the AI). */
    fun repeatLast() {
        speakAgain(_transcript.value.lastOrNull { it.role == ChatRole.ASSISTANT }?.text ?: lastSpoken ?: return)
    }

    /** Speaks one of Seema's replies again (the 🔊 button under a message). */
    fun speakAgain(text: String) {
        cancelWork()
        job = scope.launch {
            say(text, allowBargeIn = false)
            if (_state.value != AssistantState.Error) _state.value = AssistantState.Idle
        }
    }

    /** Mic permission was refused: show it as its own state (not as a generic error). */
    fun permissionMissing(message: String) {
        scope.launch { fail(message, AssistantState.WaitingPermission) }
    }

    /** "Ruko": stop speaking right now but keep the conversation (hands-free listens again next). */
    // ---------------------------------------------------------------- phone calls

    @Volatile private var callCommandUntil = 0L
    @Volatile private var pausedForCall = false
    private var handsFreeBeforeCall = false

    /** A call rings: her own reply stops; the microphone stays usable so she can hear "utha lo" / "kaat do". */
    fun onCallRinging() {
        trace("call ringing")
        cancelWork()
    }

    /** After the caller announcement ("… ka call aa raha hai. Utha lun?"): listens for "haan / utha lo" or "kaat do". */
    fun listenForCallCommand() {
        if (settings.value.assistantPaused || !voice.isAvailable() || pausedForCall) return
        callCommandUntil = clock() + CALL_COMMAND_WINDOW_MS
        if (asleep) leaveSleep()
        _voiceSession.value = true
        listen()
    }

    /** "Utha lo" / "kaat do" while a call rings is handled here at once, without the AI. True when it was a call command. */
    private fun handleCallCommand(text: String): Boolean {
        if (clock() >= callCommandUntil) return false
        when (callCommand(text)) {
            CallCommand.ANSWER -> {
                callCommandUntil = 0
                trace("call: answer")
                val outcome = tools.answerCall()
                scope.launch { report(AssistantAction.AnswerCall, outcome) }
            }
            CallCommand.REJECT -> {
                callCommandUntil = 0
                trace("call: reject")
                val outcome = tools.endCall()
                scope.launch { report(AssistantAction.EndCall, outcome) }
            }
            null -> { // not a yes / no: she asked once, so she stops instead of listening in a loop
                callCommandUntil = 0
                trace("call: not an answer, stop listening")
                returnToStandby()
            }
        }
        return true
    }

    @Volatile private var replyUntil = 0L
    private var replyHandler: ((Boolean?) -> Unit)? = null
    private var replyTimeout: Job? = null

    /**
     * After "Padh ke sunau kya?": listens a few seconds for "haan / padho" (true) or "rehne do /
     * nahi" (false); silence or anything else gives null. Returns false when she is busy (a
     * conversation, a call, a question on screen): the question then stays on the screen.
     */
    fun listenForReadReply(windowMs: Long = READ_REPLY_WINDOW_MS, onReply: (Boolean?) -> Unit): Boolean {
        if (settings.value.assistantPaused || !voice.isAvailable() || pausedForCall || clock() < callCommandUntil) return false
        if (_voiceSession.value || job?.isActive == true || _pending.value != null || replyHandler != null) return false
        replyHandler = onReply
        replyUntil = clock() + windowMs
        replyTimeout?.cancel()
        replyTimeout = scope.launch {
            delay(windowMs)
            if (replyHandler != null) {
                trace("read reply: no answer")
                finishReadReply(null)
            }
        }
        if (asleep) leaveSleep()
        _voiceSession.value = true
        trace("read reply: listening")
        listen()
        return true
    }

    private fun handleReadReply(text: String): Boolean {
        if (replyHandler == null || clock() >= replyUntil) return false
        val answer = readReply(text)
        trace("read reply: ${answer ?: "unclear"}")
        finishReadReply(answer)
        return true
    }

    private fun finishReadReply(answer: Boolean?) {
        val handler = replyHandler ?: return
        replyHandler = null
        replyUntil = 0
        replyTimeout?.cancel()
        replyTimeout = null
        listenGen++
        voice.stop()
        returnToStandby()
        handler(answer)
    }

    /**
     * The call was answered (or dialled): Seema stops talking and releases the microphone for the
     * call. After the call hands-free comes back by itself ([resumeAfterCall]).
     */
    fun pauseForCall() {
        callCommandUntil = 0
        if (pausedForCall) return
        pausedForCall = true
        handsFreeBeforeCall = handsFree
        trace("phone call: pause")
        cancelWork()
        speaker.stop()
        conversationMode.exit()
        heldJob?.cancel()
        heldVoiceText = null
        if (asleep) leaveSleep()
        _voiceSession.value = false
        listenGen++
        voice.stop()
        _state.value = AssistantState.Idle
    }

    /** The call ended: hands-free (or the wake-word standby) continues as before the call. */
    fun resumeAfterCall() {
        callCommandUntil = 0
        if (!pausedForCall) return
        pausedForCall = false
        val wasHandsFree = handsFreeBeforeCall
        handsFreeBeforeCall = false
        trace("phone call ended")
        if (settings.value.assistantPaused) return
        if (wasHandsFree && handsFree) startHandsFree() else returnToStandby()
    }

    fun interrupt() {
        speaker.stop()
    }

    // ---------------------------------------------------------------- actions

    private suspend fun execute(action: AssistantAction, provider: AiProvider, model: String, system: String, speak: Boolean) {
        val s = settings.value
        val sensitive = action is AssistantAction.Call || action is AssistantAction.WhatsApp ||
            action is AssistantAction.WhatsAppCall ||
            action is AssistantAction.OpenApp || action is AssistantAction.PlayMusic || action is AssistantAction.Sms
        if (s.safetyMode && sensitive) {
            report(action, ActionStatus.BLOCKED, "Safety Mode is on, so \"${action.label}\" was not performed. Turn it off with \"Turn off\" on the Home screen (or Settings → Safety Mode) to allow it.")
            return
        }
        _state.value = AssistantState.Executing
        when (action) {
            is AssistantAction.Call -> callContact(action, speak)
            is AssistantAction.WhatsApp -> whatsApp(action, speak)
            is AssistantAction.WhatsAppCall -> whatsAppCall(action, speak)
            is AssistantAction.FindContact -> findContact(action, speak)
            is AssistantAction.LastCall -> checkLastCall(action, speak)
            is AssistantAction.ReadNotification -> readNotification(action, speak)
            is AssistantAction.Screenshot -> takeScreenshot(action, speak)
            is AssistantAction.OpenApp -> report(action, tools.openApp(action.name))
            is AssistantAction.OpenSettings -> report(action, tools.openSettings(action.page))
            is AssistantAction.Battery -> {
                val outcome = tools.batteryStatus()
                report(action, outcome)
                // Informational result: in a voice conversation the user expects to hear it.
                if (speak && outcome is ToolOutcome.Done) say(outcome.message)
            }
            is AssistantAction.FindPhone -> {
                val outcome = tools.findMyPhone()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "मैं आपके फोन की घंटी बजा रही हूँ राजू, सुनिए!", "আমি আপনার ফোনের রিং বাজাচ্ছি রাজু, শুনুন!"))
            }
            is AssistantAction.AnswerCall -> report(action, tools.answerCall())
            is AssistantAction.TakePhoto -> camera(action, action.front, question = null, speak = speak)
            is AssistantAction.LookCamera -> camera(action, action.front, question = action.question, speak = speak)
            is AssistantAction.EndCall -> {
                val outcome = tools.endCall()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "कॉल कट कर दी गई है राजू।", "কল কেটে দেওয়া হয়েছে রাজু।"))
            }
            is AssistantAction.GoHome -> {
                val outcome = tools.goHome()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "लीजिए राजू, होम स्क्रीन पर आ गए।", "এই যে রাজু, হোম স্ক্রিনে চলে এসেছি।"))
            }
            is AssistantAction.OpenSeema -> {
                val outcome = tools.openSeema()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "हाँ राजू, मैं अपनी स्क्रीन पर वापस आ गई!", "হ্যাঁ রাজু, আমি আমার স্ক্রিনে ফিরে এসেছি!"))
            }
            is AssistantAction.PlayMusic -> {
                val outcome = tools.playMusic(action.query, action.service ?: s.musicService)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "राजू, यूट्यूब पर आपके लिए गाना बजा दिया है, सुनिए!", "রাজু, ইউটিউবে গান চালিয়ে দিয়েছি, শুনুন!"))
            }
            is AssistantAction.Remember -> remember(action)
            is AssistantAction.SetTimer -> report(action, tools.setTimer(action.seconds, action.note))
            is AssistantAction.SetAlarm -> report(action, tools.setAlarm(action.hour, action.minute, action.note))
            is AssistantAction.Flashlight -> report(action, tools.setFlashlight(action.on))
            is AssistantAction.Volume -> report(action, tools.setVolume(action.percent))
            is AssistantAction.Media -> {
                val outcome = tools.mediaControl(action.command)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "गाना बंद कर दिया गया है राजू।", "গান বন্ধ করে দেওয়া হয়েছে রাজু।"))
            }
            is AssistantAction.Sms -> sendSms(action)
            is AssistantAction.AddEvent -> addEvent(action)
            is AssistantAction.Navigate -> report(action, tools.navigate(action.destination, action.mode))
            is AssistantAction.CopyText -> report(action, tools.copyToClipboard(action.text))
            is AssistantAction.SearchWeb -> searchWeb(action, provider, model, system, speak)
            is AssistantAction.ExpenseAdd -> {
                val saved = runCatching { business.addExpense(action.amountPaise, action.note, action.category) }.isSuccess
                if (!saved) report(action, ActionStatus.FAILED, "Could not save the expense.")
                else {
                    val (from, to) = periodRange(Period.TODAY, clock(), zone())
                    val total = runCatching { business.expensesBetween(from, to).sumOf { it.amountPaise } }.getOrDefault(action.amountPaise)
                    report(action, ActionStatus.SUCCESS, "Khata: ${formatRupees(action.amountPaise)} ${action.note} (${action.category}) saved. Today's total ${formatRupees(total)}.")
                }
            }
            is AssistantAction.ExpenseUndo -> when (val removed = runCatching { business.deleteLastExpense() }.getOrNull()) {
                null -> report(action, ActionStatus.FAILED, "There is no expense to remove.")
                else -> report(action, ActionStatus.SUCCESS, "Removed: ${formatRupees(removed.amountPaise)} ${removed.note}.")
            }
            is AssistantAction.ExpenseSummary -> {
                val (from, to) = periodRange(action.period, clock(), zone())
                val list = runCatching { business.expensesBetween(from, to) }.getOrNull()
                if (list == null) report(action, ActionStatus.FAILED, "Could not read the khata.")
                else informational(action, summarizeExpenses(list, action.period, prefersHindi()), speak)
            }
            is AssistantAction.JourneySave -> {
                val ok = runCatching { business.saveJourney(action.journey) }.isSuccess
                report(action, if (ok) ActionStatus.SUCCESS else ActionStatus.FAILED, if (ok) "Saved in PNR diary: ${describeJourney(action.journey)}." else "Could not save the journey.")
            }
            is AssistantAction.JourneyList -> {
                val list = runCatching { business.journeysFrom(today()) }.getOrNull()
                if (list == null) report(action, ActionStatus.FAILED, "Could not read the PNR diary.")
                else informational(action, summarizeJourneys(list, prefersHindi()), speak)
            }
            is AssistantAction.JourneyRemove -> {
                val ok = runCatching { business.removeJourney(action.pnr) }.getOrDefault(false)
                report(action, if (ok) ActionStatus.SUCCESS else ActionStatus.FAILED, if (ok) "PNR ${action.pnr} removed from the diary." else "PNR ${action.pnr} is not in the diary.")
            }
            is AssistantAction.PnrStatus -> {
                // There is no free official PNR API: open the official page with the PNR ready to paste.
                tools.copyToClipboard(action.pnr)
                when (val o = tools.openWebPage(PNR_ENQUIRY_URL)) {
                    is ToolOutcome.Done -> report(action, ActionStatus.SUCCESS, "Official PNR page opened — PNR ${action.pnr} is copied, paste it there and fill the captcha.")
                    else -> report(action, o)
                }
            }
            is AssistantAction.MandiPrice -> {
                val place = action.place?.let { " $it" }.orEmpty()
                searchWeb(AssistantAction.SearchWeb("${action.commodity} mandi bhav today$place price per quintal agmarknet"), provider, model, system, speak)
            }
            is AssistantAction.BookingSave -> saveBooking(action, speak)
            is AssistantAction.BookingUpdate -> updateBooking(action, speak)
            is AssistantAction.BookingSetStatus -> setBookingStatus(action, speak)
            is AssistantAction.BookingList -> {
                val list = runCatching { bookings.all() }.getOrNull()
                if (list == null) report(action, ActionStatus.FAILED, "Could not read the bookings.")
                else informational(action, summarizeBookings(list, action.status, action.query), speak)
            }
            is AssistantAction.BookingSend -> {
                val b = findBooking(action.id)
                if (b == null) report(action, ActionStatus.FAILED, "वह बुकिंग नहीं मिली।") else sendSlip(action, b, speak)
            }
            is AssistantAction.SetPersonality -> setPersonality(action, speak)
            is AssistantAction.SaveFile -> report(action, tools.shareTextFile(action.name, action.content))
            is AssistantAction.Research -> research(action, provider, model, system, speak)
        }
    }

    /** A result the user asked to know (summary, list): shown, logged and, in a voice turn, spoken. */
    private suspend fun informational(action: AssistantAction, message: String, speak: Boolean) {
        report(action, ActionStatus.SUCCESS, message)
        if (speak) say(message)
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    /** Khata/PNR summaries exist in Hindi and English; English is no longer a reply language, so Hindi. */
    private fun prefersHindi(): Boolean = true

    /** Today's khata and upcoming journeys for the prompt (null when empty or unreadable). */
    private suspend fun businessSnapshot(): String? = runCatching {
        val (from, to) = periodRange(Period.TODAY, clock(), zone())
        businessContext(business.expensesBetween(from, to), business.journeysFrom(today()))
    }.getOrNull()

    // ---------------------------------------------------------------- train booking slips

    private suspend fun bookingSnapshot(): String? = runCatching { bookingContext(bookings.all(), today(), clock()) }.getOrNull()

    /** By number, or (no number) the booking saved or changed last. */
    private suspend fun findBooking(id: Long?): Booking? = runCatching {
        val all = bookings.all()
        if (id != null) all.firstOrNull { it.id == id } else lastTouched(all)
    }.getOrNull()

    private suspend fun saveBooking(action: AssistantAction.BookingSave, speak: Boolean) {
        val draft = action.booking
        val d = ask(PendingRequest(PendingRequest.Kind.CONFIRM, "बुकिंग सेव करूँ?", formatSlip(draft) + "\n\nकुछ गलत हो तो Cancel दबाकर बोलिए, जैसे \"नाम आनंद है\"."))
        if (d !is Decision.Approve) {
            report(action, ActionStatus.CANCELLED, "बुकिंग सेव नहीं की। सही जानकारी बोलिए, मैं फिर से बना दूँगी।")
            return
        }
        val saved = runCatching { bookings.upsert(draft) }.getOrNull()
        if (saved == null) {
            report(action, ActionStatus.FAILED, "बुकिंग सेव नहीं हो पाई।")
            return
        }
        report(action, ActionStatus.SUCCESS, "बुकिंग #${saved.id} सेव हो गई (फ़ाइल: $BOOKING_FOLDER/${daySlipFileName(saved.journeyDate)})\n${formatSlip(saved)}")
        if (speak) say(phrase(lang(), "बुकिंग नंबर ${saved.id} सेव हो गई, ${saved.passengers.size} यात्री।", "বুকিং নম্বর ${saved.id} সেভ হয়েছে।"))
        if (settings.value.bookingWhatsApp.isNotBlank()) sendSlip(action, saved, speak)
    }

    private suspend fun updateBooking(action: AssistantAction.BookingUpdate, speak: Boolean) {
        val old = findBooking(action.id)
        if (old == null) {
            report(action, ActionStatus.FAILED, "कोई बुकिंग नहीं मिली जिसे सुधारूँ।")
            return
        }
        when (val r = applyEdit(old, action.edit)) {
            is EditResult.Problem -> {
                report(action, ActionStatus.CANCELLED, r.message)
                if (speak) say(r.message)
            }
            is EditResult.Ok -> {
                val d = ask(PendingRequest(PendingRequest.Kind.CONFIRM, "बुकिंग #${old.id} ऐसे सुधार दूँ?", formatSlip(r.booking)))
                if (d !is Decision.Approve) {
                    report(action, ActionStatus.CANCELLED, "बुकिंग #${old.id} नहीं बदली।")
                    return
                }
                val saved = runCatching { bookings.upsert(r.booking) }.getOrNull()
                if (saved == null) report(action, ActionStatus.FAILED, "बदलाव सेव नहीं हो पाया।")
                else {
                    report(action, ActionStatus.SUCCESS, "बुकिंग #${saved.id} सुधार दी।\n${formatSlip(saved)}")
                    if (speak) say(phrase(lang(), "ठीक है, बुकिंग नंबर ${saved.id} सुधार दी।", "ঠিক আছে, বুকিং ${saved.id} ঠিক করে দিয়েছি।"))
                }
            }
        }
    }

    private suspend fun setBookingStatus(action: AssistantAction.BookingSetStatus, speak: Boolean) {
        val b = findBooking(action.id)
        if (b == null) {
            report(action, ActionStatus.FAILED, "वह बुकिंग नहीं मिली।")
            return
        }
        val saved = runCatching { bookings.upsert(b.copy(status = action.status)) }.getOrNull()
        if (saved == null) report(action, ActionStatus.FAILED, "स्टेटस नहीं बदल पाया।")
        else {
            val msg = "बुकिंग #${saved.id} (${saved.from} → ${saved.to}) अब ${saved.status.label} है।"
            report(action, ActionStatus.SUCCESS, msg)
            if (speak) say(msg)
        }
    }

    /** Opens WhatsApp with the slip for the saved number — only after the user says yes. */
    private suspend fun sendSlip(action: AssistantAction, b: Booking, speak: Boolean) {
        val number = settings.value.bookingWhatsApp.takeIf { it.isNotBlank() }
        val d = ask(PendingRequest(PendingRequest.Kind.CONFIRM, "पर्ची WhatsApp पर भेजूँ?", (number?.let { "नंबर: $it" } ?: "WhatsApp में चैट आप चुनेंगे") + "\n\n" + formatSlip(b)))
        if (d !is Decision.Approve) {
            report(action, ActionStatus.CANCELLED, "पर्ची #${b.id} नहीं भेजी।")
            return
        }
        report(action, tools.openWhatsApp(number, formatSlip(b)))
    }

    private suspend fun setPersonality(action: AssistantAction.SetPersonality, speak: Boolean) {
        onPersonality(action.personality)
        val tapori = action.personality == Personality.TAPORI
        report(action, ActionStatus.SUCCESS, if (tapori) "टपोरी मोड चालू 😎" else "मोड: ${action.personality.label}")
        if (speak) {
            say(
                if (tapori) phrase(lang(), "चल भिड़ू, टपोरी मोड ऑन! अब बोल, क्या सीन है?", "চল বন্ধু, টপোরি মোড অন!")
                else phrase(lang(), "मैं वापस आ गई राजू, अपनी सीमा।", "আমি ফিরে এসেছি রাজু।"),
            )
        }
    }

    /** Deep research: up to three searches, merged and de-duplicated, summarised as a report with sources. */
    private suspend fun research(action: AssistantAction.Research, provider: AiProvider, model: String, system: String, speak: Boolean) {
        val key = secret(SecretId.TAVILY_API_KEY)
        if (key.isNullOrBlank()) {
            report(action, ActionStatus.FAILED, "Research needs a Tavily API key (Settings → Web search).")
            return
        }
        if (speak && !replySpoken) say(phrase(lang(), "ठीक है, अच्छे से रिसर्च करती हूँ…", "ঠিক আছে, ভালো করে খুঁজে দেখছি…"), allowBargeIn = false)
        _state.value = AssistantState.Executing
        val client = searchFor(key)
        val results = mutableListOf<com.raju.aiassistant.data.search.SearchResult>()
        var lastError: String? = null
        for (q in action.queries) {
            when (val r = client.search(q, maxResults = 4)) {
                is AiResult.Success -> results += r.value.results
                is AiResult.Failure -> lastError = r.message
            }
        }
        val merged = results.distinctBy { it.url }.take(8)
        if (merged.isEmpty()) {
            report(action, ActionStatus.FAILED, "Research found nothing" + (lastError?.let { ": $it" } ?: " for \"${action.topic}\"."))
            return
        }
        actionLog.safeLog(action, ActionStatus.SUCCESS, "${action.queries.size} searches, ${merged.size} sources")
        _state.value = AssistantState.Thinking
        val followUp = buildResearchFollowUp(action.topic, merged.map { it.title to it.url }, merged.map { it.snippet })
        val turns = historyTurns(model) +
            com.raju.aiassistant.domain.ai.Turn(com.raju.aiassistant.domain.ai.TurnRole.USER, followUp)
        val sources = merged.mapIndexed { i, r -> "[${i + 1}] ${r.title} — ${r.domain}\n${r.url}" }.joinToString("\n")
        when (val r = provider.chat(model, system, turns)) {
            is AiResult.Success -> {
                val reportText = parseReply(r.value).text
                record(ChatRole.ASSISTANT, "$reportText\n\nSources:\n$sources")
                // Only the opening summary is read aloud; the full report stays in Chat.
                if (speak) {
                    val first = reportText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(300)
                    say(first + phrase(lang(), " पूरी रिपोर्ट चैट में है।", " পুরো রিপোর্ট চ্যাটে আছে।"))
                }
            }
            is AiResult.Failure -> {
                record(ChatRole.SYSTEM, "Found sources but the AI report failed (${r.message}).\n$sources")
                _state.value = AssistantState.Error
            }
        }
    }

    private suspend fun callContact(action: AssistantAction.Call, speak: Boolean = false) {
        val candidates: List<Contact> = if (action.number != null && action.contact == null) {
            listOf(Contact(action.number, action.number))
        } else {
            when (val found = lookup(action.contact ?: action.number.orEmpty())) {
                null -> { report(action, ActionStatus.CANCELLED, "Contacts permission was not granted, so I could not find ${action.contact}."); return }
                else -> found
            }
        }
        if (candidates.isEmpty()) {
            report(action, ActionStatus.FAILED, "No contact named \"${action.contact}\" was found.")
            return
        }
        val chosen = if (candidates.size == 1 && settings.value.directCalling) {
            candidates[0]
        } else {
            confirmContact(
                title = "Call?",
                message = if (candidates.size == 1) "Call ${candidates[0].name} (${candidates[0].number})?" else "Multiple contacts match \"${action.contact}\". Choose one to call:",
                candidates = candidates,
            )
        } ?: run { report(action, ActionStatus.CANCELLED, "Call cancelled."); return }
        val outcome = tools.dial(chosen, settings.value.directCalling)
        report(action, outcome)
        if (speak && outcome is ToolOutcome.Done) {
            say(phrase(lang(), "हाँ राजू, मैंने ${chosen.name} को कॉल लगा दी है!", "হ্যাঁ রাজু, আমি ${chosen.name} কে কল লাগিয়ে দিয়েছি!"))
        }
    }

    private suspend fun sendSms(action: AssistantAction.Sms) {
        val candidates: List<Contact> = if (action.number != null && action.contact == null) {
            listOf(Contact(action.number, action.number))
        } else {
            when (val found = lookup(action.contact ?: action.number.orEmpty())) {
                null -> { report(action, ActionStatus.CANCELLED, "Contacts permission was not granted, so I could not find ${action.contact}."); return }
                else -> found
            }
        }
        if (candidates.isEmpty()) {
            report(action, ActionStatus.FAILED, "No contact named \"${action.contact}\" was found.")
            return
        }
        val chosen = confirmContact(
            title = "SMS bhejein?",
            message = if (candidates.size == 1) "${candidates[0].name} (${candidates[0].number}) ko yeh SMS bheja jayega:\n\n\"${action.text}\""
            else "Kis contact ko SMS bhejna hai?\n\n\"${action.text}\"",
            candidates = candidates,
        ) ?: run { report(action, ActionStatus.CANCELLED, "SMS cancelled."); return }
        val smsPermission = "android.permission.SEND_SMS"
        if (!tools.hasPermission(smsPermission)) {
            val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "SMS permission", "SMS bhejne ke liye permission chahiye. SMS aapke SIM se jayega (carrier charges lag sakte hain).", permission = smsPermission))
            if (d !is Decision.PermissionResult || !d.granted) { report(action, ActionStatus.CANCELLED, "SMS permission was not granted, so nothing was sent."); return }
        }
        report(action, tools.sendSms(chosen, action.text))
    }

    private suspend fun addEvent(action: AssistantAction.AddEvent) {
        val zone = ZoneId.systemDefault()
        var start = action.start
        fun millis() = runCatching { start.atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        // "8 baje class" said after 8 o'clock today means tomorrow: a time that passed in the last day moves forward one day.
        val first = millis()
        if (first != null && first < clock() - 5 * 60_000L && first > clock() - DAY_MS) start = start.plusDays(1)
        val startMillis = millis()
        if (startMillis == null || startMillis < clock() - 5 * 60_000L) {
            report(action, ActionStatus.FAILED, "The event time ${action.start} is in the past or invalid, so the calendar was not opened. Please say the date and time again.")
            return
        }
        report(action, tools.addCalendarEvent(action.title, startMillis, action.durationMinutes, action.location, action.repeat?.rrule))
    }

    private suspend fun findContact(action: AssistantAction.FindContact, speak: Boolean) {
        val found = lookup(action.name)
        val msg = if (found.isNullOrEmpty()) {
            phrase(lang(), "फोन बुक में \"${action.name}\" नाम से कोई नंबर नहीं मिला।", "ফোনবুকে \"${action.name}\" নামের কোনো নম্বর নেই।")
        } else {
            val details = found.joinToString(", ") { "${it.name}: ${it.number}" }
            phrase(lang(), "हाँ, फोन बुक में संपर्क मिला: $details", "হ্যাঁ, ফোনবুকে নম্বর পাওয়া গেছে: $details")
        }
        report(action, if (found.isNullOrEmpty()) ActionStatus.FAILED else ActionStatus.SUCCESS, msg)
        if (speak) say(msg)
    }

    private suspend fun checkLastCall(action: AssistantAction.LastCall, speak: Boolean) {
        when (val outcome = tools.getLastCall()) {
            is ToolOutcome.Done -> {
                report(action, ActionStatus.SUCCESS, outcome.message)
                if (speak) say(outcome.message)
            }
            is ToolOutcome.NeedsPermission -> {
                val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "Call Log permission", outcome.reason, permission = outcome.permission))
                if (d is Decision.PermissionResult && d.granted) {
                    val retry = tools.getLastCall()
                    report(action, retry)
                    if (speak && retry is ToolOutcome.Done) say(retry.message)
                } else {
                    report(action, ActionStatus.CANCELLED, "Call log permission was denied.")
                }
            }
            is ToolOutcome.Failed -> {
                report(action, ActionStatus.FAILED, outcome.message)
                if (speak) say(outcome.message)
            }
        }
    }

    private suspend fun readNotification(action: AssistantAction.ReadNotification, speak: Boolean) {
        val summary = latestNotification()
        val text = summary ?: phrase(lang(), "अभी कोई नया नोटिफिकेशन या मैसेज नहीं आया है।", "এখন কোনো নতুন নোটিফিকেশন নেই।")
        report(action, ActionStatus.SUCCESS, text)
        if (speak) say(text)
    }

    /** "Photo lo" (saved to the gallery) or, with [question], "dekho kya dikh raha hai" (photo → Gemini → spoken answer). */
    private suspend fun camera(action: AssistantAction, front: Boolean, question: String?, speak: Boolean) {
        var r = tools.takePhoto(front, save = question == null)
        if (r is PhotoResult.NeedsPermission) {
            val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "Camera permission", r.reason, permission = r.permission))
            r = if (d is Decision.PermissionResult && d.granted) tools.takePhoto(front, save = question == null)
            else return report(action, ActionStatus.CANCELLED, "Camera permission was denied.")
        }
        when (r) {
            is PhotoResult.Failed -> {
                report(action, ActionStatus.FAILED, r.message)
                if (speak) say(r.message)
            }
            is PhotoResult.NeedsPermission -> report(action, ActionStatus.FAILED, r.reason)
            is PhotoResult.Taken -> if (question == null) {
                val msg = phrase(lang(), "फोटो ले ली है राजू" + (r.savedAs?.let { ", गैलरी में Seema फोल्डर में रखी है।" } ?: "।"), "ছবি তুলে নিয়েছি রাজু।")
                report(action, ActionStatus.SUCCESS, msg + (r.savedAs?.let { " ($it)" } ?: ""))
                if (speak) say(msg)
            } else {
                _state.value = AssistantState.Thinking
                val answer = describeImage(r.jpeg, question + " (" + (if (lang() == SeemaLanguage.BENGALI) "Answer in Bengali" else "Answer in short, natural Hindi") + ")")
                when (answer) {
                    is AiResult.Success -> {
                        report(action, ActionStatus.SUCCESS, answer.value)
                        if (speak) say(answer.value)
                    }
                    is AiResult.Failure -> {
                        report(action, ActionStatus.FAILED, answer.message)
                        if (speak) say(phrase(lang(), "फोटो तो ले ली, पर उसे समझ नहीं पाई: ${answer.message}", "ছবি নিয়েছি, কিন্তু বুঝতে পারিনি।"))
                    }
                }
            }
        }
    }

    private suspend fun takeScreenshot(action: AssistantAction.Screenshot, speak: Boolean) {
        when (val outcome = tools.takeScreenshot()) {
            is ToolOutcome.Done -> {
                report(action, ActionStatus.SUCCESS, outcome.message)
                if (speak) say(outcome.message)
            }
            is ToolOutcome.Failed -> {
                report(action, ActionStatus.FAILED, outcome.message)
                if (speak) say(outcome.message)
            }
            is ToolOutcome.NeedsPermission -> {
                report(action, ActionStatus.FAILED, outcome.reason)
            }
        }
    }

    private suspend fun whatsApp(action: AssistantAction.WhatsApp, speak: Boolean = false) {
        // The AI understood a WhatsApp request: the same who / what / "भेज दूँ?" conversation as the
        // request understood on the phone. Nothing is sent before the user says yes.
        whatsAppFlow(WhatsAppRequest(action.contact?.takeIf { it.isNotBlank() }, action.text.takeIf { it.isNotBlank() }), byVoice = voiceTurn, speak = speak)
    }

    /** What the user answered to a question Seema asked during a task. */
    private sealed interface Reply {
        data class Said(val text: String) : Reply
        data class Tapped(val decision: Decision) : Reply
        data object Silent : Reply
    }

    /**
     * Asks [question] and waits for the answer: spoken (when [byVoice]) or a tap on [dialog]. One
     * retry when nothing understandable was heard. The answer is shown in the chat.
     */
    private suspend fun hearReply(question: String, dialog: PendingRequest?, byVoice: Boolean, speak: Boolean): Reply {
        val tap = CompletableDeferred<Decision>()
        if (dialog != null) {
            decision = tap
            _pending.value = dialog
        }
        try {
            record(ChatRole.ASSISTANT, question)
            if (speak || byVoice) say(question, allowBargeIn = false)
            if (!byVoice || !voice.isAvailable()) {
                return if (dialog != null) Reply.Tapped(tap.await()) else Reply.Silent
            }
            repeat(2) { attempt ->
                if (tap.isCompleted) return Reply.Tapped(tap.getCompleted())
                val heard = CompletableDeferred<String?>()
                val gen = ++listenGen
                _state.value = AssistantState.Listening
                _heard.value = null
                trace("listen for answer")
                voice.start(
                    lang().tag,
                    onResult = { if (gen == listenGen) heard.complete(it) },
                    onError = { if (gen == listenGen) { trace("answer: ${it.message}"); heard.complete(null) } },
                )
                val reply = kotlinx.coroutines.selects.select<Reply?> {
                    heard.onAwait { text -> text?.trim()?.takeIf { it.isNotEmpty() }?.let { Reply.Said(it) } }
                    if (dialog != null) tap.onAwait { Reply.Tapped(it) }
                }
                if (reply != null) {
                    voice.stop()
                    if (reply is Reply.Said) {
                        _heard.value = reply.text
                        record(ChatRole.USER, reply.text)
                        trace("answer heard (${reply.text.length} chars)")
                    }
                    lastActivityAt = clock()
                    _state.value = AssistantState.Executing
                    return reply
                }
                if (attempt == 0) say(phrase(lang(), "माफ़ कीजिए, सुनाई नहीं दिया। फिर से बोलिए।", "দুঃখিত, শুনতে পাইনি। আবার বলুন।"), allowBargeIn = false)
            }
            _state.value = AssistantState.Executing
            return if (tap.isCompleted) Reply.Tapped(tap.getCompleted()) else Reply.Silent
        } finally {
            voice.stop()
            if (decision === tap) {
                decision = null
                _pending.value = null
            }
        }
    }

    /**
     * WhatsApp by voice: who (asked if not said; several matching names are read out to choose from),
     * what (asked if not said), then the message is read back and goes out only after "हाँ".
     * "नहीं" cancels; any other answer at that point is taken as the corrected message.
     */
    private suspend fun whatsAppFlow(request: WhatsAppRequest, byVoice: Boolean, speak: Boolean) {
        val action = AssistantAction.WhatsApp(request.contact, request.message.orEmpty())
        if (settings.value.safetyMode) {
            report(action, ActionStatus.BLOCKED, "Safety Mode is on, so the WhatsApp message was not sent. Turn it off with \"Turn off\" on the Home screen (or Settings → Safety Mode) to allow it.")
            return
        }
        _state.value = AssistantState.Executing
        suspend fun cancel(why: String = phrase(lang(), "ठीक है, मैसेज नहीं भेजा।", "ঠিক আছে, মেসেজ পাঠাইনি।")) {
            report(action, ActionStatus.CANCELLED, why)
            if (speak || byVoice) say(why, allowBargeIn = false)
        }
        if (byVoice && voice.isAvailable()) {
            // Answers are heard in this app; the normal listening loop waits until the task is done.
            listenGen++
            voice.stop()
        }

        var name = request.contact
        var target: Contact? = null
        var text = request.message
        var style = request.style
        var notFoundBefore: String? = null
        var rounds = 0
        // ---- who
        while (target == null) {
            if (++rounds > 4) return cancel()
            if (name == null) {
                if (!byVoice) {
                    report(action, ActionStatus.FAILED, phrase(lang(), "किसको भेजना है? नाम के साथ लिखिए, जैसे \"Rizwan Travels को WhatsApp करो कि पेमेंट भेजो\"।", "কাকে পাঠাব? নাম সহ লিখুন।"))
                    return
                }
                val q = notFoundBefore?.let { phrase(lang(), "\"$it\" नाम Contacts में नहीं मिला। नाम फिर से बोलिए, जैसे phone में save है।", "\"$it\" নামে কাউকে পাইনি। নামটা আবার বলুন।") }
                    ?: phrase(lang(), "किसको WhatsApp करना है? नाम बोलिए।", "কাকে হোয়াটসঅ্যাপ করবেন? নাম বলুন।")
                when (val r = hearReply(q, null, byVoice = true, speak = speak)) {
                    is Reply.Said -> {
                        if (VoiceReply.yesNo(r.text) == false) return cancel()
                        // "Rizwan Travels को" / "Rizwan Travels को मैसेज भेजो कि …" as the answer
                        val parsed = WhatsAppRequest.parse(r.text)
                        name = parsed?.contact ?: r.text.trim().replace(Regex("[\\s,.!?।]+(?:को|ko)[\\s,.!?।]*$", RegexOption.IGNORE_CASE), "").trim(',', '.', '!', '?', '।', ' ')
                        if (text == null && parsed?.message != null) { text = parsed.message; style = parsed.style ?: style }
                    }
                    else -> return cancel()
                }
            }
            val said = name ?: continue
            // A phone number said instead of a name ("98765 43210 को मैसेज करो").
            val digitsOnly = said.filter { it.isDigit() }
            if (digitsOnly.length >= 7 && digitsOnly.length * 2 >= said.count { !it.isWhitespace() }) {
                target = Contact(said, said)
                break
            }
            val found = lookup(said) ?: run {
                report(action, ActionStatus.CANCELLED, "Contacts permission was not granted, so \"$said\" could not be found.")
                return
            }
            val ranked = com.raju.aiassistant.domain.tools.ContactMatch.rank(said, found, limit = 6)
                .ifEmpty { found.take(6).map { com.raju.aiassistant.domain.tools.ContactMatch.Scored(it, 0.5) } }
            trace("whatsapp: ${ranked.size} contact(s) for the name")
            when {
                ranked.isEmpty() -> {
                    if (!byVoice) {
                        val missing = phrase(lang(), "Contacts में \"$said\" नाम का कोई नंबर नहीं मिला। Contacts में जो नाम सेव है वही लिखिए, या नंबर लिखिए।", "কন্টাক্টে \"$said\" নামে কোনো নম্বর পাইনি।")
                        report(action, ActionStatus.FAILED, missing)
                        if (speak) say(missing)
                        return
                    }
                    notFoundBefore = said
                    name = null
                }
                ranked.size == 1 || com.raju.aiassistant.domain.tools.ContactMatch.isClearWinner(ranked) -> target = ranked[0].contact
                else -> {
                    val options = ranked.take(4).map { it.contact }
                    val list = options.mapIndexed { i, c -> "${i + 1}. ${c.name}" }.joinToString(", ")
                    val q = phrase(lang(), "\"$said\" से ${options.size} नाम मिले: $list. किसको भेजूँ?", "\"$said\" নামে ${options.size}টি নাম পেয়েছি: $list. কাকে পাঠাব?")
                    val dialog = PendingRequest(PendingRequest.Kind.CONFIRM, "किसको भेजें?", q, options = options)
                    when (val r = hearReply(q, dialog, byVoice, speak)) {
                        is Reply.Tapped -> {
                            val d = r.decision
                            target = (if (d is Decision.Approve) d.option else null) ?: return cancel()
                        }
                        is Reply.Said -> {
                            val pick = VoiceReply.choice(r.text, options.size)
                            when {
                                pick != null -> target = options[pick]
                                VoiceReply.yesNo(r.text) == false -> return cancel()
                                else -> {
                                    val again = com.raju.aiassistant.domain.tools.ContactMatch.rank(r.text, options)
                                    if (again.isNotEmpty() && again[0].score >= com.raju.aiassistant.domain.tools.ContactMatch.FUZZY_MIN) target = again[0].contact
                                    else name = r.text.trim() // another name: search again
                                }
                            }
                        }
                        Reply.Silent -> return cancel()
                    }
                }
            }
        }
        val to = target ?: return cancel()
        // ---- what
        if (text == null) {
            val q = phrase(lang(), "${to.name} को। क्या मैसेज भेजूँ?", "${to.name} কে। কী মেসেজ পাঠাব?")
            if (!byVoice) {
                report(action, ActionStatus.FAILED, phrase(lang(), "${to.name} को क्या लिखना है? पूरा लिखिए, जैसे \"${to.name} को WhatsApp करो कि …\"।", "${to.name} কে কী লিখব?"))
                return
            }
            when (val r = hearReply(q, null, byVoice = true, speak = speak)) {
                is Reply.Said -> {
                    // "नहीं" here: the name was wrong.
                    if (VoiceReply.yesNo(r.text) == false) {
                        return whatsAppFlow(WhatsAppRequest(null, null, style), byVoice, speak)
                    }
                    val (m, st) = WhatsAppRequest.message(r.text)
                    text = m ?: return cancel()
                    style = st ?: style
                }
                else -> return cancel()
            }
        }
        var message = text ?: return cancel()
        style?.let { message = restyle(message, it) }
        // ---- "भेज दूँ?"
        repeat(3) {
            val q = phrase(lang(), "${to.name} को WhatsApp पर यह भेजूँगी: \"$message\"। भेज दूँ?", "${to.name} কে হোয়াটসঅ্যাপে পাঠাব: \"$message\"। পাঠিয়ে দিই?")
            val dialog = PendingRequest(PendingRequest.Kind.CONFIRM, "WhatsApp पर भेजें?", "${to.name} (${to.number})\n\n\"$message\"\n\n\"हाँ\" बोलिए या Send दबाइए, \"नहीं\" बोलिए तो नहीं जाएगा।")
            when (val r = hearReply(q, dialog, byVoice, speak)) {
                is Reply.Tapped -> return if (r.decision is Decision.Approve) sendWhatsApp(action, to, message, speak || byVoice) else cancel()
                is Reply.Said -> when (VoiceReply.yesNo(r.text)) {
                    true -> return sendWhatsApp(action, to, message, speak || byVoice)
                    false -> return cancel()
                    null -> {
                        // "नहीं, लिखो कि …" or a new message: read the corrected one back.
                        val corrected = WhatsAppRequest.message(VoiceReply.afterNo(r.text) ?: r.text)
                        if (r.text.trim().split(Regex("\\s+")).size >= 2 && corrected.first != null) {
                            message = corrected.second?.let { st -> restyle(corrected.first!!, st) } ?: corrected.first!!
                        }
                    }
                }
                Reply.Silent -> return cancel()
            }
        }
        cancel()
    }

    private suspend fun sendWhatsApp(action: AssistantAction.WhatsApp, to: Contact, message: String, speak: Boolean) {
        val autoSend = com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
        trace("whatsapp: confirmed, auto-send ${if (autoSend) "on" else "off"}")
        val outcome = tools.openWhatsApp(to.number, message)
        report(AssistantAction.WhatsApp(to.name, message), outcome)
        if (speak) {
            say(
                when {
                    outcome !is ToolOutcome.Done -> phrase(lang(), "WhatsApp नहीं खुल पाया।", "হোয়াটসঅ্যাপ খুলতে পারলাম না।")
                    autoSend -> phrase(lang(), "ठीक है, ${to.name} को मैसेज भेज रही हूँ।", "ঠিক আছে, ${to.name} কে মেসেজ পাঠাচ্ছি।")
                    else -> phrase(lang(), "${to.name} के लिए WhatsApp में मैसेज तैयार है, बस Send दबा दीजिए।", "${to.name} এর জন্য মেসেজ তৈরি, শুধু Send চাপুন।")
                },
                allowBargeIn = false,
            )
        }
    }

    /** "Hinglish में" / "English में": rewritten by the AI when it answers quickly, else spelled in Latin letters. */
    private suspend fun restyle(text: String, style: MessageStyle): String {
        if (style == MessageStyle.HINDI) return text
        val hasIndic = com.raju.aiassistant.domain.language.Transliterate.hasIndic(text)
        if (style == MessageStyle.HINGLISH && !hasIndic) return text
        val local = com.raju.aiassistant.domain.language.Transliterate.toLatin(text)
        if (!isOnline()) return local
        val id = settings.value.defaultProvider
        val key = keyFor(id) ?: return local
        val provider = providerFor(id, key)
        val model = (resolveModel(id, key, provider) as? AiResult.Success)?.value ?: return local
        val instruction = if (style == MessageStyle.HINGLISH) {
            "Rewrite this WhatsApp message in Hinglish: Hindi words written in Roman (Latin) letters, the way Indians type on WhatsApp. Keep the meaning, tone and names. Reply with only the message."
        } else {
            "Translate this WhatsApp message to short, simple English. Keep names as they are. Reply with only the message."
        }
        val r = withTimeoutOrNull(8_000) { provider.chat(model, instruction, listOf(Turn(TurnRole.USER, text))) }
        val out = (r as? AiResult.Success)?.value?.trim()?.trim('"', '“', '”')?.trim()
        return out?.takeIf { it.isNotBlank() && it.length <= text.length * 3 + 40 && !com.raju.aiassistant.domain.language.Transliterate.hasIndic(it) } ?: local
    }

    private suspend fun whatsAppCall(action: AssistantAction.WhatsAppCall, speak: Boolean = false) {
        var target: Contact? = null
        if (!action.number.isNullOrBlank()) {
            target = Contact(action.contact ?: action.number, action.number)
        } else if (action.contact != null) {
            val found = lookup(action.contact)
            if (found.isNullOrEmpty()) {
                note(if (found == null) "Contacts permission not granted." else "No contact named \"${action.contact}\".")
            } else {
                target = if (found.size == 1 && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
                    found[0]
                } else {
                    confirmContact(if (action.video) "WhatsApp video call?" else "WhatsApp call?", "Start WhatsApp ${if (action.video) "video call" else "call"} to:", found)
                        ?: run { report(action, ActionStatus.CANCELLED, "WhatsApp call cancelled."); return }
                }
            }
        }
        val targetNumber = target?.number ?: action.number
        if (targetNumber == null && !com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
            report(action, ActionStatus.FAILED, "No recipient specified for WhatsApp call.")
            return
        }
        val outcome = tools.openWhatsAppCall(targetNumber, action.video)
        report(action, outcome)
        if (speak && outcome is ToolOutcome.Done) {
            val name = target?.name ?: action.contact ?: "जिओ"
            val typeHi = if (action.video) "वीडियो कॉल" else "कॉल"
            val typeBn = if (action.video) "ভিডিও কল" else "কল"
            say(phrase(lang(), "हाँ राजू, मैंने $name को व्हाट्सएप $typeHi लगा दी है!", "হ্যাঁ রাজু, আমি $name কে হোয়াটসঅ্যাপ $typeBn লাগিয়ে দিয়েছি!"))
        }
    }

    /** Returns contacts, or null if permission was refused. Handles the permission prompt. */
    private suspend fun lookup(query: String): List<Contact>? {
        repeat(2) {
            when (val r = tools.findContacts(query)) {
                is ContactLookup.Found -> return distinctContacts(r.contacts)
                is ContactLookup.Failed -> { note(r.message); return emptyList() }
                is ContactLookup.NeedsPermission -> {
                    val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "Contacts permission", r.reason, permission = r.permission))
                    if (d !is Decision.PermissionResult || !d.granted) return null
                }
            }
        }
        return null
    }

    private suspend fun confirmContact(title: String, message: String, candidates: List<Contact>): Contact? {
        val d = ask(PendingRequest(PendingRequest.Kind.CONFIRM, title, message, options = candidates.take(8)))
        return when {
            d !is Decision.Approve -> null
            d.option != null -> d.option
            candidates.size == 1 -> candidates[0]
            else -> null
        }
    }

    private suspend fun remember(action: AssistantAction.Remember) {
        val s = settings.value
        when {
            !s.memoryEnabled -> report(action, ActionStatus.BLOCKED, "Memory is turned off in Settings, so nothing was saved.")
            else -> when (val check = validateMemory(action.text, runCatching { memories.all().map { it.text } }.getOrDefault(emptyList()))) {
                is MemoryCheck.Rejected -> report(action, ActionStatus.BLOCKED, check.reason)
                is MemoryCheck.Ok -> {
                    val d = ask(PendingRequest(PendingRequest.Kind.CONFIRM, "Remember this?", check.text))
                    if (d !is Decision.Approve) report(action, ActionStatus.CANCELLED, "Not saved.")
                    else {
                        val saved = runCatching { memories.add(check.text) }.isSuccess
                        report(action, if (saved) ActionStatus.SUCCESS else ActionStatus.FAILED, if (saved) "Saved to memory: \"${check.text}\"" else "Could not save the memory.")
                    }
                }
            }
        }
    }

    private suspend fun searchWeb(action: AssistantAction.SearchWeb, provider: AiProvider, model: String, system: String, speak: Boolean) {
        val key = secret(SecretId.TAVILY_API_KEY)
        if (key.isNullOrBlank()) {
            report(action, ActionStatus.FAILED, "Web search needs a Tavily API key (Settings → Web search).")
            return
        }
        // Searching takes a few seconds: like a person, say so instead of going silent.
        if (speak && !replySpoken) say(phrase(lang(), "एक सेकंड, देखती हूँ…", "এক সেকেন্ড, দেখছি…"), allowBargeIn = false)
        _state.value = AssistantState.Executing
        val response = when (val r = searchFor(key).search(action.query, topic = action.topic)) {
            is AiResult.Success -> r.value
            is AiResult.Failure -> { report(action, ActionStatus.FAILED, "Web search failed: ${r.message}"); return }
        }
        if (response.results.isEmpty()) {
            report(action, ActionStatus.FAILED, "Web search returned no results for \"${action.query}\".")
            return
        }
        actionLog.safeLog(action, ActionStatus.SUCCESS, "${response.results.size} results")
        _state.value = AssistantState.Thinking
        val followUp = buildSearchFollowUp(action.query, response.results.map { it.title to it.url }, response.results.map { it.snippet }, response.answer)
        val turns = historyTurns(model) +
            com.raju.aiassistant.domain.ai.Turn(com.raju.aiassistant.domain.ai.TurnRole.USER, followUp)
        val sources = response.results.mapIndexed { i, r ->
            "[${i + 1}] ${r.title} — ${r.domain}" + (r.publishedDate?.let { " ($it)" } ?: "") + "\n${r.url}"
        }.joinToString("\n")
        when (val r = provider.chat(model, system, turns)) {
            is AiResult.Success -> {
                val summary = parseReply(r.value).text // actions in follow-ups are ignored on purpose
                record(ChatRole.ASSISTANT, "$summary\n\nSources:\n$sources")
                if (speak) say(summary)
            }
            is AiResult.Failure -> {
                record(ChatRole.SYSTEM, "Found sources but the AI summary failed (${r.message}).\n$sources")
                _state.value = AssistantState.Error
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun ask(request: PendingRequest): Decision {
        val d = CompletableDeferred<Decision>()
        decision = d
        _pending.value = request
        val before = _state.value
        if (request.kind == PendingRequest.Kind.PERMISSION) _state.value = AssistantState.WaitingPermission
        return try {
            d.await()
        } finally {
            if (_state.value == AssistantState.WaitingPermission) _state.value = before
            if (decision === d) {
                _pending.value = null
                decision = null
            }
        }
    }

    private suspend fun report(action: AssistantAction, outcome: ToolOutcome) {
        when (outcome) {
            is ToolOutcome.Done -> report(action, ActionStatus.SUCCESS, outcome.message)
            is ToolOutcome.Failed -> report(action, ActionStatus.FAILED, outcome.message)
            is ToolOutcome.NeedsPermission -> report(action, ActionStatus.FAILED, outcome.reason)
        }
    }

    private suspend fun report(action: AssistantAction, status: ActionStatus, message: String) {
        val icon = when (status) {
            ActionStatus.SUCCESS -> "✅"
            ActionStatus.FAILED -> "❌"
            ActionStatus.CANCELLED -> "⏹️"
            ActionStatus.BLOCKED -> "🛡️"
        }
        record(ChatRole.SYSTEM, "$icon $message")
        actionLog.safeLog(action, status, message)
        if (status == ActionStatus.FAILED) _state.value = AssistantState.Error
    }

    private suspend fun ActionLog.safeLog(action: AssistantAction, status: ActionStatus, message: String) {
        runCatching { log(action.javaClass.simpleName, "${action.label} — $message".take(300), status) }
    }

    private suspend fun note(text: String) = record(ChatRole.SYSTEM, text)

    private suspend fun record(role: ChatRole, text: String, via: String? = null) {
        val saved = if (settings.value.saveHistory) runCatching { history.add(role, text) }.getOrNull() else null
        val message = ChatMessage(id = saved ?: localId--, conversationId = 0, role = role, text = text, createdAt = clock(), via = via)
        // Only the recent conversation is kept in memory; the full history stays in the database.
        _transcript.value = (_transcript.value + message).takeLast(MAX_TRANSCRIPT_IN_MEMORY)
    }

    private suspend fun fail(message: String, state: AssistantState = AssistantState.Error, spoken: String? = null) {
        _lastError.value = message
        record(ChatRole.SYSTEM, "⚠️ $message")
        trace("error shown")
        if (voiceTurn) {
            // The user is talking, maybe not looking at the screen: say briefly that it failed.
            voiceTurn = false
            say(spoken ?: phrase(lang(), "माफ़ कीजिए, अभी जवाब नहीं दे पाई — वजह स्क्रीन पर लिखी है।", "দুঃখিত, এখন উত্তর দিতে পারলাম না — কারণ স্ক্রিনে লেখা আছে।"), allowBargeIn = false)
        }
        _state.value = state
        _voiceSession.value = false
        scheduleErrorReset()
        if (handsFree && !settings.value.assistantPaused && voice.isAvailable()) {
            // Hands-free: show the error, then go back to listening.
            scope.launch {
                delay(4_500)
                if (handsFree && !_voiceSession.value && job?.isActive != true && _pending.value == null) startVoice()
            }
        }
    }

    /** What she says without internet: what still works. */
    private fun offlineSpoken() = phrase(
        lang(),
        "इंटरनेट नहीं है। बिना इंटरनेट के मैं टॉर्च, टाइमर, अलार्म, कॉल, फोटो और ऐप खोलने जैसे काम कर सकती हूँ।",
        "ইন্টারনেট নেই। ইন্টারনেট ছাড়া আমি টর্চ, টাইমার, অ্যালার্ম, কল, ছবি আর অ্যাপ খোলার মতো কাজ করতে পারি।",
    )

    /** Error is shown briefly, then the reactor returns to Ready. */
    private fun scheduleErrorReset() {
        scope.launch {
            delay(4_000)
            if (_state.value == AssistantState.Error || _state.value == AssistantState.WaitingPermission) _state.value = AssistantState.Idle
        }
    }

    private fun failAsync(message: String) {
        scope.launch { fail(message, AssistantState.Error) }
    }

    private fun cancelWork() {
        decision?.complete(Decision.Deny)
        job?.cancel(CancellationException("Stopped by user"))
        job = null
    }
}
