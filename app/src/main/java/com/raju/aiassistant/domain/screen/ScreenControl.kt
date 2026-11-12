package com.raju.aiassistant.domain.screen

/**
 * What Seema's Accessibility service sees of the app in front: its visible texts, buttons, input
 * boxes and lists. Plain data, so matching, safety checks and verification are tested without a phone.
 */
enum class ElementKind { BUTTON, INPUT, TEXT, LIST, IMAGE }

data class ScreenElement(
    /** Visible text, or the content description for icons ("Send", "Voice call"). Empty for a password box. */
    val text: String,
    val kind: ElementKind,
    /** The element (or a parent) reacts to a tap. */
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    /** A password box: its content is never read, typed into or sent to the AI. */
    val password: Boolean = false,
    /** Hint of an input box ("Type a message"). */
    val hint: String = "",
    /** The app's own id for the view ("com.whatsapp:id/send"), empty when the app gives none. */
    val id: String = "",
)

data class ScreenSnapshot(
    val packageName: String,
    val appLabel: String,
    val elements: List<ScreenElement>,
) {
    /** Changes whenever the screen really changes (another page, a sent message, a scrolled list). */
    val fingerprint: Int
        get() = (packageName + "\u0001" + elements.joinToString("\u0002") { "${it.kind}:${it.text}:${it.hint}" }).hashCode()
}

sealed interface ScreenRead {
    data class Ok(val snapshot: ScreenSnapshot) : ScreenRead
    data class Unavailable(val reason: String) : ScreenRead
}

/** One step Seema may do on the screen — only after the user said yes. */
sealed interface ScreenOp {
    data class Tap(val target: String) : ScreenOp
    data object Back : ScreenOp
    data class Scroll(val down: Boolean) : ScreenOp
    data class Type(val text: String, val field: String?) : ScreenOp
}

/** Banking, UPI and password screens: never read, tapped or typed into. */
object SensitiveScreens {
    private val FINANCIAL_KEYWORDS = listOf(
        "paisa", "phonepe", "paytm", "bhim", "gpay", "google pay", "nbu.paisa", "upi",
        "yono", "sbi", "bank", "banking", "wallet", "cred", "hdfc",
        "icici", "axis mobile", "axismobile", "kotak", "canara", "union bank",
        "pnb one", "bob world", "mobikwik", "freecharge", "zerodha", "groww", "amazon pay", "navi",
    )

    /** Text on a screen that asks for a secret: PIN, OTP, CVV, password, card details. */
    private val SECRET_TEXT = Regex(
        "\\b(upi pin|enter pin|mpin|m-pin|atm pin|cvv|otp|one[ -]time password|password|passcode|card number|net ?banking)\\b|पासवर्ड|पिन डाल|ओटीपी|यूपीआई पिन|পাসওয়ার্ড|পিন",
        RegexOption.IGNORE_CASE,
    )

    fun isFinancialApp(packageName: String, label: String): Boolean {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        // Short, ambiguous words only as a whole word or package part: "Navigation" or "credits" must not count.
        return FINANCIAL_KEYWORDS.any { k ->
            if (k in WHOLE_WORD_ONLY) {
                val r = Regex("(^|[^a-z])${Regex.escape(k)}([^a-z]|$)")
                r.containsMatchIn(pkg) || r.containsMatchIn(name)
            } else pkg.contains(k) || name.contains(k)
        }
    }

    private val WHOLE_WORD_ONLY = setOf("upi", "navi", "sbi", "cred")

    /** Why this screen is off limits (spoken to the user), or null when Seema may work on it. */
    fun reason(s: ScreenSnapshot): String? = when {
        isFinancialApp(s.packageName, s.appLabel) -> "बैंक / UPI / पेमेंट ऐप"
        s.elements.any { it.password } -> "पासवर्ड वाली स्क्रीन"
        s.elements.any { SECRET_TEXT.containsMatchIn(it.text) || SECRET_TEXT.containsMatchIn(it.hint) } -> "PIN / OTP / पासवर्ड वाली स्क्रीन"
        else -> null
    }
}

/** Buttons whose tap cannot be undone easily: the confirmation then carries a warning. */
object RiskyButtons {
    private val RISKY = Regex(
        "\\b(delete|remove|block|report|pay|buy|purchase|uninstall|reset|erase|format|log ?out|sign ?out|clear|unsubscribe|exit group|leave)\\b" +
            "|हटा|डिलीट|ब्लॉक|भुगतान|खरीद|मिटा|মুছ|ব্লক",
        RegexOption.IGNORE_CASE,
    )

    fun isRisky(label: String): Boolean = RISKY.containsMatchIn(label)
}

/** Finds the element the user means ("Send", "भेजें", "search", "video call"). */
object ScreenMatch {
    /** Same button in English, Hindi and Bengali. */
    private val SYNONYMS: List<Set<String>> = listOf(
        setOf("send", "भेजें", "भेजो", "भेज", "পাঠান", "পাঠাও"),
        setOf("search", "खोजें", "खोजो", "सर्च", "খুঁজুন"),
        setOf("back", "navigate up", "पीछे", "वापस", "পিছনে"),
        setOf("ok", "okay", "ठीक है", "ओके", "ঠিক আছে"),
        setOf("cancel", "रद्द करें", "कैंसल", "বাতিল"),
        setOf("next", "आगे", "अगला", "পরবর্তী"),
        setOf("done", "हो गया", "पूर्ण", "সম্পন্ন"),
        setOf("allow", "अनुमति दें", "অনুমতি দিন"),
        setOf("call", "voice call", "audio call", "कॉल", "वॉइस कॉल", "কল", "ভয়েস কল"),
        setOf("video call", "वीडियो कॉल", "ভিডিও কল"),
        setOf("attach", "attachment", "अटैच", "সংযুক্ত"),
        setOf("camera", "कैमरा", "ক্যামেরা"),
        setOf("more options", "menu", "मेनू", "मेन्यू", "মেনু"),
        setOf("play", "चलाओ", "प्ले", "চালাও"),
        setOf("pause", "रोको", "पॉज़", "থামাও"),
        setOf("like", "लाइक", "লাইক"),
        setOf("subscribe", "सब्सक्राइब", "সাবস্ক্রাইব"),
    )

    fun normalize(s: String): String = s.lowercase()
        .replace(Regex("[\\p{P}\\p{S}&&[^#+]]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun variants(target: String): Set<String> {
        val t = normalize(target)
        val group = SYNONYMS.firstOrNull { g -> g.any { normalize(it) == t } }
        return (group?.map { normalize(it) }?.toSet() ?: emptySet()) + t
    }

    private fun score(label: String, wanted: Set<String>): Int {
        val l = normalize(label)
        if (l.isEmpty()) return 0
        return wanted.maxOf { w ->
            when {
                w.isEmpty() -> 0
                l == w -> 3
                l.startsWith("$w ") || l.startsWith(w) && w.length >= 4 -> 2
                Regex("(^|\\s)${Regex.escape(w)}(\\s|$)").containsMatchIn(l) -> 1
                else -> 0
            }
        }
    }

    /**
     * The best matching tappable elements, best first. More than one with different texts means the
     * user must say which one; nothing found means the button is not on this screen.
     */
    fun candidates(s: ScreenSnapshot, target: String): List<ScreenElement> {
        val wanted = variants(target)
        val scored = s.elements
            .filter { !it.password && (it.clickable || it.editable) }
            .map { it to maxOf(score(it.text, wanted), score(it.hint, wanted)) }
            .filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second } ?: return emptyList()
        return scored.filter { it.second == best }.map { it.first }.distinctBy { normalize(it.text) to it.kind }
    }

    /** The input box to type into: the named one, else the only (or first) editable box. */
    fun inputFor(s: ScreenSnapshot, field: String?): ScreenElement? {
        val inputs = s.elements.filter { it.editable && !it.password }
        if (inputs.isEmpty()) return null
        if (field.isNullOrBlank()) return inputs.first()
        val wanted = variants(field)
        return inputs.maxByOrNull { maxOf(score(it.text, wanted), score(it.hint, wanted)) }
            ?.takeIf { maxOf(score(it.text, wanted), score(it.hint, wanted)) > 0 } ?: inputs.first()
    }

    /** The typed text is visible in an input box now (the screen accepted it). */
    fun showsTyped(s: ScreenSnapshot, typed: String): Boolean {
        val t = normalize(typed)
        return t.isNotEmpty() && s.elements.any { it.editable && normalize(it.text).contains(t) }
    }
}

/** Card / account numbers (12–19 digits, spaces or dashes allowed) are never read out or sent: only the last 4. */
private val LONG_NUMBER = Regex("\\b(?:\\d[ -]?){8,15}(\\d{4})\\b")

fun maskSecrets(text: String): String = LONG_NUMBER.replace(text) { m ->
    val digits = m.value.count { it.isDigit() }
    if (digits in 12..19) "••••${m.groupValues[1]}" else m.value
}

/** What the AI gets to know about the screen (never a password; long screens are cut). */
fun ScreenSnapshot.describeForAi(maxChars: Int = 2_500): String {
    val sb = StringBuilder("App on screen: $appLabel ($packageName)\n")
    for (e in elements) {
        val text = maskSecrets(e.text)
        val line = when {
            e.password -> "[password box — hidden]"
            e.kind == ElementKind.INPUT -> "[input] " + (text.ifBlank { "(empty)" }) + (if (e.hint.isNotBlank()) " — hint: ${e.hint}" else "")
            e.kind == ElementKind.BUTTON -> "[button] $text"
            e.kind == ElementKind.LIST -> "[scrollable list]"
            e.kind == ElementKind.IMAGE -> "[image] $text"
            else -> text
        }
        if (line.isBlank()) continue
        if (sb.length + line.length + 1 > maxChars) { sb.append("…(more on screen)"); break }
        sb.append(line).append('\n')
    }
    return sb.toString().trimEnd()
}

/**
 * A short spoken summary made on the phone, without any AI (offline, or when the AI fails): the app
 * and its first readable lines in screen order, numbers masked, buttons and empty boxes left out.
 */
fun ScreenSnapshot.localSummary(bengali: Boolean = false, maxLines: Int = 6, maxChars: Int = 260): String {
    val lines = elements.asSequence()
        .filter { !it.password && !it.editable && it.kind != ElementKind.LIST }
        .map { maskSecrets(it.text).trim() }
        .filter { t -> t.length >= 2 && t.any { it.isLetterOrDigit() } && !ScreenMatch.normalize(t).equals(ScreenMatch.normalize(appLabel)) }
        .distinctBy { ScreenMatch.normalize(it) }
        .take(maxLines)
        .toList()
    val head = if (bengali) "স্ক্রিনে $appLabel খোলা।" else "स्क्रीन पर $appLabel खुला है।"
    if (lines.isEmpty()) return head + if (bengali) " পড়ার মতো লেখা নেই।" else " पढ़ने लायक कुछ लिखा नहीं दिख रहा।"
    var body = lines.joinToString("। ")
    if (body.length > maxChars) body = body.take(maxChars).substringBeforeLast(' ') + "…"
    return head + (if (bengali) " লেখা আছে: " else " लिखा है: ") + body
}

/** The user's spoken answer to "ये बटन दबा दूँ?": true = yes, false = no, null = not an answer. */
fun confirmReply(heard: String): Boolean? {
    val t = heard.trim()
    if (t.isEmpty()) return null
    return when {
        CONFIRM_NO.containsMatchIn(t) -> false
        CONFIRM_YES.containsMatchIn(t) -> true
        else -> null
    }
}

// "No" first: "nahi, mat dabao" must never tap.
private val CONFIRM_NO = Regex(
    "\\b(nahi|nahin|nai|no|nope|mat|ruko|ruk jao|rehne do|rahne do|cancel|chhodo|chodo|band karo|stop)\\b|नहीं|नही|मत|रुको|रुक जाओ|रहने दो|छोड़ो|कैंसल|না|থাক|থামো",
    RegexOption.IGNORE_CASE,
)
private val CONFIRM_YES = Regex(
    "\\b(haan|han|ha|haa|haanji|ji haan|yes|yeah|ok|okay|theek hai|thik hai|kar do|kardo|daba do|dabao|bhej do|likh do|chalo|sure|lagao|laga do|lagado|laga dijiye|lagaiye|milao|mila do|call karo|call kar do|phone karo|phone lagao)\\b|हाँ|हां|जी हाँ|ठीक है|कर दो|दबा दो|दबाओ|भेज दो|लिख दो|ओके|लगाओ|लगा दो|लगा दीजिए|लगाइए|मिलाओ|मिला दो|कॉल करो|फ़ोन करो|फोन करो|হ্যাঁ|ঠিক আছে|করো|দাও",
    RegexOption.IGNORE_CASE,
)

/** WhatsApp's own call buttons, matched exactly — "Calls" (the tab) or "Call log" must never match. */
object WhatsAppButtons {
    private val VOICE = setOf("voice call", "audio call", "call", "कॉल", "वॉइस कॉल", "ऑडियो कॉल", "কল", "ভয়েস কল", "অডিও কল")
    private val VIDEO = setOf("video call", "वीडियो कॉल", "ভিডিও কল")

    fun isCallButton(description: String, video: Boolean): Boolean {
        val d = ScreenMatch.normalize(description)
        if (d.isEmpty()) return false
        val set = if (video) VIDEO else VOICE
        return d in set.map { ScreenMatch.normalize(it) }
    }

    /** A WhatsApp confirm dialog that is about starting a call (and nothing else, like "Block?"). */
    fun isCallDialog(texts: List<String>): Boolean {
        val all = texts.joinToString(" ") { ScreenMatch.normalize(it) }
        val aboutCall = Regex("\\b(call|calling)\\b|कॉल|কল").containsMatchIn(all)
        val dangerous = RiskyButtons.isRisky(all)
        return aboutCall && !dangerous
    }
}

/**
 * Whether WhatsApp's message box holds the message the user confirmed, so Send is pressed only in the
 * chat Seema opened with it. Unknown box or no armed text: false (nothing is pressed).
 */
fun whatsAppDraftMatches(boxText: String?, armedText: String?): Boolean {
    val box = boxText?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
    val armed = armedText?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
    if (box.isEmpty() || armed.isEmpty()) return false
    return box == armed
}

