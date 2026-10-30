package com.raju.aiassistant.domain.model

import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.voice.VoiceCatalog

/**
 * Speech output engines. The order is also the fallback order after the chosen one (see providerOrder):
 * cloud voices first, the offline Android voice always last.
 */
enum class TtsEngine(val label: String, val hint: String) {
    SARVAM("Sarvam AI — Bulbul", "Indian voices, best Hindi pronunciation, cheap"),
    ELEVENLABS("ElevenLabs", "★ most natural and sweet voice; Hindi + English"),
    CARTESIA("Cartesia Sonic", "Natural voice with moods; Hindi + English"),
    DEEPGRAM("Deepgram Aura", "English only — Hindi replies use the next voice"),
    ANDROID("Android TTS (offline)", "Free, works without internet"),
}

/** How Seema talks. Changes only the tone; honesty and safety rules stay the same. */
enum class Personality(val label: String, val hint: String) {
    MASTI("Masti / Fun", "★ witty, playful, a little filmy — never boring"),
    DOST("Dost / Friendly", "warm and caring, light humour"),
    PRO("Professional", "short, clear and to the point"),
}

/** Speech input (listening) engines. */
enum class SttEngine(val label: String, val hint: String) {
    ANDROID("Android / Google (free)", "Built in; works well for clear Hindi or English"),
    DEEPGRAM("Deepgram Nova-3", "★ better for Hinglish (Hindi + English mixed); needs a Deepgram key"),
}

enum class MusicService(val label: String) { YOUTUBE_MUSIC("YouTube Music"), SPOTIFY("Spotify") }

/** Apps whose notifications can be announced. */
enum class AnnounceApp(val packageName: String, val label: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
    TELEGRAM("org.telegram.messenger", "Telegram"),
    GOOGLE_MESSAGES("com.google.android.apps.messaging", "Google Messages"),
    SAMSUNG_MESSAGES("com.samsung.android.messaging", "Samsung Messages"),
}

/** Result of the last real connection test for a provider. */
data class ConnectionTest(val success: Boolean, val message: String, val timestamp: Long)

/**
 * How the microphone is used (power vs convenience):
 * - PUSH_TO_TALK: the mic opens only when the user taps it, for one request (lowest battery use).
 * - SESSION: tapping Mic starts a conversation that keeps listening and ends by itself after the
 *   sleep timeout of silence (or Stop).
 * - ALWAYS: listening starts when the app opens; after the timeout she sleeps and wakes on her name.
 *   Only while the app is on screen — there is no background microphone service.
 */
enum class VoiceMode(val label: String, val hint: String) {
    PUSH_TO_TALK("Push-to-talk (battery saver)", "★ Mic opens only when you tap it"),
    SESSION("Conversation session", "Tap Mic once, talk freely; it stops after a quiet while"),
    ALWAYS("Always listening (app open)", "Listens as soon as the app opens; sleeps and wakes on \"Seema\""),
}

/** The two Home screen designs (Settings → AI core appearance). Only the chosen one runs. */
enum class CoreAppearance(val label: String, val hint: String) {
    LEGACY("Legacy Arc Reactor", "The original reactor: always turning, beats like a heart, colours change with her mood"),
    MODERN("3D Holographic Core", "New JARVIS-style 3D core with blue holographic rings, clock and live phone status"),
    AUTO("Auto / Smart", "Chosen once when the app opens: 3D core on capable phones, Arc Reactor on light phones or in Battery Saver"),
}

/** Non-secret user preferences (persisted in DataStore). */
data class UserSettings(
    val defaultProvider: ProviderId = ProviderId.GEMINI,
    val models: Map<ProviderId, String> = emptyMap(),
    val lastTests: Map<ProviderId, ConnectionTest> = emptyMap(),
    val lowPowerMode: Boolean = false,
    val reducedMotion: Boolean = false,
    val saveHistory: Boolean = true,
    val historyLimit: Int = 20,
    val ttsEngine: TtsEngine = TtsEngine.SARVAM,
    val sarvamSpeaker: String = SARVAM_FEMALE_SPEAKERS.first(),
    val elevenModel: String = VoiceCatalog.ELEVEN_DEFAULT_MODEL,
    /** null = not chosen yet: the best female voice of the account is picked on first use. */
    val elevenVoiceId: String? = null,
    val elevenVoiceName: String? = null,
    val cartesiaModel: String = VoiceCatalog.CARTESIA_DEFAULT_MODEL,
    val cartesiaVoiceId: String? = null,
    val cartesiaVoiceName: String? = null,
    /** Cartesia Sonic 3 emotion, "" = natural. */
    val cartesiaMood: String = "",
    val deepgramVoice: String = VoiceCatalog.DEEPGRAM_DEFAULT_VOICE,
    val sttEngine: SttEngine = SttEngine.ANDROID,
    val deepgramSttModel: String = VoiceCatalog.DEEPGRAM_DEFAULT_STT,
    val speechRate: Float = 1.0f,
    /** Voice pitch for the Android voice: a little below 1 sounds deeper and calmer. */
    val speechPitch: Float = 0.92f,
    val androidVoice: String? = null,
    /** "auto" (Hindi/Bengali detected from the user), "hi-IN" or "bn-IN". */
    val speechLanguage: String = com.raju.aiassistant.domain.language.LANGUAGE_AUTO,
    val autoSpeakReplies: Boolean = true,
    val continuousConversation: Boolean = false,
    /** How the microphone is used. Battery saver (push-to-talk) by default. */
    val voiceMode: VoiceMode = VoiceMode.PUSH_TO_TALK,
    /** Talking while Seema speaks stops her (voice conversations only). */
    val bargeIn: Boolean = true,
    /** Hands-free: after this many seconds without the user speaking Seema goes to sleep (0 = never). */
    val sleepAfterSeconds: Int = 60,
    /** While asleep, wake only when called by name ("Seema …"); off = any speech wakes her. */
    val wakeWord: Boolean = true,
    /** Morning briefing (journeys, yesterday's spending) and the Sunday expense report as notifications. */
    val businessReminders: Boolean = true,
    /** Smart power: animations slow down and stop when nobody is using the app. */
    val smartPower: Boolean = true,
    /** Thermal protection: lighter visuals when Android reports the phone is getting hot. */
    val thermalProtection: Boolean = true,
    /** Which AI core the Home screen shows. */
    val coreAppearance: CoreAppearance = CoreAppearance.LEGACY,
    val personality: Personality = Personality.MASTI,
    val announceNotifications: Boolean = false,
    val announceApps: Set<String> = setOf(AnnounceApp.WHATSAPP.packageName, AnnounceApp.WHATSAPP_BUSINESS.packageName),
    /** Say only who wrote, and read the message only after the user taps Read. */
    val askBeforeReading: Boolean = true,
    /** Announce incoming phone (SIM) calls. Needs the call-screening role. Off until the user opts in. */
    val announceCalls: Boolean = false,
    /** Speak the number of callers who are not in Contacts (otherwise "unknown number"). */
    val speakCallerNumber: Boolean = false,
    /** Best effort: announce WhatsApp/Telegram calls from their call notifications. */
    val announceAppCalls: Boolean = false,
    val directCalling: Boolean = false,
    val musicService: MusicService = MusicService.YOUTUBE_MUSIC,
    val memoryEnabled: Boolean = true,
    val safetyMode: Boolean = false,
    val assistantPaused: Boolean = false,
) {
    fun modelFor(provider: ProviderId): String? = models[provider]

    /** Start listening as soon as the app opens (only in Always-listening mode). */
    val autoListen: Boolean get() = voiceMode == VoiceMode.ALWAYS

    /** Notification announcements are allowed right now. */
    val announcementsActive: Boolean get() = announceNotifications && !assistantPaused && !safetyMode
}

/** Female voices of Sarvam Bulbul v3 (names are lowercase and case-sensitive). The first one is the default. */
val SARVAM_FEMALE_SPEAKERS = listOf("priya", "ritu", "kavya", "neha", "simran", "ishita", "shreya", "pooja", "roopa")

/** A saved Bulbul v2 voice (anushka, manisha, ...) is rejected by v3, so it falls back to the default. */
fun validSarvamSpeaker(saved: String?): String = saved?.takeIf { it in SARVAM_FEMALE_SPEAKERS } ?: SARVAM_FEMALE_SPEAKERS.first()
val SPEECH_LANGUAGES = com.raju.aiassistant.domain.language.LANGUAGE_CHOICES
