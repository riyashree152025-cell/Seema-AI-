package com.raju.aiassistant.di

import com.raju.aiassistant.data.tts.local.installedEngines
import android.content.Context
import com.raju.aiassistant.core.audio.AudioPlayer
import com.raju.aiassistant.core.audio.ConversationAudio
import com.raju.aiassistant.core.network.ConnectivityObserver
import com.raju.aiassistant.core.security.SecureKeyStore
import com.raju.aiassistant.data.ai.AiProviderFactory
import com.raju.aiassistant.data.ai.defaultHttpClient
import com.raju.aiassistant.data.db.RajuDatabase
import com.raju.aiassistant.data.prefs.SettingsRepository
import com.raju.aiassistant.data.repository.ActionLogRepository
import com.raju.aiassistant.data.repository.ConversationRepository
import com.raju.aiassistant.data.repository.MemoryRepository
import com.raju.aiassistant.data.repository.BusinessRepository
import com.raju.aiassistant.data.search.TavilyClient
import com.raju.aiassistant.data.tools.AndroidDeviceTools
import com.raju.aiassistant.data.tts.AndroidTts
import com.raju.aiassistant.data.tts.AndroidTtsProvider
import com.raju.aiassistant.data.tts.CartesiaTtsProvider
import com.raju.aiassistant.data.tts.DeepgramTtsProvider
import com.raju.aiassistant.data.tts.ElevenLabsTtsProvider
import com.raju.aiassistant.data.tts.SarvamTtsProvider
import com.raju.aiassistant.data.tts.TtsManager
import com.raju.aiassistant.data.voice.DeepgramVoiceInput
import com.raju.aiassistant.data.voice.MicBargeIn
import com.raju.aiassistant.data.voice.MicWakeListener
import com.raju.aiassistant.data.voice.starredContactNames
import kotlinx.coroutines.withContext
import com.raju.aiassistant.core.audio.pcm16ToWav
import com.raju.aiassistant.data.tts.DeepgramClient
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.assistant.Transcriber
import com.raju.aiassistant.data.voice.SwitchingVoiceInput
import com.raju.aiassistant.data.voice.VoiceInputManager
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.domain.model.SttEngine
import com.raju.aiassistant.domain.voice.VoiceCatalog
import com.raju.aiassistant.domain.assistant.AnnouncementDeduper
import com.raju.aiassistant.domain.notify.AnnouncementCoordinator
import com.raju.aiassistant.domain.notify.CallDeduper
import com.raju.aiassistant.notifications.ConsentNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import com.raju.aiassistant.domain.assistant.AssistantEngine
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.SpeechPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.ui.LaunchRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Manual dependency container: creates and wires every service once per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Lives as long as the process; main-thread by default (SpeechRecognizer requires it). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database by lazy { RajuDatabase.create(appContext) }
    /** Password-locked backup and restore of the user's data (never the API keys). */
    val backupManager by lazy {
        com.raju.aiassistant.data.backup.BackupManager(
            appContext,
            flushDatabase = { database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close() },
            closeDatabase = { database.close() },
        )
    }
    val chatDao by lazy { database.chatDao() }
    val actionLogDao by lazy { database.actionLogDao() }
    val http by lazy { defaultHttpClient() }

    val secureKeyStore by lazy { SecureKeyStore(appContext) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val currentSettings: StateFlow<UserSettings> by lazy {
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, UserSettings())
    }

    val conversationRepository by lazy { ConversationRepository(database.chatDao()) }
    val memoryRepository by lazy { MemoryRepository(database.memoryDao()) }
    val actionLogRepository by lazy { ActionLogRepository(database.actionLogDao()) }
    val businessRepository by lazy { BusinessRepository(database.businessDao()) }
    /** PNR status from the service set up in Settings (none by default). */
    val railKit by lazy { com.raju.aiassistant.data.rail.RailKitClient(http, secureKeyStore) }
    /** RailKit when its key is saved; otherwise the user's own PNR URL. A waiting ticket goes on the chart watch. */
    val pnrSource: com.raju.aiassistant.domain.business.PnrSource by lazy {
        com.raju.aiassistant.data.rail.RailPnrSource(railKit, com.raju.aiassistant.data.rail.HttpPnrSource(http, secureKeyStore)) { answer ->
            com.raju.aiassistant.notifications.ChartWatch.onPnrChecked(appContext, businessRepository, answer)
        }
    }
    /** Imported documents (RAG), stored and searched on the phone. */
    val knowledgeRepository by lazy { com.raju.aiassistant.data.knowledge.KnowledgeRepository(appContext, database.knowledgeDao()) }
    /** Hindi or Bengali right now: set by the engine from what the user says, used by the voice. */
    val conversationLanguage = MutableStateFlow(com.raju.aiassistant.domain.language.SeemaLanguage.HINDI)

    val connectivityObserver by lazy { ConnectivityObserver(appContext) }
    val online: StateFlow<Boolean> by lazy { connectivityObserver.isOnline.stateIn(appScope, SharingStarted.Eagerly, true) }

    /** Thermal status and Battery Saver from Android (event-driven, no polling). */
    val powerMonitor by lazy { com.raju.aiassistant.core.power.PowerMonitor(appContext) }

    /** Weather for the 3D dashboard (Open-Meteo, coarse location, cached 30 min). */
    val weather by lazy {
        com.raju.aiassistant.data.weather.WeatherRepository(
            com.raju.aiassistant.data.weather.OpenMeteoClient(http),
            com.raju.aiassistant.data.weather.CoarseLocation(appContext),
        ) { online.value }
    }

    /** Debug builds only: lets the emulator test show a state's visuals (for the demo recording). */
    val debugPreview = MutableStateFlow<com.raju.aiassistant.domain.model.AssistantState?>(null)

    /**
     * What "Auto" means on this phone, decided once per app start (never switches while in use):
     * the light Arc Reactor on low-memory phones or in Battery Saver, otherwise the 3D core.
     */
    val autoCoreAppearance: com.raju.aiassistant.domain.model.CoreAppearance by lazy {
        val light = runCatching { com.raju.aiassistant.core.power.DeviceStats.prefersLightCore(appContext) }.getOrDefault(true) ||
            powerMonitor.state.value.systemPowerSave
        if (light) com.raju.aiassistant.domain.model.CoreAppearance.LEGACY else com.raju.aiassistant.domain.model.CoreAppearance.MODERN
    }

    /** The design the Home screen really shows: Auto resolved to Legacy or Modern. */
    fun resolveCore(choice: com.raju.aiassistant.domain.model.CoreAppearance) =
        if (choice == com.raju.aiassistant.domain.model.CoreAppearance.AUTO) autoCoreAppearance else choice

    /**
     * The app went to the background for a while (Android trims memory): free what is cheap to
     * recreate. The TTS engine binding is rebuilt on the next reply; chats and memories are untouched.
     */
    fun trimForBackground() {
        if (!engine.voiceSession.value) {
            androidTts.release()
            // The offline voice models hold 50–200 MB of native memory: free it; the next reply reloads it.
            appScope.launch { sherpaHost.release() }
        }
        cleanSharedFiles()
    }

    /** Files Seema created for sharing (code, notes) are deleted after a day; nothing else is touched. */
    fun cleanSharedFiles() {
        val dir = java.io.File(appContext.cacheDir, "shared")
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60_000L
        dir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    val providerFactory by lazy { AiProviderFactory(http) }
    val pcSyncClient by lazy { com.raju.aiassistant.data.sync.PcSyncClient(http, secureKeyStore) }
    val androidTts by lazy { AndroidTts(appContext) }
    /** Call-style audio (echo cancellation) during hands-free conversations. */
    val conversationAudio by lazy { ConversationAudio(appContext) }
    val audioPlayer by lazy { AudioPlayer(appContext) { conversationAudio.active } }
    /** Offline voices: model downloads, the sherpa-onnx runtime and measured engine speed. */
    val voiceModels by lazy { com.raju.aiassistant.data.tts.local.voiceModels(appContext, http, appScope) }
    /** The offline "Hey Seema" model download (same downloader as the offline voices). */
    val wakeModels by lazy {
        com.raju.aiassistant.data.tts.local.VoiceModelManager(appContext, http, appScope, listOf(com.raju.aiassistant.domain.voice.WAKE_WORD_MODEL))
    }
    val kwsWakeListener by lazy {
        com.raju.aiassistant.data.voice.KwsWakeListener(
            appContext, appScope, wakeModels, com.raju.aiassistant.domain.voice.WAKE_WORD_MODEL, { sherpaHost.nativeAvailable },
            hub = micHub,
            useHub = { currentSettings.value.micHub },
        ) {
            android.util.Log.i("SeemaVoice", it)
        }
    }
    /** Standby: offline "Hey Seema" when downloaded and "wake by name" is on; otherwise the voice detector (+ Deepgram name check). */
    /** The one shared microphone reader (Phase 1). Only used while the micHub setting is on. */
    val micHub: com.raju.aiassistant.data.voice.MicHub by lazy {
        com.raju.aiassistant.data.voice.MicHub(
            appScope,
            { com.raju.aiassistant.data.voice.AudioRecordFrameSource(enhance = { audioInterruptions.ringing() }) },
            ringFrames = com.raju.aiassistant.data.voice.ringFramesFor(com.raju.aiassistant.data.voice.minMicBufferBytes()),
            log = { android.util.Log.i("SeemaVoice", it) },
        )
    }
    val wakeListener by lazy {
        com.raju.aiassistant.data.voice.SwitchingWakeListener(
            kwsWakeListener,
            MicWakeListener(appContext, appScope, micHub) { currentSettings.value.micHub },
            preferCloud = { online.value && deepgramTranscriber.isAvailable() },
        ) { currentSettings.value.wakeWord }
    }
    /** Headphones or a headset (wired, Bluetooth, USB) are connected: Seema's voice is not on the loudspeaker. */
    fun headphonesConnected(): Boolean {
        val outputs = appContext.getSystemService(android.media.AudioManager::class.java)
            ?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        return outputs.any {
            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    /** Phone calls and headphone disconnects (started in RajuApp). */
    val audioInterruptions: com.raju.aiassistant.core.audio.AudioInterruptions by lazy {
        com.raju.aiassistant.core.audio.AudioInterruptions(
            appContext,
            onCall = { phase ->
                when (phase) {
                    com.raju.aiassistant.core.audio.CallPhase.RINGING -> {
                        engine.onCallRinging()
                        val s = currentSettings.value
                        // The ringtone down to ~20 % while she announces and listens; restored below.
                        if (s.announceCalls && !s.assistantPaused && !s.safetyMode) ringDucker.duck()
                        announceRingingIfNobodyDid()
                    }
                    com.raju.aiassistant.core.audio.CallPhase.ACTIVE -> {
                        ringDucker.restore()
                        engine.pauseForCall()
                        if (currentSettings.value.micHub) micHub.pauseForCall()
                    }
                    com.raju.aiassistant.core.audio.CallPhase.NONE -> {
                        ringDucker.restore()
                        com.raju.aiassistant.notifications.CallControls.clear(appContext)
                        engine.resumeAfterCall()
                        if (currentSettings.value.micHub) micHub.resumeAfterCall()
                    }
                }
            },
            onBecomingNoisy = { engine.interrupt() },
        )
    }
    val sherpaHost by lazy { com.raju.aiassistant.data.tts.local.SherpaTtsHost(voiceModels) }
    val voiceEngineStats by lazy { com.raju.aiassistant.data.tts.local.VoiceEngineStatsStore(appContext) }

    /** The voice engines that will be tried, in order (Automatic / Manual selection, installed offline voices, measured speed). */
    fun voiceEnginesFor(s: com.raju.aiassistant.domain.model.UserSettings): List<TtsEngine> {
        val installed = if (sherpaHost.nativeAvailable) voiceModels.installedEngines() else emptySet()
        return com.raju.aiassistant.domain.voice.voiceEngineOrder(s.voiceEngineAuto, s.ttsEngine, installed, voiceEngineStats.stats.value)
    }

    val speaker by lazy {
        val providers = listOf(
            com.raju.aiassistant.data.tts.local.LocalTtsProvider(TtsEngine.KOKORO, sherpaHost, voiceModels) { currentSettings.value },
            com.raju.aiassistant.data.tts.local.LocalTtsProvider(TtsEngine.PIPER, sherpaHost, voiceModels) { currentSettings.value },
            SarvamTtsProvider(http, { secureKeyStore.get(it) }, { online.value }, { currentSettings.value.sarvamSpeaker }),
            ElevenLabsTtsProvider(http, { secureKeyStore.get(it) }, { online.value }, { currentSettings.value }) { v ->
                settingsRepository.setElevenVoice(v.id, v.name)
            },
            CartesiaTtsProvider(http, { secureKeyStore.get(it) }, { online.value }, { currentSettings.value }) { v ->
                settingsRepository.setCartesiaVoice(v.id, v.name)
            },
            DeepgramTtsProvider(http, { secureKeyStore.get(it) }, { online.value }, { currentSettings.value }),
            AndroidTtsProvider(androidTts, { currentSettings.value.androidVoice }) { currentSettings.value.speechPitch },
        )
        TtsManager(
            appScope, SpeechPipeline(audioPlayer), providers.map { com.raju.aiassistant.data.tts.local.MeasuredTtsProvider(it, voiceEngineStats) },
            androidTts, currentSettings, conversationLanguage,
        ) { s -> voiceEnginesFor(s) }
    }
    val voiceInput by lazy { VoiceInputManager(appContext) { android.util.Log.i("SeemaVoice", it) } }
    val deepgramVoiceInput by lazy {
        DeepgramVoiceInput(
            appContext, http, appScope,
            apiKey = { secureKeyStore.get(SecretId.DEEPGRAM_API_KEY) },
            model = { VoiceCatalog.sttOption(currentSettings.value.deepgramSttModel) },
            configured = { SecretId.DEEPGRAM_API_KEY in secureKeyStore.configured.value && online.value },
            keyterms = { keyterms() },
            hub = micHub,
            useHub = { currentSettings.value.micHub },
        )
    }

    private var cachedKeyterms: Pair<Long, List<String>>? = null

    /**
     * Words the recogniser should get right: her name and the user's favourite (starred) contacts,
     * so "Riyashree ko call karo" is not heard as something else. Cached for 10 minutes.
     */
    private suspend fun keyterms(): List<String> {
        val now = System.currentTimeMillis()
        cachedKeyterms?.let { (at, terms) -> if (now - at < 10 * 60_000L) return terms }
        val terms = listOf("Seema", "सीमा") + withContext(Dispatchers.IO) { starredContactNames(appContext) }
        cachedKeyterms = now to terms
        return terms
    }
    /** Turns the user's interrupting utterance into text (Deepgram, when its key is saved). */
    private val deepgramTranscriber = object : Transcriber {
        override fun isAvailable() = SecretId.DEEPGRAM_API_KEY in secureKeyStore.configured.value && online.value

        override suspend fun transcribe(pcm16k: ByteArray): String? {
            val key = secureKeyStore.get(SecretId.DEEPGRAM_API_KEY) ?: return null
            val option = VoiceCatalog.sttOption(currentSettings.value.deepgramSttModel)
            // Quiet wake clips are raised first: Deepgram returned nothing for many of them on the phone.
            val audio = com.raju.aiassistant.domain.voice.louderForStt(pcm16k)
            return when (val r = DeepgramClient(http, key).transcribeDetailed(pcm16ToWav(audio, 16_000), option.model, option.language, keyterms())) {
                is AiResult.Success -> r.value.text
                is AiResult.Failure -> null
            }
        }
    }

    /** What the assistant listens with: Android's recogniser or Deepgram, as chosen in Settings. */
    val assistantVoiceInput: SwitchingVoiceInput by lazy {
        SwitchingVoiceInput(
            voiceInput,
            deepgramVoiceInput,
            useDeepgram = {
                // Without internet Deepgram cannot answer: the phone's own recogniser takes the turn, so
                // the offline commands (torch, alarm, call…) still work by voice.
                online.value &&
                // Deepgram's models used here understand Hindi/Hinglish, not Bengali: Bengali uses the phone's recogniser.
                (com.raju.aiassistant.domain.language.SeemaLanguage.fromTag(currentSettings.value.speechLanguage) ?: conversationLanguage.value) !=
                    com.raju.aiassistant.domain.language.SeemaLanguage.BENGALI &&
                    // Hands-free restarts the recogniser often; Deepgram does that silently, so it is
                    // preferred whenever its key is saved.
                    (currentSettings.value.sttEngine == SttEngine.DEEPGRAM ||
                        // … and for "uthao / kaat do" on a ringing call: no beep, and a quick cut of the turn.
                        ((engine.handsFreeActive.value || engine.callCommandOpen()) && SecretId.DEEPGRAM_API_KEY in secureKeyStore.configured.value))
            },
            scope = appScope,
        )
    }
/**
     * A question about one or more pictures, answered by Gemini (the only vision model set up here).
     * The pictures leave the phone for this, so callers ask the user first unless the user picked them.
     */
    suspend fun askAboutImages(jpegs: List<ByteArray>, question: String, system: String): com.raju.aiassistant.domain.ai.AiResult<String> {
        val key = secureKeyStore.get(SecretId.GEMINI_API_KEY)
        if (key.isNullOrBlank()) {
            return com.raju.aiassistant.domain.ai.AiResult.Failure(com.raju.aiassistant.domain.ai.FailureKind.NOT_CONFIGURED, "Photo samajhne ke liye Gemini key chahiye (Settings → Brain).")
        }
        if (!online.value) {
            return com.raju.aiassistant.domain.ai.AiResult.Failure(com.raju.aiassistant.domain.ai.FailureKind.NETWORK, "Photo samajhne ke liye internet chahiye.")
        }
        val gemini = com.raju.aiassistant.data.ai.GeminiProvider(http, key)
        // The saved Gemini model, or (auto / retired) the newest Flash model the key lists.
        val model = currentSettings.value.modelFor(com.raju.aiassistant.domain.ai.ProviderId.GEMINI)
            ?.takeUnless { com.raju.aiassistant.domain.ai.ModelAdvice.isRetiredGemini(it) }
            ?: (gemini.listModels() as? com.raju.aiassistant.domain.ai.AiResult.Success)?.value
                ?.let { com.raju.aiassistant.domain.ai.pickDefaultModel(com.raju.aiassistant.domain.ai.ProviderId.GEMINI, it)?.id }
            ?: "gemini-flash-latest"
        return gemini.describeImages(model, system, question, jpegs)
    }

    val deviceTools by lazy { AndroidDeviceTools(appContext) }
    val announcementDeduper = AnnouncementDeduper()
    /** Messages of the last hours, in memory only, for "notification ka saar batao". */
    val recentNotifications = com.raju.aiassistant.domain.notify.RecentNotifications()
    val callDeduper = CallDeduper()

    val ringDucker by lazy { com.raju.aiassistant.core.audio.RingDucker(appContext) }

    /** When a call was last announced (by the call-screening service, with the caller's name). */
    @Volatile var lastCallAnnouncedAt = 0L

    /**
     * The phone rings and the call-screening service did not announce it (Seema does not hold that
     * role, so Android gives her no number): say at least that a call is coming and listen for
     * "utha lo / kaat do / bol do …" while it rings. With the role, the service names the caller.
     */
    private fun announceRingingIfNobodyDid() {
        appScope.launch {
            kotlinx.coroutines.delay(800) // the screening service, when it runs, announces first
            val s = currentSettings.value
            if (System.currentTimeMillis() - lastCallAnnouncedAt < 15_000) return@launch
            if (!s.announceCalls || s.assistantPaused || s.safetyMode || !audioInterruptions.ringing()) return@launch
            lastCallAnnouncedAt = System.currentTimeMillis()
            android.util.Log.i("SeemaVoice", "call: announced without caller details (no call-screening role)")
            speaker.speak(com.raju.aiassistant.domain.notify.ringingAnnouncementText(s.userName) + " " + com.raju.aiassistant.domain.notify.ASK_TO_ANSWER)
            if (audioInterruptions.ringing()) engine.listenForCallCommand(null, null)
        }
    }

    /** True while Android has the notification listener connected (not just permission granted). */
    val listenerConnected = MutableStateFlow(false)

    val consentNotifier by lazy { ConsentNotifier(appContext) }
    val announcements: AnnouncementCoordinator by lazy {
        AnnouncementCoordinator(
            appScope, speaker, currentSettings,
            // "Padh ke sunau kya?" → listen ~5 s: "haan" reads it, "rehne do" / silence drops it quietly.
            onAsked = { p ->
                engine.listenForReadReply { yes ->
                    when (yes) {
                        true -> announcements.read(p.id)
                        false -> { announcements.skip(p.id); speaker.stop() }
                        // Silence: she goes quiet, but a later "batao / padho" still reads it.
                        null -> Unit
                    }
                }
            },
        ).also { coordinator ->
            appScope.launch { coordinator.pending.collect { consentNotifier.show(it) } }
        }
    }

    /** Set by MainActivity for shortcuts and shared text; consumed by the navigation host. */
    val launchRequests = MutableStateFlow<LaunchRequest?>(null)

    /** Stop button: assistant, speech and any pending message announcement. */
    fun stopEverything() {
        engine.stop()
        announcements.stopAll()
        speaker.stop()
    }

    val engine: AssistantEngine by lazy {
        AssistantEngine(
            scope = appScope,
            settings = currentSettings,
            secret = { secureKeyStore.get(it) },
            providerFor = { id, key -> providerFactory.create(id, key) },
            searchFor = { key -> TavilyClient(http, key) },
            history = conversationRepository,
            memories = memoryRepository,
            actionLog = actionLogRepository,
            tools = deviceTools,
            speaker = speaker,
            voice = assistantVoiceInput,
            isOnline = { online.value },
            bargeIn = MicBargeIn(
                appContext, appScope, playbackLevel = { audioPlayer.levelNow() }, callStyle = { conversationAudio.active },
                hub = micHub, useHub = { currentSettings.value.micHub },
                speakerMode = { !headphonesConnected() && currentSettings.value.bargeInSpeaker },
            ),
            conversationMode = conversationAudio,
            transcriber = deepgramTranscriber,
            wakeListener = wakeListener,
            onWake = { com.raju.aiassistant.voice.SeemaWakeService.wakeScreen(appContext) },
            onModelReplaced = { id, model -> appScope.launch { settingsRepository.setModel(id, model) } },
            headphones = ::headphonesConnected,
            describeImage = { jpeg, question ->
                askAboutImages(
                    listOf(jpeg), question,
                    "You are Seema, the user's phone assistant, looking at one picture (the phone camera or the phone's screen). Describe briefly and helpfully what is in it; answer the user's question about it. Say what you see and what you only guess.",
                )
            },
            business = businessRepository,
            releaseMic = { micHub.releaseAll() },
            restoreMic = { micHub.unmute() },
            pnrSource = pnrSource,
            rail = railKit,
            routines = { com.raju.aiassistant.notifications.RoutineStore.all(appContext) },
            recentMessages = { recentNotifications.since(System.currentTimeMillis()) },
            replyTargets = { com.raju.aiassistant.notifications.NotificationReplies.targets() },
            sendNotificationReply = { key, text -> com.raju.aiassistant.notifications.NotificationReplies.send(appContext, key, text) },
            log = { android.util.Log.i("SeemaVoice", it) },
            conversationLanguage = conversationLanguage,
            knowledge = knowledgeRepository,
            latestNotification = { announcements.getLatestMessageSummary() },
            notificationFollowUp = { announcements.followUpPending() },
            // Auto fallback: the other AI providers whose keys are saved, tried when the chosen one fails.
            fallbackProviders = { val configured = secureKeyStore.configured.value; listOf(com.raju.aiassistant.domain.ai.ProviderId.GEMINI, com.raju.aiassistant.domain.ai.ProviderId.GROQ, com.raju.aiassistant.domain.ai.ProviderId.CEREBRAS, com.raju.aiassistant.domain.ai.ProviderId.OPENROUTER, com.raju.aiassistant.domain.ai.ProviderId.DEEPSEEK).filter { it.secret in configured } + if (com.raju.aiassistant.domain.model.SecretId.OLLAMA_ENDPOINT in configured) listOf(com.raju.aiassistant.domain.ai.ProviderId.OLLAMA) else emptyList() },
            nowDescription = { SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a (yyyy-MM-dd'T'HH:mm, z)", Locale.ENGLISH).format(Date()) },
        ).also { e ->
            appScope.launch { e.load() }
            // Compile the fast router's patterns now, off the main thread, so the first voice command is fast too.
            appScope.launch(Dispatchers.Default) { com.raju.aiassistant.domain.assistant.LocalCommands.parse("torch on") }
        }
    }
}
