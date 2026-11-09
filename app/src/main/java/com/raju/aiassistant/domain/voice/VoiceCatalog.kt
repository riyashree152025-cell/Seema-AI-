package com.raju.aiassistant.domain.voice

/** A model the user can pick for a cloud voice. [note] says in one line when to choose it. */
data class VoiceModelOption(val id: String, val title: String, val note: String, val recommended: Boolean = false)

/** A voice offered by a cloud provider (loaded from the user's account). */
data class CloudVoice(
    val id: String,
    val name: String,
    val detail: String = "",
    val female: Boolean = false,
    val hindi: Boolean = false,
    /** Voice Library voice: works through the API only on a paid plan. */
    val paidOnly: Boolean = false,
) {
    val label: String get() = name + (if (hindi) " · Hindi" else "") + (if (paidOnly) " · paid plan" else "") + (if (detail.isNotBlank()) " — $detail" else "")
}

/** Sarvam AI Indian speaker choice with description. */
data class SarvamSpeakerOption(val id: String, val name: String, val note: String, val recommended: Boolean = false)

/** Deepgram speech-to-text choice: "model:language", e.g. "nova-3:multi". */
data class SttModelOption(val id: String, val title: String, val note: String, val recommended: Boolean = false) {
    val model: String get() = id.substringBefore(':')
    val language: String get() = id.substringAfter(':', "multi")
}

/**
 * Models, voices and recommendations for the cloud voice providers. Recommendations are for this app:
 * a Hindi/Hinglish assistant that speaks short replies sentence by sentence, often on a free plan.
 */
object VoiceCatalog {
    // ---------------------------------------------------------------- ElevenLabs
    const val ELEVEN_DEFAULT_MODEL = "eleven_multilingual_v2"
    /** "Sarah", one of ElevenLabs' default (premade) female voices; used until the user picks one. */
    const val ELEVEN_FALLBACK_VOICE = "EXAVITQu4vr4xnSDxMaL"

    val elevenModels = listOf(
        VoiceModelOption("eleven_multilingual_v2", "Multilingual v2", "सबसे प्राकृतिक और मीठी Hindi आवाज़ · 1 credit/अक्षर", recommended = true),
        VoiceModelOption("eleven_flash_v2_5", "Flash v2.5", "सबसे तेज़, आधे credits · free plan पर ज़्यादा देर चलेगा", recommended = true),
        VoiceModelOption("eleven_v3_conversational", "v3 Conversational", "भावपूर्ण बातचीत, तेज़ · आधे credits"),
        VoiceModelOption("eleven_turbo_v2_5", "Turbo v2.5", "तेज़, आधे credits"),
        VoiceModelOption("eleven_v3", "v3", "सबसे भावपूर्ण, पर धीमा · 1 credit/अक्षर"),
        VoiceModelOption("eleven_v4_turbo", "v4 Turbo", "नया, बहुत तेज़ · plan के हिसाब से"),
        VoiceModelOption("eleven_v4", "v4", "नया · plan के हिसाब से"),
    )

    /**
     * Known models first (in recommended order), then any other text-to-speech model the account offers.
     * [available] is (id, name) from the API; when it is empty (not loaded yet) the known list is shown.
     */
    fun mergeElevenModels(available: List<Pair<String, String>>): List<VoiceModelOption> {
        if (available.isEmpty()) return elevenModels
        val ids = available.map { it.first }.toSet()
        val known = elevenModels.filter { it.id in ids }
        val extra = available.filter { (id, _) -> elevenModels.none { it.id == id } }
            .map { (id, name) -> VoiceModelOption(id, name.ifBlank { id }, "आपके account में उपलब्ध") }
        return known + extra
    }

    // ---------------------------------------------------------------- Cartesia
    const val CARTESIA_DEFAULT_MODEL = "sonic-3"
    val cartesiaModels = listOf(
        VoiceModelOption("sonic-3", "Sonic 3", "Hindi समझता है, भाव (mood) के साथ · 1 credit/अक्षर", recommended = true),
        VoiceModelOption("sonic-3.5", "Sonic 3.5", "सबसे नया, ज़्यादा प्राकृतिक एवं स्पष्ट आवाज़"),
        VoiceModelOption("sonic-latest", "Sonic latest", "हमेशा Cartesia का सबसे नया model"),
    )

    /** Default popular Cartesia female voices (available without initial network wait). */
    val defaultCartesiaVoices = listOf(
        CloudVoice("3d253570-d3bc-487c-a3ed-b8d7669b3ff0", "Meera", "Bright Companion · दोस्ताना, नटखट और सजीव बातचीत", female = true, hindi = true),
        CloudVoice("ffa0d297-8cf9-4ec9-8e4e-99a59e939b02", "Ananya", "Paced Helper · कोमल, धीमी और स्पष्ट हिन्दी/बंगाली", female = true, hindi = true),
        CloudVoice("256a42a0-47b8-4034-8461-5509a24bb887", "Sarah", "Multilingual Female · Natural", female = true, hindi = false),
        CloudVoice("694f120f-baa9-4482-a42e-13c5443e03d4", "Madame Secretary", "Professional Female", female = true, hindi = false),
    )

    /** Cartesia Sonic 3 emotions that suit a friendly assistant. "" = natural (no emotion hint). */
    val cartesiaMoods = listOf(
        "" to "Natural (सामान्य)",
        "affectionate" to "Pyaar bhari (स्नेही)",
        "content" to "Khush (प्रसन्न)",
        "calm" to "Shaant (शांत)",
        "happy" to "Excited (उत्साहित)",
    )

    /**
     * The emotion sent to Cartesia for a saved mood, or null for none. Older versions of the mood buttons
     * saved names Cartesia does not know ("natural", "pyaar_bhari", "khush", "shaant"); those are mapped,
     * and anything else unknown is not sent at all.
     */
    fun cartesiaEmotion(mood: String?): String? = when (val m = mood?.trim()?.lowercase().orEmpty()) {
        "", "natural", "normal" -> null
        "pyaar_bhari" -> "affectionate"
        "khush" -> "content"
        "shaant" -> "calm"
        else -> m.takeIf { v -> cartesiaMoods.any { it.first == v } }
    }

    fun mergeCartesiaVoices(available: List<CloudVoice>): List<CloudVoice> {
        if (available.isEmpty()) return defaultCartesiaVoices
        val ids = available.map { it.id }.toSet()
        val extra = defaultCartesiaVoices.filter { it.id !in ids }
        return sortVoices(available + extra)
    }

    // ---------------------------------------------------------------- Sarvam AI
    val sarvamSpeakers = listOf(
        SarvamSpeakerOption("roopa", "Roopa (रूपा)", "★ सुकून भरी, कोमल और सजीव बातचीत (Companion - पसंदीदा)", recommended = true),
        SarvamSpeakerOption("suman", "Suman (सुमन)", "★ मीठी, सांसों की नज़ाकत वाली आवाज़ (Companion - पसंदीदा)", recommended = true),
        SarvamSpeakerOption("simran", "Simran (सिमरन)", "चुलबुली, युवा और दोस्ताना बातचीत (Companion)", recommended = true),
        SarvamSpeakerOption("sanchita", "Sanchita (संचिता)", "रोमांटिक, धीमी और कोमल आवाज़ (Storytelling)"),
        SarvamSpeakerOption("priya", "Priya (प्रिया)", "मधुर और स्वाभाविक हिन्दी"),
        SarvamSpeakerOption("kavya", "Kavya (काव्या)", "दोस्ताना और स्पष्ट बातचीत"),
        SarvamSpeakerOption("ritu", "Ritu (रितु)", "शांत और कोमल आवाज़"),
        SarvamSpeakerOption("neha", "Neha (नेहा)", "ब्राइट और स्पष्ट टोन"),
        SarvamSpeakerOption("ishita", "Ishita (इशिता)", "कोमल और शांत बातचीत"),
        SarvamSpeakerOption("shreya", "Shreya (श्रेया)", "मेलोडिक और भावुक"),
        SarvamSpeakerOption("pooja", "Pooja (पूजा)", "घरेलू और परिचित लहज़ा"),
    )

    // ---------------------------------------------------------------- Deepgram
    const val DEEPGRAM_DEFAULT_VOICE = "aura-2-thalia-en"
    /** Deepgram Aura-2 female voices. Aura has no Hindi voice, so these read English only. */
    val deepgramVoices = listOf(
        VoiceModelOption("aura-2-thalia-en", "Thalia", "साफ़, गर्मजोशी भरी", recommended = true),
        VoiceModelOption("aura-2-andromeda-en", "Andromeda", "नरम, सहज", recommended = true),
        VoiceModelOption("aura-2-helena-en", "Helena", "देखभाल करने वाली, मधुर"),
        VoiceModelOption("aura-2-asteria-en", "Asteria", "स्पष्ट, भरोसेमंद"),
        VoiceModelOption("aura-2-luna-en", "Luna", "हल्की, दोस्ताना"),
        VoiceModelOption("aura-2-cora-en", "Cora", "शांत"),
        VoiceModelOption("aura-2-iris-en", "Iris", "खुशमिज़ाज"),
        VoiceModelOption("aura-2-aurora-en", "Aurora", "ऊर्जावान"),
        VoiceModelOption("aura-2-delia-en", "Delia", "मीठी, बातचीत वाली"),
        VoiceModelOption("aura-2-harmonia-en", "Harmonia", "सौम्य"),
    )

    const val DEEPGRAM_DEFAULT_STT = "nova-3:multi"
    val deepgramSttModels = listOf(
        SttModelOption("nova-3:multi", "Nova-3 Hinglish", "Hindi + English मिलाकर बोलें तो सबसे अच्छा", recommended = true),
        SttModelOption("nova-3:hi", "Nova-3 Hindi", "सिर्फ़ Hindi बोलते हों तो"),
        SttModelOption("nova-2:hi", "Nova-2 Hindi", "पुराना model, backup के लिए"),
        SttModelOption("nova-3:en", "Nova-3 English", "सिर्फ़ English"),
    )

    fun sttOption(id: String): SttModelOption = deepgramSttModels.firstOrNull { it.id == id } ?: deepgramSttModels.first()

    /** Female voices first, Hindi-capable ones before others, voices that work on every plan before paid-only ones, then by name. */
    fun sortVoices(voices: List<CloudVoice>): List<CloudVoice> =
        voices.sortedWith(
            compareByDescending<CloudVoice> { it.female && it.hindi }
                .thenByDescending { it.female }
                .thenBy { it.paidOnly }
                .thenByDescending { it.hindi }
                .thenBy { it.name.lowercase() },
        )

    /** The voice to use when the user has not picked one yet: one that works on every plan. */
    fun bestDefault(voices: List<CloudVoice>): CloudVoice? = sortVoices(voices.filter { !it.paidOnly }).firstOrNull()

    /** ElevenLabs refuses Voice Library voices on the free plan with HTTP 402 "payment_required". */
    fun isPaidVoiceError(message: String): Boolean =
        message.contains("402") || message.contains("payment_required", ignoreCase = true) || message.contains("library voices", ignoreCase = true)
}
