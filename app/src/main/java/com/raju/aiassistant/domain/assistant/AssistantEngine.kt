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
import com.raju.aiassistant.domain.memory.isEmpty
import com.raju.aiassistant.domain.voice.isLikelyOwnEcho
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
/** The short "which phone task is this" request: if it takes longer, the normal conversation answers. */
private const val ROUTER_TIMEOUT_MS = 2_500L
/** Each backup AI after a failure: answer within this or the next one is asked. */
private const val FALLBACK_TIMEOUT_MS = 15_000L
/** After the first sentence is ready: if the whole reply follows within this, it is spoken in one go. */
private const val EARLY_HEAD_GRACE_MS = 1_200L
/** How long the answer to "WhatsApp par kya likhun?" is waited for. */
private const val WA_ASK_MS = 60_000L
/** Exchanges of recent conversation sent with each AI request, and their size limit. */
private const val RECENT_EXCHANGES = 6
private const val HISTORY_CHARS_PER_REQUEST = 4_000
/** A streaming AI that has not written a single word by now is given up on when another AI can answer. */
private const val FIRST_TEXT_TIMEOUT_MS = 4_000L
/**
 * After "Hey Seema" the wake-word recorder lets go of the microphone and the speech recogniser starts
 * only once it is free: a short safety gap, so the first words are not lost to a busy microphone.
 */
private const val MIC_HANDOFF_MS = 180L
/** How long the pieces of a PNR said in parts are collected. */
private const val PNR_COLLECT_MS = 90_000L
/** Careful thinking (deep tier) may take longer before its first words on mobile data: Gemini gets 28 s. */
private const val DEEP_FIRST_TEXT_TIMEOUT_MS = 6_000L
/** How often the background guard checks that Seema is back to waiting for "Hey Seema". */
private const val BACKGROUND_GUARD_MS = 3_000L
/**
 * After she has done something for the user, the next sentence needs no "Hey Seema" for this long
 * (also while another app is on screen). Every new command starts it again.
 */
/** After her reply he may answer without "Hey Seema" for this long; then only her name wakes her. */
private const val FOLLOW_UP_MS = 7_000L
/** A tap on the mic: the next sentence is for her (he pressed it on purpose). */
private const val TAP_GRANT_MS = 15_000L
/** "Kaun si UPI ID?": the answer is taken this long after the question. */
private const val UPI_ASK_MS = 10_000L
/** A shorter form of a name is offered only when it fits this few contacts (each is confirmed). */
private const val MAX_NEAR_MATCHES = 5
/** Between "X mil gaya, ab call laga rahi hoon" and the call / send: time to say "ruko" or the right name. */
private const val SAFETY_WINDOW_MS = 2_500L
/** "Padh ke sunau kya?" stays open this long: a "haan sunao" in standby needs no "Hey Seema". */
private const val NOTIFICATION_ASK_TTL_MS = 30_000L
/** How long Seema waits to see whether WhatsApp's Send/Call button was really pressed. */
/** How long a WhatsApp Send / Call press is waited for (slow networks, a dialog in between). */
private const val AUTO_ACTION_WAIT_MS = 14_000L
/** How long "Send dabao" still means the WhatsApp send that just failed. */
private const val UNSENT_RETRY_MS = 3 * 60_000L
/** How long the quick AI may take for a short summary before the local one is said. */
private const val QUICK_AI_TIMEOUT_MS = 8_000L
/** A call this long or longer gets "कॉल में कुछ तय हुआ?" afterwards. */
private const val POST_CALL_ASK_AFTER_MS = 30_000L
/** How long he has to answer that question without her name. */
private const val POST_CALL_ANSWER_MS = 20_000L
/** How long the PC / VPS bridge may take to answer. */
private const val REMOTE_TIMEOUT_MS = 15_000L
/** How long after a call what Raju says is checked for promises ("Rahul kal 5000 dega"). */
private const val POST_CALL_MS = 3 * 60_000L
/** "Yaad rakhna: …", "note karo …", "মনে রেখো …": a promise to keep, said any time. */
private val REMEMBER_NOTE = Regex("^(?:seema[, ]+)?(?:yaad rakhna|yaad rakho|note karo|note kar lo|याद रखना|याद रखो|नोट करो|नोट कर लो|মনে রেখো|মনে রাখবে|নোট করো)[:, ]*", RegexOption.IGNORE_CASE)
/** How long "सब मिला के बोलो" still means the money question just answered. */
private const val MONEY_FOLLOW_UP_MS = 3 * 60_000L
/** How long a spoken "haan / nahi" is listened for after "ये बटन दबा दूँ?". */
private const val SCREEN_CONFIRM_WINDOW_MS = 12_000L
/** Time the app on screen gets to react before Seema checks that the action really happened. */
private const val SCREEN_VERIFY_DELAY_MS = 900L
/** How long Seema waits for the phone to report that a call she placed has started. */
private const val CALL_START_WAIT_MS = 8_000L
/** A booking note left unfinished for this long is dropped (the next answer then is a normal question). */
private const val BOOKING_TIMEOUT_MS = 5 * 60_000L
/** How far back the bank SMS are searched for a refund. */
private const val PAYMENT_DAYS = 60
/** After the phone reports the call, the loudspeaker is switched on (once the dialer has set up its audio). */
private const val SPEAKER_AFTER_DIAL_MS = 1_200L
/** Failures where another provider may still answer (not a blocked or empty request). */
private const val TEST_SYSTEM_PROMPT = "You are a connection test. Reply with the single word OK."
private val FALLBACK_KINDS = setOf(FailureKind.RATE_LIMITED, FailureKind.QUOTA_EXCEEDED, FailureKind.TIMEOUT, FailureKind.SERVER, FailureKind.INVALID_KEY, FailureKind.UNKNOWN)
/** Consecutive recogniser errors (not silence) tolerated in hands-free mode before it stops. */
/** How long after a caller announcement "haan / utha lo" or "kaat do" is listened for. */
private const val CALL_COMMAND_WINDOW_MS = 35_000L
/** After a voice answer / cut: how long before it is checked that the phone really stopped ringing. */
private const val CALL_VERIFY_MS = 3_000L
/** How often the call state is looked at while waiting for an answer / cut to take effect. */
private const val CALL_POLL_MS = 250L
/** The loudspeaker goes on this long after "uthao" worked, and once more [SPEAKER_RETRY_MS] later. */
private const val SPEAKER_AFTER_ANSWER_MS = 500L
private const val SPEAKER_RETRY_MS = 1_000L
/** After cutting a call, the time Android takes to write it to the call log. */
private const val CALL_LOG_SETTLE_MS = 1_500L
/** How long she listens for "haan / rehne do" after "Padh ke sunau kya?". */
private const val READ_REPLY_WINDOW_MS = 10_000L
/** Extra time for an answer that started inside the window to be transcribed. */
/** "Padh ke sunao", "पढ़ के सुनाओ", "পড়ে শোনাও" with nothing else: read the newest message. */
private val BARE_READ = Regex(
    "^(?:(?:seema|सीमा|সীমা)[, ]+)?(?:(?:haan|हाँ|हां|হ্যাঁ)[, ]+)?(?:padh ke sunao|padhke sunao|padh kar sunao|padh do|padho|" +
        "पढ़ के सुनाओ|पढ़के सुनाओ|पढ़कर सुनाओ|पढ़ दो|पढ़ो|পড়ে শোনাও|পড়ো|পড়ে দাও)(?: (?:na|ना|তো))?$",
    RegexOption.IGNORE_CASE,
)
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
    // Silent for seconds (Gemini under heavy demand): the next questions go straight to the next AI for a while
    // instead of waiting for it again (each wait cost 6-9 s before the fallback answered).
    kind == FailureKind.TIMEOUT -> SLOW_PROVIDER_COOLDOWN_MS
    else -> 60_000L
}

/** "tokens per minute (TPM)", "requests per minute (RPM)": a limit that resets within a minute. */
internal fun isPerMinuteLimit(message: String): Boolean {
    val m = message.lowercase()
    return "per minute" in m || "(tpm)" in m || "(rpm)" in m
}

private const val MAX_LISTEN_FAILURES = 3
/** An AI that did not start answering in time rests this long. */
internal const val SLOW_PROVIDER_COOLDOWN_MS = 5 * 60_000L
/** A "no speech" sooner than this is the recogniser failing, not a quiet room. */
private const val FAST_NO_SPEECH_MS = 1_000L
/** After this many fast "no speech" in a row, the re-listen waits 2 s instead of 300 ms. */
private const val BACKOFF_AFTER_FAST_NO_SPEECH = 3
/** After this many, the hands-free loop stops and she waits (standby or the Mic button). */
private const val MAX_FAST_NO_SPEECH = 6
/** Chat messages kept in memory (older ones stay in the database). */
private const val MAX_TRANSCRIPT_IN_MEMORY = 300
/** A voice message ending in one of these was cut off by a pause: wait for the rest. */
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
    /** Saves the conversation language chosen by voice ("bn-IN", "hi-IN" or "auto"), so it stays after a restart. */
    private val setSpeechLanguage: suspend (String) -> Unit = {},
    /** Turns the spoken money-in alert on or off (saved in Settings). */
    private val setAnnounceMoneyIn: suspend (Boolean) -> Unit = {},
    /** Payments the UPI apps' notifications showed (amount, person, time), kept on the phone for "3630 किसका आया?". */
    private val moneyNotes: com.raju.aiassistant.domain.sms.MoneyNotes = com.raju.aiassistant.domain.sms.InMemoryMoneyNotes(),
    /** A yes / no for the message question that is waiting ("padh ke sunau?"); true when there was one. */
    private val answerAnnouncement: (Boolean) -> Boolean = { false },
    /** Switched ON again: the microphone may be used again (MicHub.unmute). */
    private val restoreMic: () -> Unit = {},
    /** PNR status from the user's PNR service; NotConfigured falls back to the official page. */
    private val pnrSource: com.raju.aiassistant.domain.business.PnrSource = com.raju.aiassistant.domain.business.PnrSource { com.raju.aiassistant.domain.business.PnrFetch.NotConfigured },
    /** Trains, live status, seats and fare from the rail service (RailKit); NotConfigured without a key. */
    private val rail: com.raju.aiassistant.domain.rail.RailSource = com.raju.aiassistant.domain.rail.NoRail,
    /** The user's saved routines (Automation screen). */
    private val routines: suspend () -> List<com.raju.aiassistant.domain.routine.Routine> = { emptyList() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Messages that came in during the last hours (in memory only), for the spoken summary. */
    private val recentMessages: () -> List<com.raju.aiassistant.domain.notify.IncomingItem> = { emptyList() },
    /** "Only my voice": the speaker check for wake-ups. */
    private val voiceId: com.raju.aiassistant.domain.voice.VoiceIdPort = com.raju.aiassistant.domain.voice.NoVoiceId,
    /** Gemini hears an audio file (a call recording) and answers [prompt]. */
    private val describeAudio: suspend (ByteArray, String, String) -> AiResult<String> = { _, _, _ -> AiResult.Failure(com.raju.aiassistant.domain.ai.FailureKind.NOT_CONFIGURED, "Audio is not supported here.") },
    /** Today's weather line for the briefing (rain / heat / cold), or null. */
    private val weatherToday: suspend () -> String? = { null },
    /** Saves a routine (Automation screen; a timed one is scheduled), for "roz 9 baje dawai ki yaad dilana". */
    private val saveRoutine: suspend (com.raju.aiassistant.domain.routine.Routine) -> Unit = {},
    /** When Raju last talked to her (kept on the phone), for "kahan the itni der se?" and late-night care. */
    private val presence: PresenceStore = InMemoryPresenceStore(),
    /** The PC / VPS bridge: sends the companion's action and params, says what it answered. */
    private val remoteBridge: suspend (String, Map<String, String>) -> com.raju.aiassistant.domain.remote.RemoteResult = { _, _ -> com.raju.aiassistant.domain.remote.RemoteResult.NotConfigured },
    /** Messages that can be answered from their notification (newest first), and the sender for one of them. */
    private val replyTargets: () -> List<com.raju.aiassistant.domain.notify.ReplyTarget> = { emptyList() },
    private val sendNotificationReply: (key: String, text: String) -> Boolean = { _, _ -> false },
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
    /** A phone call is still ringing (the audio mode says so): an answer / cut that changed nothing is retried on the screen. */
    private val callRinging: () -> Boolean = { false },
    /** A SIM call is up (answered or dialled): what the microphone hears now is the call, never a request. */
    private val inPhoneCall: () -> Boolean = { false },
    /** How the user says names, learned from their own choices (on the phone only). */
    private val contactAliases: com.raju.aiassistant.domain.tools.ContactAliasStore = com.raju.aiassistant.domain.tools.InMemoryContactAliases(),
    /** A soft, short chime when listening for an answer starts. */
    private val micOpenChime: () -> Unit = {},
) {
    /** The language Seema is speaking now (Settings choice, or detected from the user in Auto). */
    val language: StateFlow<SeemaLanguage> = conversationLanguage.asStateFlow()
    private fun lang(): SeemaLanguage =
        SeemaLanguage.fromTag(settings.value.speechLanguage) ?: conversationLanguage.value

    private val endearment = Endearment()

    /**
     * Her own fixed lines in the conversation's language. She knows the user's name but does not keep
     * saying it: the name in a line becomes a soft pet name now and then, or is left out.
     */
    private fun phrase(language: SeemaLanguage, hindi: String, bengali: String): String =
        endearment.personalize(com.raju.aiassistant.domain.language.phrase(language, hindi, bengali), language)
    /** The last message mixed Hindi and Bengali evenly: ask which language instead of guessing. */
    private var languageUnclear = false

    private val _state = MutableStateFlow(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    private val _pending = MutableStateFlow<PendingRequest?>(null)
    val pending: StateFlow<PendingRequest?> = _pending.asStateFlow()

    /** The passenger card on screen (name, age, gender with copy buttons); empty = none. */
    private val _passengers = MutableStateFlow<List<com.raju.aiassistant.domain.tatkal.Passenger>>(emptyList())
    val passengers: StateFlow<List<com.raju.aiassistant.domain.tatkal.Passenger>> = _passengers.asStateFlow()
    fun showPassengers(list: List<com.raju.aiassistant.domain.tatkal.Passenger>) { _passengers.value = list }
    fun dismissPassengers() { _passengers.value = emptyList() }

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
    /** Until this time a spoken sentence is taken as a command without the wake word. */
    @Volatile private var followUpUntil = 0L
    /** The 7-second window after her reply (see [com.raju.aiassistant.domain.voice.ConversationSessionTimer]). */
    private val session = com.raju.aiassistant.domain.voice.ConversationSessionTimer(clock)
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
        loadCorrections()
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
        // An open booking note takes the answer (a new "booking note" starts it again).
        if (bookingDraft != null && clock() < bookingUntil && !com.raju.aiassistant.domain.booking.BookingNote.isStart(trimmed)) {
            followUpUntil = clock() + FOLLOW_UP_MS
            leaveSleep()
            lastActivityAt = clock()
            cancelWork()
            lastUserText = trimmed
            job = scope.launch { bookingTurn(trimmed, speak = fromVoice || settings.value.autoSpeakReplies) }
            return
        }
        followUpUntil = clock() + FOLLOW_UP_MS
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
    private fun onHeard(rawHeard: String) {
        // Hindi written in another Indian script by the recogniser ("గీతా") is read as Devanagari.
        // Words he taught her ("mitthu" → "Mitu") are put right first.
        val heard = com.raju.aiassistant.domain.language.SpeechCorrections.apply(
            com.raju.aiassistant.domain.language.fixCommonMishearings(com.raju.aiassistant.domain.language.toDevanagari(rawHeard)), corrections,
        )
        // Switched off: nothing that was heard is acted on or answered.
        if (settings.value.assistantPaused) { trace("off: heard result dropped"); return }
        trace("recognized (${heard.length} chars)")
        fastNoSpeech = 0
        correctionWait?.let { if (!it.isCompleted) { it.complete(heard); return } }
        if (handleVoiceConfirm(heard)) return
        if (handleReadReply(heard)) return
        if (handleCallCommand(heard)) return
        if (handleVideoListening(heard)) return
        // A SIM call is up: the microphone heard the call itself (the caller on the loudspeaker, or the user
        // talking to them). That is never a request — it went to the AI and got a reply in the wrong language.
        if (inPhoneCall()) {
            trace("phone call on: heard result dropped (${heard.length} chars)")
            listenGen++
            voice.stop()
            _voiceSession.value = false
            return
        }
        // "Haan sunao" / "nahi" while a message question waits but its own listening was not open:
        // it is still the answer to that question, never a new request for the AI.
        if (heard.trim().split(Regex("\\s+")).size <= 4) {
            com.raju.aiassistant.domain.notify.readReply(heard)?.let { yes ->
                if (answerAnnouncement(yes)) { trace("read reply: ${if (yes) "yes" else "no"} (waiting question)"); return }
            }
        }
        // STRICT GATE: what she hears on her own is for her only when it calls her by name, or inside the short
        // window after her reply / a tap on the mic. Talk with someone else or another assistant is dropped
        // silently — never sent to an AI, never answered.
        val open = session.accepts(listenStartedAt) || clock() < followUpUntil || heldVoiceText != null
        val verdict = com.raju.aiassistant.domain.voice.WakeWordGate.check(heard, open)
        when (verdict) {
            com.raju.aiassistant.domain.voice.WakeWordGate.Verdict.Ignore -> {
                trace("gate: not for her, dropped (${heard.length} chars)")
                ignoreHeard()
                return
            }
            com.raju.aiassistant.domain.voice.WakeWordGate.Verdict.OtherAssistant -> {
                trace("gate: spoken to another assistant, follow-up closed")
                closeFollowUp()
                ignoreHeard()
                return
            }
            // Taken: this window (a tap's, or the last reply's) is used up; her next reply opens a fresh 7 s.
            else -> session.close()
        }
        val text = heldVoiceText?.let { "$it $heard" } ?: heard
        _heard.value = text
        heldVoiceText = null
        heldJob?.cancel()
        if (isUnfinished(text) && text.trim().split(Regex("\\s+")).size >= 2) {
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
        val requestAfterWake = (verdict as? com.raju.aiassistant.domain.voice.WakeWordGate.Verdict.Called)?.request
            ?: if (settings.value.wakeWord) afterWakeWord(text) else null
        if (requestAfterWake != null) {
            if (requestAfterWake.isBlank()) {
                // The user only called her name ("हे सीमा" / "सीमा" / "Hey Seema") to wake/greet her:
                // Instantly reply "हाँ जी, बोलिए?" (or Bengali "হ্যাঁ, বলুন?") and keep listening!
                startWakeSession()
                cancelWork()
                job = scope.launch {
                    say(phrase(lang(), "हाँ जी, बोलिए?", "হ্যাঁ, বলুন?"), allowBargeIn = false)
                    session.openAfterReply()
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
        // He pressed the mic: what he says next is for her.
        session.openFor(TAP_GRANT_MS)
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
        if (wakeToStart()) {
            // "Hey Seema" se hi shuru: opening the app (or hands-free) never answers what it hears; she
            // waits asleep for her name. After it, the conversation goes on without the name until
            // [sleepDue] (quiet for the "sleep after" time) puts her back to sleep.
            listenFailures = 0
            if (settings.value.assistantPaused) return
            trace("hands-free: waiting for \"Hey Seema\" first")
            val busy = _state.value == AssistantState.Thinking || _state.value == AssistantState.Speaking || _state.value == AssistantState.Executing
            if (!asleep && !busy && _pending.value == null && !_voiceSession.value) goToSleep()
            return
        }
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
        if (canStandby()) {
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

    /** Voice is answered only after "Hey Seema" wakes her (and needs a working wake word). */
    private fun wakeToStart(): Boolean = settings.value.wakeToStart && canSleep()

    /** "Hey Seema" standby is possible: wake word on, not switched off, a listener exists. */
    private fun canSleep(): Boolean =
        settings.value.wakeWord && !settings.value.assistantPaused && wakeListener !== NoWakeListener

    /** A follow-up is open: she keeps listening in the background even with the wake word switched off. */
    fun followUpOpen(): Boolean = !paused() && clock() < followUpUntil && wakeListener !== NoWakeListener

    /** Standby is possible now: the wake word, or an open follow-up window. */
    private fun canStandby(): Boolean = canSleep() || followUpOpen()

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
                // The follow-up window closed while the wake word is off: nothing listens any more.
                if (asleep && !settings.value.wakeWord && !followUpOpen()) leaveSleep()
                if (canStandby() && !asleep && !pausedForCall && !busy && job?.isActive != true &&
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
    private fun callStyleAllowed() = settings.value.bargeIn && voice.capturesInApp() && headphones() && clock() >= callCommandUntil

    /** When the microphone is free for speech again after a wake (see [MIC_HANDOFF_MS]); 0 when it always is. */
    private var micFreeAt = 0L

    private fun listen(afterHandoff: Boolean = false) {
        if (paused()) {
            // Switched OFF: never open the microphone, whatever asked for it.
            voice.stop()
            _voiceSession.value = false
            _state.value = AssistantState.Idle
            return
        }
        val gap = micFreeAt - clock()
        if (!afterHandoff && gap > 0) {
            // Still handing the microphone over: start the recogniser once the gap has passed (never before).
            trace("[SeemaVoice] waiting ${gap} ms for the microphone")
            _state.value = AssistantState.Listening
            scope.launch { delay(gap); listen(afterHandoff = true) }
            return
        }
        trace("[SeemaVoice] listening, microphone free")
        // A soft 90 ms chime when she starts listening for an answer (after her name or a question), so it is
        // clear from across the room that she is listening.
        if (chimeOnNextListen) {
            chimeOnNextListen = false
            runCatching { micOpenChime() }
        }
        val gen = ++listenGen
        // A ringing call or a "haan / nahi" question: a short answer, cut quickly.
        com.raju.aiassistant.domain.voice.TurnPacing.quick = clock() < callCommandUntil || voiceConfirmOpen()
        _state.value = AssistantState.Listening
        _heard.value = null
        _hint.value = null
        // The system recogniser (another app) gets only silence while call-style audio is on, and
        // without headphones call-style audio would play her voice from the earpiece.
        if (!callStyleAllowed()) conversationMode.exit()
        trace("listen ${if (handsFree) "hands-free" else "tap"} ${if (voice.capturesInApp()) "in-app" else "system"} recogniser${if (conversationMode.active) " (call-style audio)" else ""}")
        listenStartedAt = clock()
        voice.start(
            lang().tag,
            onResult = { heard -> if (gen == listenGen) onHeard(heard) else trace("stale result dropped") },
            onError = { err ->
                if (gen != listenGen) { trace("stale error dropped"); return@start }
                trace("listen error: ${err.message}")
                // Waiting for "haan / nahi" to a screen action: keep listening until the window ends.
                if (voiceConfirmOpen()) {
                    val g = listenGen
                    scope.launch { delay(300); if (g == listenGen && voiceConfirmOpen()) listen() }
                    return@start
                }
                // "Padh ke sunau kya?": a short "haan / nahi" often comes back as low-confidence. Its
                // yes/no words are still clear enough; anything else is asked again while time remains.
                if (replyHandler != null) {
                    val unsure = err.heard?.let { readReply(it) }
                    when {
                        unsure != null -> { trace("read reply: ${if (unsure) "yes" else "no"} (low confidence)"); finishReadReply(unsure) }
                        (err.noSpeech || err.unclear) && clock() < replyUntil - 1_000 -> {
                            val g = listenGen
                            scope.launch { delay(250); if (g == listenGen && replyHandler != null) listen() }
                        }
                        else -> finishReadReply(null)
                    }
                    return@start
                }
                if (correctionWait != null) return@start // the stop window: silence means "go ahead"
                if (videoListening && tools.recordingVideo() && !err.permissionDenied) {
                    // Recording: silence or noise never ends the listening — "band kar do" can come any moment.
                    val g = listenGen
                    scope.launch { delay(if (err.noSpeech || err.unclear) 250 else 800); if (g == listenGen && videoListening && tools.recordingVideo()) listen() }
                    return@start
                }
                if (clock() < callCommandUntil) {
                    // While it rings: "utha lo / kaat do" heard unsurely still counts (never an SMS from
                    // unsure words); silence or noise → listen again for the rest of the ring.
                    val unsure = err.heard?.let { callCommand(it) }
                    if (unsure != null && err.heard != null && callReplyText(err.heard) == null) {
                        handleCallCommand(err.heard)
                    } else if (!err.permissionDenied && clock() < callCommandUntil - 1_500) {
                        // Silence, noise, the ringtone, a busy microphone or a network hiccup: none of these
                        // may end the listening while the phone still rings — "utha lo" can come any moment.
                        trace("call: nothing clear yet (${if (err.noSpeech) "silence" else if (err.unclear) "unclear" else "recogniser error"}), listening again")
                        val g = listenGen
                        scope.launch { delay(if (err.noSpeech || err.unclear) 250 else 600); if (g == listenGen && clock() < callCommandUntil) listen() }
                    } else {
                        callCommandUntil = 0
                        trace("call: no answer heard, stop listening")
                        returnToStandby()
                    }
                    return@start
                }
                val keepGoing = handsFree && _voiceSession.value && !settings.value.assistantPaused && !err.permissionDenied
                val held = heldVoiceText
                if (inBackground && held == null && canSleep() && (err.noSpeech || err.unclear) && !wakeConversation()) {
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
                    val windowOver = !session.isOpen() && clock() >= followUpUntil
                    if (windowOver && _pending.value == null && replyHandler == null && (canSleep() || settings.value.voiceMode == VoiceMode.SESSION)) {
                        // Her 7 s were up and nobody spoke to her: only "Hey Seema" wakes her now (a conversation
                        // session simply ends: microphone off).
                        trace("follow-up window over")
                        if (settings.value.voiceMode == VoiceMode.SESSION && !inBackground) endSession() else goToSleep()
                    } else if (sleepDue()) {
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
    /** Heard speech that was not for her: nothing is said; she waits for her name (or keeps listening, gated). */
    private fun ignoreHeard() {
        heldVoiceText = null
        heldJob?.cancel()
        if (paused()) return
        when {
            canSleep() && _pending.value == null && (inBackground || settings.value.voiceMode != VoiceMode.SESSION) -> goToSleep()
            handsFree && _voiceSession.value -> relisten(300)
            else -> returnToStandby()
        }
    }

    /** "Chup raho", or he turned to another assistant: the no-name window is shut at once. */
    private fun closeFollowUp() {
        session.close()
        followUpUntil = 0L
        heldVoiceText = null
        heldJob?.cancel()
    }

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

    /**
     * Her name woke her: with "Hey Seema se hi shuru" the conversation now goes on without the name —
     * she listens again after every reply — until she is quiet for the "sleep after" time.
     */
    /** Woken by name ("wake to start") and still inside the quiet timer: the conversation is on. */
    private fun wakeConversation(): Boolean = settings.value.wakeToStart && handsFree && !sleepDue()

    private fun startWakeSession() {
        if (!settings.value.wakeToStart) return
        handsFree = true
        _handsFreeActive.value = true
        lastActivityAt = clock()
    }

    /** Quiet for a while: release the recogniser (no beeps, no data) and wait on the phone for her name. */
    private fun goToSleep() {
        liveCameraFront = null
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
        // A SIM call is up (often on the loudspeaker): its voices are not "Hey Seema" or a request.
        if (inPhoneCall()) {
            trace("wake: phone call on, not checked")
            armWakeListener()
            return
        }
        if (wakeListener.spotsWakeWord) {
            // "Only my voice": someone else saying her name does not wake her (no voice print / too short: allowed).
            if (pcm.isNotEmpty() && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { voiceId.matchesOwner(pcm) } == false) {
                trace("wake word: not the owner's voice, ignored")
                armWakeListener()
                return
            }
            // The offline detector heard "Hey Seema" on the phone itself: wake and listen.
            trace("wake word (offline)")
            onWake()
            startWakeSession()
            wakeUp(greet = true)
            return
        }
        val now = clock()
        // Follow-up: she has just done something for the user, so the next sentence needs no "Hey Seema".
        val followUp = followUpOpen()
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
            // Battery and data: a faint sound, or the 5th noise in a row without her name, is not
            // sent online at all (the offline detector, when installed, never needs this).
            when (wakeGate.decide(pcm)) {
                com.raju.aiassistant.domain.voice.WakeCheckGate.Decision.TOO_QUIET -> {
                    trace("wake: too quiet, not checked online")
                    armWakeListener()
                    return
                }
                com.raju.aiassistant.domain.voice.WakeCheckGate.Decision.CHECK -> Unit
            }
            // Keep listening while her name is checked online (1-3 s on a slow connection): a
            // "Hey Seema" said during the check used to fall into that gap and was never heard.
            armWakeListener()
            var raw = runCatching { t.transcribe(pcm) }.getOrNull()
            if (raw == null && asleep) {
                // The check itself failed (network): try once more rather than drop her name.
                trace("wake: name check failed, trying once more")
                raw = runCatching { t.transcribe(pcm) }.getOrNull()
            }
            // (Hindi written in another Indian script by the recogniser is read as Devanagari.)
            val heard = com.raju.aiassistant.domain.language.toDevanagari(raw?.trim().orEmpty())
            // Privacy: only lengths in the log, never what was said.
            trace("wake heard: ${heard.length} chars")
            // The phone rings and "Seema, call uthao" was in this recording: it is the answer to the call question
            // (it used to be dropped because the call question had already woken her).
            if (callCommandOpen() && heard.isNotBlank() && com.raju.aiassistant.domain.notify.callCommand(heard) != null) {
                trace("call: answer heard in the wake recording")
                handleCallCommand(heard)
                return
            }
            if (!asleep) return
            val request = when {
                // Her name said: the rest is the request (as before). No name: the whole sentence,
                // unless it is just the echo of her own answer, which was said within the last 2.5 s.
                followUp -> com.raju.aiassistant.domain.voice.WakeWordGate.request(heard) ?: afterWakeWord(heard)
                    ?: heard.takeIf { it.length >= 2 && now - lastSpokeAt >= 2_500L && !com.raju.aiassistant.domain.voice.WakeWordGate.addressesOther(it) }
                settings.value.wakeWord -> afterWakeWord(heard)
                // Wake word switched off and her window closed: still only her name wakes her, never other talk.
                else -> com.raju.aiassistant.domain.voice.WakeWordGate.request(heard)
            }
            trace("wake request: ${when { request == null -> "no name"; request.isBlank() -> "name only"; else -> "${request.length} chars" }}")
            when {
                request == null -> Unit // someone else talking, TV, noise: keep sleeping (still listening)
                request.isBlank() -> { onWake(); startWakeSession(); wakeUp(greet = true) } // "Seema?" → "Haan ji, boliye?"
                else -> {
                    onWake()
                    startWakeSession()
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
            // Her name cannot be checked: a voice wakes her only inside her follow-up window, never other talk later.
            if (!followUp) {
                trace("wake: name cannot be checked and the window is closed, staying asleep")
                armWakeListener()
                return
            }
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
        // Her name or a tap woke her: the next sentence is for her.
        if (!greet) { session.openFor(TAP_GRANT_MS); listen(); return }
        cancelWork()
        job = scope.launch {
            say(phrase(lang(), "हाँ जी, बोलिए?", "হ্যাঁ, বলুন?"), allowBargeIn = false)
            session.openFor(TAP_GRANT_MS)
            if (_voiceSession.value) listen()
        }
    }

    private fun leaveSleep() {
        if (!asleep) return
        asleep = false
        wakeListener.stop()
        // The wake-word recorder has been told to stop: the recogniser waits for the handoff gap below.
        micFreeAt = clock() + MIC_HANDOFF_MS
        trace("[SeemaWake] wake-word microphone released; speech starts in $MIC_HANDOFF_MS ms")
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
                trace("beacon ringing: ignored (${text.length} chars)")
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
        // How long he was away (and the hour), read before this talk is marked; used in this reply's instructions.
        val lastTalk = presence.lastInteraction()
        feelingNow = EmotionStateTracker.promptLine(EmotionStateTracker.cues(clock(), lastTalk, zone()), clock(), lastTalk, lang() == SeemaLanguage.BENGALI)
        presence.markInteraction(clock())
        // "Sir dard hai": remembered; hours later she asks how he is (once, in the next AI reply).
        careAsked = false
        val concern = com.raju.aiassistant.domain.memory.CareFollowUp.concernOf(text)
        if (concern != null) presence.setConcern(concern, clock())
        else presence.concern()?.let { (c, at) ->
            com.raju.aiassistant.domain.memory.CareFollowUp.promptLine(c, at, clock(), lang() == SeemaLanguage.BENGALI)?.let { line ->
                feelingNow = listOfNotNull(feelingNow, line).joinToString("\n")
                careAsked = true
            } ?: run { if (clock() - at > com.raju.aiassistant.domain.memory.CareFollowUp.FORGET_AFTER_MS) presence.setConcern(null, clock()) }
        }
        if (feelingNow != null) trace("feeling: ${EmotionStateTracker.cues(clock(), lastTalk, zone()).joinToString { it.name.lowercase() }}")
        // "Band kar do" while a video is being recorded (typed, or after "Hey Seema").
        if (tools.recordingVideo() && com.raju.aiassistant.domain.camera.CameraVoice.isStop(text)) {
            if (runLocalCommand(AssistantAction.StopVideo, speak = fromVoice || s.autoSpeakReplies)) return
        }
        // "Mera bhai Amit hai", "mujhe chai pasand hai": kept quietly as a long-term fact; the turn goes on as usual.
        if (s.memoryEnabled) learnFact(text)

        // "QR kisko bhejna hai?" → "Rizwan ko" / "Rizwan".
        pendingPayTo?.let { pay ->
            pendingPayTo = null
            if (clock() < pendingPayToUntil && Correction.parse(text) != Correction.Cancel) {
                val who = com.raju.aiassistant.domain.payments.PaymentShare.nameFromAnswer(text)
                val next = when (pay) {
                    is AssistantAction.SendPaymentQr -> pay.copy(contact = who, amountRupees = pay.amountRupees ?: com.raju.aiassistant.domain.payments.PaymentShare.amount(text))
                    is AssistantAction.SendUpiId -> pay.copy(contact = who)
                    else -> null
                }
                if (next != null && who.length >= 2 && runLocalCommand(next, speak = fromVoice || s.autoSpeakReplies)) return
            }
        }
        // "Jalal bhai ko kaun si UPI ID bhejni hai — Paytm ya PhonePe?" → "Paytm wala".
        pendingUpi?.let { p ->
            pendingUpi = null
            if (clock() < p.until) {
                val entry = com.raju.aiassistant.domain.payments.PaymentShare.pick(s.upiIds, text)
                if (entry != null) {
                    trace("upi: chosen from the answer")
                    if (runLocalCommand(AssistantAction.SendUpiId(p.contact, entry.label, p.amountRupees), speak = fromVoice || s.autoSpeakReplies)) return
                } else if (Correction.parse(text) == Correction.Cancel) {
                    val line = phrase(lang(), "ठीक है, UPI ID नहीं भेजी।", "ঠিক আছে, UPI ID পাঠাইনি।")
                    record(ChatRole.ASSISTANT, line)
                    if (fromVoice || s.autoSpeakReplies) say(line)
                    finishTurn()
                    return
                }
            }
        }
        // "ठीक है रुको, सब मिला के बोलो" right after a list of amounts: the same question, the total only.
        lastMoneyAsk?.let { last ->
            if (clock() - lastMoneyAskAt <= MONEY_FOLLOW_UP_MS && com.raju.aiassistant.domain.sms.asksTotalOnly(text) &&
                LocalCommands.parse(text) !is AssistantAction.MoneyQuestion
            ) {
                val again = when (last) {
                    is com.raju.aiassistant.domain.sms.MoneyAsk.Amounts -> last.copy(totalOnly = true, withNames = false)
                    else -> null
                }
                if (again != null && runLocalCommand(AssistantAction.MoneyQuestion(again), speak = fromVoice || s.autoSpeakReplies)) return
            }
        }
        // After a call (or "yaad rakhna: …" any time): "Rahul kal 5000 dega", "shaam ko Amit ko call karna hai" become
        // reminders, with one short confirmation. Nothing is said when no promise is in the sentence.
        if (clock() < postCallUntil || REMEMBER_NOTE.containsMatchIn(text)) {
            val now = java.time.Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDateTime()
            val promises = com.raju.aiassistant.domain.memory.CallMemoryExtractor.extract(text.replace(REMEMBER_NOTE, " "), now)
            if (promises.isNotEmpty()) {
                postCallUntil = 0
                saveCommitments(promises, speak = fromVoice || s.autoSpeakReplies)
                if (_state.value != AssistantState.Error) _state.value = AssistantState.Success
                finishTurn()
                return
            }
        }
        // "Tum dabao / Send kar do" right after WhatsApp's Send could not be pressed: press it again for that same
        // chat (already confirmed), never a new message or the AI guessing.
        unsentShare?.let { u ->
            if (clock() >= u.until) {
                unsentShare = null
            } else if (SendRetry.asksToPress(text) && LocalCommands.parse(text).let { a ->
                    a == null || (a is AssistantAction.SendPaymentQr && a.contact.isBlank()) || (a is AssistantAction.SendUpiId && a.contact.isBlank())
                }) {
                unsentShare = null
                retryUnsentShare(u, speak = fromVoice || s.autoSpeakReplies)
                if (_state.value != AssistantState.Error) _state.value = AssistantState.Success
                finishTurn()
                return
            }
        }

        // Simple phone commands ("torch on karo", "Mummy ko call karo", "5 minute ka timer") run on
        // the phone at once: no AI request, no tokens, and they also work without internet.
        // "Batao" / "haan padho" a little after "Padh ke sunau kya?": read that message.
        if (notificationFollowUp() && readReply(text) == true && text.trim().split(Regex("\\s+")).size <= 6) {
            if (runLocalCommand(AssistantAction.ReadNotification, speak = fromVoice || s.autoSpeakReplies)) return
        }
        // "Padh ke sunao" on its own, later (the question's two minutes are over): the newest message is read.
        if (BARE_READ.matches(text.trim().trimEnd('.', '!', '?', '।')) && recentMessages().any { !it.isCall }) {
            if (runLocalCommand(AssistantAction.ReadNotification, speak = fromVoice || s.autoSpeakReplies)) return
        }
        // "Rizwan Travels ko kya likhun?" was asked: this sentence is the message (sent after the usual confirm).
        waAskContact?.let { who ->
            waAskContact = null
            if (clock() < waAskUntil && text.isNotBlank()) {
                trace("whatsapp text received")
                if (runLocalCommand(AssistantAction.WhatsApp(who, text.trim()), speak = fromVoice || s.autoSpeakReplies)) return
            }
        }
        // A PNR said in pieces ("8 3 5 3", then "2 1 9 …"): digits keep being added until all 10 are there.
        if (clock() < pnrCollectUntil && text.isNotBlank()) {
            val more = com.raju.aiassistant.domain.rail.VoiceNumberSanitizer.digits(text)
            val other = LocalCommands.parse(text)
            when {
                Regex("(?i)rehne do|rahne do|chhodo|cancel|रहने दो|छोड़ो|कैंसल").containsMatchIn(text) -> {
                    pnrCollectUntil = 0
                    pnrDigits = ""
                    trace("pnr: collecting cancelled")
                }
                more.isNotEmpty() && (other == null || other is AssistantAction.PnrTrack) -> {
                    trace("pnr: +${more.length} digits")
                    if (runLocalCommand(AssistantAction.PnrTrack(pnrDigits + more), speak = fromVoice || s.autoSpeakReplies)) return
                }
            }
        }
        // Live camera on: a question that is not a phone command is answered from a fresh photo.
        liveCameraFront?.let { front ->
            if (clock() >= liveCameraUntil) {
                liveCameraFront = null
            } else if (LocalCommands.parse(text) == null) {
                liveCameraUntil = clock() + com.raju.aiassistant.domain.vision.LIVE_CAMERA_MS
                trace("live camera question")
                if (runLocalCommand(AssistantAction.LookCamera(front, text), speak = fromVoice || s.autoSpeakReplies)) return
            }
        }
        // A saved routine by its name ("good night chalao"): the user's own name wins over other commands.
        runCatching { com.raju.aiassistant.domain.routine.routineFor(text, routines()) }.getOrNull()?.let { r ->
            trace("routine by voice (${r.steps.size} steps)")
            runRoutine(r, speak = fromVoice || s.autoSpeakReplies)
            return
        }
        var screenQuestion: AssistantAction.ReadScreen? = null
        LocalCommands.parse(text)?.let { action ->
            // "Anand ka booking aaya hai Pune se Howrah 3A, note karo": the details said with it are read by
            // the short request (online) instead of starting an empty note that asks for each again.
            if (action is AssistantAction.BookingNoteStart && s.smartUnderstanding && isOnline() &&
                text.trim().split(Regex("\\s+")).size > 6
            ) {
                trace("booking with details: short request")
                return@let
            }
            if (action is AssistantAction.ReadScreen && isOnline()) {
                // "Screen padho": the screen goes to the AI in one request (no routing request first).
                trace("fast route: ${action::class.simpleName} (matched in ${clock() - turnStartedAt} ms, one AI request)")
                screenQuestion = action
                return@let
            }
            // Fast route: a plain phone command is done on the phone, with no AI request at all.
            trace("fast route: ${action::class.simpleName} (matched in ${clock() - turnStartedAt} ms, no AI)")
            if (runLocalCommand(action, speak = fromVoice || s.autoSpeakReplies)) return
            trace("fast route declined: ${action::class.simpleName}; asking the AI")
        }
        // "Torch on karo aur volume 50 karo": several phone commands in one sentence, done one by one on
        // the phone. Only when every part is such a command; anything else is one question for the AI.
        if (screenQuestion == null) com.raju.aiassistant.domain.routine.splitChain(text)?.let { parts ->
            val actions = parts.map { LocalCommands.parse(it) }
            if (actions.all { it != null && it !is AssistantAction.ReadScreen && it !is AssistantAction.BookingNoteStart }) {
                if (runSteps(actions.filterNotNull(), speak = fromVoice || s.autoSpeakReplies)) return
            }
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
        // Hybrid brain: quick messages to Groq, careful thinking to Gemini — when that key is saved and
        // the provider is not resting after a limit. The usual fallback still covers any failure.
        val tier = brainTier(text)
        if (s.hybridBrain) {
            val preferred = preferredProvider(tier)
            if (preferred != providerId && (providerCooldown[preferred] ?: 0L) <= clock() && !keyFor(preferred).isNullOrBlank()) {
                trace("brain: ${tier.name.lowercase()} → ${preferred.shortName}")
                providerId = preferred
            }
        }
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
        // A phone task in other words than the fixed rules know: a short request (only the list of what
        // the phone can do) decides it; the phone then does it. Chat goes on to the full conversation.
        if (s.smartUnderstanding && IntentRouter.looksLikeTask(text)) {
            // The router asks one short question: the fastest AI answers it (Groq, ~0.4 s), even when the
            // reply itself goes to Gemini for careful thinking — on Gemini the router alone took 3 s.
            val (routerProvider, routerModel) = fastRouter() ?: (provider to model)
            val routed = withTimeoutOrNull(ROUTER_TIMEOUT_MS) {
                runCatching { routerProvider.chat(routerModel, IntentRouter.system(today()), listOf(Turn(TurnRole.USER, text))) }.getOrNull()
            }
            val action = (routed as? AiResult.Success)?.value?.let { IntentRouter.toAction(it) }
            trace("intent router: ${action?.let { it::class.simpleName } ?: "chat / not a task"}")
            if (action != null) {
                val speakIt = fromVoice || s.autoSpeakReplies
                if (action is AssistantAction.BookingNoteWith) {
                    bookingDraft = com.raju.aiassistant.domain.booking.BookingDraft()
                    bookingLastPrompt = null
                    bookingRepeats = 0
                    bookingTurn(action.said, speakIt)
                    return
                }
                if (runLocalCommand(action, speakIt)) return
                executeAndExplain(action, provider, model, "", speakIt)
                if (_state.value != AssistantState.Error) _state.value = AssistantState.Success
                finishTurn()
                return
            }
        }
        val allMemories = if (s.memoryEnabled) runCatching { memories.all().map { it.text } }.getOrDefault(emptyList()) else emptyList()
        // Smart memory: with many memories, the ones related to this message are used.
        val memoryTexts = relevantMemories(allMemories, text, memoryLimitFor(if (s.hybridBrain) tier else BrainTier.DEEP))
        val recentOpenings = _transcript.value.asReversed().asSequence()
            .filter { it.role == ChatRole.ASSISTANT }.take(3).map { openingOf(it.text) }.filter { it.isNotBlank() }.toList()
        // Knowledge Base: passages of the user's documents that match this message (searched on the phone).
        val documents = runCatching { knowledge.search(text) }.getOrDefault(emptyList())
        if (documents.isNotEmpty()) trace("knowledge: ${documents.size} passage(s)")
        val context = PromptContext(
            voiceTurn = fromVoice, recentOpenings = recentOpenings, partOfDay = partOfDay(hourOfDay()), business = businessSnapshot(),
            language = lang(), languageUnclear = languageUnclear, documents = documents, savedPayment = savedPaymentLine(s),
            feeling = feelingNow,
        )
        // The care question goes into this reply: asked once.
        if (careAsked) presence.setConcern(null, clock())
        // The mood the user's own words show, for this answer only (nothing is stored).
        val mood = moodOf(text)
        if (mood != null) trace("mood: ${mood.name.lowercase()}")
        val system = buildSystemPrompt(memoryTexts, nowDescription(), context) +
            (mood?.let { "\nMOOD NOW: ${it.instruction}" } ?: "")
        screenQuestion?.let { action ->
            readScreenAction(action, provider, model, system, speak = fromVoice || s.autoSpeakReplies)
            if (_state.value != AssistantState.Error) _state.value = AssistantState.Success
            finishTurn()
            return
        }
        val turns = historyTurns(model, if (s.hybridBrain) tier else BrainTier.DEEP)

        trace("ai request ${providerId.shortName}")
        // Silence allowed before switching to another AI: longer for careful thinking on Gemini.
        val firstTextWait = if (s.hybridBrain && tier == BrainTier.DEEP && providerId == ProviderId.GEMINI) DEEP_FIRST_TEXT_TIMEOUT_MS else FIRST_TEXT_TIMEOUT_MS
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
                            delay(firstTextWait)
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
                        else if (silentTooLong) AiResult.Failure(FailureKind.TIMEOUT, "${providerId.shortName} did not start answering within ${firstTextWait / 1000} seconds.")
                        else AiResult.Failure(FailureKind.TIMEOUT, "${providerId.shortName} did not answer within ${AI_TIMEOUT_MS / 1000} seconds.")
                    }
                }
                headReady.await()?.let { head ->
                    // A fast AI has the whole reply a moment later: one say() then (its next chunk is
                    // prepared while the first plays) is smoother than the head, a gap, and the rest.
                    if (withTimeoutOrNull(EARLY_HEAD_GRACE_MS) { request.await() } == null) {
                        // The head may start with the mood tag: the voice reads and removes it; the
                        // remainder is matched against the head without it.
                        spokenHead = com.raju.aiassistant.domain.voice.VoiceMoodParser.parse(head).text
                        trace("speaking early (${head.length} chars)")
                        say(head)
                    }
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
                // Short: the service's own error text (quota numbers, organisation ids) means nothing to the user.
                val why = if (streamed.kind == com.raju.aiassistant.domain.ai.FailureKind.QUOTA_EXCEEDED) "limit reached" else streamed.message.take(80)
                note("↪ ${providerId.shortName}: $why — answered by ${backup.provider.shortName} instead.")
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
        // Layer 2: the quick AI gave up ("समझ नहीं आया", "पता नहीं") — Gemini answers it properly instead.
        if (spokenHead == null && answeredBy != ProviderId.GEMINI && parsedRaw.actions.isEmpty() && SmartEscalation.isUnsure(parsedRaw.text)) {
            askGemini(system + SmartEscalation.ESCALATION_NOTE, turns)?.let { g ->
                val again = parseReply(g.text)
                if (again.text.isNotBlank() && !SmartEscalation.isUnsure(again.text)) {
                    note("↪ ${answeredBy.shortName} was not sure — answered by Gemini.")
                    answeredBy = g.provider
                    answeredModel = g.model
                    raw = g.text
                    parsedRaw = again
                }
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
                // The AI's mood for this answer (or the default for the hour).
                speaker.setMood(parsed.mood)
                if (rest.isNotBlank()) say(rest)
            } finally {
                turnActive = false
            }
        }
        recordLatency(answeredBy, answeredModel, firstTextAt.get(), ok = true)

        var hadActions = false
        for (action in parsed.actions) {
            hadActions = true
            trace("ai action: ${action::class.simpleName}")
            val activeProvider = if (answeredBy == providerId) provider else keyFor(answeredBy)?.let { providerFor(answeredBy, it) } ?: provider
            executeAndExplain(action, activeProvider, answeredModel, system, speak)
        }

        if (hadActions && _state.value != AssistantState.Error) {
            _state.value = AssistantState.Success
            delay(doneShownMs())
        }
        finishTurn()
    }

    private val _latencies = MutableStateFlow<List<TurnLatency>>(emptyList())
    /** Response times of the last replies (Tools → Response time). */
    val latencies: StateFlow<List<TurnLatency>> = _latencies.asStateFlow()
    /** Which standby sounds are worth an online name check (faint ones and noise streaks are not). */
    private val wakeGate = com.raju.aiassistant.domain.voice.WakeCheckGate()

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
    private fun historyTurns(model: String, tier: BrainTier = BrainTier.DEEP): List<Turn> = toTurns(
        _transcript.value,
        historyExchangesFor(tier, minOf(settings.value.historyLimit, RECENT_EXCHANGES)) * 2,
        minOf(ModelAdvice.historyCharBudget(model), HISTORY_CHARS_PER_REQUEST),
    )

    private class FallbackReply(val provider: ProviderId, val model: String, val text: String)

    private suspend fun keyFor(id: ProviderId): String? {
        val raw = secret(id.secret)?.takeIf { it.isNotBlank() }
        // Ollama has no key; its saved address stands in for one. No built-in address: without one saved,
        // nothing is sent (it used to go to a fixed LAN address, which could be any device on the Wi-Fi).
        return raw
    }

    /** Tries the other configured providers in turn; returns the first real reply, or null. */
    private suspend fun fallbackReply(failed: ProviderId, system: String, turns: List<Turn>): FallbackReply? {
        val now = clock()
        for (id in fallbackProviders().filter { it != failed && (providerCooldown[it] ?: 0L) <= now }) {
            val key = keyFor(id) ?: continue
            val p = providerFor(id, key)
            val model = (resolveModel(id, key, p) as? AiResult.Success)?.value ?: continue
            trace("ai fallback ${id.shortName}")
            // A backup answers quickly or not at all: the user is already waiting for the first AI.
            val r = withTimeoutOrNull(FALLBACK_TIMEOUT_MS) { p.chat(model, system, turns) } ?: continue
            if (r is AiResult.Success && r.value.isNotBlank()) return FallbackReply(id, model, r.value)
            if (r is AiResult.Failure) {
                providerCooldown[id] = now + cooldownMsFor(r.kind, r.message)
            }
        }
        return null
    }

    /**
     * One careful answer from Google AI Studio (Gemini), for a question the quick layer could not answer; null when
     * no Gemini key is saved, Gemini is resting after a limit, or it does not answer in time.
     */
    private suspend fun askGemini(system: String, turns: List<Turn>): FallbackReply? {
        val id = ProviderId.GEMINI
        if ((providerCooldown[id] ?: 0L) > clock()) return null
        val key = keyFor(id) ?: return null
        val p = providerFor(id, key)
        val model = (resolveModel(id, key, p) as? AiResult.Success)?.value ?: return null
        trace("escalation: gemini")
        val r = withTimeoutOrNull(FALLBACK_TIMEOUT_MS) { p.chat(model, system, turns) } ?: return null
        return when (r) {
            is AiResult.Success -> r.value.takeIf { it.isNotBlank() }?.let { FallbackReply(id, model, it) }
            is AiResult.Failure -> {
                providerCooldown[id] = clock() + cooldownMsFor(r.kind, r.message)
                null
            }
        }
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
    /** Groq for the intent router when its key is saved and it is not resting after a limit; null otherwise. */
    private suspend fun fastRouter(): Pair<AiProvider, String>? {
        val id = ProviderId.GROQ
        if ((providerCooldown[id] ?: 0L) > clock()) return null
        val key = keyFor(id)?.takeIf { it.isNotBlank() } ?: return null
        val p = providerFor(id, key)
        val m = (withTimeoutOrNull(1_500L) { resolveModel(id, key, p) } as? AiResult.Success)?.value ?: return null
        return p to m
    }

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
        val key = keyFor(id) ?: return result(
            error = if (id == ProviderId.OLLAMA) "Ollama PC address is not set (Settings → AI Brain → Ollama)." else "${id.displayName} API key is not set.",
        )
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
    private suspend fun runLocalCommand(action: AssistantAction, speak: Boolean, endTurn: Boolean = true): Boolean {
        val s = settings.value
        if (s.safetyMode && (action is AssistantAction.Call || action is AssistantAction.OpenApp || action is AssistantAction.WhatsApp ||
                action is AssistantAction.WhatsAppFile || action is AssistantAction.Sms || action is AssistantAction.ScreenTap || action is AssistantAction.ScreenType)
        ) return false
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
            is AssistantAction.SetTatkalAlarm -> {
                trace("offline command: tatkal alarm")
                val outcome = tools.setTatkalAlarm(action.hour, action.minute, action.kind)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) {
                    val time = "%02d:%02d".format(action.hour, action.minute)
                    val late = outcome.message.contains(com.raju.aiassistant.domain.tools.TATKAL_INEXACT)
                    say(
                        phrase(lang(), "${action.kind} Tatkal का अलार्म रोज़ $time बजे लगा दिया है।", "${action.kind} Tatkal-এর অ্যালার্ম রোজ $time-এ দিয়ে দিয়েছি।") +
                            if (late) phrase(lang(), " अलार्म ठीक समय पर बजे, इसके लिए फ़ोन में Alarms & reminders की अनुमति दीजिए।", " অ্যালার্ম ঠিক সময়ে বাজার জন্য ফোনে Alarms & reminders অনুমতি দিন।") else "",
                    )
                }
            }
            is AssistantAction.StartVideo -> {
                trace("offline command: video start")
                _state.value = AssistantState.Executing
                startVideo(action, speak)
            }
            AssistantAction.StopVideo -> {
                trace("offline command: video stop")
                _state.value = AssistantState.Executing
                stopVideo(action, speak)
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
                // A ringing call is cut the verified way (never "कट गई" while it still rings).
                if (callRinging()) cutRingingCall() else {
                    val outcome = tools.endCall()
                    report(action, outcome)
                    if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "कॉल कट कर दी गई है राजू।", "কল কেটে দেওয়া হয়েছে রাজু।"))
                }
            }
            is AssistantAction.AnswerCall -> {
                trace("offline command: answer call")
                // Verified like "uthao" after the announcement: never "कॉल उठा ली" while it still rings.
                answerRingingCall()
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
            is AssistantAction.SetLanguage -> {
                trace("offline command: language ${action.tag}")
                switchLanguage(action, speak)
            }
            AssistantAction.GoToSleep -> {
                trace("offline command: sleep")
                sleepNow(action, speak)
            }
            is AssistantAction.Greeting -> {
                trace("offline command: greeting")
                informational(action, action.reply, speak)
            }
            AssistantAction.OpenSetupCheck -> {
                trace("offline command: setup check")
                val outcome = tools.openSetupCheck()
                report(action, outcome)
                if (speak) say(
                    if (outcome is ToolOutcome.Done) phrase(lang(), "मेरी जांच वाली लिस्ट खोल दी है। जिस लाइन पर लाल निशान है, उसके \"Theek karein\" दबाइए, मैं वहीं ले चलूँगी।", "আমার পরীক্ষার তালিকা খুলে দিয়েছি। যেখানে লাল চিহ্ন আছে, \"Theek karein\" চাপুন।")
                    else (outcome as? ToolOutcome.Failed)?.message ?: phrase(lang(), "लिस्ट नहीं खुल पाई।", "তালিকা খোলা গেল না।"),
                )
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
            is AssistantAction.Sms -> {
                // Same path as when the AI asks: contact choice by voice, then send.
                trace("offline command: sms")
                _state.value = AssistantState.Executing
                sendSms(action, speak)
            }
            is AssistantAction.ScreenTap -> {
                trace("offline command: tap")
                _state.value = AssistantState.Executing
                screenTap(action, speak)
            }
            is AssistantAction.ScreenType -> {
                trace("offline command: type")
                _state.value = AssistantState.Executing
                screenType(action, speak)
            }
            is AssistantAction.ScreenScroll -> {
                trace("offline command: scroll")
                _state.value = AssistantState.Executing
                screenScroll(action, speak)
            }
            is AssistantAction.PnrStatus -> {
                trace("offline command: pnr status")
                _state.value = AssistantState.Executing
                narrated(action, speak) { pnrStatus(action, speak) }
            }
            is AssistantAction.PnrTrack -> {
                trace("offline command: track pnr (${action.digits.length} digits)")
                _state.value = AssistantState.Executing
                trackPnr(action, speak)
            }
            AssistantAction.ReadTicket -> {
                trace("offline command: read ticket")
                _state.value = AssistantState.Executing
                narrated(action, speak) { readTicket(AssistantAction.ReadTicket, speak) }
            }
            is AssistantAction.PersonBrief -> {
                trace("offline command: person brief")
                // Nothing saved about this name (a place, a film …): a normal question for the AI.
                if (!personBrief(action, speak)) return false
            }
            AssistantAction.HeadsUp -> {
                trace("offline command: heads-up")
                headsUpNow(action, speak)
            }
            is AssistantAction.PaymentCheck -> {
                trace("offline command: payment check")
                _state.value = AssistantState.Executing
                paymentCheck(action, speak)
            }
            is AssistantAction.LookCamera -> {
                _state.value = AssistantState.Executing
                narrated(action, speak) { camera(action, action.front, question = action.question, speak = speak) }
            }
            is AssistantAction.LiveCamera -> {
                trace("offline command: live camera ${if (action.on) "on" else "off"}")
                liveCameraFront = if (action.on) action.front else null
                liveCameraUntil = if (action.on) clock() + com.raju.aiassistant.domain.vision.LIVE_CAMERA_MS else 0L
                informational(
                    action,
                    if (action.on) phrase(lang(), "Live camera चालू है। अगले 2 मिनट जो भी पूछेंगे, ${if (action.front) "आगे वाले" else "पीछे वाले"} कैमरे से फ़ोटो लेकर बताऊँगी। बंद करने के लिए \"camera band karo\" बोलिए।", "Live camera চালু। পরের ২ মিনিট যা জিজ্ঞেস করবেন, ছবি তুলে বলব। বন্ধ করতে \"camera band karo\" বলুন।")
                    else phrase(lang(), "Live camera बंद कर दिया।", "Live camera বন্ধ করেছি।"),
                    speak,
                )
            }
            is AssistantAction.TatkalReminder -> {
                trace("offline command: tatkal reminder")
                tatkalReminder(action, speak)
            }
            AssistantAction.ReminderNeedsTime -> {
                trace("offline command: reminder without a time")
                informational(action, phrase(lang(), "कब याद दिलाऊँ? समय के साथ बोलिए, जैसे \"कल सुबह 9 बजे याद दिलाना\" या \"2 घंटे बाद याद दिलाना\"।", "কখন মনে করাব? সময় বলুন, যেমন \"কাল সকাল ৯টায় মনে করিও\"।"), speak)
            }
            is AssistantAction.AppSearch -> {
                trace("offline command: app search")
                val outcome = tools.openWebPage(appSearchUrl(action.app, action.query))
                report(action, outcome)
                if (speak) say(
                    if (outcome is ToolOutcome.Done) phrase(lang(), "${action.query} खोल दिया है।", "${action.query} খুলে দিয়েছি।")
                    else (outcome as? ToolOutcome.Failed)?.message ?: phrase(lang(), "नहीं खुल पाया।", "খোলা গেল না।"),
                )
            }
            is AssistantAction.WhatsAppAskText -> {
                waAskContact = action.contact
                waAskUntil = clock() + WA_ASK_MS
                informational(action, phrase(lang(), "${action.contact} को WhatsApp पर क्या लिखूँ?", "${action.contact}-কে WhatsApp-এ কী লিখব?"), speak)
            }
            AssistantAction.TatkalWhen -> {
                trace("offline command: tatkal opening")
                informational(action, com.raju.aiassistant.domain.tatkal.spokenTatkalWindows(Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDateTime()), speak)
            }
            is AssistantAction.MoneyQuestion -> {
                trace("offline command: money question")
                _state.value = AssistantState.Executing
                moneyQuestion(action, speak)
            }
            AssistantAction.MoneyAlertOn -> {
                trace("offline command: money alert on")
                runCatching { setAnnounceMoneyIn(true) }
                informational(
                    action,
                    phrase(lang(), "ठीक है, अब जब भी पैसा आएगा, मैं बोलकर बता दूँगी।", "ঠিক আছে, এখন থেকে টাকা এলেই বলে দেব।") +
                        " " + phrase(lang(), "(इसके लिए Seema को Notification access चाहिए।)", "(এর জন্য Seema-র Notification access লাগবে।)"),
                    speak,
                )
            }
            is AssistantAction.InboxCheck -> {
                trace("offline command: inbox check")
                _state.value = AssistantState.Executing
                inboxCheck(action, speak)
            }
            is AssistantAction.BookingNoteStart -> {
                trace("offline command: booking note")
                _state.value = AssistantState.Executing
                startBooking(action, speak)
            }
            is AssistantAction.PassengerAges -> {
                trace("offline command: passenger ages")
                passengerAges(action, speak)
            }
            is AssistantAction.TrainsBetween -> {
                trace("offline command: trains between stations")
                _state.value = AssistantState.Executing
                narrated(action, speak) { trainsBetween(action, speak) }
            }
            is AssistantAction.TrainLive -> {
                trace("offline command: train live status")
                _state.value = AssistantState.Executing
                narrated(action, speak) { trainLive(action, speak) }
            }
            is AssistantAction.WhatsAppPeek -> {
                trace("offline command: whatsapp check")
                _state.value = AssistantState.Executing
                narrated(action, speak) { whatsAppPeek(action, speak) }
            }
            is AssistantAction.WhatsAppPlayVoice -> {
                trace("offline command: whatsapp voice")
                _state.value = AssistantState.Executing
                whatsAppPlayVoice(action, speak)
            }
            is AssistantAction.WhatsAppFile -> {
                trace("offline command: whatsapp file")
                _state.value = AssistantState.Executing
                whatsAppFile(action, speak)
            }
            is AssistantAction.SendPaymentQr -> {
                trace("offline command: payment qr")
                _state.value = AssistantState.Executing
                sendPaymentQr(action, speak)
            }
            is AssistantAction.SendUpiId -> {
                trace("offline command: upi id")
                _state.value = AssistantState.Executing
                sendUpiId(action, speak)
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
            is AssistantAction.LedgerRecord, is AssistantAction.LedgerBalance, AssistantAction.LedgerUndo,
            AssistantAction.LedgerPending, is AssistantAction.LedgerSettle,
            is AssistantAction.ReminderAdd, AssistantAction.ReminderList, is AssistantAction.ReminderCancel,
            AssistantAction.TatkalList, is AssistantAction.TatkalCancel, AssistantAction.TatkalCancelAll -> ledgerOrTatkal(action, speak)
            is AssistantAction.NotificationReply -> {
                trace("offline command: notification reply")
                notificationReply(action, speak)
            }
            is AssistantAction.CalendarRead -> {
                trace("offline command: calendar")
                calendarRead(action, speak)
            }
            AssistantAction.DayBriefing -> {
                // The same briefing as the morning notification, asked of the AI with the day's facts.
                trace("offline command: day briefing")
                _state.value = AssistantState.Thinking
                submit(DAY_BRIEFING_REQUEST, fromVoice = true)
            }
            AssistantAction.WhatsAppDigest -> {
                trace("offline command: whatsapp digest")
                _state.value = AssistantState.Thinking
                whatsAppDigest(action, speak)
            }
            is AssistantAction.SpecialDayAdd -> { trace("offline command: special day"); specialDay(action, speak) }
            is AssistantAction.DailyCareAdd -> { trace("offline command: daily care"); dailyCare(action, speak) }
            is AssistantAction.ScheduleMessage -> { trace("offline command: scheduled message"); scheduleMessage(action, speak) }
            AssistantAction.DuesPdf -> { trace("offline command: dues pdf"); duesPdf(action, speak) }
            AssistantAction.CallRecordingGist -> { trace("offline command: call recording"); _state.value = AssistantState.Thinking; callRecordingGist(action, speak) }
            AssistantAction.BillScanAsk -> { trace("offline command: bill scan"); _state.value = AssistantState.Executing; billScan(action, speak) }
            is AssistantAction.SavePlace -> { trace("offline command: save place"); savePlace(action, speak) }
            is AssistantAction.VoiceIdSetup -> { trace("offline command: voice id"); voiceIdSetup(action, speak) }
            is AssistantAction.TeachWord -> { trace("offline command: teach word"); teachWord(action, speak) }
            is AssistantAction.WatchAmount -> { watches += action.watch; informational(action, com.raju.aiassistant.domain.business.AmountWatches.confirmation(action.watch), speak) }
            is AssistantAction.BillPdf -> { trace("offline command: bill pdf"); billPdf(action, speak) }
            AssistantAction.ActionDiaryAsk -> informational(action, com.raju.aiassistant.domain.business.ActionDiary.summary(doneToday()), speak)
            AssistantAction.SaveCallerNumber -> { trace("offline command: save number"); saveCallerNumber(action, speak) }
            is AssistantAction.BusyMode -> { trace("offline command: busy mode"); busyMode(action, speak) }
            is AssistantAction.DoNotDisturb -> {
                trace("offline command: dnd")
                val outcome = tools.setDoNotDisturb(action.on)
                report(action, outcome)
                if (speak) say(if (outcome is ToolOutcome.Done) phrase(lang(), if (action.on) "DND चालू कर दिया, अब कोई आवाज़ परेशान नहीं करेगी।" else "DND बंद कर दिया।", if (action.on) "DND চালু করেছি।" else "DND বন্ধ করেছি।") else outcome.spokenReason())
            }
            is AssistantAction.PeriodReportAsk -> { trace("offline command: period report"); _state.value = AssistantState.Thinking; periodReport(action, speak) }
            is AssistantAction.DuesRemind -> { trace("offline command: dues reminder"); duesRemind(action, speak) }
            is AssistantAction.Remote -> {
                trace("offline command: remote bridge")
                _state.value = AssistantState.Thinking
                remote(action, speak)
            }
            AssistantAction.NotificationSummary -> {
                trace("offline command: notification summary")
                informational(action, com.raju.aiassistant.domain.notify.notificationDigest(recentMessages(), hindi = lang() != SeemaLanguage.BENGALI), speak)
            }
            is AssistantAction.LookAtScreen -> {
                trace("command: look at screen")
                lookAtScreen(action, speak)
            }
            AssistantAction.LockScreen -> {
                trace("offline command: lock screen")
                val outcome = tools.lockScreen()
                report(action, outcome)
                val asked = outcome is ToolOutcome.Failed && outcome.message == com.raju.aiassistant.domain.tools.LOCK_PERMISSION_ASKED
                if (speak) say(
                    when {
                        outcome is ToolOutcome.Done -> phrase(lang(), "स्क्रीन लॉक कर दी है।", "স্ক্রিন লক করে দিয়েছি।")
                        // First time: Android's own permission screen is now open; one tap and it works from then on.
                        asked -> phrase(lang(), "फ़ोन लॉक करने की एक बार की अनुमति चाहिए — स्क्रीन पर Activate दबाइए, फिर दोबारा बोलिए।", "ফোন লক করার জন্য একবার অনুমতি লাগবে — স্ক্রিনে Activate চাপুন, তারপর আবার বলুন।")
                        else -> phrase(lang(), "स्क्रीन लॉक नहीं हो पाई। Settings → Permissions & Privacy में \"Lock by voice\" चालू कीजिए।", "স্ক্রিন লক হয়নি। Settings → Permissions & Privacy-তে \"Lock by voice\" চালু করুন।")
                    },
                )
            }
            else -> return false
        }
        if (!endTurn) return true
        if (_state.value != AssistantState.Error) {
            _state.value = AssistantState.Success
            delay(doneShownMs())
        }
        finishTurn()
        return true
    }

    /**
     * Phone commands one after another (a chain in one sentence, or a routine). The first one declining
     * hands the sentence to the AI; a later one failing stops the rest and says which.
     */
    private suspend fun runSteps(actions: List<AssistantAction>, speak: Boolean, intro: String? = null): Boolean {
        trace("steps: ${actions.size}")
        intro?.let { if (speak) say(it) }
        for ((i, a) in actions.withIndex()) {
            val done = runLocalCommand(a, speak, endTurn = false)
            if (!done) {
                if (i == 0 && intro == null) return false
                val msg = phrase(lang(), "${i + 1} नंबर का काम (${a.label}) नहीं हो पाया, बाकी रोक दिए।", "${i + 1} নম্বর কাজটা (${a.label}) হল না, বাকিগুলো থামিয়ে দিলাম।")
                report(a, ActionStatus.FAILED, msg)
                if (speak) say(msg)
                break
            }
        }
        if (_state.value != AssistantState.Error) _state.value = AssistantState.Success
        finishTurn()
        return true
    }

    /** A saved routine: steps Seema understands on the phone run in order; others are named, not guessed. */
    private suspend fun runRoutine(r: com.raju.aiassistant.domain.routine.Routine, speak: Boolean) {
        val parsed = r.steps.map { it to LocalCommands.parse(it) }
        val unknown = parsed.filter { it.second == null }.map { it.first }
        if (unknown.isNotEmpty()) note("Routine \"${r.name}\": ye steps samajh nahi aaye, chhod diye: ${unknown.joinToString(" · ")}")
        val actions = parsed.mapNotNull { it.second }
        if (actions.isEmpty()) {
            val msg = phrase(lang(), "\"${r.name}\" routine में ऐसा कोई काम नहीं है जो मैं कर सकूँ।", "\"${r.name}\" রুটিনে করার মতো কোনো কাজ নেই।")
            note(msg)
            if (speak) say(msg)
            finishTurn()
            return
        }
        // A daily reminder ("याद: दवाई") is only her words: no "routine चला रही हूँ" before them.
        val intro = if (r.name.startsWith("याद:")) null else phrase(lang(), "${r.name} routine चला रही हूँ।", "${r.name} রুটিন চালাচ্ছি।")
        runSteps(actions, speak, intro = intro)
    }

    /** A routine's set time came (RoutineAlarms): run it now, unless Seema is switched off. */
    fun runRoutineNow(id: Long) {
        if (paused()) return
        cancelWork()
        job = scope.launch {
            val r = runCatching { routines() }.getOrDefault(emptyList()).firstOrNull { it.id == id && it.enabled } ?: return@launch
            trace("routine at its time (${r.steps.size} steps)")
            runRoutine(r, speak = true)
        }
    }

    /**
     * How long "Done" stays on screen before the turn ends. In a voice conversation the microphone opens
     * again only after this, so it is short there (1.2 s of not listening after every answer was lost words).
     */
    private fun doneShownMs(): Long = if (_voiceSession.value || handsFree) 200L else 1_200L

    // ---------------------------------------------------------------- payment QR and UPI IDs

    /** "Kaun si UPI ID?" was asked: the answer ("Paytm wala") is taken within [UPI_ASK_MS]. */
    private data class PendingUpiShare(val contact: String, val amountRupees: Long?, val until: Long)
    private var pendingUpi: PendingUpiShare? = null

    /** "QR / UPI ID kisko bhejna hai?" was asked: the next sentence is the person. */
    private var pendingPayTo: AssistantAction? = null
    private var pendingPayToUntil = 0L

    /** No name said ("mera QR bhejo"): asks whom, and listens for the name without "Hey Seema". */
    private suspend fun askWhomToPay(action: AssistantAction, speak: Boolean) {
        val q = phrase(lang(), if (action is AssistantAction.SendPaymentQr) "QR किसको भेजना है?" else "UPI ID किसको भेजनी है?", if (action is AssistantAction.SendPaymentQr) "QR কাকে পাঠাব?" else "UPI ID কাকে পাঠাব?")
        report(action, ActionStatus.CANCELLED, q)
        record(ChatRole.ASSISTANT, q)
        pendingPayTo = action
        pendingPayToUntil = clock() + UPI_ASK_MS
        followUpUntil = maxOf(followUpUntil, pendingPayToUntil)
        if (speak) { say(q); _voiceSession.value = true }
    }
    private fun upiQuestionOpen(): Boolean = pendingUpi?.let { clock() < it.until } == true || (pendingPayTo != null && clock() < pendingPayToUntil)

    /** "Mera QR bhej do Rizwan ko": the saved QR photo to that contact on WhatsApp (asked first, then a 2.5 s stop window). */
    private suspend fun sendPaymentQr(action: AssistantAction.SendPaymentQr, speak: Boolean) {
        if (action.contact.isBlank() && tools.hasPaymentQr()) return askWhomToPay(action, speak)
        if (!tools.hasPaymentQr()) {
            val msg = phrase(lang(), "Payment QR अभी सेव नहीं है। Apps & Tools → Payment QR & UPI IDs में QR की फ़ोटो चुनिए।", "Payment QR এখনও সেভ নেই। Apps & Tools → Payment QR & UPI IDs-এ QR-এর ছবি বেছে নিন।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        val target = pickWhatsAppContact(action, action.contact, speak, "Send payment QR?", "Payment QR WhatsApp par jayega", "इन्हें Payment QR भेजूँ", "একে Payment QR পাঠাব") ?: return
        when (val c = actionSafetyWindow(target.name, speak, "WhatsApp पर QR भेज रही हूँ")) {
            Correction.Cancel -> return cancelledLine(action, speak, phrase(lang(), "ठीक है, QR नहीं भेजा।", "ঠিক আছে, QR পাঠাইনি।"))
            is Correction.Rename -> {
                if (speak) say(phrase(lang(), "अच्छा सॉरी, ${c.name} को ढूँढ रही हूँ…", "আচ্ছা সরি, ${c.name}-কে খুঁজছি…"))
                return sendPaymentQr(action.copy(contact = c.name), speak)
            }
            null -> Unit
        }
        val caption = com.raju.aiassistant.domain.payments.PaymentShare.qrCaption(action.amountRupees)
        val outcome = tools.sharePaymentQr(target.number, caption)
        report(action, outcome)
        val sent = if (outcome is ToolOutcome.Done && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) tools.awaitAutoAction(AUTO_ACTION_WAIT_MS) else null
        if (sent == false) report(action, ActionStatus.FAILED, "WhatsApp में Send नहीं दबा पाई — WhatsApp में खुद Send दबाइए।")
        lastWhatsAppChat = target.name
        unsentShare = if (sent == false) UnsentShare(action, target.name, target.number, caption, media = true, until = clock() + UNSENT_RETRY_MS) else null
        if (speak) say(
            when {
                outcome !is ToolOutcome.Done -> phrase(lang(), "माफ़ कीजिए, QR नहीं भेज पाई — ${outcome.spokenReason()}", "দুঃখিত, QR পাঠাতে পারিনি।")
                sent == true -> phrase(lang(), "हो गया, ${target.name} को Payment QR भेज दिया।", "হয়ে গেছে, ${target.name}-কে Payment QR পাঠিয়ে দিয়েছি।")
                sent == false -> phrase(lang(), "QR WhatsApp में तैयार है, पर Send नहीं दब पाया। \"Send दबाओ\" बोलिए, मैं फिर से कोशिश करूँगी।", "QR হোয়াটসঅ্যাপে তৈরি, কিন্তু Send চাপতে পারিনি। \"Send চাপো\" বলুন, আবার চেষ্টা করব।")
                else -> phrase(lang(), "QR WhatsApp में ${target.name} के लिए तैयार है — Send दबा दीजिए।", "QR হোয়াটসঅ্যাপে তৈরি — Send চাপুন।")
            },
        )
    }

    /**
     * "Jalal bhai ko UPI ID bhej do": the named saved ID, the only one, or — with several and none named — "kaun si?"
     * (the answer is heard within [UPI_ASK_MS], no "Hey Seema" needed). The message goes through the usual WhatsApp
     * path (asked first, then sent).
     */
    private suspend fun sendUpiId(action: AssistantAction.SendUpiId, speak: Boolean) {
        val saved = settings.value.upiIds
        if (action.contact.isBlank() && saved.isNotEmpty()) return askWhomToPay(action, speak)
        if (saved.isEmpty()) {
            val msg = phrase(lang(), "कोई UPI ID सेव नहीं है। Apps & Tools → Payment QR & UPI IDs में UPI ID जोड़िए।", "কোনো UPI ID সেভ নেই। Apps & Tools-এ UPI ID যোগ করুন।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        // Named in the request ("Paytm wala UPI ID…"), or the only one saved.
        val entry = action.upiLabel?.let { l -> saved.firstOrNull { it.label.equals(l, true) } ?: com.raju.aiassistant.domain.payments.PaymentShare.pick(saved, l) }
            ?: lastUserText?.let { said -> com.raju.aiassistant.domain.payments.PaymentShare.pick(saved, said.replace(action.contact, " ")) }
            ?: saved.singleOrNull()
        if (entry == null) {
            val q = com.raju.aiassistant.domain.payments.PaymentShare.whichQuestion(action.contact, saved)
            report(action, ActionStatus.CANCELLED, q)
            record(ChatRole.ASSISTANT, q)
            if (speak) say(q)
            // The answer may come in the next 10 s, without "Hey Seema".
            pendingUpi = PendingUpiShare(action.contact, action.amountRupees, clock() + UPI_ASK_MS)
            followUpUntil = maxOf(followUpUntil, clock() + UPI_ASK_MS)
            if (speak) _voiceSession.value = true
            trace("upi: asked which of ${saved.size}")
            return
        }
        val message = com.raju.aiassistant.domain.payments.PaymentShare.upiMessage(entry, settings.value.upiPayeeName, action.amountRupees)
        whatsApp(AssistantAction.WhatsApp(action.contact, message), speak)
    }

    /** Finds [name] in Contacts for a WhatsApp send, saying what she does, and asks before anything is sent. */
    private suspend fun pickWhatsAppContact(action: AssistantAction, name: String, speak: Boolean, title: String, why: String, verbHi: String, verbBn: String): Contact? {
        if (speak) say(phrase(lang(), "Contacts में $name का नंबर निकाल रही हूँ…", "কন্টাক্টে $name-এর নম্বর খুঁজছি…"))
        val found = lookup(name)
        if (found == null) { report(action, ActionStatus.CANCELLED, "Contacts permission was not granted."); return null }
        val usable = found.filter { normalizeForWhatsApp(it.number) != null }
        if (usable.isEmpty()) {
            val msg = phrase(lang(), "Contacts में \"$name\" का कोई WhatsApp नंबर नहीं मिला। सेव किया हुआ नाम बोलिए।", "কন্টাক্টে \"$name\" এর কোনো নম্বর পাইনি।")
            report(action, ActionStatus.FAILED, msg)
            // The next sentence is the saved name (no "Hey Seema", no new request needed).
            if (action is AssistantAction.SendPaymentQr) {
                pendingPayTo = action.copy(contact = "")
                pendingPayToUntil = clock() + UPI_ASK_MS
                followUpUntil = maxOf(followUpUntil, pendingPayToUntil)
                if (speak) _voiceSession.value = true
            }
            if (speak) say(msg)
            return null
        }
        return chooseContact(title, why, usable, name, speak, verbHi = verbHi, verbBn = verbBn)
            ?: run { cancelledLine(action, speak, phrase(lang(), "ठीक है, नहीं भेजा।", "ঠিক আছে, পাঠাইনি।")); null }
    }

    private suspend fun cancelledLine(action: AssistantAction, speak: Boolean, line: String) {
        report(action, ActionStatus.CANCELLED, line)
        if (speak) say(line)
    }

    /** What a failed tool said, short enough to speak. */
    private fun ToolOutcome.spokenReason(): String = when (this) {
        is ToolOutcome.Failed -> message.take(140)
        is ToolOutcome.NeedsPermission -> reason.take(140)
        else -> ""
    }

    // ---------------------------------------------------------------- the 2.5 s stop window

    /** Set while the stop window listens: the next words heard go here instead of a new request. */
    private var correctionWait: CompletableDeferred<String>? = null

    /**
     * "[name] mil gaya, ab call laga rahi hoon" — then 2.5 s in which "ruko", "nahi", "galat", "cancel" or "Siya nahi
     * Riya" stops or corrects the action before it happens. Only in a voice turn; null = go ahead.
     */
    private suspend fun actionSafetyWindow(name: String, speak: Boolean, doing: String): Correction? {
        if (!speak) return null
        say(phrase(lang(), "$name मिल गए, अब $doing…", "$name পেয়েছি, এখন পাঠাচ্ছি…"), allowBargeIn = false)
        if (!voice.isAvailable() || paused()) {
            delay(SAFETY_WINDOW_MS)
            return null
        }
        val wait = CompletableDeferred<String>()
        correctionWait = wait
        _voiceSession.value = true
        listen()
        val heard = withTimeoutOrNull(SAFETY_WINDOW_MS) { wait.await() }
        correctionWait = null
        listenGen++
        voice.stop()
        val c = heard?.let { Correction.parse(it) }
        trace("stop window: ${when (c) { null -> "go ahead"; Correction.Cancel -> "cancelled"; is Correction.Rename -> "corrected name" }}")
        return c
    }

    private fun finishTurn() {
        val s = settings.value
        val wasVoice = voiceTurn
        voiceTurn = false
        lastActivityAt = clock() // the sleep timer counts from the end of her answer
        // Her reply is over: 7 s in which he may answer without her name ("chup raho" opens none).
        if (quietUntilName) quietUntilName = false
        else {
            session.openAfterReply()
            followUpUntil = maxOf(followUpUntil, clock() + FOLLOW_UP_MS)
        }
        if (videoListening && tools.recordingVideo() && !s.assistantPaused) {
            // Recording: the microphone stays open for "band kar do" (whatever the conversation mode).
            listen()
            return
        }
        if (inBackground && canSleep() && _voiceSession.value) {
            // Another app is on screen: listen on only when she just asked something
            // ("किसको भेजना है?"); otherwise wait for "Hey Seema", not for the app's sound.
            val asked = lastSpoken?.trimEnd()?.let { it.endsWith("?") || it.endsWith("？") } == true && clock() - lastSpokeAt < 5_000L
            // A conversation she was woken into by name goes on (until the quiet timer), even over another app.
            if (asked || wakeConversation()) listen() else goToSleep()
        } else if ((_voiceSession.value || upiQuestionOpen()) && (s.continuousConversation || handsFree || upiQuestionOpen()) && !s.assistantPaused) {
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

    /** Lines spoken so far (to know whether an action already said why it failed). */
    private var spokenLines = 0
    private var lastReportMessage: String = ""

    /**
     * Runs an action the AI asked for and, when it failed without a word, says why — "माफ़ कीजिए, Phonepe फ़ोन में
     * नहीं मिला" — instead of failing silently (the reason was only in the log).
     */
    private suspend fun executeAndExplain(action: AssistantAction, provider: AiProvider, model: String, system: String, speak: Boolean) {
        lastReportStatus = null
        val before = spokenLines
        execute(action, provider, model, system, speak)
        if (speak && lastReportStatus == ActionStatus.FAILED && spokenLines == before) {
            val shown = _state.value
            say(failureLine(action, lastReportMessage))
            // The failure stays on screen as a failure (speaking it does not turn it into "done").
            if (shown == AssistantState.Error) _state.value = AssistantState.Error
        }
    }

    /** A plain spoken reason for a failed action. */
    private fun failureLine(action: AssistantAction, message: String): String = when (action) {
        is AssistantAction.OpenApp ->
            if (message.contains("नहीं मिला") || message.contains("not found", true) || message.contains("not installed", true)) phrase(lang(), "माफ़ कीजिए, ${action.name} फ़ोन में नहीं मिला।", "দুঃখিত, ${action.name} ফোনে পাইনি।")
            else message.take(160).ifBlank { phrase(lang(), "माफ़ कीजिए, ${action.name} नहीं खुल पाया।", "দুঃখিত, ${action.name} খোলা গেল না।") }
        else -> {
            val why = message.trim().take(140)
            phrase(lang(), if (why.isEmpty()) "माफ़ कीजिए, यह काम नहीं हो पाया।" else "माफ़ कीजिए, यह नहीं हो पाया — $why", "দুঃখিত, কাজটা হল না।")
        }
    }

    /** After her name ("haan ji, boliye?") or a question of hers, the next listening starts with a soft chime. */
    private var chimeOnNextListen = false

    private suspend fun say(text: String, allowBargeIn: Boolean = true, depth: Int = 0) {
        spokenLines++
        if (text.trimEnd().endsWith("?") || text.trimEnd().endsWith("？")) chimeOnNextListen = true
        // "Speaking" is shown only once audio really plays; while the voice is being prepared she is thinking.
        if (_state.value != AssistantState.Thinking && _state.value != AssistantState.Executing) _state.value = AssistantState.Thinking
        val spoken = speakableText(text)
        val showSpeaking = scope.launch {
            speaker.playing.first { it }
            if (turnActive && turnFirstAudioAt == null) turnFirstAudioAt = clock()
            if (_state.value == AssistantState.Thinking || _state.value == AssistantState.Executing) _state.value = AssistantState.Speaking
        }
        // In a voice conversation the user can talk over Seema, like interrupting a person. What was heard
        // is always checked by speech-to-text first.
        // - Headphones: the phone's echo canceller is on (call-style audio), as before.
        // - Loudspeaker ("Loudspeaker par bhi"): her own voice comes back into the mic loudly (it once
        //   stopped her after a few words on the phone), so a stricter detector is used, a recording that
        //   is only her own words lets her carry on, and after one such false stop the rest of that reply
        //   is spoken without barge-in. Tapping the core always stops her.
        val onHeadphones = headphones()
        val onSpeaker = !onHeadphones && settings.value.bargeInSpeaker && depth == 0
        val canBargeIn = allowBargeIn && settings.value.bargeIn && (handsFree || _voiceSession.value) &&
            transcriber?.isAvailable() == true && ((onHeadphones && conversationMode.active) || onSpeaker)
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
        trace("speak (${spoken.length} chars)${if (canBargeIn) " barge-in on${if (onSpeaker) " (loudspeaker)" else ""}" else ""}")
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
            onInterrupted(spoken, capture, utterance, depth, onSpeaker)
            return
        }
        trace("spoke: ${result.engineLabel}, ${if (result.completed) "complete" else "NOT complete"}${result.error?.let { " — $it" } ?: ""}" +
            // Cut off with no error: something stopped her — the log names what (code names only, no words).
            if (!result.completed && result.error == null) " — stopped by ${speaker.lastStopBy ?: "?"}" else "")
        when {
            // Nothing (or not all) was heard: always tell the user, never fail silently.
            !result.completed && result.error != null -> note("🔇 Voice failed: ${result.error}")
            // A fallback reason (e.g. "Sarvam failed … using Edge Neural") is shown once, not under every reply.
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
    private suspend fun onInterrupted(spoken: String, capture: Boolean, utterance: CompletableDeferred<ByteArray?>, depth: Int, onSpeaker: Boolean = false) {
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
        val raw = com.raju.aiassistant.domain.language.toDevanagari(audio?.let { runCatching { transcriber?.transcribe(it) }.getOrNull() }?.trim().orEmpty())
        val heard = stripEcho(raw, spoken)
        // On the loudspeaker the "interruption" may be only her own words coming back: that is no reason to stop.
        val ownEcho = onSpeaker && !isStopCommand(heard) && isLikelyOwnEcho(heard, spoken)
        if (ownEcho) trace("interruption was her own voice (loudspeaker), continuing")
        when {
            heard.length < 2 || ownEcho -> {
                // False alarm: carry on from the sentence she was saying.
                val rest = chunkForSpeech(spoken).drop(played).joinToString(" ")
                if (rest.isNotBlank() && depth < 2) say(resumePrefix(rest) + rest, depth = depth + 1)
            }
            isStopCommand(heard) -> {
                record(ChatRole.USER, heard)
                say(if (lang() == SeemaLanguage.BENGALI || BENGALI_SCRIPT.containsMatchIn(heard)) "হ্যাঁ, বলুন?" else if (DEVANAGARI.containsMatchIn(heard)) "हाँ, बोलिए?" else "Haan, boliye?", allowBargeIn = false)
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

    /**
     * The ringing call ended (the caller hung up, or the user did): the open "uthao / kaat do" question is
     * dropped at once — her speech stops and she no longer listens for an answer to a call that is gone.
     */
    fun callEnded() {
        if (!callCommandOpen()) return
        trace("call: ended while waiting for an answer, question dropped")
        callCommandUntil = 0
        ringingNumber = null
        ringingName = null
        speaker.stop()
        stop()
    }

    /** True while she listens for "uthao / kaat do" on a ringing call. */
    fun callCommandOpen(): Boolean = clock() < callCommandUntil

    /** "Utha lo" / "kaat do" while a call rings is handled here at once, without the AI. True when it was a call command. */
    private fun handleCallCommand(text: String): Boolean {
        if (clock() >= callCommandUntil) return false
        callCutByVoiceAt = clock()
        // "Bol do abhi busy hoon" / "10 minute mein call karta hoon": cut the call, send those words by SMS.
        callReplyText(text)?.let { reply ->
            callCommandUntil = 0
            trace("call: reject with an SMS reply")
            val ended = tools.endCall().let { telecom ->
                if (telecom is ToolOutcome.Done) telecom else tools.endCallOnScreen().let { screen -> if (screen is ToolOutcome.Done) screen else telecom }
            }
            val screened = ringingNumber?.takeIf { n -> n.count { it.isDigit() } >= 6 }?.let { Contact(ringingName ?: it, it) }
            val screenedName = ringingName
            scope.launch {
                try {
                    report(AssistantAction.EndCall, ended)
                    // Without the call-screening role Android gave no number while it rang: the call
                    // log has the call a moment after it was cut.
                    val caller = screened ?: run {
                        delay(CALL_LOG_SETTLE_MS)
                        tools.recentCaller()?.also { trace("call: number from the call log") }
                    }
                    if (caller == null) {
                        say(phrase(lang(), "कॉल काट दी। कॉल करने वाले का नंबर नहीं मिला (Call log की permission या Seema का call-screening role चाहिए), इसलिए मैसेज नहीं भेज पाई।", "কল কেটে দিয়েছি। নম্বর পাইনি, তাই মেসেজ পাঠাতে পারিনি।"))
                        return@launch
                    }
                    val who = caller.name
                    val sms = tools.sendSms(caller, reply)
                    report(AssistantAction.Sms(screenedName ?: caller.name, caller.number, reply), sms)
                    say(
                        if (sms is ToolOutcome.Done) phrase(lang(), "कॉल काट दी और $who को मैसेज भेज दिया: \"$reply\"", "কল কেটে $who কে মেসেজ পাঠিয়েছি: \"$reply\"")
                        else phrase(lang(), "कॉल काट दी, पर मैसेज नहीं गया — SMS की permission चाहिए।", "কল কেটেছি, কিন্তু মেসেজ যায়নি — SMS permission লাগবে।"),
                    )
                } finally {
                    // Done with this call: nothing left on screen or listening for it.
                    ringingNumber = null
                    ringingName = null
                    if (!pausedForCall) returnToStandby()
                }
            }
            return true
        }
        when (callCommand(text)) {
            CallCommand.ANSWER -> {
                callCommandUntil = 0
                trace("call: answer")
                scope.launch { answerRingingCall() }
            }
            CallCommand.REJECT -> {
                callCommandUntil = 0
                trace("call: reject")
                ringingNumber = null
                ringingName = null
                scope.launch {
                    cutRingingCall()
                    if (!pausedForCall) returnToStandby()
                }
            }
            null -> {
                // Not a yes / no (often the ringtone or the room, heard as some word): one wrong hearing
                // used to end the listening, and the call rang on with nobody listening. She keeps
                // listening for as long as the call rings (the window ends by itself).
                if (clock() < callCommandUntil - 1_500) {
                    trace("call: not an answer, listening again")
                    val g = listenGen
                    scope.launch { delay(150); if (g == listenGen && clock() < callCommandUntil) listen() }
                } else {
                    callCommandUntil = 0
                    trace("call: not an answer, window over")
                    returnToStandby()
                }
            }
        }
        return true
    }

    /**
     * "Uthao": TelecomManager first; when it refuses (no Phone permission) or changes nothing (some Samsung /
     * Xiaomi ROMs ignore acceptRingingCall()), the dialer's Answer button is pressed through the accessibility
     * service. Once the call is up the loudspeaker goes on after [SPEAKER_AFTER_ANSWER_MS], so the user can talk
     * hands-free (again a little later: dialers reset the audio route while the call connects).
     */
    private suspend fun answerRingingCall() {
        if (!callRinging()) {
            // Nothing rings (the call is already up, or it ended): no button is pressed and nothing is claimed.
            val msg = phrase(lang(), "अभी कोई कॉल बज नहीं रही — कॉल पहले से उठी हुई है या कट चुकी है।", "এখন কোনো কল বাজছে না — কলটা আগেই ধরা হয়েছে বা কেটে গেছে।")
            trace("call: answer asked, nothing ringing")
            report(AssistantAction.AnswerCall, ActionStatus.CANCELLED, msg)
            if (!inPhoneCall()) say(msg)
            return
        }
        val outcome = tryCallSteps(answer = true)
        report(AssistantAction.AnswerCall, outcome)
        if (outcome is ToolOutcome.Done) {
            delay(SPEAKER_AFTER_ANSWER_MS)
            runCatching { tools.setSpeakerphone(true) }
            delay(SPEAKER_RETRY_MS)
            runCatching { tools.setSpeakerphone(true) }
        } else {
            // Never silent when it did not work: say why (most often the Phone permission).
            say(callFailureText(outcome, answer = true))
        }
    }

    /** "Kaato": the same three ways, checked the same way. */
    private suspend fun cutRingingCall() {
        if (!callRinging()) {
            // Not ringing: an active call is hung up the usual way.
            val outcome = tools.endCall()
            report(AssistantAction.EndCall, outcome)
            if (outcome !is ToolOutcome.Done) say(callFailureText(outcome, answer = false))
            return
        }
        val outcome = tryCallSteps(answer = false)
        report(AssistantAction.EndCall, outcome)
        if (outcome !is ToolOutcome.Done) say(callFailureText(outcome, answer = false))
    }

    /**
     * Answers (or cuts) the ringing call, and only calls it done when the phone has really stopped ringing:
     * 1. the call notification's own Answer / Decline button (what the user taps; any answer style, screen off too),
     * 2. TelecomManager (some ROMs say "done" and do nothing — the reason "कॉल उठा ली" was shown while it rang on),
     * 3. the call screen's button through Seema's Accessibility (a drag on Samsung's handle as the last try).
     * After each step it waits [CALL_VERIFY_MS] and checks the phone's own call state before trying the next.
     */
    private suspend fun tryCallSteps(answer: Boolean): ToolOutcome {
        val steps: List<Pair<String, () -> ToolOutcome>> = listOf(
            "notification" to { tools.pressCallNotification(answer) },
            "telecom" to { if (answer) tools.answerCall() else tools.endCall() },
            "screen" to { if (answer) tools.answerCallOnScreen() else tools.endCallOnScreen() },
        )
        var last: ToolOutcome = ToolOutcome.Failed(if (answer) "कॉल नहीं उठ पाई।" else "कॉल नहीं कट पाई।")
        var anyDone = false
        for ((name, step) in steps) {
            val r = runCatching { step() }.getOrElse { ToolOutcome.Failed(it.message ?: "error") }
            trace("call: ${if (answer) "answer" else "cut"} via $name ${if (r is ToolOutcome.Done) "pressed" else "not available"}")
            if (r !is ToolOutcome.Done) {
                // Keep the most useful reason (a missing permission says what to allow).
                if (!anyDone && (last !is ToolOutcome.NeedsPermission)) last = r
                continue
            }
            anyDone = true
            // Dialers take a moment to connect: looked at every 250 ms for up to 3 s, so a slow but real answer
            // is not reported as "still ringing" (and the next button is not pressed on an answered call).
            var waited = 0L
            while (callRinging() && waited < CALL_VERIFY_MS) { delay(CALL_POLL_MS); waited += CALL_POLL_MS }
            if (!callRinging()) {
                trace("call: ${if (answer) "answered" else "cut"} (no longer ringing)")
                return r
            }
            trace("call: still ringing after $name")
            last = ToolOutcome.Failed(if (answer) "फ़ोन अब भी बज रहा है — कॉल नहीं उठ पाई, खुद उठाइए।" else "फ़ोन अब भी बज रहा है — कॉल नहीं कट पाई।")
        }
        return last
    }

    /** Why answering / cutting a ringing call did not work, said out loud. */
    private fun callFailureText(outcome: ToolOutcome, answer: Boolean): String = when (outcome) {
        is ToolOutcome.NeedsPermission -> phrase(
            lang(),
            if (answer) "कॉल उठाने की अनुमति नहीं है। Settings → Apps → Seema AI → Permissions → Phone को Allow कीजिए।" else "कॉल काटने की अनुमति नहीं है। Settings → Apps → Seema AI → Permissions → Phone को Allow कीजिए।",
            "কল ধরার/কাটার অনুমতি নেই। Settings → Apps → Seema AI → Permissions → Phone চালু করুন।",
        )
        is ToolOutcome.Failed -> outcome.message
        else -> ""
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
        if (job?.isActive == true || _pending.value != null || replyHandler != null) return false
        // Hands-free is already listening: the answer comes through that same listening (her question
        // interrupted it, so it is started again once the question has finished playing).
        val alreadyInConversation = _voiceSession.value
        replyHandler = onReply
        // If the window ends in silence she goes back to standby: "haan sunao" said a little later must still
        // count without "Hey Seema" (the question is open for [NOTIFICATION_ASK_TTL_MS]).
        followUpUntil = maxOf(followUpUntil, clock() + NOTIFICATION_ASK_TTL_MS)
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
            trace(if (alreadyInConversation) "read reply: listening (in the conversation)" else "read reply: listening")
            if (!voice.listening.value) listen()
        }
        return true
    }

    private fun handleReadReply(text: String): Boolean {
        // Accepted until the window is closed by the timeout (an answer begun in time is not lost).
        if (replyHandler == null) return false
        val answer = readReply(text)
        trace("read reply: ${answer ?: "unclear"}")
        // Something that is neither yes nor no (a cough, the TV, the tail of her own voice) used to close the
        // window, so his "padh ke sunao" a moment later was never heard: inside the window she listens again.
        if (answer == null && clock() < replyUntil) {
            listen()
            return true
        }
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
        if (callToVerify != null) scope.launch {
            reportCallStarted()
            // Her own call goes on the loudspeaker (also with phone apps that ignore "start on speaker").
            // Launched apart from the turn: cancelWork() below stops the turn that placed the call.
            delay(SPEAKER_AFTER_DIAL_MS)
            tools.setSpeakerphone(true)
        }
        callCommandUntil = 0
        if (pausedForCall) return
        pausedForCall = true
        callStartedAt = clock()
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
        // For a few minutes after a call, what Raju tells Seema is checked for promises ("Rahul kal 5000 dega").
        postCallUntil = clock() + POST_CALL_MS
        if (settings.value.assistantPaused) return
        // A real conversation (half a minute or more): she asks once what was agreed, and listens for the answer
        // without "Hey Seema" (it becomes a reminder or a khata entry). Short calls get no question.
        if (clock() - callStartedAt >= POST_CALL_ASK_AFTER_MS && !settings.value.safetyMode && job?.isActive != true) {
            job = scope.launch {
                delay(1_500) // the phone's own call-ended sound first
                if (pausedForCall || paused()) return@launch
                leaveSleep()
                say(phrase(lang(), "कॉल में कुछ तय हुआ? बता दो, याद रख लूँगी।", "ফোনে কিছু ঠিক হল? বলো, মনে রেখে দেব।"), allowBargeIn = false)
                session.openFor(POST_CALL_ANSWER_MS)
                followUpUntil = maxOf(followUpUntil, clock() + POST_CALL_ANSWER_MS)
                _voiceSession.value = true
                listen()
            }
            return
        }
        if (wasHandsFree && handsFree && !inBackground) startHandsFree() else returnToStandby()
    }

    /** When the current SIM call began (for the "what was agreed?" question). */
    private var callStartedAt = 0L

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
            is AssistantAction.WhatsAppPeek -> narrated(action, speak) { whatsAppPeek(action, speak) }
            is AssistantAction.WhatsAppPlayVoice -> whatsAppPlayVoice(action, speak)
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
            AssistantAction.OpenSetupCheck -> report(action, tools.openSetupCheck())
            is AssistantAction.SetLanguage -> switchLanguage(action, speak)
            AssistantAction.GoToSleep -> sleepNow(action, speak)
            is AssistantAction.Greeting -> informational(action, action.reply, speak)
            is AssistantAction.SendPaymentQr -> sendPaymentQr(action, speak)
            is AssistantAction.SendUpiId -> sendUpiId(action, speak)
            is AssistantAction.Battery -> {
                val outcome = tools.batteryStatus()
                report(action, outcome)
                // Informational result: in a voice conversation the user expects to hear it.
                if (speak && outcome is ToolOutcome.Done) say(outcome.message)
            }
            is AssistantAction.FindPhone -> findPhone(action, speak)
            is AssistantAction.AnswerCall -> answerRingingCall()
            is AssistantAction.TakePhoto -> camera(action, action.front, question = null, speak = speak)
            is AssistantAction.StartVideo -> startVideo(action, speak)
            AssistantAction.StopVideo -> stopVideo(action, speak)
            is AssistantAction.LookCamera -> narrated(action, speak) { camera(action, action.front, question = action.question, speak = speak) }
            is AssistantAction.EndCall -> if (callRinging()) cutRingingCall() else {
                val outcome = tools.endCall()
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(phrase(lang(), "कॉल कट कर दी गई है राजू।", "কল কেটে দেওয়া হয়েছে রাজু।"))
            }
            AssistantAction.LockScreen -> report(action, tools.lockScreen())
            is AssistantAction.LookAtScreen -> lookAtScreen(action, speak)
            is AssistantAction.NotificationReply -> notificationReply(action, speak)
            is AssistantAction.CalendarRead -> calendarRead(action, speak)
            AssistantAction.DayBriefing -> submit(DAY_BRIEFING_REQUEST, fromVoice = true)
            AssistantAction.NotificationSummary -> informational(action, com.raju.aiassistant.domain.notify.notificationDigest(recentMessages(), hindi = lang() != SeemaLanguage.BENGALI), speak)
            AssistantAction.WhatsAppDigest -> whatsAppDigest(action, speak)
            is AssistantAction.Remote -> remote(action, speak)
            is AssistantAction.SpecialDayAdd -> specialDay(action, speak)
            is AssistantAction.DailyCareAdd -> dailyCare(action, speak)
            is AssistantAction.ScheduleMessage -> scheduleMessage(action, speak)
            AssistantAction.DuesPdf -> duesPdf(action, speak)
            AssistantAction.CallRecordingGist -> callRecordingGist(action, speak)
            AssistantAction.BillScanAsk -> billScan(action, speak)
            is AssistantAction.SavePlace -> savePlace(action, speak)
            is AssistantAction.VoiceIdSetup -> voiceIdSetup(action, speak)
            is AssistantAction.TeachWord -> teachWord(action, speak)
            is AssistantAction.WatchAmount -> { watches += action.watch; informational(action, com.raju.aiassistant.domain.business.AmountWatches.confirmation(action.watch), speak) }
            is AssistantAction.BillPdf -> billPdf(action, speak)
            AssistantAction.ActionDiaryAsk -> informational(action, com.raju.aiassistant.domain.business.ActionDiary.summary(doneToday()), speak)
            AssistantAction.SaveCallerNumber -> saveCallerNumber(action, speak)
            is AssistantAction.BusyMode -> busyMode(action, speak)
            is AssistantAction.DoNotDisturb -> report(action, tools.setDoNotDisturb(action.on))
            is AssistantAction.PeriodReportAsk -> periodReport(action, speak)
            is AssistantAction.DuesRemind -> duesRemind(action, speak)
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
            is AssistantAction.Remember -> remember(action, speak)
            is AssistantAction.SetTimer -> report(action, tools.setTimer(action.seconds, action.note))
            is AssistantAction.SetAlarm -> report(action, tools.setAlarm(action.hour, action.minute, action.note))
            is AssistantAction.SetTatkalAlarm -> {
                val outcome = tools.setTatkalAlarm(action.hour, action.minute, action.kind)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done && outcome.message.contains(com.raju.aiassistant.domain.tools.TATKAL_INEXACT)) {
                    say(phrase(lang(), "अलार्म ठीक समय पर बजे, इसके लिए फ़ोन में Alarms & reminders की अनुमति दीजिए।", "অ্যালার্ম ঠিক সময়ে বাজার জন্য ফোনে Alarms & reminders অনুমতি দিন।"))
                }
            }
            is AssistantAction.LedgerRecord, is AssistantAction.LedgerBalance, AssistantAction.LedgerUndo,
            AssistantAction.LedgerPending, is AssistantAction.LedgerSettle,
            is AssistantAction.ReminderAdd, AssistantAction.ReminderList, is AssistantAction.ReminderCancel,
            AssistantAction.TatkalList, is AssistantAction.TatkalCancel, AssistantAction.TatkalCancelAll -> ledgerOrTatkal(action, speak)
            is AssistantAction.Flashlight -> report(action, tools.setFlashlight(action.on))
            is AssistantAction.Volume -> report(action, tools.setVolume(action.percent))
            is AssistantAction.Media -> {
                val outcome = tools.mediaControl(action.command)
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Done) say(mediaSpoken(action.command))
            }
            is AssistantAction.TellTime -> informational(action, spokenClock(action), speak)
            is AssistantAction.Sms -> sendSms(action, speak)
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
            is AssistantAction.PnrStatus -> narrated(action, speak) { pnrStatus(action, speak) }
            AssistantAction.ReadTicket -> narrated(action, speak) { readTicket(AssistantAction.ReadTicket, speak) }
            is AssistantAction.PnrTrack -> trackPnr(action, speak)
            is AssistantAction.BookingNoteStart -> startBooking(action, speak)
            is AssistantAction.BookingNoteWith -> startBooking(action, speak)
            is AssistantAction.PaymentCheck -> paymentCheck(action, speak)
            is AssistantAction.InboxCheck -> inboxCheck(action, speak)
            is AssistantAction.MoneyQuestion -> moneyQuestion(action, speak)
            AssistantAction.MoneyAlertOn -> { runCatching { setAnnounceMoneyIn(true) }; report(action, ActionStatus.SUCCESS, "Money alert on.") }
            is AssistantAction.AppSearch -> report(action, tools.openWebPage(appSearchUrl(action.app, action.query)))
            is AssistantAction.WhatsAppAskText -> { waAskContact = action.contact; waAskUntil = clock() + WA_ASK_MS; informational(action, phrase(lang(), "${action.contact} को WhatsApp पर क्या लिखूँ?", "${action.contact}-কে WhatsApp-এ কী লিখব?"), speak) }
            is AssistantAction.TatkalReminder -> tatkalReminder(action, speak)
            AssistantAction.ReminderNeedsTime -> report(action, ActionStatus.SUCCESS, "Asked when to remind.")
            is AssistantAction.LiveCamera -> {
                liveCameraFront = if (action.on) action.front else null
                liveCameraUntil = if (action.on) clock() + com.raju.aiassistant.domain.vision.LIVE_CAMERA_MS else 0L
                report(action, ActionStatus.SUCCESS, if (action.on) "Live camera on for 2 minutes." else "Live camera off.")
            }
            AssistantAction.TatkalWhen -> informational(action, com.raju.aiassistant.domain.tatkal.spokenTatkalWindows(Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDateTime()), speak)
            is AssistantAction.PersonBrief -> { personBrief(action, speak) }
            AssistantAction.HeadsUp -> headsUpNow(action, speak)
            is AssistantAction.PassengerAges -> passengerAges(action, speak)
            is AssistantAction.TrainsBetween -> narrated(action, speak) { trainsBetween(action, speak) }
            is AssistantAction.TrainLive -> narrated(action, speak) { trainLive(action, speak) }
            is AssistantAction.TrainFare -> narrated(action, speak) { trainFare(action, speak) }
            is AssistantAction.MandiPrice -> {
                val place = action.place?.let { " $it" }.orEmpty()
                searchWeb(AssistantAction.SearchWeb("${action.commodity} mandi bhav today$place price per quintal agmarknet"), provider, model, system, speak)
            }
            is AssistantAction.SaveFile -> report(action, tools.shareTextFile(action.name, action.content))
            is AssistantAction.Research -> research(action, provider, model, system, speak)
        }
    }

    // ---- Railway answers: read from the rail service by the app itself, word for word. The AI adds
    // nothing to them, so no train number, time, status or fare is ever a guess.

    /** Everything saved on the phone about one person, said in one go (nothing goes to the AI). */
    private suspend fun personBrief(action: AssistantAction.PersonBrief, speak: Boolean): Boolean {
        val today = today()
        val facts = com.raju.aiassistant.domain.memory.factsAbout(
            action.name,
            runCatching { business.ledgerBalance(action.name) }.getOrNull(),
            runCatching { business.journeysFrom(today.minusDays(7)) }.getOrDefault(emptyList()),
            runCatching { memories.all().map { it.text } }.getOrDefault(emptyList()),
            runCatching { tools.listReminders() }.getOrDefault(emptyList()),
        )
        if (facts.isEmpty()) return false
        informational(action, com.raju.aiassistant.domain.memory.spokenPersonBrief(facts, today, zone()), speak)
        return true
    }

    /** Today's and tomorrow's journeys (with pending money), the biggest dues and today's reminders. */
    private suspend fun headsUpNow(action: AssistantAction, speak: Boolean) {
        informational(action, dailyBriefingText(), speak)
    }

    /**
     * "Aaj ka update do" and the 9 AM / 9 PM check-in: today's money in and out, the dues and today's tasks in three
     * short sentences, plus today's / tomorrow's journeys (R Travels). The SMS are read only if the permission is
     * already there (no question in the middle of a greeting).
     */
    private suspend fun dailyBriefingText(): String {
        val z = zone()
        val now = clock()
        val today = today()
        val start = today.atStartOfDay(z).toInstant().toEpochMilli()
        val cash = when (val inbox = runCatching { tools.smsInbox(start) }.getOrNull()) {
            is com.raju.aiassistant.domain.sms.SmsInbox.Messages -> {
                val notes = runCatching { moneyNotes.since(start) }.getOrDefault(emptyList())
                com.raju.aiassistant.domain.business.DailyBriefing.cashflow(
                    com.raju.aiassistant.domain.sms.moneyEvents(inbox.list.filter { it.atMillis >= start }, notes),
                )
            }
            else -> null
        }
        val balances = runCatching { business.allBalances() }.getOrDefault(emptyList())
        val reminders = runCatching { tools.listReminders() }.getOrDefault(emptyList())
        val brief = com.raju.aiassistant.domain.business.DailyBriefing.build(now, z, cash, balances, reminders, bengali = lang() == SeemaLanguage.BENGALI)
        val journeys = com.raju.aiassistant.domain.memory.headsUp(
            today, runCatching { business.journeysFrom(today) }.getOrDefault(emptyList()), emptyList(), emptyList(), z,
        )
        trace("briefing: cash ${if (cash == null) "unread" else "read"}, ${balances.size} balances, ${reminders.size} reminders")
        // Birthdays and anniversaries he told her about, every year on the day.
        val special = com.raju.aiassistant.domain.memory.SpecialDays.todayLine(runCatching { memories.all().map { it.text } }.getOrDefault(emptyList()), today)
        // Bills due in the next three days (from the last month's SMS).
        val bills = when (val inbox = runCatching { tools.smsInbox(start - 30 * 86_400_000L) }.getOrNull()) {
            is com.raju.aiassistant.domain.sms.SmsInbox.Messages -> com.raju.aiassistant.domain.automation.BillDues.line(inbox.list, today, z)
            else -> null
        }
        val weather = runCatching { withTimeoutOrNull(6_000) { weatherToday() } }.getOrNull()
        return listOfNotNull(special, brief, weather, bills, journeys).joinToString("\n")
    }

    /** The check-in said by itself (phone unlocked after 9 AM / 9 PM): only while Seema is idle and allowed to speak. */
    fun speakDailyBriefing() {
        if (paused() || job?.isActive == true || _state.value == AssistantState.Speaking || settings.value.safetyMode) return
        job = scope.launch {
            val text = dailyBriefingText()
            record(ChatRole.ASSISTANT, text)
            say(text)
            finishTurn()
        }
    }

    /** Looks for the amount in the bank SMS of the last [PAYMENT_DAYS] days, on the phone; no SMS text leaves it. */
    private suspend fun paymentCheck(action: AssistantAction.PaymentCheck, speak: Boolean) {
        val since = clock() - PAYMENT_DAYS * 24L * 3_600_000L
        val inbox = readSmsInbox(action, since) ?: return
        val text = when (inbox) {
            is com.raju.aiassistant.domain.sms.SmsInbox.Messages -> {
                val query = com.raju.aiassistant.domain.sms.PaymentQuery(action.amountPaise)
                trace("payment check: ${inbox.list.size} SMS scanned")
                com.raju.aiassistant.domain.sms.spokenPaymentAnswer(query, com.raju.aiassistant.domain.sms.findPayments(inbox.list, query), zone(), PAYMENT_DAYS)
            }
            is com.raju.aiassistant.domain.sms.SmsInbox.Failed -> return report(action, ActionStatus.FAILED, inbox.message).also { if (speak) say(inbox.message) }
            is com.raju.aiassistant.domain.sms.SmsInbox.NeedsPermission -> return report(action, ActionStatus.FAILED, inbox.reason)
        }
        informational(action, text, speak)
    }

    /** A reminder 5 minutes before the next Tatkal opening (AC 10:00 or Sleeper 11:00), on the phone. */
    private suspend fun tatkalReminder(action: AssistantAction.TatkalReminder, speak: Boolean) {
        val z = zone()
        val now = Instant.ofEpochMilli(clock()).atZone(z).toLocalDateTime()
        val at = com.raju.aiassistant.domain.reminder.tatkalReminderAt(now, action.sleeper)
        val cls = if (action.sleeper) "Sleeper" else "AC"
        val who = action.name?.let { "$it की " } ?: ""
        val text = "${who}$cls Tatkal booking — ${if (action.sleeper) "11:00" else "10:00"} बजे खुलेगा"
        when (val outcome = tools.addReminder(at.atZone(z).toInstant().toEpochMilli(), text)) {
            is ToolOutcome.Done -> {
                val day = if (at.toLocalDate() == now.toLocalDate()) "आज" else "कल"
                val time = "%d:%02d".format(at.hour, at.minute)
                informational(action, "ठीक है, $day सुबह $time बजे ${who}$cls Tatkal booking याद दिलाऊँगी।" + if (!action.sleeper) " Sleeper है तो \"sleeper tatkal\" बोलिए।" else "", speak)
            }
            else -> {
                report(action, outcome)
                if (speak && outcome is ToolOutcome.Failed) say(outcome.message)
            }
        }
    }

    /** The SMS since [since], asking for the permission first when needed; null after a refusal (already reported). */
    private suspend fun readSmsInbox(action: AssistantAction, since: Long): com.raju.aiassistant.domain.sms.SmsInbox? {
        var inbox = tools.smsInbox(since)
        if (inbox is com.raju.aiassistant.domain.sms.SmsInbox.NeedsPermission) {
            val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "SMS permission", inbox.reason, permission = inbox.permission))
            inbox = if (d is Decision.PermissionResult && d.granted) tools.smsInbox(since)
            else { report(action, ActionStatus.CANCELLED, "SMS permission was denied."); return null }
        }
        return inbox
    }

    /** Counts the phone's SMS and the bank's credits / debits for the asked period; nothing leaves the phone. */
    private suspend fun inboxCheck(action: AssistantAction.InboxCheck, speak: Boolean) {
        val z = zone()
        val today = today()
        val since = com.raju.aiassistant.domain.sms.inboxSince(action.period, today, z, action.date)
        val inbox = readSmsInbox(action, since) ?: return
        val text = when (inbox) {
            is com.raju.aiassistant.domain.sms.SmsInbox.Messages -> {
                val q = com.raju.aiassistant.domain.sms.InboxQuery(action.period, action.kind, action.date)
                trace("inbox check: ${inbox.list.size} SMS scanned")
                com.raju.aiassistant.domain.sms.spokenInboxAnswer(q, com.raju.aiassistant.domain.sms.summarizeInbox(inbox.list, q, today, z), z)
            }
            is com.raju.aiassistant.domain.sms.SmsInbox.Failed -> return report(action, ActionStatus.FAILED, inbox.message).also { if (speak) say(inbox.message) }
            is com.raju.aiassistant.domain.sms.SmsInbox.NeedsPermission -> return report(action, ActionStatus.FAILED, inbox.reason)
        }
        informational(action, text, speak)
    }

    /**
     * "कल कितना कितना पैसा आया?", "1 घंटे में किसको भेजा?", "3630 किसका आया?": the bank SMS of that time (plus the
     * UPI notes for names), read and answered on the phone. Only amounts and names are said, never account numbers.
     */
    private suspend fun moneyQuestion(action: AssistantAction.MoneyQuestion, speak: Boolean) {
        lastMoneyAsk = action.ask
        lastMoneyAskAt = clock()
        val z = zone()
        val now = clock()
        val today = today()
        val window = when (val a = action.ask) {
            is com.raju.aiassistant.domain.sms.MoneyAsk.Amounts -> com.raju.aiassistant.domain.sms.windowOf(a.time, now, z)
            is com.raju.aiassistant.domain.sms.MoneyAsk.Who -> a.time?.let { com.raju.aiassistant.domain.sms.windowOf(it, now, z) }
                ?: com.raju.aiassistant.domain.sms.MoneyWindow(now - com.raju.aiassistant.domain.sms.WHO_SEARCH_DAYS * 86_400_000L, now + 1)
        }
        val inbox = readSmsInbox(action, window.fromMillis) ?: return
        val sms = when (inbox) {
            is com.raju.aiassistant.domain.sms.SmsInbox.Messages -> inbox.list
            is com.raju.aiassistant.domain.sms.SmsInbox.Failed -> return report(action, ActionStatus.FAILED, inbox.message).also { if (speak) say(inbox.message) }
            is com.raju.aiassistant.domain.sms.SmsInbox.NeedsPermission -> return report(action, ActionStatus.FAILED, inbox.reason)
        }.filter { it.atMillis >= window.fromMillis && it.atMillis < window.untilMillis }
        val notes = runCatching { moneyNotes.since(window.fromMillis) }.getOrDefault(emptyList()).filter { it.atMillis < window.untilMillis }
        val events = com.raju.aiassistant.domain.sms.moneyEvents(sms, notes)
        // Privacy: only counts in the log.
        trace("money question: ${sms.size} SMS, ${notes.size} notes, ${events.size} payments")
        // Bank messages in a format the phone does not know yet: Gemini reads them (redacted) instead of "nothing found".
        if (events.isEmpty()) {
            val unknown = SmartEscalation.bankLike(sms)
            val question = lastUserText
            if (unknown.isNotEmpty() && question != null) {
                trace("money question: ${unknown.size} unread bank SMS → gemini")
                val prompt = SmartEscalation.moneyPrompt(question, unknown) { ms ->
                    java.time.Instant.ofEpochMilli(ms).atZone(z).toLocalDateTime().toString().replace('T', ' ').take(16)
                }
                val system = buildSystemPrompt(emptyList(), nowDescription(), PromptContext(voiceTurn = speak, language = lang()))
                askGemini(system, listOf(Turn(TurnRole.USER, prompt)))?.let { g ->
                    val text = parseReply(g.text).text
                    if (text.isNotBlank()) return informational(action, text, speak)
                }
            }
        }
        val text = when (val a = action.ask) {
            is com.raju.aiassistant.domain.sms.MoneyAsk.Amounts -> com.raju.aiassistant.domain.sms.spokenMoneyAmounts(a, events, today, lang())
            is com.raju.aiassistant.domain.sms.MoneyAsk.Who -> com.raju.aiassistant.domain.sms.spokenMoneyWho(a, events, today, z, lang())
        }
        informational(action, text, speak)
    }

    // ---- Booking note: a short conversation that ends in a note (see domain/booking/BookingNote.kt).

    private var bookingDraft: com.raju.aiassistant.domain.booking.BookingDraft? = null
    /** The booking note's last question and how many times in a row it was asked (badly heard answers). */
    private var bookingLastPrompt: String? = null
    private var bookingRepeats = 0

    /** "Kya likhun?" asked for a WhatsApp message: the contact, and until when the next sentence is the text. */
    /** Until when what Raju says is checked for promises made on the call that just ended. */
    @Volatile private var postCallUntil = 0L

    /** Each promise becomes one of Seema's reminders; one short spoken confirmation for all of them. */
    private suspend fun saveCommitments(promises: List<com.raju.aiassistant.domain.memory.Commitment>, speak: Boolean) {
        val bn = lang() == SeemaLanguage.BENGALI
        val z = zone()
        val today = today()
        val saved = promises.mapNotNull { c ->
            val text = com.raju.aiassistant.domain.memory.CallMemoryExtractor.reminderText(c, bn)
            val at = c.due.atZone(z).toInstant().toEpochMilli()
            if (tools.addReminder(at, text) !is ToolOutcome.Done) return@mapNotNull null
            val day = when (c.due.toLocalDate()) {
                today -> phrase(lang(), "आज", "আজ")
                today.plusDays(1) -> phrase(lang(), "कल", "কাল")
                else -> "${c.due.dayOfMonth}/${c.due.monthValue}"
            }
            val clock = "${c.due.hour}:${c.due.minute.toString().padStart(2, '0')}"
            phrase(lang(), "$day $clock बजे — $text", "$day $clock-এ — $text")
        }
        trace("call memory: ${promises.size} promise(s), ${saved.size} reminder(s)")
        val line = if (saved.isEmpty()) phrase(lang(), "याद रखने के लिए reminder नहीं बन पाया।", "রিমাইন্ডার তৈরি করতে পারিনি।")
        else phrase(lang(), "ठीक है, याद रख लिया: ", "ঠিক আছে, মনে রাখলাম: ") + saved.joinToString("; ") + phrase(lang(), "। उस वक़्त याद दिला दूँगी।", "। সময়মতো মনে করিয়ে দেব।")
        record(ChatRole.ASSISTANT, line)
        report(AssistantAction.ReminderList, if (saved.isEmpty()) ActionStatus.FAILED else ActionStatus.SUCCESS, line)
        if (speak) say(line)
    }

    /** The last money question, for "सब मिला के बोलो" right after it. */
    private var lastMoneyAsk: com.raju.aiassistant.domain.sms.MoneyAsk? = null
    private var lastMoneyAskAt = 0L

    /** A WhatsApp send whose Send could not be pressed: "Send dabao" presses it again (see [SendRetry]). */
    private class UnsentShare(val action: AssistantAction, val name: String, val number: String, val payload: String, val media: Boolean, val until: Long)
    private var unsentShare: UnsentShare? = null
    /** The chat Seema last sent something to, for "WhatsApp khol ke dikhao bheja ki nahi". */
    private var lastWhatsAppChat: String? = null

    /** For the AI: which payment details Seema holds (labels only, never the IDs themselves). */
    private fun savedPaymentLine(s: UserSettings): String? {
        val qr = runCatching { tools.hasPaymentQr() }.getOrDefault(false)
        val labels = s.upiIds.map { it.label }.filter { it.isNotBlank() }
        if (!qr && labels.isEmpty()) return null
        return listOfNotNull(
            if (qr) "the user's payment QR photo is saved" else null,
            if (labels.isNotEmpty()) "UPI IDs saved: ${labels.joinToString(", ")}" else null,
        ).joinToString("; ")
    }

    private suspend fun retryUnsentShare(u: UnsentShare, speak: Boolean) {
        trace("send retry: ${if (u.media) "qr" else "text"}")
        _state.value = AssistantState.Executing
        if (speak) say(phrase(lang(), "ठीक है, ${u.name} के लिए फिर से भेज रही हूँ…", "আচ্ছা, ${u.name}-কে আবার পাঠাচ্ছি…"), allowBargeIn = false)
        val outcome = if (u.media) tools.sharePaymentQr(u.number, u.payload) else tools.openWhatsApp(u.number, u.payload)
        report(u.action, outcome)
        val sent = if (outcome is ToolOutcome.Done && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) tools.awaitAutoAction(AUTO_ACTION_WAIT_MS) else null
        when (sent) {
            true -> report(u.action, ActionStatus.SUCCESS, "WhatsApp: Send दबाया गया (पक्का भेजा गया)।")
            false -> {
                report(u.action, ActionStatus.FAILED, "WhatsApp में Send फिर भी नहीं दब पाया — WhatsApp में खुद Send दबाइए।")
                unsentShare = UnsentShare(u.action, u.name, u.number, u.payload, u.media, clock() + UNSENT_RETRY_MS)
            }
            null -> Unit
        }
        if (speak) say(
            when {
                outcome !is ToolOutcome.Done -> phrase(lang(), "माफ़ कीजिए, नहीं भेज पाई — ${outcome.spokenReason()}", "দুঃখিত, পাঠাতে পারিনি।")
                sent == true -> phrase(lang(), "हो गया, ${u.name} को भेज दिया।", "হয়ে গেছে, ${u.name}-কে পাঠিয়ে দিয়েছি।")
                sent == false -> phrase(lang(), "फिर भी Send नहीं दब पाया। WhatsApp खुला है, Send का बटन आप एक बार दबा दीजिए।", "এবারও Send চাপতে পারিনি। হোয়াটসঅ্যাপ খোলা, Send বোতামটা একবার চাপুন।")
                else -> phrase(lang(), "WhatsApp में तैयार है — Send दबा दीजिए।", "হোয়াটসঅ্যাপে তৈরি — Send চাপুন।")
            },
        )
    }

    private var waAskContact: String? = null
    private var waAskUntil = 0L

    /** Live camera: the camera in use (front or not) while on, and when it switches itself off. */
    private var liveCameraFront: Boolean? = null
    private var liveCameraUntil = 0L
    private var bookingUntil = 0L

    private suspend fun startBooking(action: AssistantAction, speak: Boolean) {
        bookingDraft = com.raju.aiassistant.domain.booking.BookingDraft()
        bookingLastPrompt = null
        bookingRepeats = 0
        bookingUntil = clock() + BOOKING_TIMEOUT_MS
        val msg = phrase(lang(), "ठीक है, बुकिंग नोट बनाती हूँ। कहाँ से? जैसे पुणे।", "ঠিক আছে, বুকিং নোট বানাচ্ছি। কোথা থেকে?")
        report(action, ActionStatus.SUCCESS, msg)
        if (speak) say(msg)
    }

    /** One answer in the open booking note: the next question, or the finished note (saved as a file). */
    private suspend fun bookingTurn(text: String, speak: Boolean) {
        val draft = bookingDraft ?: return
        val action = AssistantAction.BookingNoteStart
        bookingUntil = clock() + BOOKING_TIMEOUT_MS
        when (val step = com.raju.aiassistant.domain.booking.BookingNote.step(draft, text, today())) {
            is com.raju.aiassistant.domain.booking.BookingStep.Ask -> {
                // The same question a third time: the answers are not being understood, so stop
                // instead of asking forever.
                bookingRepeats = if (step.prompt == bookingLastPrompt) bookingRepeats + 1 else 0
                bookingLastPrompt = step.prompt
                if (bookingRepeats >= 2) {
                    bookingDraft = null
                    bookingLastPrompt = null
                    bookingRepeats = 0
                    val msg = phrase(lang(), "जवाब ठीक से समझ नहीं आया, इसलिए बुकिंग नोट रोक दिया। फिर से शुरू करने के लिए \"booking note banao\" बोलिए।", "উত্তর বুঝতে পারিনি, বুকিং নোট থামিয়ে দিলাম।")
                    report(action, ActionStatus.CANCELLED, msg)
                    if (speak) say(msg)
                    finishTurn()
                    return
                }
                bookingDraft = step.draft
                report(action, ActionStatus.SUCCESS, step.prompt)
                if (speak) say(step.prompt)
            }
            is com.raju.aiassistant.domain.booking.BookingStep.Done -> {
                bookingDraft = null
                report(action, ActionStatus.SUCCESS, step.note)
                val saved = tools.shareTextFile(com.raju.aiassistant.domain.booking.BookingNote.fileName(step.draft), step.note)
                if (saved is ToolOutcome.Failed) report(action, ActionStatus.FAILED, saved.message)
                if (speak) say(step.spoken)
            }
            com.raju.aiassistant.domain.booking.BookingStep.Cancelled -> {
                bookingDraft = null
                val msg = phrase(lang(), "ठीक है, बुकिंग नोट रद्द।", "ঠিক আছে, বুকিং নোট বাতিল।")
                report(action, ActionStatus.CANCELLED, msg)
                if (speak) say(msg)
            }
        }
        finishTurn()
    }

    /** Tatkal helper: the ages are the app's own subtraction; the card on screen has a copy button for each. */
    private suspend fun passengerAges(action: AssistantAction.PassengerAges, speak: Boolean) {
        _passengers.value = action.passengers
        report(action, ActionStatus.SUCCESS, com.raju.aiassistant.domain.tatkal.writtenPassengers(action.passengers))
        if (speak) say(com.raju.aiassistant.domain.tatkal.spokenPassengers(action.passengers, java.time.LocalDate.now().year))
    }

    private suspend fun railFailed(action: AssistantAction, r: com.raju.aiassistant.domain.rail.RailResult<*>, speak: Boolean) {
        val msg = when (r) {
            is com.raju.aiassistant.domain.rail.RailResult.Failed -> r.message
            else -> "रेल की जानकारी के लिए RailKit API key चाहिए — Settings → Brain & AI → Web search में \"RailKit API key\" डालिए।"
        }
        report(action, ActionStatus.FAILED, msg)
        if (speak) say(msg)
    }

    /** The station meant by a spoken name or code; null after telling the user why not. */
    private suspend fun stationFor(action: AssistantAction, said: String, speak: Boolean): com.raju.aiassistant.domain.rail.Station? {
        com.raju.aiassistant.domain.rail.asStationCode(said)?.let { return com.raju.aiassistant.domain.rail.Station(it, it) }
        return when (val r = rail.stations(com.raju.aiassistant.domain.rail.stationQuery(said))) {
            is com.raju.aiassistant.domain.rail.RailResult.Ok -> com.raju.aiassistant.domain.rail.pickStation(said, r.value) ?: run {
                val msg = "\"$said\" नाम का स्टेशन नहीं मिला — स्टेशन का नाम या कोड दोबारा बोलिए।"
                report(action, ActionStatus.FAILED, msg)
                if (speak) say(msg)
                null
            }
            else -> { railFailed(action, r, speak); null }
        }
    }

    private suspend fun trainsBetween(action: AssistantAction.TrainsBetween, speak: Boolean) {
        val from = stationFor(action, action.from, speak) ?: return
        val to = stationFor(action, action.to, speak) ?: return
        when (val r = rail.trainsBetween(from.code, to.code, action.date)) {
            is com.raju.aiassistant.domain.rail.RailResult.Ok -> {
                report(action, ActionStatus.SUCCESS, com.raju.aiassistant.domain.rail.writtenTrainsBetween(from, to, action.date, r.value))
                if (speak) say(com.raju.aiassistant.domain.rail.spokenTrainsBetween(from, to, action.date, r.value))
            }
            else -> railFailed(action, r, speak)
        }
    }

    private suspend fun trainLive(action: AssistantAction.TrainLive, speak: Boolean) {
        when (val r = rail.live(action.train, action.date ?: today())) {
            is com.raju.aiassistant.domain.rail.RailResult.Ok -> informational(action, com.raju.aiassistant.domain.rail.spokenLive(r.value), speak)
            else -> railFailed(action, r, speak)
        }
    }

    private suspend fun trainFare(action: AssistantAction.TrainFare, speak: Boolean) {
        val from = stationFor(action, action.from, speak) ?: return
        val to = stationFor(action, action.to, speak) ?: return
        if (action.seats) {
            when (val r = rail.seats(action.train, from.code, to.code, action.date, action.travelClass, action.quota)) {
                is com.raju.aiassistant.domain.rail.RailResult.Ok -> informational(action, com.raju.aiassistant.domain.rail.spokenSeats(r.value, action.travelClass), speak)
                else -> railFailed(action, r, speak)
            }
        } else {
            when (val r = rail.fare(action.train, from.code, to.code, action.date, action.travelClass, action.quota)) {
                is com.raju.aiassistant.domain.rail.RailResult.Ok -> informational(action, com.raju.aiassistant.domain.rail.spokenFare(r.value, from.name, to.name), speak)
                else -> railFailed(action, r, speak)
            }
        }
    }

    /**
     * "Mera PNR status kya hai": read out by voice (train, status, coach and berth) from the user's PNR
     * service, for the PNR said or the journeys of the next 7 days. Without a service: the official
     * page opens with the PNR copied (no free official API exists).
     */
    /** Udhaar book and Tatkal alarm list / cancel: no AI, the answer is spoken and kept in the action log. */
    private suspend fun ledgerOrTatkal(action: AssistantAction, speak: Boolean) {
        when (action) {
            is AssistantAction.LedgerRecord -> {
                trace("offline command: ledger entry")
                val paid = formatRupees(action.amountPaise)
                val got = action.kind == com.raju.aiassistant.domain.ledger.LedgerKind.GOT
                // Money is never written on a guess: a misheard amount or the same sentence heard twice
                // would stay in the book. The user hears (or sees) the entry and says yes first.
                val question = if (got) phrase(lang(), "${action.name} से $paid मिले — खाते में लिख दूँ?", "${action.name} থেকে $paid পেলাম — খাতায় লিখব?")
                    else phrase(lang(), "${action.name} को $paid दिए — खाते में लिख दूँ?", "${action.name}-কে $paid দিলাম — খাতায় লিখব?")
                if (!confirmScreenAction("Udhaar entry?", question, speak)) {
                    report(action, ActionStatus.CANCELLED, "Udhaar entry not saved.")
                    if (speak) say(phrase(lang(), "ठीक है, नहीं लिखा।", "ঠিক আছে, লিখিনি।"))
                    return
                }
                val balance = runCatching { business.recordLedger(action.name, action.amountPaise, action.kind) }.getOrNull()
                if (balance == null) {
                    report(action, ActionStatus.FAILED, "Udhaar book could not save the entry.")
                    return
                }
                val head = if (got)
                    phrase(lang(), "Entry saved: ${action.name} se $paid mile।", "Entry saved: ${action.name} থেকে $paid পেলাম।")
                else phrase(lang(), "Entry saved: ${action.name} ko $paid diye।", "Entry saved: ${action.name}-কে $paid দিলাম।")
                val text = head + " " + ledgerBalanceText(action.name, balance)
                report(action, ActionStatus.SUCCESS, text)
                if (speak) say(text)
            }
            is AssistantAction.LedgerBalance -> {
                trace("offline command: ledger balance")
                val balance = runCatching { business.ledgerBalance(action.name) }.getOrNull()
                val text = if (balance == null) phrase(lang(), "${action.name} के नाम से कोई हिसाब नहीं मिला।", "${action.name}-এর নামে কোনো হিসাব পাইনি।")
                    else ledgerBalanceText(action.name, balance)
                report(action, if (balance == null) ActionStatus.FAILED else ActionStatus.SUCCESS, text)
                if (speak) say(text)
            }
            AssistantAction.LedgerPending -> {
                trace("offline command: ledger pending")
                val balances = runCatching { business.allBalances() }.getOrDefault(emptyList())
                informational(action, com.raju.aiassistant.domain.ledger.pendingSummary(balances), speak)
            }
            is AssistantAction.LedgerSettle -> {
                trace("offline command: ledger settle")
                val balance = runCatching { business.ledgerBalance(action.name) }.getOrNull()
                val entry = balance?.let { com.raju.aiassistant.domain.ledger.settlingEntry(it) }
                if (balance == null || entry == null) {
                    val text = if (balance == null) phrase(lang(), "${action.name} के नाम से कोई हिसाब नहीं मिला।", "${action.name}-এর নামে কোনো হিসাব নেই।")
                        else phrase(lang(), "${action.name} का हिसाब पहले से बराबर है।", "${action.name}-এর হিসাব আগেই মিটে গেছে।")
                    report(action, if (balance == null) ActionStatus.FAILED else ActionStatus.SUCCESS, text)
                    if (speak) say(text)
                    return
                }
                val amount = formatRupees(entry.amountPaise)
                val what = if (entry.kind == com.raju.aiassistant.domain.ledger.LedgerKind.GOT)
                    phrase(lang(), "${action.name} से $amount मिल गए", "${action.name} থেকে $amount পেয়ে গেছি")
                else phrase(lang(), "${action.name} को $amount दे दिए", "${action.name}-কে $amount দিয়ে দিয়েছি")
                // Money is never written on a guess: the settling entry is said first and needs a yes.
                if (!confirmScreenAction("Settle udhaar?", phrase(lang(), "$what — हिसाब बराबर कर दूँ?", "$what — হিসাব মিটিয়ে দেব?"), speak)) {
                    report(action, ActionStatus.CANCELLED, "Udhaar not settled.")
                    if (speak) say(phrase(lang(), "ठीक है, हिसाब वैसा ही है।", "ঠিক আছে, হিসাব যেমন ছিল।"))
                    return
                }
                val after = runCatching { business.recordLedger(action.name, entry.amountPaise, entry.kind) }.getOrNull()
                val text = if (after == 0L) phrase(lang(), "हो गया, ${action.name} का हिसाब बराबर हो गया।", "হয়ে গেছে, ${action.name}-এর হিসাব মিটে গেছে।")
                    else phrase(lang(), "हिसाब बराबर नहीं हो पाया, खाता खोलकर देख लीजिए।", "হিসাব মেটানো গেল না, খাতা খুলে দেখুন।")
                report(action, if (after == 0L) ActionStatus.SUCCESS else ActionStatus.FAILED, text)
                if (speak) say(text)
            }
            AssistantAction.LedgerUndo -> {
                trace("offline command: ledger undo")
                val last = runCatching { business.lastLedgerEntry() }.getOrNull()
                if (last == null) {
                    val text = phrase(lang(), "खाते में कोई entry नहीं है।", "খাতায় কোনো entry নেই।")
                    report(action, ActionStatus.FAILED, text)
                    if (speak) say(text)
                    return
                }
                val paid = formatRupees(last.amountPaise)
                val what = if (last.kind == com.raju.aiassistant.domain.ledger.LedgerKind.GOT) phrase(lang(), "${last.name} से $paid मिले", "${last.name} থেকে $paid পেলাম")
                    else phrase(lang(), "${last.name} को $paid दिए", "${last.name}-কে $paid দিলাম")
                if (!confirmScreenAction("Remove entry?", phrase(lang(), "आख़िरी entry: $what — हटा दूँ?", "শেষ entry: $what — সরিয়ে দেব?"), speak)) {
                    report(action, ActionStatus.CANCELLED, "Entry kept.")
                    if (speak) say(phrase(lang(), "ठीक है, रहने दिया।", "ঠিক আছে, রেখে দিলাম।"))
                    return
                }
                val removed = runCatching { business.deleteLedgerEntry(last.id) }.getOrDefault(false)
                val text = if (removed) phrase(lang(), "हटा दी: $what।", "সরিয়ে দিয়েছি: $what।") else phrase(lang(), "entry हट नहीं पाई।", "entry সরানো গেল না।")
                report(action, if (removed) ActionStatus.SUCCESS else ActionStatus.FAILED, text)
                if (speak) say(text)
            }
            is AssistantAction.ReminderAdd -> {
                trace("offline command: reminder")
                // Already set (by a command a moment ago, and now again by the AI's chat answer): not a second alarm.
                com.raju.aiassistant.domain.reminder.duplicateReminder(runCatching { tools.listReminders() }.getOrDefault(emptyList()), action.atMillis, action.text)?.let {
                    trace("reminder: already set, not added again")
                    report(action, ActionStatus.SUCCESS, "Already set.")
                    if (speak && !replySpoken) say(phrase(lang(), "ये reminder पहले से लगा है।", "এই reminder আগেই দেওয়া আছে।"))
                    return
                }
                val outcome = tools.addReminder(action.atMillis, action.text)
                val now = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock()), zone())
                val at = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(action.atMillis), zone())
                val whenText = com.raju.aiassistant.domain.reminder.spokenReminderTime(at, now)
                val text = if (outcome is ToolOutcome.Done) {
                    phrase(lang(), "ठीक है, $whenText याद दिला दूँगी: ${action.text}।", "ঠিক আছে, $whenText মনে করিয়ে দেব: ${action.text}।") +
                        if (outcome.message.contains(com.raju.aiassistant.domain.tools.TATKAL_INEXACT)) phrase(lang(), " ठीक समय पर आए, इसके लिए फ़ोन में Alarms & reminders की अनुमति दीजिए।", "") else ""
                } else phrase(lang(), "Reminder नहीं लग पाया।", "Reminder দেওয়া গেল না।")
                report(action, outcome)
                if (speak) say(text)
            }
            AssistantAction.ReminderList -> {
                trace("offline command: reminder list")
                val list = tools.listReminders()
                val now = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock()), zone())
                val text = if (list.isEmpty()) phrase(lang(), "कोई reminder नहीं लगा है।", "কোনো reminder নেই।")
                    else list.take(5).joinToString("; ") { r ->
                        com.raju.aiassistant.domain.reminder.spokenReminderTime(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(r.atMillis), zone()), now) + " — " + r.text
                    }
                report(action, ActionStatus.SUCCESS, text)
                if (speak) say(text)
            }
            is AssistantAction.ReminderCancel -> {
                trace("offline command: reminder cancel")
                val outcome = tools.cancelReminders(action.all)
                val text = if (outcome is ToolOutcome.Done) phrase(lang(), if (action.all) "सारे reminder हटा दिए।" else "आख़िरी reminder हटा दिया।", "Reminder সরিয়ে দিয়েছি।")
                    else phrase(lang(), "कोई reminder लगा नहीं था।", "কোনো reminder ছিল না।")
                report(action, outcome)
                if (speak) say(text)
            }
            AssistantAction.TatkalList -> {
                trace("offline command: tatkal list")
                val list = tools.listTatkalAlarms()
                val text = if (list.isEmpty()) phrase(lang(), "कोई Tatkal alarm लगा नहीं है।", "কোনো Tatkal অ্যালার্ম নেই।")
                    else phrase(lang(), "Tatkal alarm रोज़: ", "রোজ Tatkal অ্যালার্ম: ") + list.joinToString(", ") { "${it.label} ${it.timeText}" }
                report(action, ActionStatus.SUCCESS, text)
                if (speak) say(text)
            }
            is AssistantAction.TatkalCancel -> {
                trace("offline command: tatkal cancel")
                val outcome = tools.cancelTatkalAlarms(action.hour, action.minute, action.kind)
                val time = "%02d:%02d".format(action.hour, action.minute)
                val text = if (outcome is ToolOutcome.Done) phrase(lang(), "$time वाला Tatkal alarm हटा दिया।", "$time-এর Tatkal অ্যালার্ম সরিয়ে দিয়েছি।")
                    else phrase(lang(), "$time पर कोई Tatkal alarm नहीं मिला।", "$time-এ কোনো Tatkal অ্যালার্ম পাইনি।")
                report(action, outcome)
                if (speak) say(text)
            }
            AssistantAction.TatkalCancelAll -> {
                trace("offline command: tatkal cancel all")
                val outcome = tools.cancelAllTatkalAlarms()
                val text = if (outcome is ToolOutcome.Done) phrase(lang(), "सारे Tatkal alarm हटा दिए।", "সব Tatkal অ্যালার্ম সরিয়ে দিয়েছি।")
                    else phrase(lang(), "कोई Tatkal alarm लगा नहीं था।", "কোনো Tatkal অ্যালার্ম ছিল না।")
                report(action, outcome)
                if (speak) say(text)
            }
            else -> Unit
        }
    }

    /** "Rahul ka baaki ₹900 hai." / "Aapko Rahul ko ₹900 dene hain." / "Hisaab barabar hai." */
    private fun ledgerBalanceText(name: String, balance: Long): String {
        val amount = formatRupees(kotlin.math.abs(balance))
        return when {
            balance > 0 -> phrase(lang(), "$name का बाकी $amount है।", "$name-এর বাকি $amount।")
            balance < 0 -> phrase(lang(), "आपको $name को $amount देने हैं।", "আপনাকে $name-কে $amount দিতে হবে।")
            else -> phrase(lang(), "$name का हिसाब बराबर है।", "$name-এর হিসাব সমান।")
        }
    }

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
                    // There is no free official PNR API: say so first, then open the official page with
                    // the PNR ready to paste (not a silent jump into Chrome).
                    tools.copyToClipboard(pnr)
                    if (speak) say(phrase(
                        lang(),
                        "मेरे पास रेलवे की official PNR सेवा जुड़ी नहीं है। मैंने PNR कॉपी करके रेलवे का पेज खोल दिया है — वहाँ PNR डालकर captcha भर दीजिए।",
                        "আমার কাছে রেলের official PNR পরিষেবা নেই। PNR কপি করে রেলের পেজ খুলে দিচ্ছি — সেখানে PNR দিয়ে captcha পূরণ করুন।",
                    ))
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
        // Calling out to him to find the phone: here his name is right.
        val line = com.raju.aiassistant.domain.language.phrase(lang(), "राजू जी, फोन यहाँ है!", "রাজু, ফোন এখানে!")
        if (!beacon.start(hardware, line)) {
            report(action, ActionStatus.FAILED, "फोन की आवाज़ चालू नहीं हो पाई।")
            return
        }
        trace("beacon started")
        report(action, ActionStatus.SUCCESS, "घंटी बज रही है — फोन उठाते, अनलॉक करते, आवाज़ का बटन दबाते या \"मिल गया\" बोलते ही बंद हो जाएगी।")
    }

    /**
     * "SEEMA FIND <word>" came by SMS (the word matched): the phone rings loudly as for "mera phone dhundo" — even when
     * Seema is switched off or in Safety Mode, since the phone may be lost — and [reply] is sent back to [from].
     */
    fun findPhoneRemote(from: String, reply: suspend () -> String) {
        scope.launch {
            trace("find phone by SMS")
            findPhone(AssistantAction.FindPhone, speak = false)
            runCatching { tools.sendSms(Contact("", from), reply()) }
        }
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
        // Said as she does it: "Phonebook mein Riya ko dhoondh rahi hoon…"
        if (speak && !spokenNumber && !action.contact.isNullOrBlank()) {
            say(phrase(lang(), "फ़ोनबुक में ${action.contact} को ढूँढ रही हूँ…", "ফোনবুকে ${action.contact}-কে খুঁজছি…"), allowBargeIn = false)
        }
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
        // A spelling guess ("Ramesh" found "Rakesh") is never dialled without the question first.
        val guess = ContactMatch.isSpellingGuess(action.contact ?: action.number.orEmpty(), candidates)
        val chosen = if (candidates.size == 1 && settings.value.directCalling && !guess) {
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
                confirmSpokenPick = false,
            )
        } ?: return endWith(phrase(lang(), "ठीक है, कॉल नहीं लगाई।", "ঠিক আছে, কল করিনি।"), ActionStatus.CANCELLED)
        // The user has confirmed the person: call straight away when Android allows it,
        // otherwise open the dialer and say plainly that the green button is theirs to press.
        val direct = tools.hasPermission("android.permission.CALL_PHONE")
        // "Riya mil gayi, ab call laga rahi hoon" — and 2.5 s to say "ruko" or "Siya nahi Riya" before it rings.
        if (direct) when (val c = actionSafetyWindow(chosen.name, speak, "कॉल लगा रही हूँ")) {
            Correction.Cancel -> return endWith(phrase(lang(), "ठीक है, कॉल नहीं लगाई।", "ঠিক আছে, কল করিনি।"), ActionStatus.CANCELLED)
            is Correction.Rename -> {
                if (speak) say(phrase(lang(), "अच्छा सॉरी, ${c.name} को ढूँढ रही हूँ…", "আচ্ছা সরি, ${c.name}-কে খুঁজছি…"), allowBargeIn = false)
                return callContact(action.copy(contact = c.name, number = null), speak)
            }
            null -> Unit
        }
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
        // The button is there from the first ring: "नहीं उठाया" → one tap ends it (her mic is silent during a call).
        tools.showHangUpButton(chosen.name)
        when (tools.awaitCallStarted(CALL_START_WAIT_MS)) {
            true -> {
                val ours = callToVerify != null
                reportCallStarted() // no speech: her voice would go into the call
                // Loudspeaker, also for phone apps that ignore the "start on speaker" request
                // (when pauseForCall saw the call first, it does this instead).
                if (ours) {
                    delay(SPEAKER_AFTER_DIAL_MS)
                    tools.setSpeakerphone(true)
                }
            }
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

    private suspend fun sendSms(action: AssistantAction.Sms, speak: Boolean = false) {
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
        // The same Smart Contact Brain as calls and WhatsApp: asked out loud and answered by voice
        // ("pehla", "doosra", the last digits, "haan") — or by a tap on the card.
        val chosen = chooseContact(
            "SMS bhejein?",
            if (candidates.size == 1) "${candidates[0].name} (${candidates[0].number}) ko yeh SMS bheja jayega:\n\n\"${action.text}\""
            else "Kis number par SMS bhejna hai?\n\n\"${action.text}\"",
            candidates, action.contact, speak,
            verbHi = "इन्हें SMS भेजूँ", verbBn = "একে SMS পাঠাব",
        ) ?: run {
            report(action, ActionStatus.CANCELLED, "SMS cancelled.")
            if (speak) say(phrase(lang(), "ठीक है, SMS नहीं भेजा।", "ঠিক আছে, SMS পাঠাইনি।"))
            return
        }
        val smsPermission = "android.permission.SEND_SMS"
        if (!tools.hasPermission(smsPermission)) {
            val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "SMS permission", "SMS bhejne ke liye permission chahiye. SMS aapke SIM se jayega (carrier charges lag sakte hain).", permission = smsPermission))
            if (d !is Decision.PermissionResult || !d.granted) { report(action, ActionStatus.CANCELLED, "SMS permission was not granted, so nothing was sent."); return }
        }
        val outcome = tools.sendSms(chosen, action.text)
        report(action, outcome)
        if (speak) say(
            if (outcome is ToolOutcome.Done) phrase(lang(), "${chosen.name} को SMS भेज दिया।", "${chosen.name}-কে SMS পাঠিয়ে দিয়েছি।")
            else phrase(lang(), "SMS नहीं जा पाया।", "SMS যায়নি।"),
        )
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
    /** How she feels as he speaks this time (back after hours, late night), for this reply only. */
    private var feelingNow: String? = null
    /** This turn carries "how is your headache now?". */
    private var careAsked = false

    /** While a video records: only "band kar do" is acted on; everything else heard is ignored (it is the video's sound). */
    private var videoListening = false
    private var videoAutoStop: Job? = null

    /**
     * "Video recording chalu karo": she says so first (so her voice is not on the video), then records with the
     * phone's camera and microphone. While it records she only listens for "band kar do" — talk in the video never
     * goes to the AI. It stops by itself after [com.raju.aiassistant.domain.camera.CameraVoice.MAX_SECONDS].
     */
    private suspend fun startVideo(action: AssistantAction.StartVideo, speak: Boolean) {
        if (tools.recordingVideo()) return informational(action, phrase(lang(), "रिकॉर्डिंग पहले से चल रही है। रोकनी हो तो \"बंद कर दो\" बोलिए।", "রেকর্ডিং তো চলছে। থামাতে \"বন্ধ করো\" বলো।"), speak)
        if (speak) say(phrase(lang(), "ठीक है, रिकॉर्डिंग शुरू कर रही हूँ। रोकनी हो तो बोलिए \"बंद कर दो\"।", "ঠিক আছে, রেকর্ডিং শুরু করছি। থামাতে বলো \"বন্ধ করো\"।"), allowBargeIn = false)
        var r = tools.startVideo(action.front)
        if (r is com.raju.aiassistant.domain.camera.VideoStart.NeedsPermission) {
            val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "Camera / microphone permission", r.reason, permission = r.permission))
            r = if (d is Decision.PermissionResult && d.granted) tools.startVideo(action.front)
            else return report(action, ActionStatus.CANCELLED, "Permission was denied.")
        }
        when (r) {
            com.raju.aiassistant.domain.camera.VideoStart.Started -> {
                trace("video: recording")
                report(action, ActionStatus.SUCCESS, phrase(lang(), "वीडियो रिकॉर्ड हो रहा है… \"बंद कर दो\" बोलते ही सेव कर दूँगी।", "ভিডিও রেকর্ড হচ্ছে… \"বন্ধ করো\" বললেই সেভ করে দেব।"))
                videoAutoStop?.cancel()
                videoAutoStop = scope.launch {
                    delay(com.raju.aiassistant.domain.camera.CameraVoice.MAX_SECONDS * 1_000L)
                    if (tools.recordingVideo()) { trace("video: longest length reached"); stopVideo(AssistantAction.StopVideo, speak = true) }
                }
                // The end of this turn opens the microphone for "band kar do" ([finishTurn]).
                if (speak || voiceTurn) {
                    videoListening = true
                    _voiceSession.value = true
                }
            }
            is com.raju.aiassistant.domain.camera.VideoStart.Failed -> {
                report(action, ActionStatus.FAILED, r.message)
                if (speak) say(r.message)
            }
            is com.raju.aiassistant.domain.camera.VideoStart.NeedsPermission -> report(action, ActionStatus.FAILED, r.reason)
        }
    }

    private suspend fun stopVideo(action: AssistantAction, speak: Boolean) {
        videoListening = false
        videoAutoStop?.cancel()
        videoAutoStop = null
        val bn = lang() == SeemaLanguage.BENGALI
        when (val r = tools.stopVideo()) {
            is com.raju.aiassistant.domain.camera.VideoStop.Saved -> {
                trace("video: saved (${r.seconds} s)")
                val len = com.raju.aiassistant.domain.camera.CameraVoice.duration(r.seconds, bn)
                informational(action, phrase(lang(), "रिकॉर्डिंग बंद कर दी। $len का वीडियो गैलरी में Seema फ़ोल्डर में सेव है।", "রেকর্ডিং বন্ধ করেছি। $len-এর ভিডিও গ্যালারিতে Seema ফোল্ডারে সেভ আছে।"), speak)
            }
            com.raju.aiassistant.domain.camera.VideoStop.NotRecording ->
                informational(action, phrase(lang(), "अभी कोई रिकॉर्डिंग नहीं चल रही।", "এখন কোনো রেকর্ডিং চলছে না।"), speak)
            is com.raju.aiassistant.domain.camera.VideoStop.Failed -> {
                report(action, ActionStatus.FAILED, r.message)
                if (speak) say(r.message)
            }
        }
    }

    /** Heard while recording: "band kar do" stops and saves; anything else is the video's own sound — listen on. */
    private fun handleVideoListening(heard: String): Boolean {
        if (!videoListening) return false
        if (!tools.recordingVideo()) { videoListening = false; return false }
        if (com.raju.aiassistant.domain.camera.CameraVoice.isStop(heard)) {
            videoListening = false
            job = scope.launch { stopVideo(AssistantAction.StopVideo, speak = true); finishTurn() }
            return true
        }
        trace("video: speech ignored (${heard.length} chars)")
        listen()
        return true
    }

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

    /** "Reply karo ki …": into the reply field of the message's own notification, after the user says yes. */
    /** The chat the last WhatsApp digest was about, so "unko bol do …" goes there (for [UNSENT_RETRY_MS]). */
    private var digestReplyWho: String? = null
    private var digestReplyUntil = 0L

    /** "WhatsApp ka haal batao": the WhatsApp messages by person, summed up in two sentences, then "reply karna hai?". */
    private suspend fun whatsAppDigest(action: AssistantAction, speak: Boolean) {
        val bn = lang() == SeemaLanguage.BENGALI
        val groups = com.raju.aiassistant.domain.notify.WhatsAppDigest.group(recentMessages())
        trace("whatsapp digest: ${groups.size} chats, ${groups.sumOf { it.count }} messages")
        if (groups.isEmpty()) return informational(action, com.raju.aiassistant.domain.notify.WhatsAppDigest.localSummary(groups, bn), speak)
        val ai = quickAi(com.raju.aiassistant.domain.notify.WhatsAppDigest.aiPrompt(groups, bn))
            ?.let { parseReply(it).text }?.takeIf { it.isNotBlank() && !SmartEscalation.isUnsure(it) }
        val top = groups.first().who
        val text = ai?.let { it + " " + com.raju.aiassistant.domain.notify.WhatsAppDigest.replyQuestion(top, bn) }
            ?: com.raju.aiassistant.domain.notify.WhatsAppDigest.localSummary(groups, bn)
        digestReplyWho = top
        digestReplyUntil = clock() + UNSENT_RETRY_MS
        followUpUntil = maxOf(followUpUntil, digestReplyUntil)
        informational(action, text, speak)
    }

    /**
     * The PC / VPS bridge. An instruction is read back and sent only after his yes; an app the companion would
     * refuse is answered here without sending anything. What the companion answered is spoken.
     */
    private suspend fun remote(action: AssistantAction.Remote, speak: Boolean) {
        val bn = lang() == SeemaLanguage.BENGALI
        val c = action.command
        if (c is com.raju.aiassistant.domain.remote.RemoteCommand.OpenApp) {
            com.raju.aiassistant.domain.remote.RemoteBridge.unknownApp(c, bn)?.let { return cancelledLine(action, speak, it) }
        }
        if (c is com.raju.aiassistant.domain.remote.RemoteCommand.Instruction) {
            val ok = confirmScreenAction("PC / VPS", com.raju.aiassistant.domain.remote.RemoteBridge.confirmQuestion(c, bn), speak)
            if (!ok) return cancelledLine(action, speak, phrase(lang(), "ठीक है, नहीं भेजी।", "ঠিক আছে, পাঠাইনি।"))
        }
        val (name, params) = com.raju.aiassistant.domain.remote.RemoteBridge.request(c)
        val result = withTimeoutOrNull(REMOTE_TIMEOUT_MS) { runCatching { remoteBridge(name, params) }.getOrNull() }
            ?: com.raju.aiassistant.domain.remote.RemoteResult.Unreachable
        trace("remote bridge: $name -> ${result::class.simpleName}")
        val line = com.raju.aiassistant.domain.remote.RemoteBridge.spoken(c, result, bn)
        if (result is com.raju.aiassistant.domain.remote.RemoteResult.Done) informational(action, line, speak)
        else {
            report(action, ActionStatus.FAILED, line)
            if (speak) say(line)
        }
    }

    /** "Kal 8 baje Amit ko message bhejna ki …": one reminder; when it comes, the WhatsApp goes through the confirm. */
    private suspend fun scheduleMessage(action: AssistantAction.ScheduleMessage, speak: Boolean) {
        val m = action.message
        val at = m.at.atZone(zone()).toInstant().toEpochMilli()
        val outcome = tools.addReminder(at, com.raju.aiassistant.domain.business.ScheduledMessages.reminderText(m))
        if (outcome !is ToolOutcome.Done) return report(action, outcome).also { if (speak) say(outcome.spokenReason()) }
        informational(action, com.raju.aiassistant.domain.business.ScheduledMessages.confirmation(m), speak)
    }

    /** A scheduled message's time came (from the reminder): the usual WhatsApp path, confirm included. */
    fun sendScheduledMessage(who: String, text: String) {
        if (paused() || settings.value.safetyMode) return
        cancelWork()
        job = scope.launch {
            leaveSleep()
            whatsApp(AssistantAction.WhatsApp(who, text), speak = true)
            finishTurn()
        }
    }

    /** Amounts he is waiting for ("3750 ka refund aaye to batana"), kept while the app runs. */
    private val watches = mutableListOf<com.raju.aiassistant.domain.business.AmountWatch>()

    /**
     * Money came in ([paise], from [payer] when the bank / app names them): a watched amount is announced first; then, if
     * the payer owes him in the khata, she asks once whether to reduce it — and records it only on his yes.
     */
    fun onMoneyIn(paise: Long, payer: String?) {
        if (paused() || settings.value.safetyMode || pausedForCall) return
        scope.launch {
            com.raju.aiassistant.domain.business.AmountWatches.match(watches, paise)?.let { w ->
                watches.remove(w)
                trace("money watch: arrived")
                if (job?.isActive != true) say(com.raju.aiassistant.domain.business.AmountWatches.arrived(w))
            }
            val client = com.raju.aiassistant.domain.business.MoneyMatch.client(payer, runCatching { business.allBalances() }.getOrDefault(emptyList())) ?: return@launch
            if (job?.isActive == true || _pending.value != null) return@launch
            job = scope.launch {
                leaveSleep()
                val ask = com.raju.aiassistant.domain.business.MoneyMatch.question(client, paise)
                val action = AssistantAction.LedgerRecord(client.name, paise, com.raju.aiassistant.domain.ledger.LedgerKind.GOT)
                val yes = askOutLoud(PendingRequest(PendingRequest.Kind.CONFIRM, "Khata", ask), spoken = ask, listenForAnswer = true) { yesNo(it) } is Decision.Approve
                if (yes && runCatching { business.recordLedger(client.name, paise, com.raju.aiassistant.domain.ledger.LedgerKind.GOT) }.getOrNull() != null) {
                    informational(action, com.raju.aiassistant.domain.business.MoneyMatch.done(client, paise), speak = true)
                } else {
                    report(action, ActionStatus.CANCELLED, "Khata not changed.")
                }
                finishTurn()
            }
        }
    }

    /** Headphones / earbuds just connected: a short "बोलिए" and her window opens (no name needed for a moment). */
    fun onHeadphonesConnected() {
        if (paused() || settings.value.safetyMode || pausedForCall || job?.isActive == true || _state.value == AssistantState.Speaking) return
        job = scope.launch {
            leaveSleep()
            say(phrase(lang(), "हेडफ़ोन लग गए — बोलिए, सुन रही हूँ।", "হেডফোন লেগেছে — বলো, শুনছি।"), allowBargeIn = false)
            session.openFor(TAP_GRANT_MS)
            _voiceSession.value = true
            listen()
        }
    }

    /** "Meri awaaz pehchano": model once (28 MB), then 6 s of his voice → his voice print. "Band karo": deleted. */
    private suspend fun voiceIdSetup(action: AssistantAction.VoiceIdSetup, speak: Boolean) {
        if (!action.enroll) {
            voiceId.disable()
            return informational(action, phrase(lang(), "ठीक है, आवाज़ पहचानना बंद। अब कोई भी \"हे सीमा\" बोलेगा तो जागूँगी।", "ঠিক আছে, গলা চেনা বন্ধ।"), speak)
        }
        if (speak) say(phrase(lang(), "ठीक है। पहले एक बार 28 MB का आवाज़ वाला model लाना होगा, एक मिनट…", "ঠিক আছে, একবার মডেল আনতে হবে, এক মিনিট…"), allowBargeIn = false)
        if (!voiceId.ensureModel()) {
            val msg = phrase(lang(), "आवाज़ वाला model नहीं आ पाया — इंटरनेट देखकर फिर बोलिए।", "মডেল আনা গেল না।")
            report(action, ActionStatus.FAILED, msg); if (speak) say(msg); return
        }
        if (speak) say(phrase(lang(), "अब छह सेकंड तक अपनी आम आवाज़ में कुछ भी बोलते रहिए… शुरू कीजिए।", "এখন ছয় সেকেন্ড কিছু বলতে থাকো… শুরু করো।"), allowBargeIn = false)
        listenGen++
        voice.stop()
        releaseMic()
        val ok = voiceId.enroll()
        restoreMic()
        if (ok) informational(action, phrase(lang(), "हो गया, आपकी आवाज़ पहचान ली। अब \"हे सीमा\" सिर्फ़ आपकी आवाज़ पर जागूँगी। बंद करना हो तो बोलिए \"आवाज़ पहचानना बंद करो\"।", "হয়ে গেছে, তোমার গলা চিনে রেখেছি।"), speak)
        else { val msg = phrase(lang(), "आवाज़ साफ़ नहीं मिली — शांत जगह पर फिर कोशिश कीजिए।", "গলা পরিষ্কার পাইনি।"); report(action, ActionStatus.FAILED, msg); if (speak) say(msg) }
    }

    private suspend fun savePlace(action: AssistantAction.SavePlace, speak: Boolean) {
        val outcome = tools.savePlaceHere(action.name)
        if (outcome is ToolOutcome.Done) informational(action, com.raju.aiassistant.domain.automation.Places.saved(action.name), speak)
        else { report(action, outcome); if (speak) say(outcome.spokenReason()) }
    }

    /** He arrived at a saved place: the place and today's remaining tasks, said once when she is idle. */
    fun onArrivedAt(name: String) {
        if (paused() || settings.value.safetyMode || pausedForCall) return
        announce(com.raju.aiassistant.domain.automation.Places.arrival(name, runCatching { tools.listReminders() }.getOrDefault(emptyList()), clock(), zone()))
    }

    /** Words he taught ("mitthu" → "Mitu"), applied to everything heard. */
    @Volatile private var corrections: List<Pair<String, String>> = emptyList()

    private suspend fun loadCorrections() {
        corrections = runCatching { memories.all().mapNotNull { com.raju.aiassistant.domain.language.SpeechCorrections.fromFact(it.text) } }.getOrDefault(emptyList())
    }

    private suspend fun teachWord(action: AssistantAction.TeachWord, speak: Boolean) {
        runCatching {
            memories.all().filter { com.raju.aiassistant.domain.language.SpeechCorrections.fromFact(it.text)?.first.equals(action.heard, true) }.forEach { memories.delete(it.id) }
            memories.add(com.raju.aiassistant.domain.language.SpeechCorrections.factText(action.heard, action.meant))
        }.onFailure { return report(action, ActionStatus.FAILED, "Could not save it.") }
        loadCorrections()
        informational(action, com.raju.aiassistant.domain.language.SpeechCorrections.confirmation(action.heard, action.meant), speak)
    }

    /** One photo of a bill → its total and shop (Gemini) → "खाते में लिख दूँ?" → saved as an expense on his yes. */
    private suspend fun billScan(action: AssistantAction, speak: Boolean) {
        var r = tools.takePhoto(front = false, save = false)
        if (r is PhotoResult.NeedsPermission) {
            val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "Camera permission", r.reason, permission = r.permission))
            r = if (d is Decision.PermissionResult && d.granted) tools.takePhoto(front = false, save = false)
            else return report(action, ActionStatus.CANCELLED, "Camera permission was denied.")
        }
        val photo = r as? PhotoResult.Taken ?: run {
            val msg = (r as? PhotoResult.Failed)?.message ?: phrase(lang(), "फ़ोटो नहीं ले पाई।", "ছবি তুলতে পারিনি।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        _state.value = AssistantState.Thinking
        val bill = (describeImage(photo.jpeg, com.raju.aiassistant.domain.business.BillScan.PROMPT) as? AiResult.Success)?.value
            ?.let { com.raju.aiassistant.domain.business.BillScan.parse(it) }
        if (bill == null) {
            val msg = phrase(lang(), "बिल की रकम साफ़ नहीं पढ़ पाई — बिल को रोशनी में सीधा रखकर फिर बोलिए।", "বিলের টাকা পরিষ্কার পড়তে পারিনি।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        val ask = com.raju.aiassistant.domain.business.BillScan.question(bill)
        val yes = askOutLoud(PendingRequest(PendingRequest.Kind.CONFIRM, "Khata", ask), spoken = ask.takeIf { speak }, listenForAnswer = speak && voiceTurn) { yesNo(it) } is Decision.Approve
        if (!yes) return cancelledLine(action, speak, phrase(lang(), "ठीक है, नहीं लिखा।", "ঠিক আছে, লিখিনি।"))
        val saved = runCatching { business.addExpense(bill.amountPaise, bill.payee ?: "bill", bill.category) }.isSuccess
        if (saved) informational(action, phrase(lang(), "लिख दिया — ${formatRupees(bill.amountPaise)} का खर्च खाते में।", "লিখে দিয়েছি।"), speak)
        else report(action, ActionStatus.FAILED, "Could not save the expense.")
    }

    /** The phone's newest call recording → its gist in 2-3 sentences, and each promise as a reminder. */
    private suspend fun callRecordingGist(action: AssistantAction, speak: Boolean) {
        var rec = tools.latestCallRecording()
        if (rec is com.raju.aiassistant.domain.tools.CallRecording.NeedsPermission) {
            val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "Recordings permission", rec.reason, permission = rec.permission))
            rec = if (d is Decision.PermissionResult && d.granted) tools.latestCallRecording()
            else return report(action, ActionStatus.CANCELLED, "Permission was denied.")
        }
        val r = rec
        if (r !is com.raju.aiassistant.domain.tools.CallRecording.Found) {
            val msg = when (r) {
                com.raju.aiassistant.domain.tools.CallRecording.TooBig -> phrase(lang(), "वो रिकॉर्डिंग बहुत लंबी है, उसका सार नहीं निकाल पाऊँगी।", "রেকর্ডিংটা খুব লম্বা।")
                else -> phrase(lang(), "पिछले हफ़्ते की कोई कॉल रिकॉर्डिंग नहीं मिली — फ़ोन के Dialer में call recording चालू हो तभी बनती है।", "গত সপ্তাহের কোনো কল রেকর্ডিং পাইনি।")
            }
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        if (speak) say(phrase(lang(), "सुन रही हूँ, एक मिनट…", "শুনছি, এক মিনিট…"), allowBargeIn = false)
        val bn = lang() == SeemaLanguage.BENGALI
        when (val a = describeAudio(r.bytes, r.mime, com.raju.aiassistant.domain.memory.CallRecordingSummary.prompt(bn))) {
            is AiResult.Failure -> {
                report(action, ActionStatus.FAILED, a.message)
                if (speak) say(phrase(lang(), "रिकॉर्डिंग समझ नहीं पाई: ${a.message.take(100)}", "রেকর্ডিং বুঝতে পারিনি।"))
            }
            is AiResult.Success -> {
                trace("call recording: gist (${a.value.length} chars)")
                informational(action, com.raju.aiassistant.domain.memory.CallRecordingSummary.gist(a.value), speak)
                val now = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDateTime()
                val promises = com.raju.aiassistant.domain.memory.CallRecordingSummary.promises(a.value)
                    .flatMap { com.raju.aiassistant.domain.memory.CallMemoryExtractor.extract(it, now) }
                if (promises.isNotEmpty()) saveCommitments(promises, speak)
            }
        }
    }

    private suspend fun duesPdf(action: AssistantAction, speak: Boolean) {
        val balances = runCatching { business.allBalances() }.getOrDefault(emptyList())
        if (balances.none { it.balancePaise > 0 }) return informational(action, phrase(lang(), "किसी का कोई बकाया नहीं है, सूची खाली है।", "কারও কোনো বাকি নেই।"), speak)
        val page = com.raju.aiassistant.domain.business.OfficePdfs.dues(balances, today())
        sharePage(action, page, speak)
    }

    private var billNumber = 0L

    private suspend fun billPdf(action: AssistantAction.BillPdf, speak: Boolean) {
        billNumber = maxOf(billNumber + 1, clock() / 1000 % 1_000_000)
        sharePage(action, com.raju.aiassistant.domain.business.OfficePdfs.bill(action.bill, today(), billNumber), speak)
    }

    private suspend fun sharePage(action: AssistantAction, page: com.raju.aiassistant.domain.business.PdfPage, speak: Boolean) {
        val outcome = tools.sharePdf(page)
        report(action, outcome)
        if (speak) say(if (outcome is ToolOutcome.Done) com.raju.aiassistant.domain.business.OfficePdfs.spokenDone(page) else outcome.spokenReason())
    }

    private suspend fun saveCallerNumber(action: AssistantAction, speak: Boolean) {
        val caller = runCatching { tools.recentCaller(30 * 60_000L) }.getOrNull()
        if (caller == null) return informational(action, phrase(lang(), "पिछले आधे घंटे में कोई कॉल नहीं मिली।", "গত আধ ঘণ্টায় কোনো কল পাইনি।"), speak)
        val outcome = tools.saveContact(caller.number)
        report(action, outcome)
        if (speak) say(if (outcome is ToolOutcome.Done) phrase(lang(), "नया contact खोल दिया है, नंबर भरा हुआ है — बस नाम लिखकर Save दबाइए।", "নতুন কন্টাক্ট খুলেছি, নম্বর দেওয়া আছে — নাম লিখে Save চাপুন।") else outcome.spokenReason())
    }

    /** What she did today (successful actions, kept while the app runs), for "aaj tumne kya kya kiya". */
    private val doneLog = ArrayDeque<Pair<Long, String>>()

    private fun doneToday(): List<String> {
        val start = today().atStartOfDay(zone()).toInstant().toEpochMilli()
        return doneLog.filter { it.first >= start }.map { it.second }
    }

    /** "Roz 9 baje dawai ki yaad dilana": a daily routine whose one step is her reminder. */
    private suspend fun dailyCare(action: AssistantAction.DailyCareAdd, speak: Boolean) {
        val c = action.care
        val r = com.raju.aiassistant.domain.routine.Routine(
            id = clock(), name = com.raju.aiassistant.domain.automation.DailyCareReminder.name(c),
            steps = listOf(com.raju.aiassistant.domain.automation.DailyCareReminder.step(c)), hour = c.hour, minute = c.minute,
        )
        runCatching { saveRoutine(r) }.onFailure { return report(action, ActionStatus.FAILED, "Could not save the daily reminder.") }
        informational(action, com.raju.aiassistant.domain.automation.DailyCareReminder.confirmation(c), speak)
    }

    /** Busy mode: on = every missed caller gets an SMS (at most once an hour each); off = back to asking. */
    private var busy = false
    private val busySmsAt = mutableMapOf<String, Long>()

    private suspend fun busyMode(action: AssistantAction.BusyMode, speak: Boolean) {
        busy = action.on
        informational(
            action,
            if (action.on) phrase(lang(), "ठीक है, busy mode चालू। जिसका कॉल छूटेगा उसे SMS चला जाएगा: \"${com.raju.aiassistant.domain.automation.CallAutomation.BUSY_SMS}\"", "ঠিক আছে, ব্যস্ত মোড চালু। যার কল মিস হবে তাকে SMS চলে যাবে।")
            else phrase(lang(), "busy mode बंद कर दिया।", "ব্যস্ত মোড বন্ধ করেছি।"),
            speak,
        )
    }

    /**
     * A call rang and nobody answered (not cut by her on his word): in busy mode the caller gets the SMS; otherwise
     * she asks "X का कॉल छूट गया — वापस लगाऊँ?" and calls only on his yes.
     */
    fun onMissedCall() {
        if (paused() || settings.value.safetyMode || (callCutByVoiceAt != null && clock() - callCutByVoiceAt!! < 60_000L)) return
        scope.launch {
            delay(1_500) // the call log is written a moment after the ring ends
            val caller = runCatching { tools.recentCaller(2 * 60_000L) }.getOrNull() ?: return@launch
            if (busy) {
                val last = busySmsAt[caller.number]
                if (last != null && clock() - last < com.raju.aiassistant.domain.automation.CallAutomation.SMS_GAP_MS) return@launch
                busySmsAt[caller.number] = clock()
                val outcome = tools.sendSms(caller, com.raju.aiassistant.domain.automation.CallAutomation.BUSY_SMS)
                trace("busy mode: missed call, SMS ${if (outcome is ToolOutcome.Done) "sent" else "failed"}")
                return@launch
            }
            if (job?.isActive == true || _pending.value != null) return@launch
            job = scope.launch {
                leaveSleep()
                val ask = com.raju.aiassistant.domain.automation.CallAutomation.missedQuestion(caller.name.takeIf { it.isNotBlank() && it != caller.number }, lang() == SeemaLanguage.BENGALI)
                val action = AssistantAction.Call(caller.name.ifBlank { null }, caller.number)
                val yes = askOutLoud(PendingRequest(PendingRequest.Kind.CONFIRM, "Missed call", ask), spoken = ask, listenForAnswer = true) { yesNo(it) } is Decision.Approve
                if (yes) {
                    report(action, tools.dial(caller, direct = settings.value.directCalling))
                } else {
                    report(action, ActionStatus.CANCELLED, "Not called back.")
                }
                finishTurn()
            }
        }
    }

    /** When she cut a ringing call on his word (no "call back?" for that one). */
    private var callCutByVoiceAt: Long? = null

    /** One line said by itself when she is idle (car mode on, …). */
    fun announce(line: String) {
        if (paused() || job?.isActive == true || _state.value == AssistantState.Speaking || pausedForCall) return
        job = scope.launch { say(line); finishTurn() }
    }

    /** Battery low (Android's own warning): said once while she is idle. */
    fun onBatteryLow(percent: Int) {
        if (paused() || job?.isActive == true || _state.value == AssistantState.Speaking || pausedForCall) return
        job = scope.launch {
            say(com.raju.aiassistant.domain.automation.CallAutomation.lowBatteryLine(percent))
            finishTurn()
        }
    }

    /** "Rahul ka birthday 5 March": one memory line (an older one for the same person and day kind is replaced). */
    private suspend fun specialDay(action: AssistantAction.SpecialDayAdd, speak: Boolean) {
        val d = action.day
        runCatching {
            memories.all().filter { m -> com.raju.aiassistant.domain.memory.SpecialDays.fromFact(m.text)?.let { it.who.equals(d.who, true) && it.kind == d.kind } == true }
                .forEach { memories.delete(it.id) }
            memories.add(com.raju.aiassistant.domain.memory.SpecialDays.factText(d))
        }.onFailure { return report(action, ActionStatus.FAILED, "Could not save it.") }
        informational(action, com.raju.aiassistant.domain.memory.SpecialDays.confirmation(d), speak)
    }

    /** "Is mahine ka hisaab": the bank SMS, the khata, the diary and the dues for that period. */
    private suspend fun periodReport(action: AssistantAction.PeriodReportAsk, speak: Boolean) {
        val z = zone()
        val (from, to) = com.raju.aiassistant.domain.business.PeriodReport.range(action.period, today())
        val start = from.atStartOfDay(z).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        val cash = when (val inbox = runCatching { tools.smsInbox(start) }.getOrNull()) {
            is com.raju.aiassistant.domain.sms.SmsInbox.Messages -> {
                val notes = runCatching { moneyNotes.since(start) }.getOrDefault(emptyList()).filter { it.atMillis < end }
                com.raju.aiassistant.domain.business.DailyBriefing.cashflow(
                    com.raju.aiassistant.domain.sms.moneyEvents(inbox.list.filter { it.atMillis in start until end }, notes),
                )
            }
            else -> null
        }
        val expenses = runCatching { business.expensesBetween(start, end) }.getOrDefault(emptyList()).sumOf { it.amountPaise }
        val journeys = runCatching { business.journeysFrom(from) }.getOrDefault(emptyList()).count { !it.date.isAfter(to) }
        val balances = runCatching { business.allBalances() }.getOrDefault(emptyList())
        trace("period report: ${action.period.name.lowercase()}")
        informational(action, com.raju.aiassistant.domain.business.PeriodReport.build(action.period, cash, expenses, journeys, balances, lang() == SeemaLanguage.BENGALI), speak)
    }

    /** "Rahul ko bakaya yaad dilao": what the khata says he owes, in a polite WhatsApp (the usual confirm first). */
    private suspend fun duesRemind(action: AssistantAction.DuesRemind, speak: Boolean) {
        val balances = runCatching { business.allBalances() }.getOrDefault(emptyList())
        val client = balances.firstOrNull { it.name.equals(action.name, true) }
            ?: balances.firstOrNull { it.name.lowercase().startsWith(action.name.lowercase()) || action.name.lowercase().startsWith(it.name.lowercase()) }
        if (client == null || client.balancePaise <= 0) {
            return informational(action, phrase(lang(), "${action.name} का खाते में कोई बकाया नहीं मिला।", "${action.name}-এর খাতায় কোনো বাকি নেই।"), speak)
        }
        whatsApp(AssistantAction.WhatsApp(client.name, com.raju.aiassistant.domain.business.DuesReminder.message(client.name, client.balancePaise)), speak)
    }

    /** One short answer from the quick AI (Groq), else Gemini; null when neither answers in time. */
    private suspend fun quickAi(prompt: String): String? {
        val system = "You are Seema, Raju's warm, caring companion. Answer exactly what is asked, briefly, in soft spoken language."
        val turns = listOf(Turn(TurnRole.USER, prompt))
        fastRouter()?.let { (p, m) ->
            val r = withTimeoutOrNull(QUICK_AI_TIMEOUT_MS) { runCatching { p.chat(m, system, turns) }.getOrNull() }
            if (r is AiResult.Success && r.value.isNotBlank()) return r.value
        }
        return askGemini(system, turns)?.text
    }

    private suspend fun notificationReply(action: AssistantAction.NotificationReply, speak: Boolean) {
        // "Unko bol do …" right after the WhatsApp digest: the chat it was about.
        val who = action.who ?: digestReplyWho?.takeIf { clock() < digestReplyUntil }
        val target = com.raju.aiassistant.domain.notify.pickReplyTarget(who, replyTargets())
        if (target == null) {
            val text = if (action.who == null) phrase(lang(), "जवाब देने लायक कोई नया संदेश अभी नहीं दिख रहा।", "উত্তর দেওয়ার মতো কোনো নতুন বার্তা নেই।")
                else phrase(lang(), "${action.who} का कोई संदेश अभी notification में नहीं है। \"${action.who} को message करो कि …\" बोलिए।", "${action.who}-এর কোনো বার্তা notification-এ নেই।")
            report(action, ActionStatus.FAILED, text)
            if (speak) say(text)
            return
        }
        val question = phrase(lang(), "${target.app} पर ${target.who} को जवाब भेजूँ: \"${action.text}\"?", "${target.app}-এ ${target.who}-কে উত্তর পাঠাব: \"${action.text}\"?")
        if (!confirmScreenAction("Send reply?", question, speak)) {
            report(action, ActionStatus.CANCELLED, "Reply not sent.")
            if (speak) say(phrase(lang(), "ठीक है, नहीं भेजा।", "ঠিক আছে, পাঠাইনি।"))
            return
        }
        // The usual 2.5 s to say "ruko" before it goes.
        when (val c = actionSafetyWindow(target.who, speak, "जवाब भेज रही हूँ")) {
            Correction.Cancel -> return cancelledLine(action, speak, phrase(lang(), "ठीक है, नहीं भेजा।", "ঠিক আছে, পাঠাইনি।"))
            is Correction.Rename -> return notificationReply(action.copy(who = c.name), speak)
            null -> Unit
        }
        digestReplyWho = null
        val sent = sendNotificationReply(target.key, action.text)
        val text = if (sent) phrase(lang(), "${target.who} को जवाब भेज दिया।", "${target.who}-কে উত্তর পাঠিয়ে দিয়েছি।")
            else phrase(lang(), "जवाब नहीं जा पाया, वह notification अब नहीं है।", "উত্তর পাঠানো গেল না।")
        report(action, if (sent) ActionStatus.SUCCESS else ActionStatus.FAILED, text)
        if (speak) say(text)
    }

    /** "Aaj ki meetings batao": the day's calendar entries, read on the phone (never sent anywhere). */
    private suspend fun calendarRead(action: AssistantAction.CalendarRead, speak: Boolean) {
        var r = tools.calendarEvents(action.dayOffset)
        if (r is com.raju.aiassistant.domain.calendar.CalendarLookup.NeedsPermission) {
            val d = ask(PendingRequest(PendingRequest.Kind.PERMISSION, "Calendar permission", r.reason, permission = r.permission))
            r = if (d is Decision.PermissionResult && d.granted) tools.calendarEvents(action.dayOffset)
            else return report(action, ActionStatus.CANCELLED, "Calendar permission was denied.")
        }
        val (status, text) = when (r) {
            is com.raju.aiassistant.domain.calendar.CalendarLookup.Found -> ActionStatus.SUCCESS to com.raju.aiassistant.domain.calendar.spokenEvents(r.events, action.dayOffset)
            is com.raju.aiassistant.domain.calendar.CalendarLookup.Failed -> ActionStatus.FAILED to r.message
            is com.raju.aiassistant.domain.calendar.CalendarLookup.NeedsPermission -> ActionStatus.FAILED to r.reason
        }
        report(action, status, text)
        if (speak) say(text)
    }

    /**
     * "Screen dekh ke batao": a picture of the screen goes to the vision model only after the user says yes
     * (it may show private things), and it is never saved.
     */
    private suspend fun lookAtScreen(action: AssistantAction.LookAtScreen, speak: Boolean) {
        val ask = phrase(lang(), "स्क्रीन की तस्वीर समझने के लिए AI को भेजूँ?", "স্ক্রিনের ছবি বোঝার জন্য AI-কে পাঠাব?")
        if (!confirmScreenAction("Send a picture of the screen?", ask, speak)) {
            report(action, ActionStatus.CANCELLED, "Screen picture not sent.")
            if (speak) say(phrase(lang(), "ठीक है, नहीं भेजी।", "ঠিক আছে, পাঠাইনি।"))
            return
        }
        // Right before the picture: never a bank / UPI / payment app or a password / PIN / OTP screen, and
        // nothing when the screen cannot be checked (the picture would leave the phone).
        screenOrSay(action, speak) ?: return
        when (val shot = tools.captureScreen()) {
            is PhotoResult.Taken -> {
                _state.value = AssistantState.Thinking
                val q = (action.question?.takeIf { it.isNotBlank() } ?: "Is screen par kya hai? Agar koi error ya zaroori baat hai to wahi batao.") +
                    " (" + (if (lang() == SeemaLanguage.BENGALI) "Answer in Bengali" else "Answer in short, natural Hindi") + ")"
                when (val answer = describeImage(shot.jpeg, q)) {
                    is AiResult.Success -> { report(action, ActionStatus.SUCCESS, answer.value); if (speak) say(answer.value) }
                    is AiResult.Failure -> {
                        report(action, ActionStatus.FAILED, answer.message)
                        if (speak) say(phrase(lang(), "स्क्रीन की तस्वीर समझ नहीं पाई: ${answer.message}", "স্ক্রিনের ছবি বুঝতে পারিনি।"))
                    }
                }
            }
            is PhotoResult.Failed -> { report(action, ActionStatus.FAILED, shot.message); if (speak) say(shot.message) }
            is PhotoResult.NeedsPermission -> { report(action, ActionStatus.FAILED, shot.reason); if (speak) say(shot.reason) }
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
        // Two questions at once (a call announcement during a khata question): the older one is answered
        // "no" instead of waiting for ever on an answer that now goes to the new one.
        decision?.complete(Decision.Deny)
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
     * and WhatsApp to settle on the person. One match: "नाम — कॉल लगाऊँ?". Several: she lists
     * them and the user says the name or "पहला / दूसरा / 1 नंबर" (or taps one); for a call that choice is
     * the go-ahead, a message asks once more. Numbers are shown on the card, never read out. Returns null when the user said no or did not answer.
     */
    /** [chooseContactInner], and the user's choice for the name they said is remembered for next time. */
    private suspend fun chooseContact(
        title: String,
        message: String,
        candidates: List<Contact>,
        query: String?,
        speak: Boolean,
        verbHi: String,
        verbBn: String,
        confirmSpokenPick: Boolean = true,
    ): Contact? {
        val chosen = chooseContactInner(title, message, candidates, query, speak, verbHi, verbBn, confirmSpokenPick) ?: return null
        // Only a name that was hard to find (spelled letters, a shortened name) is remembered: "Rahul" that simply
        // lists three Rahuls keeps asking which one.
        if (query != null && query == nearMatchQuery && com.raju.aiassistant.domain.tools.ContactAliases.worthRemembering(query, chosen)) {
            runCatching { contactAliases.put(query, chosen) }
            trace("contacts: learned how this name is said")
        }
        return chosen
    }

    private suspend fun chooseContactInner(
        title: String,
        message: String,
        candidates: List<Contact>,
        query: String?,
        speak: Boolean,
        verbHi: String,
        verbBn: String,
        confirmSpokenPick: Boolean = true,
    ): Contact? {
        val byVoice = speak && voiceTurn
        // The number stays on the card; reading ten digits out loud only slows the call down.
        fun question(c: Contact) = phrase(lang(), "${c.name} — $verbHi?", "${c.name} — $verbBn?")
        if (candidates.size == 1) {
            val c = candidates[0]
            val d = askOutLoud(
                PendingRequest(PendingRequest.Kind.CONFIRM, title, message, options = candidates),
                spoken = if (speak) question(c) else null,
                listenForAnswer = byVoice,
            ) { yesNo(it, c) }
            return (d as? Decision.Approve)?.let { it.option ?: c }
        }
        val sameNames = candidates.map { it.name.trim().lowercase() }.distinct().size < candidates.size
        val names = ContactMatch.spokenChoices(candidates, hindi = lang() != SeemaLanguage.BENGALI)
        val shown = candidates.size.coerceAtMost(4)
        val q = if (sameNames) phrase(
            lang(),
            "${candidates[0].name} के $shown नंबर हैं — स्क्रीन पर दिख रहे हैं। ${ContactMatch.ordinalChoices(shown, hindi = true)}?",
            "${candidates[0].name}-এর $shown টা নম্বর — স্ক্রিনে দেখা যাচ্ছে। ${ContactMatch.ordinalChoices(shown, hindi = false)}?",
        ) else phrase(
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
        // For a call the choice itself ("दूसरा नंबर", a name, or a tap) is the go-ahead. A message is
        // checked once more when the choice was spoken (a misheard name must not get the text).
        if (!confirmSpokenPick || !answeredByVoice) return chosen
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
        // "Neeche wala button", "pehla button": the button at that place (then confirmed like any tap).
        val found = com.raju.aiassistant.domain.screen.ScreenPilot.resolve(action.target, before)?.let { listOf(it) }
            ?: ScreenMatch.candidates(before, action.target)
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
            if (speak && digitsOnly.length < 7) say(phrase(lang(), "Contacts में ${action.contact} का नंबर निकाल रही हूँ…", "কন্টাক্টে ${action.contact}-এর নম্বর খুঁজছি…"), allowBargeIn = false)
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
                // Always a yes right before anything is sent, also with one match: with Seema's Accessibility
                // on, the next step presses Send by itself, so this question is the last chance to stop it.
                val why = when {
                    usable.size > 1 -> "\"${action.contact}\" naam se ${usable.size} number mile — kis par bhejna hai?"
                    autoSend -> "Seema ye message WhatsApp par bhej degi:"
                    else -> "Seedha bhejne ke liye Seema ki Accessibility chalu nahi hai (phone Settings → Accessibility → Seema AI → On; agar band ho to App info → ⋮ → \"Allow restricted settings\" pehle).\n\nYe message WhatsApp mein khulega, Send aap dabayenge:"
                }
                val said = action.text.trim().let { if (it.length > 120) it.take(120) + "…" else it }
                target = chooseContact(
                    "Send on WhatsApp?",
                    "$why\n\n\"${action.text}\"",
                    usable, action.contact, speak,
                    // The message is said back, so a misheard text is caught before it goes.
                    verbHi = "\"$said\" — इन्हें WhatsApp पर भेजूँ", verbBn = "\"$said\" — একে হোয়াটসঅ্যাপে পাঠাব",
                ) ?: run { report(action, ActionStatus.CANCELLED, "WhatsApp draft cancelled."); return }
            }
        }
        if (target == null && !com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
            val ok = confirmScreenAction(
                "Open WhatsApp?",
                phrase(lang(), "WhatsApp में यह मैसेज लिखकर खोल दूँ? चैट आप चुनकर Send दबाइएगा: \"${action.text}\"", "হোয়াটসঅ্যাপে মেসেজটা লিখে খুলে দেব? চ্যাট বেছে Send চাপবেন: \"${action.text}\""),
                speak,
            )
            if (!ok) { report(action, ActionStatus.CANCELLED, "WhatsApp draft cancelled."); return }
        }
        target?.let { t ->
            when (val c = actionSafetyWindow(t.name, speak, "WhatsApp पर भेज रही हूँ")) {
                Correction.Cancel -> return cancelledLine(action, speak, phrase(lang(), "ठीक है, नहीं भेजा।", "ঠিক আছে, পাঠাইনি।"))
                is Correction.Rename -> {
                    if (speak) say(phrase(lang(), "अच्छा सॉरी, ${c.name} को ढूँढ रही हूँ…", "আচ্ছা সরি, ${c.name}-কে খুঁজছি…"), allowBargeIn = false)
                    return whatsApp(action.copy(contact = c.name), speak)
                }
                null -> Unit
            }
        }
        val outcome = tools.openWhatsApp(target?.number, action.text)
        report(action, outcome)
        val autoArmed = outcome is ToolOutcome.Done && target?.name != null && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
        // Verify: did Seema's Accessibility really press Send (and the message leave the box)?
        val sent = if (autoArmed) tools.awaitAutoAction(AUTO_ACTION_WAIT_MS) else null
        if (sent == true) report(action, ActionStatus.SUCCESS, "WhatsApp: Send दबाया गया, मैसेज बॉक्स खाली हुआ (पक्का भेजा गया)।")
        if (sent == false) report(action, ActionStatus.FAILED, "WhatsApp में Send बटन नहीं दबा पाई — WhatsApp में खुद Send दबाइए।")
        target?.let { t ->
            lastWhatsAppChat = t.name
            unsentShare = if (sent == false) UnsentShare(action, t.name, t.number, action.text, media = false, until = clock() + UNSENT_RETRY_MS) else null
        }
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
            FlowOutcome.PEEKED, FlowOutcome.NO_VOICE, FlowOutcome.VOICE_PLAYED -> Triple(ActionStatus.FAILED, "मैसेज नहीं भेजा।", "মেসেজ পাঠাইনি।")
            FlowOutcome.INTERRUPTED -> Triple(ActionStatus.FAILED, "WhatsApp के ऊपर बार-बार कोई और संदेश (permission / update / system) आ गया, इसलिए मैं रुक गई और वापस आ गई। कुछ नहीं भेजा — वो संदेश देखकर फिर कहिए।", "হোয়াটসঅ্যাপের উপর বারবার অন্য বার্তা (permission / update) এসেছে, তাই থেমে ফিরে এসেছি। কিছু পাঠাইনি।")
            // Document outcomes never come from a text message.
            FlowOutcome.FILE_NOT_FOUND, FlowOutcome.SEVERAL_FILES -> Triple(ActionStatus.FAILED, "मैसेज नहीं भेज पाई।", "মেসেজ পাঠাতে পারিনি।")
        }
        val msg = phrase(lang(), hi, bn)
        report(action, status, msg)
        // Sent, stuck or interrupted: back to Seema with the honest status (not left hanging in WhatsApp).
        if (r.outcome == FlowOutcome.SENT || r.outcome == FlowOutcome.STUCK || r.outcome == FlowOutcome.INTERRUPTED) tools.openSeema()
        if (speak) say(msg)
    }

    /**
     * "WhatsApp par Riya ko ticket wali PDF bhejo": Riya's chat → 📎 → Document → the one file whose
     * name matches → Send, after a yes. The chat is found by the contact's saved name (or the name as
     * said, for a group); any doubt about the chat or the file means nothing is sent.
     */
    /** The words of an unreadable or refused WhatsApp step, shared by the chat check and the voice play. */
    private fun whatsAppStopText(outcome: FlowOutcome, chat: String): String? = when (outcome) {
        FlowOutcome.CHAT_NOT_FOUND -> phrase(lang(), "WhatsApp में \"$chat\" की चैट नहीं मिली।", "হোয়াটসঅ্যাপে \"$chat\" এর চ্যাট পাইনি।")
        FlowOutcome.SEVERAL_CHATS -> phrase(lang(), "\"$chat\" नाम की कई चैट हैं। पूरा नाम बोलिए।", "\"$chat\" নামে কয়েকটা চ্যাট আছে। পুরো নাম বলুন।")
        FlowOutcome.UNSAFE_SCREEN -> phrase(lang(), "पेमेंट / PIN वाली स्क्रीन आई, सुरक्षा के लिए मैं रुक गई।", "পেমেন্ট / পিন স্ক্রিন এসেছে, থেমে গেছি।")
        FlowOutcome.WRONG_CHAT -> phrase(lang(), "जो चैट खुली उसका नाम पक्का नहीं हुआ, मैं कुछ नहीं बताऊँगी।", "যে চ্যাট খুলেছে তার নাম নিশ্চিত হয়নি।")
        FlowOutcome.UNAVAILABLE -> phrase(lang(), "WhatsApp या Seema की Accessibility चालू नहीं है।", "হোয়াটসঅ্যাপ বা Seema-র Accessibility চালু নেই।")
        FlowOutcome.INTERRUPTED -> phrase(lang(), "WhatsApp पर बार-बार कोई और संदेश आ गया, मैं वापस आ गई।", "হোয়াটসঅ্যাপে অন্য বার্তা এসেছে, ফিরে এসেছি।")
        FlowOutcome.STUCK -> phrase(lang(), "WhatsApp समय पर वो स्क्रीन नहीं दिखा सका, मैं रुक गई।", "হোয়াটসঅ্যাপ সময়মতো স্ক্রিন দেখাতে পারেনি, থেমে গেছি।")
        else -> null
    }

    private suspend fun whatsAppPeek(action: AssistantAction.WhatsAppPeek, speak: Boolean) {
        if (!com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
            val msg = phrase(lang(), "WhatsApp की चैट देखने के लिए Seema की Accessibility चालू करनी होगी।", "হোয়াটসঅ্যাপ চ্যাট দেখতে Seema-র Accessibility চালু করতে হবে।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        // "WhatsApp khol ke dikhao bheja ki nahi": the chat Seema last sent to, not a contact called "self".
        if (SendRetry.meansNoChat(action.contact)) {
            val last = lastWhatsAppChat
            if (last != null) return whatsAppPeek(action.copy(contact = last), speak)
            val outcome = tools.openApp("WhatsApp")
            val msg = phrase(lang(), "WhatsApp खोल दिया है। किसकी चैट देखनी है, नाम बोलिए।", "হোয়াটসঅ্যাপ খুলে দিয়েছি। কার চ্যাট দেখব, নাম বলুন।")
            report(action, if (outcome is ToolOutcome.Done) ActionStatus.SUCCESS else ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        // Only reads the chat on screen; nothing is opened inside a file, nothing is sent.
        val r = tools.whatsAppPeek(action.contact)
        r.log.forEach { trace("whatsapp check: $it") }
        val chat = r.chat ?: action.contact
        val text = when (r.outcome) {
            FlowOutcome.PEEKED -> r.summary.orEmpty()
            FlowOutcome.NO_VOICE, FlowOutcome.SENT, FlowOutcome.SEVERAL_FILES, FlowOutcome.FILE_NOT_FOUND, FlowOutcome.TYPED_NOT_SENT, FlowOutcome.VOICE_PLAYED ->
                phrase(lang(), "\"$chat\" की चैट नहीं पढ़ पाई।", "\"$chat\" এর চ্যাট পড়তে পারিনি।")
            else -> whatsAppStopText(r.outcome, chat) ?: phrase(lang(), "चैट नहीं पढ़ पाई।", "চ্যাট পড়তে পারিনি।")
        }
        report(action, if (r.outcome == FlowOutcome.PEEKED) ActionStatus.SUCCESS else ActionStatus.FAILED, text)
        if (speak) say(text)
    }

    private suspend fun whatsAppPlayVoice(action: AssistantAction.WhatsAppPlayVoice, speak: Boolean) {
        if (!com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
            val msg = phrase(lang(), "वॉइस मैसेज चलाने के लिए Seema की Accessibility चालू करनी होगी।", "ভয়েস মেসেজ চালাতে Seema-র Accessibility চালু করতে হবে।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        val r = tools.whatsAppPlayVoice(action.contact)
        r.log.forEach { trace("whatsapp voice: $it") }
        val chat = r.chat ?: action.contact
        val (status, text) = when (r.outcome) {
            FlowOutcome.VOICE_PLAYED -> ActionStatus.SUCCESS to phrase(lang(), "ठीक है, \"$chat\" का आखिरी वॉइस मैसेज चला दिया है।", "ঠিক আছে, \"$chat\" এর শেষ ভয়েস মেসেজ চালিয়ে দিয়েছি।")
            FlowOutcome.NO_VOICE -> ActionStatus.FAILED to phrase(lang(), "\"$chat\" की चैट में अभी कोई वॉइस मैसेज नहीं दिख रहा।", "\"$chat\" এর চ্যাটে এখন কোনো ভয়েস মেসেজ দেখছি না।")
            else -> ActionStatus.FAILED to (whatsAppStopText(r.outcome, chat) ?: phrase(lang(), "वॉइस मैसेज नहीं चला पाई।", "ভয়েস মেসেজ চালাতে পারিনি।"))
        }
        report(action, status, text)
        if (speak) say(text)
    }

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
        // "ye ticket … ko bhej do" right after a ticket was read: that very file, by its name.
        val fileQuery = lastTicketFile
            ?.takeIf { Regex("ticket|टिकट|टिकिट", RegexOption.IGNORE_CASE).containsMatchIn(action.file) }
            ?.substringBeforeLast('.')
            ?: action.file
        val what = fileQuery.ifBlank { phrase(lang(), "सबसे नई", "সবচেয়ে নতুন") }
        val question = phrase(lang(), "WhatsApp पर \"$chat\" को \"$what\" वाली फाइल भेज दूँ?", "হোয়াটসঅ্যাপে \"$chat\" কে \"$what\" ফাইল পাঠাব?")
        if (!confirmScreenAction("Send file on WhatsApp?", question, speak)) {
            report(action, ActionStatus.CANCELLED, "WhatsApp file cancelled.")
            if (speak) say(phrase(lang(), "ठीक है, नहीं भेजी।", "ঠিক আছে, পাঠাইনি।"))
            return
        }
        _state.value = AssistantState.Executing
        val r = tools.whatsAppSendDocument(chat, fileQuery)
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
            FlowOutcome.PEEKED, FlowOutcome.NO_VOICE, FlowOutcome.VOICE_PLAYED -> Triple(ActionStatus.FAILED, "फाइल नहीं भेजी।", "ফাইল পাঠাইনি।")
            FlowOutcome.INTERRUPTED -> Triple(ActionStatus.FAILED, "WhatsApp के ऊपर बार-बार कोई और संदेश (permission / update / system) आ गया, इसलिए मैं रुक गई और वापस आ गई। कुछ नहीं भेजा — वो संदेश देखकर फिर कहिए।", "হোয়াটসঅ্যাপের উপর বারবার অন্য বার্তা (permission / update) এসেছে, তাই থেমে ফিরে এসেছি। কিছু পাঠাইনি।")
        }
        val msg = phrase(lang(), hi, bn)
        report(action, status, msg)
        // Sent, stuck or interrupted: back to Seema with the honest status (not left hanging in WhatsApp).
        if (r.outcome == FlowOutcome.SENT || r.outcome == FlowOutcome.STUCK || r.outcome == FlowOutcome.INTERRUPTED) tools.openSeema()
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
    /**
     * Contacts for a spoken name. Spelled letters are joined first ("एन जेड एम" → NZM); when the whole name finds no
     * one, shorter forms are tried ("साजिद शेख एन जेडम" → "साजिद शेख") — the person is always confirmed before a call
     * or a message, so a near match is offered, never used silently.
     */
    /** The last spoken name that was found only by a near match (it is worth remembering once confirmed). */
    private var nearMatchQuery: String? = null

    private suspend fun lookup(query: String): List<Contact>? {
        nearMatchQuery = null
        // Said this way before and confirmed then: that contact first.
        runCatching { contactAliases.get(query) }.getOrNull()?.let { known ->
            trace("contacts: known from an earlier choice")
            return listOf(known)
        }
        val first = lookupExact(query) ?: return null
        if (first.isNotEmpty()) return first
        val collapsed = com.raju.aiassistant.domain.tools.SpokenLetters.collapse(query)
        if (collapsed != query.trim()) {
            val r = lookupExact(collapsed) ?: return null
            if (r.isNotEmpty()) { trace("contacts: found with spelled letters joined"); nearMatchQuery = query; return r }
        }
        for (shorter in com.raju.aiassistant.domain.tools.SpokenLetters.shorterForms(collapsed)) {
            val r = lookupExact(shorter) ?: return null
            if (r.size in 1..MAX_NEAR_MATCHES) { trace("contacts: found with a shorter form of the name (${r.size})"); nearMatchQuery = query; return r }
            if (r.size > MAX_NEAR_MATCHES) break // too many people share that part of the name
        }
        return emptyList()
    }

    private suspend fun lookupExact(query: String): List<Contact>? {
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

    /**
     * A fact Raju said about himself ([com.raju.aiassistant.domain.memory.FactExtractor]) goes into memory without
     * a question; one on the same subject is replaced ("bhai: Amit" → "bhai: Sumit"). He sees and clears them in
     * Memory Center. Nothing is said about it; only a count goes to the log.
     */
    private suspend fun learnFact(text: String) {
        val fact = com.raju.aiassistant.domain.memory.FactExtractor.extract(text) ?: return
        runCatching {
            val existing = memories.all()
            val subject = com.raju.aiassistant.domain.memory.FactExtractor.subject(fact)
            existing.filter { it.text != fact && com.raju.aiassistant.domain.memory.FactExtractor.subject(it.text) == subject && it.text.startsWith("Raju") }
                .forEach { memories.delete(it.id) }
            val check = validateMemory(fact, memories.all().map { it.text })
            if (check is MemoryCheck.Ok) {
                memories.add(check.text)
                trace("fact learned (${check.text.length} chars)")
            }
        }
    }

    private suspend fun remember(action: AssistantAction.Remember, speak: Boolean = false) {
        val s = settings.value
        when {
            !s.memoryEnabled -> report(action, ActionStatus.BLOCKED, "Memory is turned off in Settings, so nothing was saved.")
            else -> when (val check = validateMemory(action.text, runCatching { memories.all().map { it.text } }.getOrDefault(emptyList()))) {
                is MemoryCheck.Rejected -> report(action, ActionStatus.BLOCKED, check.reason)
                is MemoryCheck.Ok -> {
                    // The AI's own idea in a chat (nobody said "yaad rakhna"): nothing is saved and nothing is
                    // asked — a "ये याद रख लूँ?" after chat answers cost ~5 s and a yes/no each time.
                    if (!asksToRemember(lastUserText.orEmpty())) {
                        trace("remember: skipped (not asked)")
                        report(action, ActionStatus.CANCELLED, "Not saved: the user did not ask to remember this.")
                        return
                    }
                    // Asked: confirmed out loud in a voice turn, then saved.
                    val ok = confirmScreenAction("Remember this?", phrase(lang(), "ये याद रख लूँ: \"${check.text}\"?", "এটা মনে রাখব: \"${check.text}\"?"), speak)
                    if (!ok) report(action, ActionStatus.CANCELLED, "Not saved.")
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
        decision?.complete(Decision.Deny) // see askOutLoud
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

    /**
     * "Bangla mein baat karo": the language is kept (saved in Settings), so listening switches to Bengali,
     * the AI is told to answer only in Bengali and her voice speaks Bengali — no more Hindi mixed in because
     * a Bengali sentence was written by a Hindi recogniser. "Hindi mein bolo" / "dono bhasha" switch back.
     */
    private suspend fun switchLanguage(action: AssistantAction.SetLanguage, speak: Boolean) {
        runCatching { setSpeechLanguage(action.tag) }
        val lang = SeemaLanguage.fromTag(action.tag)
        if (lang != null) conversationLanguage.value = lang
        val msg = when (lang) {
            SeemaLanguage.BENGALI -> "ঠিক আছে, এখন থেকে বাংলায় কথা বলব। হিন্দিতে ফিরতে বলো \"হিন্দিতে কথা বলো\"।"
            SeemaLanguage.HINDI -> "ठीक है, अब से हिंदी में बात करूँगी।"
            else -> "ठीक है, आप जिस भाषा में बोलेंगे, उसी में जवाब दूँगी। / ঠিক আছে, যে ভাষায় বলবে সেই ভাষায় উত্তর দেব।"
        }
        report(action, ActionStatus.SUCCESS, msg)
        if (speak) say(msg)
    }

    /** "So jao": a short goodnight, then she waits for "Hey Seema" (or, without the wake word, simply stops listening). */
    private suspend fun sleepNow(action: AssistantAction, speak: Boolean) {
        closeFollowUp()
        val msg = if (canSleep()) phrase(lang(), "ठीक है, जब बुलाओगे तभी बोलूँगी।", "ঠিক আছে, ডাকলে তবেই কথা বলব।")
            else phrase(lang(), "ठीक है, चुप हो रही हूँ। बात करनी हो तो माइक दबाइए।", "ঠিক আছে, চুপ করছি। কথা বলতে মাইক চাপুন।")
        report(action, ActionStatus.SUCCESS, msg)
        if (speak) say(msg)
        // Her own words must not open a new window: wake-word-only from here.
        closeFollowUp()
        quietUntilName = true
        if (canSleep()) goToSleep() else endSession()
    }

    /** After "chup raho" the turn's end must not reopen her no-name window. */
    private var quietUntilName = false

    /** Digits of a PNR heard so far, and until when more pieces are taken (see [trackPnr]). */
    private var pnrDigits = ""
    private var pnrCollectUntil = 0L

    /**
     * "PNR note karo, track karte rehna": ten digits → saved in the PNR diary (its train and date from the rail
     * service when it answers), so the background check tells when it is confirmed or the chart is ready.
     * Fewer digits → kept, and the rest is asked for; more → said plainly, and asked again.
     */
    private suspend fun trackPnr(action: AssistantAction.PnrTrack, speak: Boolean) {
        val digits = action.digits
        val spaced = digits.chunked(3).joinToString(" ")
        if (digits.length != 10) {
            pnrDigits = if (digits.length > 10) "" else digits
            pnrCollectUntil = clock() + PNR_COLLECT_MS
            val msg = when {
                digits.isEmpty() -> phrase(lang(), "PNR बोलिए — यह 10 अंकों का होता है, टुकड़ों में भी बोल सकते हैं।", "PNR বলুন — এটা ১০ অঙ্কের, ভাগে ভাগেও বলতে পারেন।")
                digits.length > 10 -> phrase(lang(), "PNR 10 अंकों का होता है, मुझे ${digits.length} अंक सुनाई दिए। पूरा PNR फिर से बोलिए।", "PNR ১০ অঙ্কের হয়, আমি ${digits.length}টা শুনলাম। আবার পুরোটা বলুন।")
                else -> phrase(lang(), "अब तक ${digits.length} अंक मिले: $spaced। बाकी ${10 - digits.length} अंक बोलिए।", "এখন পর্যন্ত ${digits.length}টা অঙ্ক: $spaced। বাকি ${10 - digits.length}টা বলুন।")
            }
            report(action, ActionStatus.SUCCESS, msg)
            if (speak) say(msg)
            return
        }
        pnrDigits = ""
        pnrCollectUntil = 0
        if (speak) say(phrase(lang(), "PNR $spaced नोट कर रही हूँ… अभी का स्टेटस भी देख लेती हूँ।", "PNR $spaced লিখে নিচ্ছি… এখনকার অবস্থাও দেখছি।"))
        val fetch = runCatching { pnrSource.status(digits) }.getOrNull()
        val snap = (fetch as? com.raju.aiassistant.domain.business.PnrFetch.Ok)?.snapshot
        val date = snap?.date?.let { journeyDate(it) }
        val journey = com.raju.aiassistant.domain.business.Journey(pnr = digits, train = snap?.trainNumber, date = date ?: today(), note = if (date == null) "date unknown" else null)
        val saved = runCatching { business.saveJourney(journey) }.isSuccess
        if (!saved) {
            val msg = phrase(lang(), "PNR डायरी में सेव नहीं हो पाया।", "PNR ডায়েরিতে সেভ হলো না।")
            report(action, ActionStatus.FAILED, msg)
            if (speak) say(msg)
            return
        }
        val now = when (fetch) {
            is com.raju.aiassistant.domain.business.PnrFetch.Ok -> com.raju.aiassistant.domain.business.spokenPnrStatus(fetch.snapshot) +
                phrase(lang(), " स्टेटस बदलते ही, या कन्फर्म होते ही, बता दूँगी।", " অবস্থা বদলালেই বা কনফার্ম হলেই জানাব।")
            com.raju.aiassistant.domain.business.PnrFetch.NotConfigured -> phrase(lang(), "पर RailKit key नहीं है, इसलिए स्टेटस अपने आप नहीं देख पाऊँगी — Settings में RailKit key डाल दीजिए।", "কিন্তু RailKit key নেই, তাই নিজে অবস্থা দেখতে পারব না।")
            is com.raju.aiassistant.domain.business.PnrFetch.Failed -> phrase(lang(), "अभी स्टेटस नहीं मिला (${fetch.message}), थोड़ी देर में फिर देखूँगी।", "এখন অবস্থা পাইনি (${fetch.message}), পরে আবার দেখব।")
            null -> phrase(lang(), "अभी स्टेटस नहीं मिला, थोड़ी देर में फिर देखूँगी।", "এখন অবস্থা পাইনি, পরে আবার দেখব।")
        }
        val dateNote = if (date == null) phrase(lang(), " यात्रा की तारीख नहीं मिली, आज-कल नज़र रखूँगी।", " যাত্রার তারিখ পাইনি।") else ""
        val msg = phrase(lang(), "PNR $spaced ट्रैकिंग में डाल दिया। ", "PNR $spaced ট্র্যাকিংয়ে রাখলাম। ") + now + dateNote
        report(action, ActionStatus.SUCCESS, msg)
        if (speak) say(msg)
    }

    /** A journey date as the rail service writes it ("2026-11-15", "15-11-2026", "15/11/2026", "15-Nov-2026"). */
    private fun journeyDate(text: String): java.time.LocalDate? {
        val t = text.trim()
        val patterns = listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "d-MMM-yyyy", "dd-MMM-yyyy", "d MMM yyyy")
        for (p in patterns) {
            runCatching { return java.time.LocalDate.parse(t, java.time.format.DateTimeFormatter.ofPattern(p, java.util.Locale.ENGLISH)) }
        }
        return null
    }

    /** The status of the last reported action (the check-in question follows only a success). */
    private var lastReportStatus: ActionStatus? = null

    /**
     * A task that takes a moment, told like a person on a call: what she is starting (stage 1), the
     * task and its own result (stage 2), then one caring question after a success (stage 3).
     */
    private suspend fun narrated(action: AssistantAction, speak: Boolean, run: suspend () -> Unit) {
        // Live camera is a quick back-and-forth: each question gets only its answer.
        if (action is AssistantAction.LookCamera && clock() < liveCameraUntil) return run()
        if (speak) TwoStageNarration.plan(action)?.let { say(phrase(lang(), it.hindi, it.bengali)) }
        lastReportStatus = null
        run()
        if (speak && lastReportStatus == ActionStatus.SUCCESS) {
            TwoStageNarration.checkIn(action)?.let {
                val q = phrase(lang(), it.hindi, it.bengali)
                // In the conversation too, so "haan" is understood as the answer to this question.
                record(ChatRole.ASSISTANT, q)
                say(q)
            }
        }
    }

    /** The file name of the ticket read last ("ye ticket … ko bhej do" sends this one). */
    private var lastTicketFile: String? = null

    private suspend fun readTicket(action: AssistantAction.ReadTicket, speak: Boolean) {
        val msg: String
        val status: ActionStatus
        when (val f = tools.latestTicketPdf()) {
            com.raju.aiassistant.domain.tools.TicketPdf.NoFolder -> {
                status = ActionStatus.FAILED
                msg = phrase(lang(), "टिकट वाला फ़ोल्डर अभी चुना नहीं है। Apps & Tools में \"टिकट फ़ोल्डर चुनें\" दबाकर Download फ़ोल्डर चुन दीजिए, फिर मैं सबसे नया टिकट पढ़ लूँगी।", "টিকিটের ফোল্ডার এখনও বাছা হয়নি। Apps & Tools-এ \"টিকিট ফোল্ডার\" বেছে Download দিন।")
            }
            com.raju.aiassistant.domain.tools.TicketPdf.NoPdf -> {
                status = ActionStatus.FAILED
                msg = phrase(lang(), "चुने हुए फ़ोल्डर में कोई PDF टिकट नहीं मिला।", "বাছা ফোল্ডারে কোনো PDF টিকিট পাইনি।")
            }
            is com.raju.aiassistant.domain.tools.TicketPdf.Unreadable -> {
                status = ActionStatus.FAILED
                msg = phrase(lang(), "\"${f.name}\" खुला, पर उसका लिखा पढ़ा नहीं जा सका — शायद स्कैन की हुई फ़ोटो है।", "\"${f.name}\" খুলেছি, কিন্তু লেখা পড়া গেল না।")
            }
            is com.raju.aiassistant.domain.tools.TicketPdf.Found -> {
                val ticket = com.raju.aiassistant.domain.rail.IrctcTicketParser.parse(f.text)
                if (ticket == null) {
                    status = ActionStatus.FAILED
                    msg = phrase(lang(), "सबसे नई PDF \"${f.name}\" रेलवे टिकट नहीं लगती।", "সবচেয়ে নতুন PDF \"${f.name}\" রেলের টিকিট মনে হচ্ছে না।")
                } else {
                    status = ActionStatus.SUCCESS
                    lastTicketFile = f.name
                    // The ticket goes into the PNR diary by itself (chart watch and briefing then know it).
                    val saved = com.raju.aiassistant.domain.business.TicketToDiary.journey(ticket)?.let { j ->
                        val known = runCatching { business.journeysFrom(j.date) }.getOrDefault(emptyList()).any { it.pnr == j.pnr }
                        if (known) false else runCatching { business.saveJourney(j) }.isSuccess
                    } == true
                    msg = com.raju.aiassistant.domain.rail.spokenTicket(ticket) +
                        if (saved) phrase(lang(), " यह टिकट डायरी में भी जोड़ दिया है।", " টিকিটটা ডায়েরিতেও রেখে দিয়েছি।") else ""
                }
            }
        }
        report(action, status, msg)
        if (speak) say(msg)
    }

    private suspend fun report(action: AssistantAction, status: ActionStatus, message: String) {
        lastReportStatus = status
        lastReportMessage = message
        if (status == ActionStatus.SUCCESS && action !is AssistantAction.ActionDiaryAsk) {
            doneLog.addLast(clock() to action.label)
            while (doneLog.size > 300) doneLog.removeFirst()
        }
        val icon = when (status) {
            ActionStatus.SUCCESS -> "✅"
            ActionStatus.FAILED -> "❌"
            ActionStatus.CANCELLED -> "⏹️"
            ActionStatus.BLOCKED -> "🛡️"
        }
        record(ChatRole.SYSTEM, "$icon $message")
        actionLog.safeLog(action, status, message)
        // Diagnostics: which action did not work and the kind of reason — never names, numbers or words.
        if (status != ActionStatus.SUCCESS) trace("fail: ${action.javaClass.simpleName} ${status.name.lowercase()} (${failReasonKind(message)})")
        if (status == ActionStatus.FAILED) _state.value = AssistantState.Error
    }

    /** The kind of a failure for the log, from its message: permission, accessibility, not found, network … */
    private fun failReasonKind(message: String): String {
        val m = message.lowercase()
        return when {
            "permission" in m || "अनुमति" in m -> "permission"
            "accessibility" in m -> "accessibility"
            "safety mode" in m -> "safety mode"
            "नहीं मिला" in m || "not found" in m || "no contact" in m || "नहीं मिली" in m -> "not found"
            "cancel" in m || "नहीं भेज" in m -> "cancelled"
            "internet" in m || "network" in m || "timeout" in m || "समय" in m -> "network / time"
            "बैंक" in m || "upi" in m || "पासवर्ड" in m || "pin" in m -> "sensitive screen"
            else -> "other"
        }
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
    private var errorResetJob: Job? = null

    private fun scheduleErrorReset() {
        errorResetJob?.cancel()
        errorResetJob = scope.launch {
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
