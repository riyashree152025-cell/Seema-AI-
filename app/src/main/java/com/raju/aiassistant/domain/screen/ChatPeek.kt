package com.raju.aiassistant.domain.screen

/**
 * "आनंद ने क्या भेजा?": counts what a WhatsApp chat shows on screen — photos, documents, voice notes,
 * videos — and names an ID card only when the file name or a caption says so ("Aadhaar.pdf", "आधार").
 * It never reads what a document or photo says, and it never sends anything to an AI: the words on
 * screen are only compared, not kept. It sees what is visible now, so the answer says "दिख रहे".
 */
object ChatPeek {
    enum class Kind { PHOTO, DOCUMENT, VOICE, VIDEO }

    data class Item(val kind: Kind, val label: String?)

    private val ID_CARDS = listOf(
        Regex("(?<!\\p{L})(aadhaar|aadhar|adhar)(?!\\p{L})|आधार", RegexOption.IGNORE_CASE) to "आधार कार्ड",
        Regex("(?<!\\p{L})pan(?!\\p{L})|पैन", RegexOption.IGNORE_CASE) to "पैन कार्ड",
        Regex("(?<!\\p{L})passport(?!\\p{L})|पासपोर्ट", RegexOption.IGNORE_CASE) to "पासपोर्ट",
        Regex("(?<!\\p{L})ticket(?!\\p{L})|टिकट", RegexOption.IGNORE_CASE) to "टिकट",
        Regex("(?<!\\p{L})marksheet(?!\\p{L})|मार्कशीट", RegexOption.IGNORE_CASE) to "मार्कशीट",
    )
    private val PHOTO = Regex("(?<!\\p{L})photo(?!\\p{L})|(?<!\\p{L})image(?!\\p{L})|फ़ोटो|फोटो|तस्वीर|(?<!\\p{L})IMG[_-]|\\.(jpe?g|png|webp|heic)(?!\\p{L})", RegexOption.IGNORE_CASE)
    private val VIDEO = Regex("(?<!\\p{L})video(?!\\p{L})|वीडियो|\\.(mp4|3gp|mkv)(?!\\p{L})", RegexOption.IGNORE_CASE)
    private val VOICE = Regex("voice (message|note)|वॉइस मैसेज|वॉयस मैसेज|वॉइस नोट|वॉयस नोट", RegexOption.IGNORE_CASE)
    private val DOCUMENT = Regex("(?<!\\p{L})document(?!\\p{L})|डॉक्यूमेंट|\\.(pdf|docx?|xlsx?|txt|zip)(?!\\p{L})", RegexOption.IGNORE_CASE)

    /** What one on-screen element is, if it is an attachment at all. */
    fun classify(e: ScreenElement): Item? {
        val text = e.text.trim()
        if (text.isEmpty()) return null
        val kind = when {
            VOICE.containsMatchIn(text) || e.id.contains("voice_note", ignoreCase = true) -> Kind.VOICE
            VIDEO.containsMatchIn(text) -> Kind.VIDEO
            PHOTO.containsMatchIn(text) -> Kind.PHOTO
            DOCUMENT.containsMatchIn(text) || e.id.contains("document", ignoreCase = true) -> Kind.DOCUMENT
            else -> return null
        }
        val label = ID_CARDS.firstOrNull { (re, _) -> re.containsMatchIn(text) }?.second
        return Item(kind, label)
    }

    /** The Hindi sentence for [name]'s chat, from the elements on screen. */
    fun summarize(name: String, elements: List<ScreenElement>): String {
        val items = elements.mapNotNull { classify(it) }
        if (items.isEmpty()) return "\"$name\" की चैट में अभी जो दिख रहा है उसमें कोई फ़ोटो, फ़ाइल या वॉइस मैसेज नहीं है।"
        val parts = mutableListOf<String>()
        // ID cards first, by their name: "2 आधार कार्ड".
        items.filter { it.label != null }.groupingBy { it.label!! }.eachCount().forEach { (label, n) -> parts += "$n $label" }
        val rest = items.filter { it.label == null }.groupingBy { it.kind }.eachCount()
        rest[Kind.PHOTO]?.let { parts += "$it फ़ोटो" }
        rest[Kind.DOCUMENT]?.let { parts += "$it डॉक्यूमेंट" }
        rest[Kind.VOICE]?.let { parts += "$it वॉइस मैसेज" }
        rest[Kind.VIDEO]?.let { parts += "$it वीडियो" }
        val unnamedPhotos = items.count { it.kind == Kind.PHOTO && it.label == null }
        val tail = if (unnamedPhotos > 0 && items.none { it.label != null }) " (नाम नहीं दिख रहा, इसलिए फ़ोटो में क्या है यह नहीं बता सकती)" else ""
        return "\"$name\" की चैट में अभी दिख रहा है: ${parts.joinToString(", ")}$tail।"
    }
}
