package com.raju.aiassistant.data.tts

import com.raju.aiassistant.core.audio.PcmAudio
import com.raju.aiassistant.core.audio.parseWav
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.domain.model.TtsEngine
import com.raju.aiassistant.domain.model.UserSettings
import com.raju.aiassistant.domain.voice.CloudVoice
import com.raju.aiassistant.domain.voice.TtsProvider
import com.raju.aiassistant.domain.voice.VoiceCatalog
import okhttp3.OkHttpClient

/** Sarvam AI Bulbul (cloud). Usable only with a saved key and an internet connection. */
class SarvamTtsProvider(
    private val http: OkHttpClient,
    private val secret: suspend (SecretId) -> String?,
    private val isOnline: () -> Boolean,
    private val speaker: () -> String,
) : TtsProvider {
    override val engine = TtsEngine.SARVAM
    override val label = "Sarvam AI (cloud)"

    override suspend fun isAvailable() = isOnline() && !secret(SecretId.SARVAM_API_KEY).isNullOrBlank()

    override suspend fun synthesize(text: String, languageTag: String, rate: Float): AiResult<PcmAudio> {
        val key = secret(SecretId.SARVAM_API_KEY) ?: return AiResult.Failure(FailureKind.NOT_CONFIGURED, "Sarvam API key is not set")
        return when (val r = SarvamTtsClient(http, key).synthesize(com.raju.aiassistant.domain.voice.WarmProsody.text(text), speaker(), languageTag, rate)) {
            is AiResult.Success -> parseWav(r.value)?.let { AiResult.Success(com.raju.aiassistant.domain.voice.WarmProsody.withTail(it)) }
                ?: AiResult.Failure(FailureKind.UNKNOWN, "Sarvam returned audio in an unsupported format")
            is AiResult.Failure -> r
        }
    }
}

private fun wavOrFailure(r: AiResult<ByteArray>, provider: String): AiResult<PcmAudio> = when (r) {
    is AiResult.Success -> parseWav(r.value)?.let { AiResult.Success(com.raju.aiassistant.domain.voice.WarmProsody.withTail(it)) }
        ?: AiResult.Failure(FailureKind.UNKNOWN, "$provider returned audio in an unsupported format")
    is AiResult.Failure -> r
}

/** Cartesia Sonic (cloud). Needs a voice ID: if none was picked yet, a Hindi female voice is chosen and saved. */
class CartesiaTtsProvider(
    private val http: OkHttpClient,
    private val secret: suspend (SecretId) -> String?,
    private val isOnline: () -> Boolean,
    private val settings: () -> UserSettings,
    /** The mood of the reply being spoken (warm, whisper, upset): chooses her emotion. */
    private val mood: () -> com.raju.aiassistant.domain.voice.VoiceMood? = { null },
    private val rememberVoice: suspend (CloudVoice) -> Unit,
) : TtsProvider {
    override val engine = TtsEngine.CARTESIA
    override val label = "Cartesia (cloud)"

    override suspend fun isAvailable() = isOnline() && !secret(SecretId.CARTESIA_API_KEY).isNullOrBlank()

    override suspend fun synthesize(text: String, languageTag: String, rate: Float): AiResult<PcmAudio> {
        val key = secret(SecretId.CARTESIA_API_KEY) ?: return AiResult.Failure(FailureKind.NOT_CONFIGURED, "Cartesia API key is not set")
        val client = CartesiaClient(http, key)
        val s = settings()
        val voice = s.cartesiaVoiceId
            ?: runCatching {
                when (val r = client.listVoices(maxPages = 2)) {
                    is AiResult.Success -> VoiceCatalog.bestDefault(r.value)?.also { rememberVoice(it) }?.id
                    is AiResult.Failure -> null
                }
            }.getOrNull()
            ?: VoiceCatalog.defaultCartesiaVoices.first().id
        val language = languageTag.substringBefore('-').lowercase().ifBlank { "hi" }
        // Soft, warm, unhurried: speed and emotion in the form this model understands.
        val emotion = com.raju.aiassistant.domain.voice.CartesiaControls.forModel(
            s.cartesiaModel, rate, com.raju.aiassistant.domain.voice.VoiceCatalog.cartesiaEmotion(s.cartesiaMood), mood(),
        )
        val first = client.synthesize(com.raju.aiassistant.domain.voice.WarmProsody.text(text), voice, s.cartesiaModel, language, emotion)
        // The saved voice no longer exists (Cartesia: 404 "voice not found"): choose one from the
        // account's own list, remember it, and try once more instead of failing every sentence.
        if (first is AiResult.Failure && VoiceCatalog.isMissingVoice(first.message)) {
            val replacement = (client.listVoices(maxPages = 2) as? AiResult.Success)?.value
                ?.let { VoiceCatalog.replacementVoice(it, voice) }
            if (replacement != null) {
                rememberVoice(replacement)
                return wavOrFailure(client.synthesize(com.raju.aiassistant.domain.voice.WarmProsody.text(text), replacement.id, s.cartesiaModel, language, emotion), "Cartesia")
            }
        }
        return wavOrFailure(first, "Cartesia")
    }
}
