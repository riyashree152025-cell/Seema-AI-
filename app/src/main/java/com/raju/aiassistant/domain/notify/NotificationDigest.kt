package com.raju.aiassistant.domain.notify

/**
 * The messages that came in during the last hours, kept only in memory (never written to disk, gone
 * when the app closes) and only for the apps the user allowed for announcements. Used for
 * "notification ka saar batao".
 */
class RecentNotifications(private val max: Int = 80, private val maxAgeMs: Long = 6 * 60 * 60 * 1000L) {
    private val items = ArrayDeque<Pair<Long, IncomingItem>>()

    @Synchronized
    fun add(item: IncomingItem, now: Long) {
        // The same notification updated (a chat's second message re-posts the first): keep the newest.
        items.removeAll { it.second.key == item.key && it.second.preview == item.preview }
        items.addLast(now to item)
        while (items.size > max) items.removeFirst()
    }

    @Synchronized
    fun since(now: Long): List<IncomingItem> = items.filter { now - it.first <= maxAgeMs }.map { it.second }

    @Synchronized
    fun clear() = items.clear()
}

private val CODE = Regex("\\d{4,}")
private val SECRET_WORDS = Regex("otp|one time|password|pin\\b|cvv|verification code|ओटीपी|पासवर्ड", RegexOption.IGNORE_CASE)

/** A preview that is safe to say out loud: one-time codes and passwords are never spoken. */
fun speakablePreview(preview: String?): String? {
    val p = preview?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (SECRET_WORDS.containsMatchIn(p) || CODE.containsMatchIn(p)) return null
    return if (p.length > 90) p.take(90).trimEnd() + "…" else p
}

/**
 * "WhatsApp पर 5 संदेश: Rahul 3, Mummy 2। Messages पर 1। सबसे नया, Rahul: kal milte hain."
 * Counts per app and person, then the newest two messages that are safe to read. Calls are counted apart.
 */
fun notificationDigest(items: List<IncomingItem>, hindi: Boolean = true): String {
    if (items.isEmpty()) return if (hindi) "पिछले कुछ घंटों में कोई नया संदेश नहीं आया।" else "গত কয়েক ঘণ্টায় কোনো নতুন বার্তা আসেনি।"
    val messages = items.filter { !it.isCall }
    val calls = items.count { it.isCall }
    val parts = mutableListOf<String>()
    messages.groupBy { it.appLabel }.entries.sortedByDescending { it.value.size }.forEach { (app, list) ->
        val people = list.groupBy { it.group ?: it.sender ?: "" }.filterKeys { it.isNotBlank() }
            .entries.sortedByDescending { it.value.size }.take(3).joinToString(", ") { "${it.key} ${it.value.size}" }
        parts += if (hindi) "$app पर ${list.size} संदेश" + (if (people.isNotEmpty()) ": $people" else "")
        else "$app-এ ${list.size}টি বার্তা" + (if (people.isNotEmpty()) ": $people" else "")
    }
    if (calls > 0) parts += if (hindi) "$calls कॉल" else "$calls কল"
    val newest = messages.asReversed().mapNotNull { m -> speakablePreview(m.preview)?.let { (m.sender ?: m.group ?: m.appLabel) to it } }.take(2)
    val tail = newest.mapIndexed { i, (who, text) ->
        if (hindi) (if (i == 0) "सबसे नया, " else "उससे पहले, ") + "$who: $text" else "$who: $text"
    }
    return (parts + tail).joinToString("। ") + "।"
}
