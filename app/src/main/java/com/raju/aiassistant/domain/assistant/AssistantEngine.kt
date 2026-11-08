package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.tools.PhotoResult
import com.raju.aiassistant.domain.notify.callCommand
import com.raju.aiassistant.domain.notify.callReplyText
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
import com.raju.aiassistant.domain.tools.MediaCommand
import com.raju.aiassistant.domain.tools.BeaconStop
import com.raju.aiassistant.domain.tools.PhoneBeacon
import com.raju.aiassistant.domain.tools.saysPhoneFound
import com.raju.aiassistant.domain.screen.FlowOutcome
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
import com.raju.aiassistant.domain.screen.ElementKind
import com.raju.aiassistant.domain.screen.RiskyButtons
import com.raju.aiassistant.domain.screen.ScreenMatch
import com.raju.aiassistant.domain.screen.ScreenOp
import com.raju.aiassistant.domain.screen.ScreenRead
import com.raju.aiassistant.domain.screen.ScreenSnapshot
import com.raju.aiassistant.domain.screen.SensitiveScreens
import com.raju.aiassistant.domain.screen.confirmReply
import com.raju.aiassistant.domain.screen.describeForAi
import com.raju.aiassistant.domain.screen.localSummary
import com.raju.aiassistant.domain.tools.ContactMatch
import com.raju.aiassistant.domain.tools.PhoneNumbers
import com.raju.aiassistant.domain.tools.normalizeForWhatsApp

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
/** How often the background guard checks that Seema is back to waiting for "Hey Seema". */
private const val BACKGROUND_GUARD_MS = 3_000L
/** How long Seema waits to see whether WhatsApp's Send/Call button was really pressed. */
private const val AUTO_ACTION_WAIT_MS = 9_000L
/** How long a spoken "haan / nahi" is listened for after "ये बटन दबा दूँ?". */
private const val SCREEN_CONFIRM_WINDOW_MS = 12_000L
/** Time the app on screen gets to react before Seema checks that the action really happened. */
private const val SCREEN_VERIFY_DELAY_MS = 900L
/** How long Seema waits for the phone to report that a call she placed has started. */
private const val CALL_START_WAIT_MS = 8_000L
/** Failures where another provider may still answer (not a blocked or empty request). */
private const val TEST_SYSTEM_PROMPT = "You are a connection test. Reply with the single word OK."
private val FALLBACK_KINDS = setOf(FailureKind.RATE_LIMITED, FailureKind.QUOTA_EXCEEDED, FailureKind.TIMEOUT, FailureKind.SERVER, FailureKind.INVALID_KEY, FailureKind.UNKNOWN)
/** Consecutive recogniser errors (not silence) tolerated in hands-free mode before it stops. */
/** How long after a caller announcement "haan / utha lo" or "kaat do" is listened for. */
private const val CALL_COMMAND_WINDOW_MS = 25_000L
/** How long she listens for "haan / rehne do" after "Padh ke sunau kya?". */
private const val READ_REPLY_WINDOW_MS = 7_000L
/** Extra time for an answer that started inside the window to be transcribed. */
private const val READ_REPLY_GRACE_MS = 4_000L
/** Pause after her question before the microphone opens, so the end of her own voice is not heard. */
private const val ECHO_SETTLE_MS = 400L
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
internal fun cooldownMsFor(kind: FailureKind, message: String = ""): Long = when {
    // A per-minute limit (Groq: "tokens per minute (TPM)", requests per minute) is free again within a
    // minute: benching the provider for 15 minutes sent every later turn to slower AIs (13-18 s on the phone).
    (kind == FailureKind.RATE_LIMITED || kind == FailureKind.QUOTA_EXCEEDED) && isPerMinuteLimit(message) -> 60_000L
    kind == FailureKind.RATE_LIMITED || kind == FailureKind.QUOTA_EXCEEDED || kind == FailureKind.INVALID_KEY -> 15 * 60_000L
    else -> 60_000L
}

/** "tokens per minute (TPM)", "requests per minute (RPM)": a limit that resets within a minute. */
internal fun isPerMinuteLimit(message: String): Boolean {
    val m = message.lowercase()
    return "per minute" in m || "(tpm)" in m || "(rpm)" in m
}

private const val MAX_LISTEN_FAILURES = 3
/** A "no speech" sooner than this is the recogniser failing, not a quiet room. */
private const val FAST_NO_SPEECH_MS = 1_000L
/** After this many fast "no speech" in a row, the re-listen waits 2 s instead of 300 ms. */
private const val BACKOFF_AFTER_FAST_NO_SPEECH = 3
/** After this many, the hands-free loop stops and she waits (standby or the Mic button). */
private const val MAX_FAST_NO_SPEECH = 6
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
    /** Assistant switched OFF: give every microphone lease back at the hardware level (MicHub.releaseAll). */
    private val releaseMic: () -> Unit = {},
    /** Switched ON again: the microphone may be used again (MicHub.unmute). */
    private val restoreMic: () -> Unit = {},
    /** PNR status from the user's PNR service; NotConfigured falls back to the official page. */
    private val pnrSource: com.raju.aiassistant.domain.business.PnrSource = com.raju.aiassistant.domain.business.PnrSource { com.raju.aiassistant.domain.business.PnrFetch.NotConfigured },
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
    /** She asked "Padh ke sunau kya?" a short while ago and the message is still unread. */
    private val notificationFollowUp: () -> Boolean = { false },
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
    /** When the current listen started (real time): tells a quick "no speech" from a normal quiet turn. */
    private var listenStartedAt = 0L
    /** "No speech" errors in a row that came back quickly. */
    private var fastNoSpeech = 0
    /**
     * Another app is on screen (Seema opened WhatsApp, YouTube, the camera…). Then she must always
     * end up waiting for "Hey Seema" — never deaf, and never listening openly to the other app's sound.
     */
    @Volatile private var inBackground = false
    private var backgroundGuard: Job? = null
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
        // Switched off: nothing that was heard is acted on or answered.
        if (settings.value.assistantPaused) { trace("off: heard result dropped"); return }
        trace("recognized (${heard.length} chars)")
        fastNoSpeech = 0
        if (handleVoiceConfirm(heard)) return
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
        if (canSleep() && (inBackground || settings.value.voiceMode != VoiceMode.SESSION)) {
            goToSleep()
        } else {
            _state.value = AssistantState.Idle
        }
    }

    /** App went to the background: keep wake detector alive if wakeWord is on (supported by SeemaWakeService). */
    fun suspendListening() {
        inBackground = true
        conversationMode.exit()
        heldJob?.cancel()
        heldVoiceText = null
        if (canSleep()) {
            // SeemaWakeService (a microphone foreground service) keeps the wake word alive while
            // another app is on screen. A turn still running (her "WhatsApp khol diya") finishes
            // first and then goes to sleep itself; the guard catches every other way out.
            trace("app in background: standby for \"Hey Seema\"")
            if (!asleep && !pausedForCall && job?.isActive != true) goToSleep()
            startBackgroundGuard()
        } else {
            trace("app in background: wake word off, not listening")
            if (asleep) {
                leaveSleep()
            }
            _voiceSession.value = false
            listenGen++
            voice.stop()
            _state.value = AssistantState.Idle
        }
    }

    /** Seema's own screen is visible again (ON_START): normal foreground behaviour. */
    fun onAppForeground() {
        if (inBackground) trace("app on screen")
        inBackground = false
        backgroundGuard?.cancel()
        backgroundGuard = null
    }

    /** "Hey Seema" standby is possible: wake word on, not switched off, a listener exists. */
    private fun canSleep(): Boolean =
        settings.value.wakeWord && !settings.value.assistantPaused && wakeListener !== NoWakeListener

    /**
     * While another app is on screen, whatever path a turn ends on (an error, silence, a session
     * timing out, a yes/no question nobody answered), Seema returns to waiting for her name.
     */
    private fun startBackgroundGuard() {
        backgroundGuard?.cancel()
        backgroundGuard = scope.launch {
            while (inBackground) {
                delay(BACKGROUND_GUARD_MS)
                if (!inBackground) break
                val st = _state.value
                val busy = st == AssistantState.Thinking || st == AssistantState.Speaking ||
                    st == AssistantState.Executing || st == AssistantState.Listening
                if (canSleep() && !asleep && !pausedForCall && !busy && job?.isActive != true &&
                    !_voiceSession.value && _pending.value == null && replyHandler == null && clock() >= callCommandUntil
                ) {
                    trace("background: back to \"Hey Seema\" standby")
                    goToSleep()
                }
            }
        }
    }

    /**
     * Call-style audio only serves barge-in, which needs headphones: on the loudspeaker a Samsung
     * phone played her voice from the earpiece in call-style audio, like a normal phone call.
     */
    private fun callStyleAllowed() = settings.value.bargeIn && voice.capturesInApp() && headphones()

    private fun listen() {
        if (paused()) {
            // Switched OFF: never open the microphone, whatever asked for it.
            voice.stop()
            _voiceSession.value = false
            _state.value = AssistantState.Idle
            return
        }
        val gen = ++listenGen
        _state.value = AssistantState.Listening
        _heard.value = null
        _hint.value = null
        // The system recogniser (another app) gets only silence while call-style audio is on, and
        // without headphones call-style audio would play her voice from the earpiece.
        if (!callStyleAllowed()) conversationMode.exit()
        trace("listen ${if (handsFree) "hands-free" else "tap"} ${if (voice.capturesInApp()) "in-app" else "system"} recogniser")
        listenStartedAt = clock()
        voice.start(
            lang().tag,
            onResult = { heard -> if (gen == listenGen) onHeard(heard) else trace("stale result dropped") },
            onError = { err ->
                if (gen != listenGen) { trace("stale error dropped"); return@start }
                trace("listen error: ${err.message}")
                // Waiting for "haan / nahi" to a screen action: keep listening until the window ends.
                if (voiceConfirmOpen()) {
                    scope.launch { delay(300); if (voiceConfirmOpen()) listen() }
                    return@start
                }
                // "Padh ke sunau kya?": a short "haan / nahi" often comes back as low-confidence. Its
                // yes/no words are still clear enough; anything else is asked again while time remains.
                if (replyHandler != null) {
                    val unsure = err.heard?.let { readReply(it) }
                    when {
                        unsure != null -> { trace("read reply: ${if (unsure) "yes" else "no"} (low confidence)"); finishReadReply(unsure) }
                        (err.noSpeech || err.unclear) && clock() < replyUntil - 1_000 -> scope.launch { delay(250); if (replyHandler != null) listen() }
                        else -> finishReadReply(null)
                    }
                    return@start
                }
                if (clock() < callCommandUntil) {
                    // While it rings: "utha lo / kaat do" heard unsurely still counts (never an SMS from
                    // unsure words); silence or noise → listen again for the rest of the ring.
                    val unsure = err.heard?.let { callCommand(it) }
                    if (unsure != null && err.heard != null && callReplyText(err.heard) == null) {
                        handleCallCommand(err.heard)
                    } else if ((err.noSpeech || err.unclear) && clock() < callCommandUntil - 1_500) {
                        trace("call: nothing clear yet, listening again")
                        scope.launch { delay(250); if (clock() < callCommandUntil) listen() }
                    } else {
                        callCommandUntil = 0
                        trace("call: no answer heard, stop listening")
                        returnToStandby()
                    }
                    return@start
                }
                val keepGoing = handsFree && _voiceSession.value && !settings.value.assistantPaused && !err.permissionDenied
                val held = heldVoiceText
                if (inBackground && held == null && canSleep() && (err.noSpeech || err.unclear)) {
                    // Another app is on screen: no open listening to its sound, wait for her name.
                    trace("background: nothing said, back to standby")
                    goToSleep()
                } else if (held != null && (err.noSpeech || err.unclear)) {
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
                    // A "no speech" that comes back almost at once is not a quiet room: the recogniser is
                    // failing (seen on a phone: 0.4 s each time). Back off, and stop the loop after several.
                    fastNoSpeech = if (err.noSpeech && clock() - listenStartedAt < FAST_NO_SPEECH_MS) fastNoSpeech + 1 else 0
                    if (sleepDue()) {
                        // Always-listening sleeps and waits for her name; a session simply ends (mic off).
                        if (settings.value.voiceMode == VoiceMode.ALWAYS) goToSleep() else endSession()
                    } else if (fastNoSpeech >= MAX_FAST_NO_SPEECH) {
                        trace("no speech again and again: listening loop paused")
                        fastNoSpeech = 0
                        handsFree = false
                        _handsFreeActive.value = false
                        _hint.value = phrase(lang(), "मुझे आवाज़ नहीं सुनाई दे रही। Mic दबाकर या \"हे सीमा\" बोलकर फिर कहिए।", "আমি শুনতে পাচ্ছি না। Mic চেপে বা \"হে সীমা\" বলে আবার বলুন।")
                        returnToStandby()
                    } else {
                        relisten(if (fastNoSpeech >= BACKOFF_AFTER_FAST_NO_SPEECH) 2_000 else 300)
                    }
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

    /**
     * The master switch OFF: everything stops at once — the running request, speech, listening, the
     * "Hey Seema" listener — and the microphone is released at the hardware level. Nothing listens
     * again (no standby, no wake word, no hands-free) until [resumeAssistant].
     */
    fun pauseAssistant() {
        stop()
        asleep = false
        wakeListener.stop()
        callCommandUntil = 0
        replyHandler?.let { finishReadReply(null) }
        releaseMic()
        _state.value = AssistantState.Idle
        trace("off: microphone released")
    }

    /** The master switch ON: the microphone may be used again; the app starts standby / hands-free. */
    fun resumeAssistant() {
        restoreMic()
        trace("on: microphone allowed")
    }

    private fun paused() = settings.value.assistantPaused

    /** Stop button: immediately stops listening, speech, pending questions and any running request. */
    fun stop() {
        beacon.stop(BeaconStop.STOPPED)
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
        if (inBackground && canSleep()) {
            // Behind another app the microphone must not go fully off: wait for "Hey Seema".
            goToSleep()
            return
        }
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
        if (paused()) {
            // Switched OFF: no "Hey Seema" standby either.
            asleep = false
            _state.value = AssistantState.Idle
            return
        }
        asleep = true
        _state.value = AssistantState.Sleeping
        armWakeListener()
    }

    private fun armWakeListener() {
        if (!asleep || paused()) return
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
        // The tail of her own voice must not wake her. When her name can be checked (Deepgram), the
        // words decide instead: "Hey Seema …" said right after her answer used to be thrown away.
        val nameCheckable = settings.value.wakeWord && transcriber?.isAvailable() == true
        if (now - lastSpokeAt < 4_000L && !nameCheckable) {
            armWakeListener()
            return
        }
        trace("wake audio: ${pcm.size} bytes")
        val t = transcriber
        if (t != null && t.isAvailable()) {
            // Keep listening while her name is checked online (1-3 s on a slow connection): a
            // "Hey Seema" said during the check used to fall into that gap and was never heard.
            armWakeListener()
            var raw = runCatching { t.transcribe(pcm) }.getOrNull()
            if (raw == null && asleep) {
                // The check itself failed (network): try once more rather than drop her name.
                trace("wake: name check failed, trying once more")
                raw = runCatching { t.transcribe(pcm) }.getOrNull()
            }
            val heard = raw?.trim().orEmpty()
            trace("wake heard: $heard")
            if (!asleep) return
            val request = if (settings.value.wakeWord) afterWakeWord(heard) else heard.takeIf { it.length >= 2 }
            trace("wake request: $request")
            when {
                request == null -> Unit // someone else talking, TV, noise: keep sleeping (still listening)
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
        if (beacon.active.value) {
            // While the phone rings to be found the microphone mostly hears the ringing itself:
            // only "mil gaya / bas / ruko" counts, nothing else goes to the AI.
            if (!saysPhoneFound(text)) {
                trace("beacon ringing: ignored \"${text.take(40)}\"")
                finishTurn()
                return
            }
            if (recordUser) record(ChatRole.USER, text)
            beacon.stop(BeaconStop.FOUND_SAID)
            say(phrase(lang(), "बढ़िया राजू जी, फोन मिल गया!", "দারুণ রাজু, ফোন পাওয়া গেছে!"))
            finishTurn()
            return
        }
        if (recordUser) record(ChatRole.USER, text)
        val s = settings.value

        // Simple phone commands ("torch on karo", "Mummy ko call karo", "5 minute ka timer") run on
        // the phone at once: no AI request, no tokens, and they also work without internet.
        // "Batao" / "haan padho" a little after "Padh ke sunau kya?": read that message.
        if (notificationFollowUp() && readReply(text) == true && text.trim().split(Regex("\\s+")).size <= 6) {
            if (runLocalCommand(AssistantAction.ReadNotification, speak = fromVoice || s.autoSpeakReplies)) return
        }
        var screenQuestion: AssistantAction.ReadScreen? = null
        LocalCommands.parse(text)?.let { action ->
            if (action is AssistantAction.ReadScreen && isOnline()) {
                // "Screen padho": the screen goes to the AI in one request (no routing request first).
                trace("fast route: ${action.label} (matched in ${clock() - turnStartedAt} ms, one AI request)")
                screenQuestion = action
                return@let
            }
            // Fast route: a plain phone command is done on the phone, with no AI request at all.
            trace("fast route: ${action.label} (matched in ${clock() - turnStartedAt} ms, no AI)")
            if (runLocalCommand(action, speak = fromVoice || s.autoSpeakReplies)) return
            trace("fast route declined: ${action.label}; asking the AI")
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
            voiceTurn = fromVoice, recentOpenings = recentOpenings, partOfDay = partOfDay(hourOfDay()), business = businessSnapshot(),
            language = lang(), languageUnclear = languageUnclear, documents = documents,
        )
        val system = buildSystemPrompt(memoryTexts, nowDescription(), s.personality, context)
        screenQuestion?.let { action ->
            readScreenAction(action, provider, model, system, speak = fromVoice || s.autoSpeakReplies)
            if (_state.value != AssistantState.Error) _state.value = AssistantState.Success
            finishTurn()
            return
        }
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
                providerCooldown[providerId] = clock() + cooldownMsFor(streamed.kind, streamed.message)
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
    private val beacon = PhoneBeacon(scope, speakLine = { speaker.speak(it) }, onStopped = { trace("beacon stopped: ${it.name}") })

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
                providerCooldown[id] = now + cooldownMsFor(r.kind, r.message)
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
        if (s.safetyMode && (action is AssistantAction.Call || action is AssistantAction.OpenApp || action is AssistantAction.WhatsApp || action is AssistantAction.WhatsAppFile)) return false
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
            is AssistantAction.ReadNotification -> {
                trace("offline command: read notification")
                readNotification(action, speak)
            }
            is AssistantAction.GoHome -> {
                trace("offline command: home")
                val outcome = tools.goHome()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "लीजिए राजू, होम स्क्रीन पर आ गए।", "এই যে রাজু, হোম স্ক্রিনে চলে এসেছি।"))
            }
            is AssistantAction.Media -> {
                trace("offline command: media ${action.command}")
                val outcome = tools.mediaControl(action.command)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(mediaSpoken(action.command))
            }
            is AssistantAction.Screenshot -> {
                trace("offline command: screenshot")
                _state.value = AssistantState.Executing
                takeScreenshot(action, speak)
            }
            is AssistantAction.FindPhone -> {
                trace("offline command: find phone")
                findPhone(action, speak)
            }
            is AssistantAction.LastCall -> {
                trace("offline command: last call")
                _state.value = AssistantState.Executing
                checkLastCall(action, speak)
            }
            is AssistantAction.OpenSettings -> {
                trace("offline command: settings ${action.page}")
                val outcome = tools.openSettings(action.page)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "सेटिंग्स खोल दी है।", "সেটিংস খুলে দিয়েছি।"))
            }
            is AssistantAction.ScreenBack -> {
                // Still asks "पीछे जाऊँ?" first, exactly as when the AI asks for it.
                trace("offline command: back")
                _state.value = AssistantState.Executing
                screenBack(action, speak)
            }
            is AssistantAction.OpenSeema -> {
                trace("offline command: open seema")
                val outcome = tools.openSeema()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "हाँ राजू, मैं अपनी स्क्रीन पर वापस आ गई!", "হ্যাঁ রাজু, আমি আমার স্ক্রিনে ফিরে এসেছি!"))
            }
            is AssistantAction.ReadScreen -> {
                // Offline: the phone reads the screen out by itself.
                trace("offline command: read screen")
                val snap = screenOrSay(action, speak) ?: return true.also { finishTurn() }
                val msg = snap.localSummary(bengali = lang() == SeemaLanguage.BENGALI)
                report(action, ActionStatus.SUCCESS, "Screen padhi (bina AI): ${snap.appLabel}")
                record(ChatRole.ASSISTANT, msg)
                if (speak) say(msg)
            }
            is AssistantAction.PnrStatus -> {
                trace("offline command: pnr status")
                _state.value = AssistantState.Executing
                pnrStatus(action, speak)
            }
            is AssistantAction.WhatsAppFile -> {
                trace("offline command: whatsapp file")
                _state.value = AssistantState.Executing
                whatsAppFile(action, speak)
            }
            is AssistantAction.WhatsApp -> {
                // Same path as when the AI asks for it: contacts first, WhatsApp's own search second.
                trace("offline command: whatsapp")
                _state.value = AssistantState.Executing
                whatsApp(action, speak)
            }
            is AssistantAction.TellTime -> {
                trace("offline command: ${if (action.date) "date" else "time"}")
                informational(action, spokenClock(action), speak)
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
        if (inBackground && canSleep() && _voiceSession.value) {
            // Another app is on screen: listen on only when she just asked something
            // ("किसको भेजना है?"); otherwise wait for "Hey Seema", not for the app's sound.
            val asked = lastSpoken?.trimEnd()?.let { it.endsWith("?") || it.endsWith("？") } == true && clock() - lastSpokeAt < 5_000L
            if (asked) listen() else goToSleep()
        } else if (_voiceSession.value && (s.continuousConversation || handsFree) && !s.assistantPaused) {
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
        // Already announced and listening for "utha lo / bol do …": keep that listening going.
        if (clock() < callCommandUntil) return
        cancelWork()
    }

    /** The ringing caller (number as Android gave it, name from Contacts) for a reply by SMS. */
    @Volatile private var ringingNumber: String? = null
    @Volatile private var ringingName: String? = null

    /** After the caller announcement ("… ka call aa raha hai. Utha lun?"): listens for "haan / utha lo", "kaat do" or "bol do …". */
    fun listenForCallCommand(number: String? = null, name: String? = null) {
        ringingNumber = number
        ringingName = name
        if (settings.value.assistantPaused || !voice.isAvailable() || pausedForCall) return
        callCommandUntil = clock() + CALL_COMMAND_WINDOW_MS
        if (asleep) leaveSleep()
        _voiceSession.value = true
        listen()
    }

    /** "Utha lo" / "kaat do" while a call rings is handled here at once, without the AI. True when it was a call command. */
    private fun handleCallCommand(text: String): Boolean {
        if (clock() >= callCommandUntil) return false
        // "Bol do abhi busy hoon" / "10 minute mein call karta hoon": cut the call, send those words by SMS.
        callReplyText(text)?.let { reply ->
            callCommandUntil = 0
            trace("call: reject with an SMS reply")
            val ended = tools.endCall()
            val number = ringingNumber
            val name = ringingName
            scope.launch {
                report(AssistantAction.EndCall, ended)
                if (number.isNullOrBlank() || number.count { it.isDigit() } < 6) {
                    say(phrase(lang(), "कॉल काट दी। कॉल करने वाले का नंबर नहीं मिला, इसलिए मैसेज नहीं भेज पाई।", "কল কেটে দিয়েছি। নম্বর পাইনি, তাই মেসেজ পাঠাতে পারিনি।"))
                    return@launch
                }
                val who = name ?: number
                val sms = tools.sendSms(Contact(who, number), reply)
                report(AssistantAction.Sms(name, number, reply), sms)
                say(
                    if (sms is ToolOutcome.Done) phrase(lang(), "कॉल काट दी और $who को मैसेज भेज दिया: \"$reply\"", "কল কেটে $who কে মেসেজ পাঠিয়েছি: \"$reply\"")
                    else phrase(lang(), "कॉल काट दी, पर मैसेज नहीं गया — SMS की permission चाहिए।", "কল কেটেছি, কিন্তু মেসেজ যায়নি — SMS permission লাগবে।"),
                )
            }
            return true
        }
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
        // The window counts from when the microphone opens; an answer begun inside it gets time to be
        // transcribed (a hard cap, not a race against Deepgram's reply).
        replyUntil = clock() + ECHO_SETTLE_MS + windowMs
        replyTimeout?.cancel()
        replyTimeout = scope.launch {
            delay(ECHO_SETTLE_MS + windowMs + READ_REPLY_GRACE_MS)
            if (replyHandler != null) {
                trace("read reply: no answer")
                finishReadReply(null)
            }
        }
        if (asleep) leaveSleep()
        _voiceSession.value = true
        _state.value = AssistantState.Listening
        scope.launch {
            // Her question has finished playing (the announcer calls this after speak() returns);
            // a short pause more keeps the tail of her voice out of the microphone.
            delay(ECHO_SETTLE_MS)
            if (replyHandler == null) return@launch
            trace("read reply: listening")
            listen()
        }
        return true
    }

    private fun handleReadReply(text: String): Boolean {
        // Accepted until the window is closed by the timeout (an answer begun in time is not lost).
        if (replyHandler == null) return false
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
        // The call Seema just placed has really started: that is the proof she reports.
        if (callToVerify != null) scope.launch { reportCallStarted() }
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
        if (wasHandsFree && handsFree && !inBackground) startHandsFree() else returnToStandby()
    }

    fun interrupt() {
        speaker.stop()
    }

    // ---------------------------------------------------------------- actions

    private suspend fun execute(action: AssistantAction, provider: AiProvider, model: String, system: String, speak: Boolean) {
        val s = settings.value
        val sensitive = action is AssistantAction.Call || action is AssistantAction.WhatsApp ||
            action is AssistantAction.WhatsAppCall || action is AssistantAction.WhatsAppFile ||
            action is AssistantAction.OpenApp || action is AssistantAction.PlayMusic || action is AssistantAction.Sms ||
            action is AssistantAction.ScreenTap || action is AssistantAction.ScreenType
        if (s.safetyMode && sensitive) {
            report(action, ActionStatus.BLOCKED, "Safety Mode is on, so \"${action.label}\" was not performed. Turn it off with \"Turn off\" on the Home screen (or Settings → Safety Mode) to allow it.")
            return
        }
        _state.value = AssistantState.Executing
        when (action) {
            is AssistantAction.Call -> callContact(action, speak)
            is AssistantAction.WhatsApp -> whatsApp(action, speak)
            is AssistantAction.WhatsAppCall -> whatsAppCall(action, speak)
            is AssistantAction.WhatsAppFile -> whatsAppFile(action, speak)
            is AssistantAction.FindContact -> findContact(action, speak)
            is AssistantAction.LastCall -> checkLastCall(action, speak)
            is AssistantAction.ReadNotification -> readNotification(action, speak)
            is AssistantAction.Screenshot -> takeScreenshot(action, speak)
            is AssistantAction.ReadScreen -> readScreenAction(action, provider, model, system, speak)
            is AssistantAction.ScreenTap -> screenTap(action, speak)
            is AssistantAction.ScreenBack -> screenBack(action, speak)
            is AssistantAction.ScreenScroll -> screenScroll(action, speak)
            is AssistantAction.ScreenType -> screenType(action, speak)
            is AssistantAction.OpenApp -> report(action, tools.openApp(action.name))
            is AssistantAction.OpenSettings -> report(action, tools.openSettings(action.page))
            is AssistantAction.Battery -> {
                val outcome = tools.batteryStatus()
                report(action, outcome)
                // Informational result: in a voice conversation the user expects to hear it.
                if (speak && outcome is ToolOutcome.Done) say(outcome.message)
            }
            is AssistantAction.FindPhone -> findPhone(action, speak)
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
                if (speak && outcome is ToolOutcome.Done) say(mediaSpoken(action.command))
            }
            is AssistantAction.TellTime -> informational(action, spokenClock(action), speak)
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
            is AssistantAction.PnrStatus -> pnrStatus(action, speak)
            is AssistantAction.MandiPrice -> {
                val place = action.place?.let { " $it" }.orEmpty()
                searchWeb(AssistantAction.SearchWeb("${action.commodity} mandi bhav today$place price per quintal agmarknet"), provider, model, system, speak)
            }
            is AssistantAction.SaveFile -> report(action, tools.shareTextFile(action.name, action.content))
            is AssistantAction.Research -> research(action, provider, model, system, speak)
        }
    }

    /**
     * "Mera PNR status kya hai": read out by voice (train, status, coach and berth) from the user's PNR
     * service, for the PNR said or the journeys of the next 7 days. Without a service: the official
     * page opens with the PNR copied (no free official API exists).
     */
    private suspend fun pnrStatus(action: AssistantAction.PnrStatus, speak: Boolean) {
        val pnrs = if (action.pnr.isNotBlank()) listOf(action.pnr) else {
            val today = today()
            runCatching { business.journeysFrom(today) }.getOrDefault(emptyList())
                .filter { !it.date.isAfter(today.plusDays(7)) }.map { it.pnr }.distinct().take(3)
        }
        if (pnrs.isEmpty()) {
            val msg = phrase(lang(), "आपकी डायरी में अगले हफ्ते की कोई यात्रा नहीं है। PNR बोलिए, जैसे \"PNR 4512345678 ka status\"।", "ডায়েরিতে সামনের সপ্তাহের কোনো যাত্রা নেই। PNR বলুন।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        val lines = mutableListOf<String>()
        for (pnr in pnrs) {
            when (val f = pnrSource.status(pnr)) {
                is com.raju.aiassistant.domain.business.PnrFetch.Ok -> lines += com.raju.aiassistant.domain.business.spokenPnrStatus(f.snapshot)
                is com.raju.aiassistant.domain.business.PnrFetch.Failed -> lines += "PNR $pnr: ${f.message}"
                com.raju.aiassistant.domain.business.PnrFetch.NotConfigured -> {
                    // There is no free official PNR API: open the official page with the PNR ready to paste.
                    tools.copyToClipboard(pnr)
                    when (val o = tools.openWebPage(PNR_ENQUIRY_URL)) {
                        is ToolOutcome.Done -> report(action, ActionStatus.SUCCESS, "Official PNR page opened — PNR $pnr is copied, paste it there and fill the captcha. (Settings → PNR status service: set one up for spoken status.)")
                        else -> report(action, o)
                    }
                    return
                }
            }
        }
        informational(action, lines.joinToString("\n"), speak)
    }

    /**
     * "Mera phone dhundo": a chime every 2 s and "राजू जी, फोन यहाँ है!" at full volume until the phone
     * is picked up, unlocked, a volume key is pressed or "mil gaya" is said (3 minutes at most).
     */
    private suspend fun findPhone(action: AssistantAction.FindPhone, speak: Boolean) {
        val hardware = tools.beaconHardware()
        if (hardware == null) {
            val outcome = tools.findMyPhone()
            report(action, outcome)
            if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "मैं आपके फोन की घंटी बजा रही हूँ राजू, सुनिए!", "আমি আপনার ফোনের রিং বাজাচ্ছি রাজু, শুনুন!"))
            return
        }
        val line = phrase(lang(), "राजू जी, फोन यहाँ है!", "রাজু, ফোন এখানে!")
        if (!beacon.start(hardware, line)) {
            report(action, ActionStatus.FAILED, "फोन की आवाज़ चालू नहीं हो पाई।")
            return
        }
        trace("beacon started")
        report(action, ActionStatus.SUCCESS, "घंटी बज रही है — फोन उठाते, अनलॉक करते, आवाज़ का बटन दबाते या \"मिल गया\" बोलते ही बंद हो जाएगी।")
    }

    /** What Seema says after a media key (it used to say "गाना बंद कर दिया" for every key, next too). */
    private fun mediaSpoken(command: MediaCommand): String = when (command) {
        MediaCommand.PAUSE -> phrase(lang(), "गाना रोक दिया है राजू।", "গান থামিয়ে দিয়েছি রাজু।")
        MediaCommand.PLAY -> phrase(lang(), "गाना फिर से चला दिया है।", "গান আবার চালিয়ে দিয়েছি।")
        MediaCommand.NEXT -> phrase(lang(), "अगला गाना लगा दिया है।", "পরের গান চালিয়ে দিয়েছি।")
        MediaCommand.PREVIOUS -> phrase(lang(), "पिछला गाना लगा दिया है।", "আগের গান চালিয়ে দিয়েছি।")
    }

    /** The phone's own clock, said in the conversation language. */
    private fun spokenClock(action: AssistantAction.TellTime): String {
        val now = Instant.ofEpochMilli(clock()).atZone(zone())
        val bengali = lang() == SeemaLanguage.BENGALI
        return if (action.date) SpokenClock.date(now.toLocalDate(), bengali) else SpokenClock.time(now.hour, now.minute, bengali)
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
        suspend fun endWith(msg: String, status: ActionStatus = ActionStatus.FAILED) {
            report(action, status, msg)
            if (speak) say(msg)
        }
        val spokenNumber = action.number != null && action.contact == null
        val found: List<Contact> = if (spokenNumber) {
            listOf(Contact(action.number.orEmpty(), action.number.orEmpty()))
        } else {
            when (val r = lookup(action.contact ?: action.number.orEmpty())) {
                null -> { report(action, ActionStatus.CANCELLED, "Contacts permission was not granted, so I could not find ${action.contact}."); return }
                else -> r
            }
        }
        if (found.isEmpty()) {
            return endWith(phrase(lang(), "Contacts में \"${action.contact}\" नाम का कोई नंबर नहीं मिला। सेव किया हुआ नाम या पूरा नंबर बोलिए।", "কন্টাক্টে \"${action.contact}\" নামে কোনো নম্বর পাইনি। সেভ করা নাম বা পুরো নম্বর বলুন।"))
        }
        // Only full phone numbers: never "01", "121" or service codes like "*400#".
        val candidates = found.filter { PhoneNumbers.isCallable(it.number) }
        if (candidates.isEmpty()) {
            trace("call: no full number among ${found.size} match(es)")
            return endWith(
                if (spokenNumber) phrase(lang(), "\"${action.number}\" पूरा फ़ोन नंबर नहीं है, इसलिए कॉल नहीं लगाई। पूरा 10 अंकों का नंबर या कॉन्टैक्ट का नाम बोलिए।", "\"${action.number}\" পুরো ফোন নম্বর নয়, তাই কল করিনি। পুরো ১০ সংখ্যার নম্বর বা কন্টাক্টের নাম বলুন।")
                else phrase(lang(), "\"${action.contact}\" के लिए कोई पूरा फ़ोन नंबर नहीं मिला (${found.first().number} जैसे सर्विस या छोटे नंबर पर कॉल नहीं लगाती)। Contacts में सेव पूरा नाम बोलिए।", "\"${action.contact}\" এর জন্য পুরো নম্বর পাইনি। কন্টাক্টে সেভ করা পুরো নাম বলুন।"),
            )
        }
        val chosen = if (candidates.size == 1 && settings.value.directCalling) {
            candidates[0]
        } else {
            chooseContact(
                title = "Call?",
                message = if (candidates.size == 1) "Call ${candidates[0].name} (${candidates[0].number})?" else "Multiple contacts match \"${action.contact}\". Choose one to call:",
                candidates = candidates,
                query = action.contact ?: action.number,
                speak = speak,
                verbHi = "कॉल लगाऊँ",
                verbBn = "কল করব",
            )
        } ?: return endWith(phrase(lang(), "ठीक है, कॉल नहीं लगाई।", "ঠিক আছে, কল করিনি।"), ActionStatus.CANCELLED)
        // The user has confirmed the person and number: call straight away when Android allows it,
        // otherwise open the dialer and say plainly that the green button is theirs to press.
        val direct = tools.hasPermission("android.permission.CALL_PHONE")
        if (speak && direct) say(phrase(lang(), "${chosen.name} को कॉल लगा रही हूँ।", "${chosen.name} কে কল করছি।"))
        val outcome = tools.dial(chosen, direct)
        if (outcome !is ToolOutcome.Done) {
            report(action, outcome)
            if (speak) say(phrase(lang(), "कॉल नहीं लग पाई।", "কল করা গেল না।"))
            return
        }
        if (!direct) {
            return endWith(
                phrase(lang(), "${chosen.name} (${chosen.number}) का नंबर डायलर में डाल दिया है — कॉल के लिए हरा Call बटन आप दबाइए। सीधे कॉल के लिए Seema को Phone की अनुमति दीजिए।", "${chosen.name} (${chosen.number}) নম্বর ডায়ালারে দিয়েছি — কল করতে সবুজ Call বোতাম আপনি চাপুন।"),
                ActionStatus.SUCCESS,
            )
        }
        // "Call lag gayi" only when the phone itself reports a call in progress.
        callToVerify = action to chosen
        trace("call: placed, waiting for the phone to report the call")
        when (tools.awaitCallStarted(CALL_START_WAIT_MS)) {
            true -> reportCallStarted() // no speech: her voice would go into the call
            false -> {
                callToVerify = null
                endWith(phrase(lang(), "${chosen.name} को कॉल शुरू होने की पुष्टि फ़ोन से नहीं मिली — एक बार फ़ोन पर देख लीजिए।", "${chosen.name} কে কল শুরু হওয়ার নিশ্চয়তা ফোন থেকে পাইনি — একবার ফোনে দেখে নিন।"))
            }
            null -> {
                callToVerify = null
                val msg = phrase(lang(), "${chosen.name} (${chosen.number}) को कॉल करने का निर्देश फ़ोन को दे दिया है — कॉल शुरू हुई या नहीं, यहाँ से जाँच नहीं हो सकती।", "${chosen.name} কে কল করার নির্দেশ ফোনকে দিয়েছি — কল শুরু হল কি না, এখান থেকে দেখা যাচ্ছে না।")
                report(action, ActionStatus.SUCCESS, msg)
            }
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

    // ---------------------------------------------------------------- screen control (Accessibility)

    /** Until when a spoken "haan / nahi" answers the open screen-action question (0 = none open). */
    @Volatile private var voiceConfirmUntil = 0L

    private fun voiceConfirmOpen() = voiceConfirmUntil != 0L && clock() < voiceConfirmUntil && _pending.value != null

    /** How a spoken answer to the open question is understood (null = not an answer, listen again). */
    private var voiceInterpret: ((String) -> Decision?)? = null
    /** The last question was answered by voice (not by a tap on the screen). */
    private var answeredByVoice = false

    /** A spoken answer ("haan", "nahi", "Rahul Sharma", "doosra") to the question Seema just asked. */
    private fun handleVoiceConfirm(heard: String): Boolean {
        if (!voiceConfirmOpen()) return false
        val interpret = voiceInterpret ?: return false
        val d = interpret(heard)
        if (d == null) {
            trace("voice answer: not understood, listening again")
            listen()
        } else {
            trace("voice answer: ${if (d is Decision.Approve) "yes" else "no"}")
            answeredByVoice = true
            decision?.complete(d)
        }
        return true
    }

    private fun yesNo(heard: String, option: Contact? = null): Decision? =
        confirmReply(heard)?.let { yes -> if (yes) Decision.Approve(option) else Decision.Deny }

    /**
     * Shows [request] on the screen and, when the user is talking ([listenForAnswer]), also asks it
     * out loud ([spoken]) and listens for the answer. No answer within the window = no.
     */
    private suspend fun askOutLoud(request: PendingRequest, spoken: String?, listenForAnswer: Boolean, interpret: (String) -> Decision?): Decision? {
        val d = CompletableDeferred<Decision>()
        decision = d
        answeredByVoice = false
        _pending.value = request
        return try {
            if (spoken != null) say(spoken, allowBargeIn = false)
            if (listenForAnswer) {
                voiceInterpret = interpret
                voiceConfirmUntil = clock() + SCREEN_CONFIRM_WINDOW_MS
                _voiceSession.value = true
                listen()
                withTimeoutOrNull(SCREEN_CONFIRM_WINDOW_MS + 3_000L) { d.await() }
            } else {
                d.await()
            }
        } finally {
            voiceConfirmUntil = 0L
            voiceInterpret = null
            if (decision === d) {
                _pending.value = null
                decision = null
            }
            if (listenForAnswer) {
                listenGen++
                voice.stop()
            }
        }
    }

    /**
     * Asks before any screen action: on the screen (when Seema is visible) and out loud with a short
     * "haan / nahi" listen (when the user is talking, often from inside another app). No answer = no.
     */
    private suspend fun confirmScreenAction(title: String, question: String, speak: Boolean): Boolean =
        askOutLoud(
            PendingRequest(PendingRequest.Kind.CONFIRM, title, question),
            spoken = question.takeIf { speak },
            listenForAnswer = speak && voiceTurn,
        ) { yesNo(it) } is Decision.Approve

    /**
     * Smart Contact Brain (on the phone only — contacts are never sent to the AI): one way for calls
     * and WhatsApp to settle on the person. One match: "नाम, नंबर — कॉल लगाऊँ?". Several: she lists
     * them and the user says the name or "पहला / दूसरा" (or taps one); a spoken choice is confirmed
     * once more with the full number. Returns null when the user said no or did not answer.
     */
    private suspend fun chooseContact(
        title: String,
        message: String,
        candidates: List<Contact>,
        query: String?,
        speak: Boolean,
        verbHi: String,
        verbBn: String,
    ): Contact? {
        val byVoice = speak && voiceTurn
        fun question(c: Contact) = phrase(lang(), "${c.name}, नंबर ${c.number} — $verbHi?", "${c.name}, নম্বর ${c.number} — $verbBn?")
        if (candidates.size == 1) {
            val c = candidates[0]
            val d = askOutLoud(
                PendingRequest(PendingRequest.Kind.CONFIRM, title, message, options = candidates),
                spoken = if (speak) question(c) else null,
                listenForAnswer = byVoice,
            ) { yesNo(it, c) }
            return (d as? Decision.Approve)?.let { it.option ?: c }
        }
        val names = candidates.take(4).mapIndexed { i, c -> "${i + 1}. ${c.name}" }.joinToString(", ")
        val q = phrase(
            lang(),
            "\"${query.orEmpty()}\" से ${candidates.size} कॉन्टैक्ट मिले: $names। किसे? नाम या पहला, दूसरा बोलिए।",
            "\"${query.orEmpty()}\" নামে ${candidates.size} জন পেয়েছি: $names। কাকে? নাম বা প্রথম, দ্বিতীয় বলুন।",
        )
        val d = askOutLoud(
            PendingRequest(PendingRequest.Kind.CONFIRM, title, message, options = candidates.take(8)),
            spoken = if (speak) q else null,
            listenForAnswer = byVoice,
        ) { heard -> ContactMatch.pick(heard, candidates.take(8))?.let { Decision.Approve(it) } ?: if (confirmReply(heard) == false) Decision.Deny else null }
        if (d !is Decision.Approve) return null
        val chosen = d.option ?: return null
        // A tap on a name is the confirmation; a spoken choice is checked once more with the number.
        if (!answeredByVoice) return chosen
        val again = askOutLoud(
            PendingRequest(PendingRequest.Kind.CONFIRM, title, "${chosen.name} (${chosen.number})", options = listOf(chosen)),
            spoken = question(chosen),
            listenForAnswer = true,
        ) { yesNo(it, chosen) }
        return if (again is Decision.Approve) chosen else null
    }

    /** The call Seema placed and is waiting to see start (reported once, by whichever sees it first). */
    private var callToVerify: Pair<AssistantAction, Contact>? = null

    private suspend fun reportCallStarted() {
        val (action, who) = callToVerify ?: return
        callToVerify = null
        report(action, ActionStatus.SUCCESS, "${who.name} (${who.number}) को कॉल शुरू हो गई — फ़ोन ने पुष्टि की।")
    }

    /** The app on screen, or null after telling the user why not (no Accessibility, Seema in front, a sensitive screen). */
    private suspend fun screenOrSay(action: AssistantAction, speak: Boolean, allowSensitive: Boolean = false): ScreenSnapshot? {
        return when (val r = tools.readScreen()) {
            is ScreenRead.Unavailable -> {
                report(action, ActionStatus.FAILED, r.reason)
                if (speak) say(r.reason)
                null
            }
            is ScreenRead.Ok -> {
                val why = if (allowSensitive) null else SensitiveScreens.reason(r.snapshot)
                if (why != null) {
                    val msg = phrase(lang(), "ये $why है — आपकी सुरक्षा के लिए मैं इसे न पढ़ूँगी, न छुऊँगी।", "এটা $why — আপনার সুরক্ষার জন্য আমি এটা পড়ব না, ছোঁব না।")
                    trace("screen: sensitive, refused")
                    report(action, ActionStatus.BLOCKED, msg)
                    if (speak) say(msg)
                    null
                } else {
                    r.snapshot
                }
            }
        }
    }

    private suspend fun screenCancelled(action: AssistantAction, speak: Boolean) {
        val msg = phrase(lang(), "ठीक है, कुछ नहीं किया।", "ঠিক আছে, কিছু করিনি।")
        report(action, ActionStatus.CANCELLED, msg)
        if (speak) say(msg)
    }

    /** After an action: look at the screen again and say honestly whether it really happened. */
    private suspend fun verifyAndReport(
        action: AssistantAction,
        before: ScreenSnapshot,
        outcome: ToolOutcome,
        speak: Boolean,
        done: String,
        unchanged: String,
        happened: (ScreenSnapshot) -> Boolean = { it.fingerprint != before.fingerprint },
    ) {
        if (outcome !is ToolOutcome.Done) {
            report(action, outcome)
            if (speak) say((outcome as? ToolOutcome.Failed)?.message ?: phrase(lang(), "ये काम नहीं हो पाया।", "কাজটা হয়নি।"))
            return
        }
        delay(SCREEN_VERIFY_DELAY_MS)
        // Unavailable afterwards = another screen (Seema's own, or a closed app): it did change.
        val after = (tools.readScreen() as? ScreenRead.Ok)?.snapshot
        val ok = after == null || happened(after)
        trace("screen action ${action.javaClass.simpleName}: ${if (ok) "verified" else "no change seen"}")
        report(action, if (ok) ActionStatus.SUCCESS else ActionStatus.FAILED, if (ok) done else unchanged)
        if (speak) say(if (ok) done else unchanged)
    }

    private suspend fun readScreenAction(action: AssistantAction.ReadScreen, provider: AiProvider, model: String, system: String, speak: Boolean) {
        val snap = screenOrSay(action, speak) ?: return
        trace("screen read: ${snap.elements.size} elements")
        _state.value = AssistantState.Thinking
        val question = buildString {
            appendLine("SCREEN — read just now by Seema's Accessibility service from the app in front (this is real):")
            appendLine(snap.describeForAi())
            appendLine()
            append("User's question: ").appendLine(action.question ?: "Screen par kya hai? Chhote mein batao.")
            append("Answer only from this screen text, short and natural (")
            append(if (lang() == SeemaLanguage.BENGALI) "in Bengali" else "in Hindi")
            append("). If a button should be pressed, name the exact button and ask \"दबा दूँ?\" — do not add any action tag now.")
        }
        val turns = listOf(com.raju.aiassistant.domain.ai.Turn(com.raju.aiassistant.domain.ai.TurnRole.USER, question))
        when (val r = withTimeoutOrNull(AI_TIMEOUT_MS) { provider.chat(model, system, turns) }) {
            is AiResult.Success -> {
                val text = parseReply(r.value).text.ifBlank { phrase(lang(), "स्क्रीन पर ${snap.appLabel} खुला है।", "স্ক্রিনে ${snap.appLabel} খোলা।") }
                report(action, ActionStatus.SUCCESS, "Screen padhi: ${snap.appLabel}")
                record(ChatRole.ASSISTANT, text)
                if (speak) say(text)
            }
            else -> {
                // The AI could not answer: the phone's own summary of the screen instead of nothing.
                val why = (r as? AiResult.Failure)?.message
                trace("screen read: AI failed (${why ?: "timeout"}), local summary")
                val msg = snap.localSummary(bengali = lang() == SeemaLanguage.BENGALI)
                report(action, ActionStatus.SUCCESS, "Screen padhi (bina AI): ${snap.appLabel}")
                record(ChatRole.ASSISTANT, msg)
                if (speak) say(msg)
            }
        }
    }

    private suspend fun screenTap(action: AssistantAction.ScreenTap, speak: Boolean) {
        val before = screenOrSay(action, speak) ?: return
        val found = ScreenMatch.candidates(before, action.target)
        if (found.isEmpty()) {
            val visible = before.elements.filter { it.kind == ElementKind.BUTTON && it.text.isNotBlank() }.map { it.text }.distinct().take(6)
            val msg = phrase(
                lang(),
                "स्क्रीन पर \"${action.target}\" नहीं मिला।" + (if (visible.isNotEmpty()) " दिख रहे बटन: ${visible.joinToString(", ")}।" else ""),
                "স্ক্রিনে \"${action.target}\" পাইনি।" + (if (visible.isNotEmpty()) " দেখা যাচ্ছে: ${visible.joinToString(", ")}।" else ""),
            )
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        if (found.size > 1) {
            // Never guess between two different buttons.
            val names = found.take(4).mapIndexed { i, e -> "${i + 1}. ${e.text.ifBlank { e.hint }}" }.joinToString(", ")
            val msg = phrase(lang(), "\"${action.target}\" जैसे ${found.size} बटन हैं: $names — पूरा नाम बोलिए, कौन-सा दबाऊँ?", "\"${action.target}\" এর মতো ${found.size}টা বোতাম: $names — কোনটা চাপব?")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        val el = found[0]
        val name = el.text.ifBlank { el.hint }.ifBlank { action.target }
        val warn = if (RiskyButtons.isRisky(name)) phrase(lang(), " ध्यान दें, ये बटन कुछ हटा या बदल सकता है।", " সাবধান, এটা কিছু মুছে বা বদলে দিতে পারে।") else ""
        val q = phrase(lang(), "${before.appLabel} में \"$name\" दबा दूँ?$warn", "${before.appLabel}-এ \"$name\" চাপব?$warn")
        if (!confirmScreenAction("Tap on screen?", q, speak)) return screenCancelled(action, speak)
        verifyAndReport(
            action, before, tools.screenAction(ScreenOp.Tap(name)), speak,
            done = phrase(lang(), "\"$name\" दबा दिया — स्क्रीन बदल गई।", "\"$name\" চেপে দিয়েছি — স্ক্রিন বদলেছে।"),
            unchanged = phrase(lang(), "\"$name\" दबाया, पर स्क्रीन में कोई बदलाव नहीं दिखा — शायद काम नहीं हुआ, एक बार देख लीजिए।", "\"$name\" চেপেছি, কিন্তু স্ক্রিনে কোনো বদল দেখিনি — একবার দেখে নিন।"),
        )
    }

    private suspend fun screenBack(action: AssistantAction.ScreenBack, speak: Boolean) {
        // Going back reveals nothing: allowed on any screen, still only after a yes.
        val before = screenOrSay(action, speak, allowSensitive = true) ?: return
        if (!confirmScreenAction("Go back?", phrase(lang(), "${before.appLabel} में पीछे जाऊँ?", "${before.appLabel}-এ পিছনে যাব?"), speak)) return screenCancelled(action, speak)
        verifyAndReport(
            action, before, tools.screenAction(ScreenOp.Back), speak,
            done = phrase(lang(), "पीछे आ गई।", "পিছনে এসেছি।"),
            unchanged = phrase(lang(), "पीछे जाने का बटन दबाया, पर स्क्रीन नहीं बदली।", "পিছনে যাওয়ার চেষ্টা করেছি, কিন্তু স্ক্রিন বদলায়নি।"),
        )
    }

    private suspend fun screenScroll(action: AssistantAction.ScreenScroll, speak: Boolean) {
        val before = screenOrSay(action, speak) ?: return
        val dirHi = if (action.down) "नीचे" else "ऊपर"
        val dirBn = if (action.down) "নিচে" else "উপরে"
        if (!confirmScreenAction("Scroll?", phrase(lang(), "${before.appLabel} में $dirHi स्क्रॉल करूँ?", "${before.appLabel}-এ $dirBn স্ক্রল করব?"), speak)) return screenCancelled(action, speak)
        verifyAndReport(
            action, before, tools.screenAction(ScreenOp.Scroll(action.down)), speak,
            done = phrase(lang(), "$dirHi स्क्रॉल कर दिया।", "$dirBn স্ক্রল করেছি।"),
            unchanged = phrase(lang(), "स्क्रॉल की कोशिश की, पर स्क्रीन नहीं हिली — शायद $dirHi और कुछ नहीं है।", "স্ক্রল করার চেষ্টা করেছি, কিন্তু স্ক্রিন নড়েনি।"),
        )
    }

    private suspend fun screenType(action: AssistantAction.ScreenType, speak: Boolean) {
        val before = screenOrSay(action, speak) ?: return
        val box = ScreenMatch.inputFor(before, action.field)
        if (box == null) {
            val msg = phrase(lang(), "इस स्क्रीन पर लिखने की कोई जगह नहीं मिली।", "এই স্ক্রিনে লেখার জায়গা পাইনি।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        val where = box.hint.ifBlank { box.text }.ifBlank { phrase(lang(), "लिखने की जगह", "লেখার জায়গা") }
        val q = phrase(lang(), "${before.appLabel} में \"$where\" में ये लिख दूँ: \"${action.text}\"?", "${before.appLabel}-এ \"$where\"-এ এটা লিখব: \"${action.text}\"?")
        if (!confirmScreenAction("Type on screen?", q, speak)) return screenCancelled(action, speak)
        verifyAndReport(
            action, before, tools.screenAction(ScreenOp.Type(action.text, action.field)), speak,
            done = phrase(lang(), "लिख दिया। भेजना हो तो बोलिए \"Send दबाओ\"।", "লিখে দিয়েছি। পাঠাতে চাইলে বলুন \"Send চাপো\"।"),
            unchanged = phrase(lang(), "लिखने की कोशिश की, पर स्क्रीन पर लिखा हुआ नहीं दिखा।", "লেখার চেষ্টা করেছি, কিন্তু স্ক্রিনে দেখা যাচ্ছে না।"),
            happened = { ScreenMatch.showsTyped(it, action.text) },
        )
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
        if (action.text.isBlank()) {
            val askText = phrase(lang(), "WhatsApp पर क्या लिखकर भेजूँ?", "হোয়াটসঅ্যাপে কী লিখে পাঠাব?")
            report(action, ActionStatus.CANCELLED, askText)
            if (speak) say(askText)
            return
        }
        var target: Contact? = null
        if (action.contact != null) {
            // A phone number said or typed instead of a name ("98765 43210 ko message karo").
            val digitsOnly = action.contact.filter { it.isDigit() }
            val found = if (digitsOnly.length >= 7 && digitsOnly.length * 2 >= action.contact.count { !it.isWhitespace() }) {
                listOf(Contact(action.contact, action.contact))
            } else {
                lookup(action.contact)
            }
            if (found == null) {
                note("Contacts permission not granted; WhatsApp will open without a recipient.")
            } else if (found.isEmpty()) {
                // Not in the phone's contacts: a group or a WhatsApp-only name can still be found in
                // WhatsApp's own search — with Seema's Accessibility, and only after a yes.
                if (com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
                    whatsAppBySearch(action, action.contact, speak)
                    return
                }
                // A person was named: an empty WhatsApp is not what was asked, so say so instead.
                val missing = phrase(
                    lang(),
                    "Contacts में \"${action.contact}\" नाम का कोई नंबर नहीं मिला। Contacts में जो नाम सेव है वही बोलिए, या नंबर बोलिए।",
                    "কন্টাক্টে \"${action.contact}\" নামে কোনো নম্বর পাইনি। কন্টাক্টে যে নাম সেভ আছে সেটাই বলুন, বা নম্বর বলুন।",
                )
                report(action, ActionStatus.FAILED, missing)
                if (speak) say(missing)
                return
            } else {
                val autoSend = com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
                // Only numbers WhatsApp can use: never a service code ("*400#") or a short number.
                val usable = found.filter { normalizeForWhatsApp(it.number) != null }
                if (usable.isEmpty()) {
                    val noNumber = phrase(lang(), "\"${action.contact}\" के लिए कोई पूरा फ़ोन नंबर नहीं मिला, इसलिए WhatsApp नहीं भेजा। Contacts में सेव पूरा नाम या नंबर बोलिए।", "\"${action.contact}\" এর জন্য পুরো নম্বর পাইনি, তাই হোয়াটসঅ্যাপ পাঠাইনি।")
                    report(action, ActionStatus.FAILED, noNumber)
                    if (speak) say(noNumber)
                    return
                }
                // In the phone log: why a confirmation shows instead of sending directly.
                trace("whatsapp: ${usable.size} contact(s) found, auto-send ${if (autoSend) "on" else "off (Seema Accessibility not running)"}")
                target = if (usable.size == 1 && autoSend) {
                    usable[0]
                } else {
                    val found = usable
                    val why = when {
                        found.size > 1 -> "\"${action.contact}\" naam se ${found.size} number mile — kis par bhejna hai?"
                        else -> "Seedha bhejne ke liye Seema ki Accessibility chalu nahi hai (phone Settings → Accessibility → Seema AI → On; agar band ho to App info → ⋮ → \"Allow restricted settings\" pehle)."
                    }
                    chooseContact(
                        "Send on WhatsApp?",
                        "$why\n\nYe message WhatsApp mein khulega, Send aap dabayenge:\n\n\"${action.text}\"",
                        usable, action.contact, speak,
                        verbHi = "इन्हें WhatsApp भेजूँ", verbBn = "একে হোয়াটসঅ্যাপ পাঠাব",
                    ) ?: run { report(action, ActionStatus.CANCELLED, "WhatsApp draft cancelled."); return }
                }
            }
        }
        if (target == null && !com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
            val ok = ask(PendingRequest(PendingRequest.Kind.CONFIRM, "Open WhatsApp?", "Open WhatsApp with this message? You choose the chat and tap send yourself.\n\n\"${action.text}\""))
            if (ok !is Decision.Approve) { report(action, ActionStatus.CANCELLED, "WhatsApp draft cancelled."); return }
        }
        val outcome = tools.openWhatsApp(target?.number, action.text)
        report(action, outcome)
        val autoArmed = outcome is ToolOutcome.Done && target?.name != null && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
        // Verify: did Seema's Accessibility really press Send (and the message leave the box)?
        val sent = if (autoArmed) tools.awaitAutoAction(AUTO_ACTION_WAIT_MS) else null
        if (sent == true) report(action, ActionStatus.SUCCESS, "WhatsApp: Send दबाया गया, मैसेज बॉक्स खाली हुआ (पक्का भेजा गया)।")
        if (sent == false) report(action, ActionStatus.FAILED, "WhatsApp में Send बटन नहीं दबा पाई — WhatsApp में खुद Send दबाइए।")
        if (speak && outcome is ToolOutcome.Done) {
            // "Sent" only when it really goes out by itself (a known chat + Seema's accessibility service).
            val name = target?.name
            val autoSends = name != null && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
            say(
                when {
                    sent == true -> phrase(lang(), "हो गया राजू, $name को व्हाट्सएप पर मैसेज भेज दिया।", "হয়ে গেছে রাজু, $name কে হোয়াটসঅ্যাপে মেসেজ পাঠিয়ে দিয়েছি।")
                    sent == false -> phrase(lang(), "$name का व्हाट्सएप खुला है, पर Send नहीं दबा पाई — Send आप दबा दीजिए।", "$name এর হোয়াটসঅ্যাপ খোলা, কিন্তু Send চাপতে পারিনি — আপনি চাপুন।")
                    autoSends -> phrase(lang(), "हाँ राजू, $name को व्हाट्सएप पर मैसेज भेज रही हूँ!", "হ্যাঁ রাজু, $name কে হোয়াটসঅ্যাপে মেসেজ পাঠাচ্ছি!")
                    name != null -> phrase(lang(), "$name के लिए व्हाट्सएप में मैसेज तैयार है, बस Send दबा दीजिए।", "$name এর জন্য হোয়াটসঅ্যাপে মেসেজ তৈরি, শুধু Send চাপুন।")
                    else -> phrase(lang(), "व्हाट्सएप खुल गया है, चैट चुनकर Send दबाइए।", "হোয়াটসঅ্যাপ খুলেছে, চ্যাট বেছে Send চাপুন।")
                },
            )
        }
    }

    /**
     * Phase 3 deep action: WhatsApp → search [name] → the one matching chat → type → Send, then say
     * honestly what happened. Several matches, no match or a doubtful chat: nothing is sent.
     */
    private suspend fun whatsAppBySearch(action: AssistantAction.WhatsApp, name: String, speak: Boolean) {
        val question = phrase(
            lang(),
            "Contacts में \"$name\" नहीं मिला। WhatsApp में \"$name\" खोजकर ये भेज दूँ: \"${action.text}\"?",
            "কন্টাক্টে \"$name\" পাইনি। হোয়াটসঅ্যাপে \"$name\" খুঁজে এটা পাঠাব: \"${action.text}\"?",
        )
        if (!confirmScreenAction("Search WhatsApp and send?", question, speak)) {
            report(action, ActionStatus.CANCELLED, "WhatsApp message cancelled.")
            if (speak) say(phrase(lang(), "ठीक है, नहीं भेजा।", "ঠিক আছে, পাঠাইনি।"))
            return
        }
        _state.value = AssistantState.Executing
        val r = tools.whatsAppSearchAndSend(name, action.text)
        r.log.forEach { trace("whatsapp search: $it") }
        val chat = r.chat ?: name
        val (status, hi, bn) = when (r.outcome) {
            FlowOutcome.SENT -> Triple(ActionStatus.SUCCESS, "हो गया राजू, \"$chat\" को व्हाट्सएप पर मैसेज भेज दिया।", "হয়ে গেছে রাজু, \"$chat\" কে হোয়াটসঅ্যাপে মেসেজ পাঠিয়ে দিয়েছি।")
            FlowOutcome.TYPED_NOT_SENT -> Triple(ActionStatus.FAILED, "\"$chat\" की चैट में मैसेज लिख दिया है, पर Send पक्का नहीं हुआ — एक बार देखकर Send दबा दीजिए।", "\"$chat\" এর চ্যাটে মেসেজ লিখেছি, কিন্তু Send নিশ্চিত হয়নি — একবার দেখে Send চাপুন।")
            FlowOutcome.SEVERAL_CHATS -> Triple(ActionStatus.CANCELLED, "WhatsApp में \"$name\" नाम की कई चैट हैं: ${r.choices.take(4).joinToString(", ")}। पूरा नाम बोलिए, कुछ नहीं भेजा।", "হোয়াটসঅ্যাপে \"$name\" নামে কয়েকটা চ্যাট আছে: ${r.choices.take(4).joinToString(", ")}। পুরো নাম বলুন, কিছু পাঠাইনি।")
            FlowOutcome.CHAT_NOT_FOUND -> Triple(ActionStatus.FAILED, "WhatsApp में भी \"$name\" नाम की कोई चैट नहीं मिली, कुछ नहीं भेजा।", "হোয়াটসঅ্যাপেও \"$name\" নামে কোনো চ্যাট পাইনি, কিছু পাঠাইনি।")
            FlowOutcome.WRONG_CHAT -> Triple(ActionStatus.FAILED, "जो चैट खुली उसका नाम पक्का नहीं हुआ, इसलिए कुछ नहीं लिखा और नहीं भेजा।", "যে চ্যাট খুলেছে তার নাম নিশ্চিত হয়নি, তাই কিছু লিখিনি, পাঠাইনি।")
            FlowOutcome.UNSAFE_SCREEN -> Triple(ActionStatus.BLOCKED, "WhatsApp में पेमेंट / PIN वाली स्क्रीन आई, सुरक्षा के लिए मैं रुक गई। कुछ नहीं भेजा।", "হোয়াটসঅ্যাপে পেমেন্ট / পিন স্ক্রিন এসেছে, সুরক্ষার জন্য থেমে গেছি। কিছু পাঠাইনি।")
            FlowOutcome.STUCK -> Triple(ActionStatus.FAILED, "WhatsApp ने समय पर वो नहीं दिखाया जो चाहिए था, इसलिए मैं रुक गई। मैसेज नहीं गया।", "হোয়াটসঅ্যাপ সময়মতো ঠিক স্ক্রিন দেখায়নি, তাই থেমে গেছি। মেসেজ যায়নি।")
            FlowOutcome.UNAVAILABLE -> Triple(ActionStatus.FAILED, "WhatsApp या Seema की Accessibility उपलब्ध नहीं है, मैसेज नहीं भेज पाई।", "হোয়াটসঅ্যাপ বা Seema-র Accessibility নেই, মেসেজ পাঠাতে পারিনি।")
            // Document outcomes never come from a text message.
            FlowOutcome.FILE_NOT_FOUND, FlowOutcome.SEVERAL_FILES -> Triple(ActionStatus.FAILED, "मैसेज नहीं भेज पाई।", "মেসেজ পাঠাতে পারিনি।")
        }
        val msg = phrase(lang(), hi, bn)
        report(action, status, msg)
        if (r.outcome == FlowOutcome.SENT) tools.openSeema()
        if (speak) say(msg)
    }

    /**
     * "WhatsApp par Riya ko ticket wali PDF bhejo": Riya's chat → 📎 → Document → the one file whose
     * name matches → Send, after a yes. The chat is found by the contact's saved name (or the name as
     * said, for a group); any doubt about the chat or the file means nothing is sent.
     */
    private suspend fun whatsAppFile(action: AssistantAction.WhatsAppFile, speak: Boolean) {
        if (!com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
            val msg = phrase(lang(), "फाइल भेजने के लिए Seema की Accessibility चालू करनी होगी (Settings → Accessibility → Seema AI)।", "ফাইল পাঠাতে Seema-র Accessibility চালু করতে হবে।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        // The saved name is what WhatsApp shows; a group or WhatsApp-only name is searched as said.
        val found = lookup(action.contact).orEmpty()
        val chat = when {
            found.size == 1 -> found[0].name
            found.size > 1 -> chooseContact(
                "Send file on WhatsApp?", "Kis ko file bhejni hai?", found, action.contact, speak,
                verbHi = "इन्हें फाइल भेजूँ", verbBn = "একে ফাইল পাঠাব",
            )?.name ?: run { report(action, ActionStatus.CANCELLED, "WhatsApp file cancelled."); return }
            else -> action.contact
        }
        val what = action.file.ifBlank { phrase(lang(), "सबसे नई", "সবচেয়ে নতুন") }
        val question = phrase(lang(), "WhatsApp पर \"$chat\" को \"$what\" वाली फाइल भेज दूँ?", "হোয়াটসঅ্যাপে \"$chat\" কে \"$what\" ফাইল পাঠাব?")
        if (!confirmScreenAction("Send file on WhatsApp?", question, speak)) {
            report(action, ActionStatus.CANCELLED, "WhatsApp file cancelled.")
            if (speak) say(phrase(lang(), "ठीक है, नहीं भेजी।", "ঠিক আছে, পাঠাইনি।"))
            return
        }
        _state.value = AssistantState.Executing
        val r = tools.whatsAppSendDocument(chat, action.file)
        r.log.forEach { trace("whatsapp file: $it") }
        val to = r.chat ?: chat
        val (status, hi, bn) = when (r.outcome) {
            FlowOutcome.SENT -> Triple(ActionStatus.SUCCESS, "हो गया, \"$to\" को फाइल भेज दी।", "হয়ে গেছে, \"$to\" কে ফাইল পাঠিয়ে দিয়েছি।")
            FlowOutcome.SEVERAL_FILES -> Triple(ActionStatus.CANCELLED, "ऐसी कई फाइलें हैं: ${r.choices.take(4).joinToString(", ")}। नाम का कोई और शब्द बोलिए, कुछ नहीं भेजा।", "এমন কয়েকটা ফাইল আছে: ${r.choices.take(4).joinToString(", ")}। নামের আর একটা শব্দ বলুন, কিছু পাঠাইনি।")
            FlowOutcome.FILE_NOT_FOUND -> Triple(ActionStatus.FAILED, "\"$what\" नाम की फाइल नहीं दिखी। फाइलों की लिस्ट खुली है, आप खुद चुन लीजिए।", "\"$what\" নামে ফাইল দেখিনি। ফাইলের তালিকা খোলা আছে, আপনি বেছে নিন।")
            FlowOutcome.TYPED_NOT_SENT -> Triple(ActionStatus.FAILED, "फाइल चुन ली, पर भेजना पक्का नहीं हुआ — WhatsApp में एक बार देख लीजिए।", "ফাইল বেছেছি, কিন্তু পাঠানো নিশ্চিত হয়নি — একবার দেখে নিন।")
            FlowOutcome.CHAT_NOT_FOUND -> Triple(ActionStatus.FAILED, "WhatsApp में \"$chat\" की चैट नहीं मिली, कुछ नहीं भेजा।", "হোয়াটসঅ্যাপে \"$chat\" এর চ্যাট পাইনি, কিছু পাঠাইনি।")
            FlowOutcome.SEVERAL_CHATS -> Triple(ActionStatus.CANCELLED, "\"$chat\" नाम की कई चैट हैं: ${r.choices.take(4).joinToString(", ")}। पूरा नाम बोलिए।", "\"$chat\" নামে কয়েকটা চ্যাট আছে: ${r.choices.take(4).joinToString(", ")}। পুরো নাম বলুন।")
            FlowOutcome.WRONG_CHAT -> Triple(ActionStatus.FAILED, "जो चैट खुली उसका नाम पक्का नहीं हुआ, इसलिए कुछ नहीं भेजा।", "যে চ্যাট খুলেছে তার নাম নিশ্চিত হয়নি, তাই কিছু পাঠাইনি।")
            FlowOutcome.UNSAFE_SCREEN -> Triple(ActionStatus.BLOCKED, "पेमेंट / PIN वाली स्क्रीन आई, सुरक्षा के लिए मैं रुक गई।", "পেমেন্ট / পিন স্ক্রিন এসেছে, সুরক্ষার জন্য থেমে গেছি।")
            FlowOutcome.STUCK -> Triple(ActionStatus.FAILED, "WhatsApp में अटैच या फाइल वाला हिस्सा समय पर नहीं दिखा, मैं रुक गई। फाइल नहीं गई।", "হোয়াটসঅ্যাপে অ্যাটাচ বা ফাইলের অংশ সময়মতো দেখা যায়নি, থেমে গেছি। ফাইল যায়নি।")
            FlowOutcome.UNAVAILABLE -> Triple(ActionStatus.FAILED, "WhatsApp या Seema की Accessibility उपलब्ध नहीं है।", "হোয়াটসঅ্যাপ বা Seema-র Accessibility নেই।")
        }
        val msg = phrase(lang(), hi, bn)
        report(action, status, msg)
        if (r.outcome == FlowOutcome.SENT) tools.openSeema()
        if (speak) say(msg)
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
                val usable = found.filter { normalizeForWhatsApp(it.number) != null }
                if (usable.isEmpty()) {
                    report(action, ActionStatus.FAILED, "\"${action.contact}\" के लिए कोई पूरा फ़ोन नंबर नहीं मिला।")
                    return
                }
                target = if (usable.size == 1 && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
                    usable[0]
                } else {
                    chooseContact(
                        if (action.video) "WhatsApp video call?" else "WhatsApp call?",
                        "Start WhatsApp ${if (action.video) "video call" else "call"} to:",
                        usable, action.contact, speak,
                        verbHi = if (action.video) "वीडियो कॉल लगाऊँ" else "WhatsApp कॉल लगाऊँ",
                        verbBn = if (action.video) "ভিডিও কল করব" else "হোয়াটসঅ্যাপ কল করব",
                    ) ?: run { report(action, ActionStatus.CANCELLED, "WhatsApp call cancelled."); return }
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
        val autoArmed = outcome is ToolOutcome.Done && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
        // Verify: was WhatsApp's own call button (and only a call dialog) really pressed?
        val pressed = if (autoArmed) tools.awaitAutoAction(AUTO_ACTION_WAIT_MS) else null
        if (pressed == true) report(action, ActionStatus.SUCCESS, "WhatsApp का कॉल बटन दबाया गया।")
        if (pressed == false) report(action, ActionStatus.FAILED, "WhatsApp में कॉल बटन नहीं मिला/दबा — चैट में कॉल का बटन खुद दबाइए।")
        if (speak && outcome is ToolOutcome.Done) {
            val name = target?.name ?: action.contact ?: "जिओ"
            val typeHi = if (action.video) "वीडियो कॉल" else "कॉल"
            val typeBn = if (action.video) "ভিডিও কল" else "কল"
            say(
                if (pressed == false) phrase(lang(), "$name की व्हाट्सएप चैट खुली है, पर $typeHi का बटन नहीं दबा पाई — आप दबा दीजिए।", "$name এর চ্যাট খোলা, কিন্তু $typeBn বোতাম চাপতে পারিনি — আপনি চাপুন।")
                else phrase(lang(), "हाँ राजू, मैंने $name को व्हाट्सएप $typeHi लगा दी है!", "হ্যাঁ রাজু, আমি $name কে হোয়াটসঅ্যাপ $typeBn লাগিয়ে দিয়েছি!"),
            )
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
        if (handsFree && !inBackground && !settings.value.assistantPaused && voice.isAvailable()) {
            // Hands-free: show the error, then go back to listening (behind another app the guard
            // returns her to "Hey Seema" standby instead).
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
