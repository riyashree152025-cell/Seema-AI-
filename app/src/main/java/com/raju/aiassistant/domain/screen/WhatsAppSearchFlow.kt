package com.raju.aiassistant.domain.screen

import kotlinx.coroutines.delay

/**
 * Seema's hands on the phone for a multi-step flow. Every call is bounded in time by the caller
 * (a slow or stuck app gives false / null, never a frozen assistant).
 */
interface ScreenHands {
    /** Brings WhatsApp to the front (its main screen or wherever it was left). */
    suspend fun openWhatsApp(): Boolean
    /** The app in front, or null when it cannot be read right now. */
    suspend fun read(): ScreenSnapshot?
    suspend fun tap(element: ScreenElement): Boolean
    suspend fun type(element: ScreenElement, text: String): Boolean
    suspend fun back(): Boolean
}

enum class FlowOutcome {
    /** Send was pressed and the message left the box. */
    SENT,
    /** The message is typed in the right chat, but Send could not be pressed or did not take. */
    TYPED_NOT_SENT,
    /** No chat or contact with that name in WhatsApp's search. */
    CHAT_NOT_FOUND,
    /** More than one chat matches: the user must say which ([WhatsAppFlowResult.choices]). */
    SEVERAL_CHATS,
    /** The chat that opened is not the one asked for: nothing was typed. */
    WRONG_CHAT,
    /** A payment / PIN screen came up: Seema stopped without touching it. */
    UNSAFE_SCREEN,
    /** WhatsApp did not show what was expected in time (or could not be opened / read). */
    STUCK,
    /** Seema's Accessibility is off or WhatsApp is not installed. */
    UNAVAILABLE,
}

data class WhatsAppFlowResult(
    val outcome: FlowOutcome,
    /** The chat the message went to (as WhatsApp shows its name). */
    val chat: String? = null,
    val choices: List<String> = emptyList(),
    /** Step by step, for the diagnostics log. */
    val log: List<String> = emptyList(),
)

/**
 * "WhatsApp par Family group ko 'main aa raha hoon' bhejo" for a name that is not in the phone's
 * contacts (groups, WhatsApp-only names): open WhatsApp → search → pick the one matching chat →
 * check the chat's title → type → press Send → check the box emptied. It stops at the first thing
 * that does not look right and says why; it never types into a chat it has not confirmed by name.
 */
class WhatsAppSearchFlow(
    private val hands: ScreenHands,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    /** Time the app gets to react between reads. */
    private val settleMs: Long = 500,
    /** Reads per stage before giving up (patience × settleMs ≈ the wait). */
    private val patience: Int = 10,
) {
    private val log = mutableListOf<String>()

    suspend fun send(name: String, text: String): WhatsAppFlowResult {
        log.clear()
        val wanted = ScreenMatch.normalize(name)
        if (wanted.isEmpty() || text.isBlank()) return done(FlowOutcome.STUCK, "nothing to send")
        if (!hands.openWhatsApp()) return done(FlowOutcome.UNAVAILABLE, "WhatsApp did not open")
        step("opened WhatsApp")

        // 1. The search box (tap the search button first; leave a chat WhatsApp reopened).
        var backs = 0
        var searchBox: ScreenElement? = null
        var tappedSearch = false
        for (i in 0 until patience) {
            val s = readSafe()
            if (s == null) { pause(settleMs); continue }
            if (s.isUnsafe()) return done(FlowOutcome.UNSAFE_SCREEN, "unsafe screen while looking for search")
            if (!s.isWhatsApp()) { pause(settleMs); continue }
            searchBox = WhatsAppScreen.searchBox(s)
            if (searchBox != null) break
            val button = WhatsAppScreen.searchButton(s)
            when {
                button != null && !tappedSearch -> { tappedSearch = hands.tap(button); step("tapped search: ${button.text.ifBlank { button.id }}") }
                WhatsAppScreen.chatEntry(s) != null && backs < 2 -> { hands.back(); backs++; step("left an open chat") }
            }
            pause(settleMs)
        }
        val box = searchBox ?: return done(FlowOutcome.STUCK, "WhatsApp search box not found")

        // 2. The name.
        if (!hands.type(box, name)) return done(FlowOutcome.STUCK, "could not type in search")
        step("searched \"$name\"")

        // 3. Exactly one matching result.
        var chosen: ScreenElement? = null
        for (i in 0 until patience) {
            pause(settleMs)
            val s = readSafe() ?: continue
            if (s.isUnsafe()) return done(FlowOutcome.UNSAFE_SCREEN, "unsafe screen in search")
            val matches = WhatsAppScreen.results(s, name)
            val names = matches.map { it.text.trim() }.distinctBy { ScreenMatch.normalize(it) }
            if (names.size > 1) return done(FlowOutcome.SEVERAL_CHATS, "several chats: ${names.joinToString()}", choices = names)
            if (matches.isNotEmpty()) { chosen = matches.first(); break }
        }
        val result = chosen ?: return done(FlowOutcome.CHAT_NOT_FOUND, "no chat named \"$name\"")
        if (!hands.tap(result)) return done(FlowOutcome.STUCK, "could not open the chat")
        step("opened \"${result.text}\"")

        // 4. The chat that opened is the one asked for.
        var entry: ScreenElement? = null
        var sawChat = false
        for (i in 0 until patience) {
            pause(settleMs)
            val s = readSafe() ?: continue
            if (s.isUnsafe()) return done(FlowOutcome.UNSAFE_SCREEN, "unsafe screen in chat")
            val e = WhatsAppScreen.chatEntry(s) ?: continue
            sawChat = true
            val title = WhatsAppScreen.chatTitle(s, result.text) ?: continue
            if (!WhatsAppScreen.sameChat(title, result.text)) {
                return done(FlowOutcome.WRONG_CHAT, "chat title \"$title\" is not \"${result.text}\"")
            }
            entry = e
            break
        }
        val input = entry ?: return done(
            if (sawChat) FlowOutcome.WRONG_CHAT else FlowOutcome.STUCK,
            if (sawChat) "could not confirm the chat's name" else "chat did not open",
        )

        // 5. Type, check it is there, press Send.
        if (!hands.type(input, text)) return done(FlowOutcome.STUCK, "could not type the message", chat = result.text)
        pause(settleMs)
        val typed = readSafe()
        if (typed == null || !ScreenMatch.showsTyped(typed, text.take(40))) return done(FlowOutcome.STUCK, "message not visible in the box", chat = result.text)
        step("typed the message")
        val send = WhatsAppScreen.sendButton(typed) ?: return done(FlowOutcome.TYPED_NOT_SENT, "Send button not found", chat = result.text)
        if (!hands.tap(send)) return done(FlowOutcome.TYPED_NOT_SENT, "Send could not be pressed", chat = result.text)
        step("pressed Send")

        // 6. After a real send WhatsApp empties the box.
        for (i in 0 until patience) {
            pause(settleMs)
            val s = readSafe() ?: continue
            if (!ScreenMatch.showsTyped(s, text.take(40))) return done(FlowOutcome.SENT, "box emptied", chat = result.text)
        }
        return done(FlowOutcome.TYPED_NOT_SENT, "message still in the box after Send", chat = result.text)
    }

    private suspend fun readSafe(): ScreenSnapshot? = runCatching { hands.read() }.getOrNull()

    private fun ScreenSnapshot.isWhatsApp() = packageName in WhatsAppScreen.PACKAGES

    private fun ScreenSnapshot.isUnsafe() = isWhatsApp() && SensitiveScreens.reason(this) != null

    private fun step(what: String) { log += what }

    private fun done(outcome: FlowOutcome, why: String, chat: String? = null, choices: List<String> = emptyList()): WhatsAppFlowResult {
        log += "${outcome.name}: $why"
        return WhatsAppFlowResult(outcome, chat, choices, log.toList())
    }
}

/** How WhatsApp's screens are recognised: its view ids first, its visible words as a fallback. */
object WhatsAppScreen {
    val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    private val SEARCH_IDS = listOf(":id/menuitem_search", ":id/my_search_bar", ":id/search_bar", ":id/search_button")
    private val SEARCH_WORDS = setOf("search", "खोजें", "खोजो", "सर्च", "খুঁজুন", "ask meta ai or search").map { ScreenMatch.normalize(it) }
    private val SEND_WORDS = setOf("send", "भेजें", "भेजो", "পাঠান").map { ScreenMatch.normalize(it) }
    private val HEADERS = setOf("chats", "contacts", "messages", "groups", "चैट", "संपर्क", "मैसेज").map { ScreenMatch.normalize(it) }

    fun searchButton(s: ScreenSnapshot): ScreenElement? =
        s.elements.firstOrNull { it.clickable && !it.editable && SEARCH_IDS.any { id -> it.id.endsWith(id) } }
            ?: s.elements.firstOrNull { it.clickable && !it.editable && ScreenMatch.normalize(it.text) in SEARCH_WORDS }

    /** The search input (never the chat's message box). */
    fun searchBox(s: ScreenSnapshot): ScreenElement? = s.elements.firstOrNull { e ->
        e.editable && !e.password && !isEntry(e) &&
            (e.id.contains("search") || ScreenMatch.normalize(e.hint).let { h -> h.contains("search") || h.contains("खोज") || h.contains("meta ai") })
    }

    fun chatEntry(s: ScreenSnapshot): ScreenElement? = s.elements.firstOrNull { it.editable && isEntry(it) }
        ?: s.elements.firstOrNull { e -> e.editable && e.id.isEmpty() && ScreenMatch.normalize(e.hint).let { it == "message" || it == "type a message" || it == "मैसेज" } }

    private fun isEntry(e: ScreenElement) = e.id.endsWith(":id/entry")

    /**
     * The name at the top of an open chat: WhatsApp's title view, or — when the app gives no ids — a
     * plain text that is exactly the [expected] name.
     */
    fun chatTitle(s: ScreenSnapshot, expected: String): String? =
        s.elements.firstOrNull { it.id.endsWith(":id/conversation_contact_name") && it.text.isNotBlank() }?.text
            ?: s.elements.firstOrNull { !it.editable && it.id.isEmpty() && ScreenMatch.normalize(it.text) == ScreenMatch.normalize(expected) }?.text

    fun sameChat(title: String, chosen: String): Boolean {
        val t = ScreenMatch.normalize(title)
        val c = ScreenMatch.normalize(chosen)
        return t.isNotEmpty() && (t == c || t.startsWith("$c ") || c.startsWith("$t "))
    }

    fun sendButton(s: ScreenSnapshot): ScreenElement? =
        s.elements.firstOrNull { it.clickable && it.id.endsWith(":id/send") }
            ?: s.elements.firstOrNull { it.clickable && !it.editable && ScreenMatch.normalize(it.text) in SEND_WORDS }

    /**
     * Search results for [name]: rows whose name is exactly it; otherwise rows that start with it
     * ("Ramesh" → "Ramesh Kumar"). A message line that merely mentions the name never counts.
     */
    fun results(s: ScreenSnapshot, name: String): List<ScreenElement> {
        val n = ScreenMatch.normalize(name)
        val rows = s.elements.filter { e ->
            e.clickable && !e.editable && !e.password && e.text.isNotBlank() && !e.id.contains("search") &&
                ScreenMatch.normalize(e.text) !in HEADERS
        }
        val exact = rows.filter { ScreenMatch.normalize(it.text) == n }
        if (exact.isNotEmpty()) return exact
        val nameWords = n.split(' ').size
        return rows.filter { e ->
            val t = ScreenMatch.normalize(e.text)
            t.startsWith("$n ") && t.split(' ').size <= nameWords + 2 && (e.id.isEmpty() || e.id.contains("name"))
        }
    }
}
