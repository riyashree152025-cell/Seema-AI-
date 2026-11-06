package com.raju.aiassistant.domain.notify

/** Raw fields copied out of a posted notification (no Android types, so parsing is unit-testable). */
data class NotificationFields(
    val packageName: String,
    val key: String,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val conversationTitle: String? = null,
    val isGroupConversation: Boolean = false,
    /** MessagingStyle messages, oldest first: (sender name, text). */
    val messages: List<Pair<String?, String?>> = emptyList(),
    /** Notification.category, e.g. "msg" or "call". */
    val category: String? = null,
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
)

/** A message or call worth announcing. [preview] is null when the notification hides the content. */
data class IncomingItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val sender: String?,
    val group: String?,
    val preview: String?,
    val isCall: Boolean = false,
    val count: Int = 1,
) {
    /** Same app + same person/group → merged into one announcement. */
    val threadId: String get() = "$packageName|${group.orEmpty()}|${sender.orEmpty()}"
}

private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
private val HIDDEN_PREVIEW = Regex(
    "^(\\d+\\s+)?new messages?$|contents? hidden|message hidden|^new message$|checking for new messages|" +
        "^\\d+\\s+messages?\\s+from\\s+\\d+\\s+chats?$|^you have \\d+ new|^sensitive notification content hidden$|" +
        "^(\\d+\\s+)?(नया|नए)\\s+संदेश(ों)?$|संदेश\\s+छिपा|सामग्री\\s+छिपी|^\\d+\\s+संदेश(ों)?$",
    RegexOption.IGNORE_CASE,
)

private val INCOMING_CALL = Regex("incoming|इनकमिंग|आने वाली|आ रही|ইনকামিং|আসছে", RegexOption.IGNORE_CASE)

private fun clean(s: String?): String? = s?.replace(INVISIBLE, "")?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Extracts who sent what. Uses the latest MessagingStyle message when present (so a group's name
 * isn't mistaken for the sender), falls back to title/text, and treats placeholder text such as
 * "2 new messages" as a hidden preview. Returns null for summaries and non-call ongoing notifications.
 */
fun parseNotification(f: NotificationFields, appLabel: String): IncomingItem? {
    if (f.isGroupSummary) return null
    val isCall = f.category.equals("call", ignoreCase = true)
    if (f.isOngoing && !isCall) return null
    // Only a call that is coming in: the user's own WhatsApp call ("Calling…", "Ringing…",
    // "Ongoing voice call") also has the "call" category and must not be announced as incoming.
    if (isCall && !INCOMING_CALL.containsMatchIn(listOfNotNull(f.title, f.text, f.bigText).joinToString(" "))) return null

    val conversation = clean(f.conversationTitle)
    val latest = f.messages.lastOrNull { !clean(it.second).isNullOrEmpty() }
    var sender = clean(latest?.first) ?: clean(f.title)
    var preview = clean(latest?.second) ?: clean(f.bigText) ?: clean(f.text)
    var group = if (f.isGroupConversation) conversation else null

    // Older WhatsApp style for groups: title "Family: Mom" or "Mom @ Family".
    if (group == null && latest == null) {
        val t = clean(f.title)
        if (t != null) {
            Regex("^(.+?):\\s+(.+)$").matchEntire(t)?.let { group = it.groupValues[1]; sender = it.groupValues[2] }
                ?: Regex("^(.+?)\\s+@\\s+(.+)$").matchEntire(t)?.let { sender = it.groupValues[1]; group = it.groupValues[2] }
        }
    }
    if (group != null && sender == group) sender = null
    // "WhatsApp" as the title of WhatsApp's own notice ("WhatsApp par WhatsApp ka message"): no sender.
    if (sender != null && isAppName(sender, appLabel, f.packageName)) sender = null
    if (group != null && isAppName(group, appLabel, f.packageName)) group = null
    if (preview != null && HIDDEN_PREVIEW.containsMatchIn(preview)) preview = null
    if (sender == null && preview == null && group == null) return null

    return IncomingItem(f.key, f.packageName, appLabel, sender, group, if (isCall) null else preview, isCall)
}

private fun isAppName(name: String, appLabel: String, packageName: String): Boolean {
    val n = name.lowercase().filter { it.isLetterOrDigit() }
    return n.isNotEmpty() && (n == appLabel.lowercase().filter { it.isLetterOrDigit() } || n == packageName.lowercase().substringAfterLast('.'))
}

/** A sender that is only a phone number is not read out digit by digit. */
private fun isJustANumber(s: String): Boolean = s.count { it.isDigit() } >= 7 && s.none { it.isLetter() }

/** "Mom", "Mom (Family group)", "ek anjaan number" or "kisi" when unknown. */
fun whoText(item: IncomingItem): String {
    val sender = item.sender?.let { if (isJustANumber(it)) "ek anjaan number" else it }
    return when {
        sender != null && item.group != null -> "$sender (${item.group} group)"
        sender != null -> sender
        item.group != null -> "${item.group} group"
        else -> "kisi"
    }
}

/** "Raju, " — how Seema addresses the user at the start of an announcement ("" without a name). */
fun nickPrefix(nickname: String?): String = nickname?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it, " } ?: ""

/** Spoken first, before any content: only who sent it, then asks permission to read. No app name, no text. */
fun askToReadText(item: IncomingItem, nickname: String? = null): String =
    if (item.count > 1) "${nickPrefix(nickname)}${whoText(item)} ke ${item.count} notification aaye hain. Padh ke sunau kya?"
    else "${nickPrefix(nickname)}${whoText(item)} ka notification aaya hai. Padh ke sunau kya?"

/** When the notification hides the content, only the source can be announced. */
fun hiddenPreviewText(item: IncomingItem, nickname: String? = null): String =
    "${nickPrefix(nickname)}${whoText(item)} ka notification aaya hai. Preview chhupa hua hai, isliye main use padh nahi sakti."

/** Read only after the user said yes. Long previews are shortened; links are not read out. */
fun readPreviewText(item: IncomingItem): String {
    val p = item.preview.orEmpty()
        .replace(Regex("https?://\\S+"), "link")
        .let { if (it.length > 300) it.take(300) + "…" else it }
    return "${whoText(item).replaceFirstChar { it.uppercase() }} ne likha: $p"
}

/** Best-effort announcement for app (VoIP) calls taken from the call notification. */
fun voipCallText(item: IncomingItem, nickname: String? = null): String =
    "${nickPrefix(nickname)}${whoText(item)} ka ${item.appLabel} call aa raha hai."

/** The user's spoken answer to "Padh ke sunau kya?": true = read, false = no, null = not an answer. */
fun readReply(heard: String): Boolean? {
    val t = heard.trim()
    if (t.isEmpty()) return null
    return when {
        READ_NO.containsMatchIn(t) -> false
        READ_YES.containsMatchIn(t) -> true
        else -> null
    }
}

// "No" first: "nahi, mat sunao" must never read the message.
private val READ_NO = Regex(
    "\\b(nahi|nahin|nai|no|mat|rehne do|rahne do|chup|chhodo|chodo|baad me|baad mein|band karo|skip|cancel)\\b|नहीं|नही|मत|रहने दो|चुप|छोड़ो|बाद में|না|থাক|পরে",
    RegexOption.IGNORE_CASE,
)
private val READ_YES = Regex(
    "\\b(haan|han|ha|haa|haanji|ji|yes|ok|okay|suno|sunao|suna do|padho|padh do|padhkar|padh ke|bolo|batao|read)\\b|हाँ|हां|सुनाओ|सुना दो|सुनो|पढ़ो|पढ़ दो|पढ़|बोलो|बताओ|হ্যাঁ|পড়ো|শোনাও",
    RegexOption.IGNORE_CASE,
)
