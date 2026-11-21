package com.raju.aiassistant.domain.voice

/**
 * "Only my voice": Raju records a few seconds once ("meri awaaz pehchano"); afterwards a wake-up ("Hey Seema") whose voice does not
 * match is ignored. A voice print (numbers only, never audio) stays on the phone; "awaaz pehchanna band karo" deletes it.
 * Matching is by cosine similarity of speaker embeddings; it is right most of the time, not always — so it can be switched off.
 */
object VoiceId {
    /** Same speaker at or above this (CAM++ embeddings, short phrases). */
    const val THRESHOLD = 0.45f
    /** Shorter audio than this cannot be judged: allowed through. */
    const val MIN_SAMPLES = 16_000 // 1 s at 16 kHz
    const val ENROLL_SECONDS = 6

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0.0 || nb == 0.0) 0f else (dot / (Math.sqrt(na) * Math.sqrt(nb))).toFloat()
    }

    /** true = his voice, false = someone else, null = cannot tell (no voice print, too short): treated as his. */
    fun isOwner(owner: FloatArray?, heard: FloatArray?): Boolean? =
        if (owner == null || heard == null) null else cosine(owner, heard) >= THRESHOLD

    private val ENROLL = Regex(
        "^(?:seema[, ]+)?(?:meri|मेरी|আমার)\\s+(?:awaaz|aawaz|awaz|आवाज़|आवाज|গলা|গলার স্বর|কণ্ঠ)\\s+(?:pehchano|pahchano|pehchan lo|yaad rakho|seekh lo|पहचानो|पहचान लो|याद रखो|सीख लो|চিনে রাখো|মনে রাখো)[।.!]*$",
        RegexOption.IGNORE_CASE,
    )
    private val DISABLE = Regex(
        "^(?:seema[, ]+)?(?:awaaz|aawaz|awaz|आवाज़|आवाज|গলা)\\s+(?:pehchanna|pahchanna|पहचानना|চেনা)\\s+(?:band karo|band kar do|बंद करो|बंद कर दो|বন্ধ করো)[।.!]*$",
        RegexOption.IGNORE_CASE,
    )

    /** true: enroll, false: switch off, null: not about it. */
    fun command(text: String): Boolean? = when {
        ENROLL.matches(text.trim()) -> true
        DISABLE.matches(text.trim()) -> false
        else -> null
    }

    fun encode(v: FloatArray): String = v.joinToString(",") { "%.6f".format(java.util.Locale.US, it) }
    fun decode(s: String?): FloatArray? = s?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }?.toFloatArray()?.takeIf { it.isNotEmpty() }
}

/** The phone's speaker check (sherpa-onnx); [NoVoiceId] when unavailable. */
interface VoiceIdPort {
    suspend fun ensureModel(): Boolean
    suspend fun enroll(): Boolean
    fun disable()
    /** true = his voice, false = not, null = cannot tell. */
    fun matchesOwner(pcm: ByteArray): Boolean?
}

object NoVoiceId : VoiceIdPort {
    override suspend fun ensureModel() = false
    override suspend fun enroll() = false
    override fun disable() {}
    override fun matchesOwner(pcm: ByteArray): Boolean? = null
}
