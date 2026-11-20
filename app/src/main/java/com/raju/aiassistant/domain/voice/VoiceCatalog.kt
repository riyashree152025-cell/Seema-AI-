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
) {
    val label: String get() = name + (if (hindi) " · Hindi" else "") + (if (detail.isNotBlank()) " — $detail" else "")
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
        "" to "Warm (default, स्नेही)",
        "natural" to "Natural (सामान्य)",
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
        // No choice made: her warm, affectionate voice.
        "", "affectionate" -> "affectionate"
        "natural", "normal" -> null
        "pyaar_bhari" -> "affectionate"
        "khush" -> "content"
        "shaant" -> "calm"
        else -> m.takeIf { v -> cartesiaMoods.any { it.first == v } }
    }

    /**
     * The voices to offer: the account's own list when Cartesia gave one (a built-in id that is not in it
     * no longer exists for this account and would only answer 404), the built-in list only when none came.
     */
    fun mergeCartesiaVoices(available: List<CloudVoice>): List<CloudVoice> =
        if (available.isEmpty()) defaultCartesiaVoices else sortVoices(available)

    /** Cartesia's "The requested voice was not found" (HTTP 404): the saved voice id is gone. */
    fun isMissingVoice(message: String): Boolean =
        message.contains("404") && (message.contains("voice", ignoreCase = true) || message.contains("not found", ignoreCase = true))

    /** A replacement for a voice id that no longer exists: the best voice of the account's own list, never [missing] again. */
    fun replacementVoice(available: List<CloudVoice>, missing: String): CloudVoice? =
        bestDefault(available.filter { it.id != missing })

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

    // ---------------------------------------------------------------- Deepgram (listening)
    const val DEEPGRAM_DEFAULT_STT = "nova-3:hi"
    val deepgramSttModels = listOf(
        // Hindi first: on the phone "multi" wrote Hindi speech as Spanish or in Telugu letters.
        SttModelOption("nova-3:hi", "Nova-3 Hindi", "Hindi (बीच में English शब्द भी चलेंगे) — सबसे भरोसेमंद", recommended = true),
        SttModelOption("nova-3:multi", "Nova-3 Hinglish (multi)", "कई भाषाएँ — कभी-कभी गलत भाषा पकड़ लेता है"),
        SttModelOption("nova-2:hi", "Nova-2 Hindi", "पुराना model, backup के लिए"),
        SttModelOption("nova-3:en", "Nova-3 English", "सिर्फ़ English"),
    )

    fun sttOption(id: String): SttModelOption = deepgramSttModels.firstOrNull { it.id == id } ?: deepgramSttModels.first()

    /** Female voices first, Hindi-capable ones before others, then by name. */
    fun sortVoices(voices: List<CloudVoice>): List<CloudVoice> =
        voices.sortedWith(
            compareByDescending<CloudVoice> { it.female && it.hindi }
                .thenByDescending { it.female }
                .thenByDescending { it.hindi }
                .thenBy { it.name.lowercase() },
        )

    /** The voice to use when the user has not picked one yet: one that works on every plan. */
    fun bestDefault(voices: List<CloudVoice>): CloudVoice? = sortVoices(voices).firstOrNull()
}
