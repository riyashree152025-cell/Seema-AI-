package com.raju.aiassistant.domain.notify

/** A message that can be answered from its notification (the app offered a reply field). */
data class ReplyTarget(val key: String, val who: String, val app: String)

/** "Reply karo ki …": [who] is null for "the newest message". */
data class ReplyCommand(val who: String?, val text: String)

private const val REPLY = "(?:reply|riplai|jawab|javab|answer|रिप्लाई|जवाब|उत्तर)"
private const val DO = "(?:karo|kar do|kar dena|do|de do|de dena|bhejo|bhej do|करो|कर दो|कर देना|दो|दे दो|दे देना|भेजो|भेज दो)"
private val WITH_NAME = Regex("^(.+?) (?:ko|को) $REPLY $DO(?: (?:ki|ke|कि))? (.+)$", RegexOption.IGNORE_CASE)
private val NEWEST = Regex("^(?:(?:isko|isko|use|usko|iska|uska|इसको|उसको|इसका|उसका|is message ka|इस मैसेज का) )?$REPLY $DO(?: (?:ki|ke|कि))? (.+)$", RegexOption.IGNORE_CASE)
private val NOT_A_NAME = setOf("isko", "usko", "use", "iska", "uska", "इसको", "उसको", "sab", "sabko")

/**
 * Reads "reply karo ki main aa raha hoon", "isko jawab do theek hai", "Rahul ko reply karo ki kal milte hain".
 * Null when it is not a reply command or there is nothing to send.
 */
fun parseReplyCommand(text: String): ReplyCommand? {
    val t = text.trim().trimEnd('.', '!', '?', '।').replace(Regex("\\s+"), " ")
    if (t.length > 300) return null
    NEWEST.matchEntire(t)?.let { m -> return m.groupValues[1].trim().takeIf { it.length >= 2 }?.let { ReplyCommand(null, it) } }
    WITH_NAME.matchEntire(t)?.let { m ->
        val who = m.groupValues[1].trim()
        val body = m.groupValues[2].trim()
        if (who.lowercase() in NOT_A_NAME) return body.takeIf { it.length >= 2 }?.let { ReplyCommand(null, it) }
        if (who.split(' ').size > 4 || body.length < 2) return null
        return ReplyCommand(who, body)
    }
    return null
}

/**
 * The message the reply goes to: the newest one, or the newest from [who] (part of the name is enough,
 * case ignored). Null when nothing can be answered or the name fits none. [targets] is newest first.
 */
fun pickReplyTarget(who: String?, targets: List<ReplyTarget>): ReplyTarget? {
    if (targets.isEmpty()) return null
    if (who == null) return targets.first()
    val q = who.lowercase().filter { it.isLetterOrDigit() }
    if (q.isEmpty()) return null
    return targets.firstOrNull { it.who.lowercase().filter { c -> c.isLetterOrDigit() }.contains(q) }
}
