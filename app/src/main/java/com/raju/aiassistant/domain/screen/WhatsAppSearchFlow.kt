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
    /** A permission prompt, an update screen or a system dialog kept coming over WhatsApp. */
    INTERRUPTED,
    /** No file matching the name in the document list (the list is left open for the user). */
    FILE_NOT_FOUND,
    /** The chat was read: [WhatsAppFlowResult.summary] says what is on screen. Nothing was sent. */
    PEEKED,
    /** The chat has no voice message on screen. */
    NO_VOICE,
    /** The latest voice message on screen was started on the speaker. */
    VOICE_PLAYED,
    /** More than one file matches ([WhatsAppFlowResult.choices]). Nothing was sent. */
    SEVERAL_FILES,
}

data class WhatsAppFlowResult(
    val outcome: FlowOutcome,
    /** The chat the message went to (as WhatsApp shows its name). */
    val chat: String? = null,
    val choices: List<String> = emptyList(),
    /** Step by step, for the diagnostics log. */
    val log: List<String> = emptyList(),
    /** What a chat shows (only for [FlowOutcome.PEEKED]). */
    val summary: String? = null,
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
    /** Longest wait between reads; the first waits are shorter (200, 300, 400 ms, then this). */
    private val settleMs: Long = 500,
    /** Reads per stage before giving up (patience × settleMs ≈ the wait). */
    private val patience: Int = PATIENCE_FOR_14_S,
) {
    private val log = mutableListOf<String>()

    /** The chat is open and confirmed by name: its message box and the name WhatsApp shows. */
    private class OpenChat(val entry: ScreenElement, val name: String)

    /** Opens [name]'s chat (search → one match → title checked), or says why not. */
    private suspend fun openChat(name: String): Any {
        if (!hands.openWhatsApp()) return done(FlowOutcome.UNAVAILABLE, "WhatsApp did not open")
        step("opened WhatsApp")

        // 1. The search box (tap the search button first; leave a chat WhatsApp reopened).
        var backs = 0
        var searchBox: ScreenElement? = null
        var tappedSearch = false
        for (i in 0 until patience) {
            val s = readSafe()
            if (s == null) { pause(stepDelay(i)); continue }
            if (s.isUnsafe()) return done(FlowOutcome.UNSAFE_SCREEN, "unsafe screen while looking for search")
            if (!s.isWhatsApp()) { pause(stepDelay(i)); continue }
            searchBox = WhatsAppScreen.searchBox(s)
            if (searchBox != null) break
            val button = WhatsAppScreen.searchButton(s)
            when {
                button != null && !tappedSearch -> { tappedSearch = hands.tap(button); step("tapped search: ${button.text.ifBlank { button.id }}") }
                WhatsAppScreen.chatEntry(s) != null && backs < 2 -> { hands.back(); backs++; step("left an open chat") }
            }
            pause(stepDelay(i))
        }
        val box = searchBox ?: return done(FlowOutcome.STUCK, "WhatsApp search box not found")

        // 2. The name.
        if (!hands.type(box, name)) return done(FlowOutcome.STUCK, "could not type in search")
        step("searched \"$name\"")

        // 3. Exactly one matching result.
        var chosen: ScreenElement? = null
        for (i in 0 until patience) {
            pause(stepDelay(i))
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
            pause(stepDelay(i))
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
        return OpenChat(input, result.text)
    }

    suspend fun send(name: String, text: String): WhatsAppFlowResult = guarded { sendImpl(name, text) }

    suspend fun sendDocument(name: String, fileQuery: String): WhatsAppFlowResult = guarded { sendDocumentImpl(name, fileQuery) }

    /** Opens [name]'s chat and says what is on screen in it (counts only; nothing is opened or sent). */
    suspend fun peek(name: String): WhatsAppFlowResult = guarded { peekImpl(name) }

    /** Opens [name]'s chat and starts its latest voice message on the speaker. */
    suspend fun playLastVoice(name: String): WhatsAppFlowResult = guarded { playVoiceImpl(name) }

    private suspend fun peekImpl(name: String): WhatsAppFlowResult {
        if (ScreenMatch.normalize(name).isEmpty()) return done(FlowOutcome.STUCK, "no chat named")
        val chat = when (val c = openChat(name)) { is OpenChat -> c; else -> return c as WhatsAppFlowResult }
        // The messages on screen: a few reads, so a chat that is still drawing is read once it settles.
        var seen: List<ScreenElement> = emptyList()
        for (i in 0 until 3) {
            pause(stepDelay(i))
            val s = readSafe() ?: continue
            if (s.isUnsafe()) return done(FlowOutcome.UNSAFE_SCREEN, "unsafe screen in chat", chat = chat.name)
            if (WhatsAppScreen.chatEntry(s) == null) continue
            seen = s.elements
        }
        if (seen.isEmpty()) return done(FlowOutcome.STUCK, "the chat's messages were not read", chat = chat.name)
        step("read the chat")
        val summary = ChatPeek.summarize(chat.name, seen)
        log += "PEEKED: $summary"
        return WhatsAppFlowResult(FlowOutcome.PEEKED, chat.name, log = log.toList(), summary = summary)
    }

    private suspend fun playVoiceImpl(name: String): WhatsAppFlowResult {
        if (ScreenMatch.normalize(name).isEmpty()) return done(FlowOutcome.STUCK, "no chat named")
        val chat = when (val c = openChat(name)) { is OpenChat -> c; else -> return c as WhatsAppFlowResult }
        var play: ScreenElement? = null
        for (i in 0 until 3) {
            pause(stepDelay(i))
            val s = readSafe() ?: continue
            if (s.isUnsafe()) return done(FlowOutcome.UNSAFE_SCREEN, "unsafe screen in chat", chat = chat.name)
            if (WhatsAppScreen.chatEntry(s) == null) continue
            play = WhatsAppScreen.lastVoicePlay(s)
            if (play != null) break
        }
        val button = play ?: return done(FlowOutcome.NO_VOICE, "no voice message on screen", chat = chat.name)
        if (!hands.tap(button)) return done(FlowOutcome.STUCK, "play did not react", chat = chat.name)
        step("pressed play on the latest voice message")
        return done(FlowOutcome.VOICE_PLAYED, "voice message started", chat = chat.name)
    }

    private suspend fun guarded(run: suspend () -> WhatsAppFlowResult): WhatsAppFlowResult {
        log.clear()
        interruptions = 0
        return try {
            run()
        } catch (e: FlowInterrupted) {
            done(FlowOutcome.INTERRUPTED, "kept being interrupted by ${e.by}")
        }
    }

    private suspend fun sendImpl(name: String, text: String): WhatsAppFlowResult {
        if (ScreenMatch.normalize(name).isEmpty() || text.isBlank()) return done(FlowOutcome.STUCK, "nothing to send")
        val chat = when (val c = openChat(name)) { is OpenChat -> c; else -> return c as WhatsAppFlowResult }
        val input = chat.entry
        val result = ScreenElement(chat.name, ElementKind.TEXT)

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
            pause(stepDelay(i))
            val s = readSafe() ?: continue
            if (!ScreenMatch.showsTyped(s, text.take(40))) return done(FlowOutcome.SENT, "box emptied", chat = result.text)
        }
        return done(FlowOutcome.TYPED_NOT_SENT, "message still in the box after Send", chat = result.text)
    }

    /**
     * Sends a document to [name]: chat → attach (📎) → Document → the one file matching [fileQuery]
     * ("ticket", "invoice pdf"; empty = the newest) → Send → the file shows in the chat.
     */
    private suspend fun sendDocumentImpl(name: String, fileQuery: String): WhatsAppFlowResult {
        if (ScreenMatch.normalize(name).isEmpty()) return done(FlowOutcome.STUCK, "no chat named")
        val chat = when (val c = openChat(name)) { is OpenChat -> c; else -> return c as WhatsAppFlowResult }

        // What the chat showed before: the file must appear one more time after sending.
        val chatBefore = readSafe()
        // 1. The attach button, 2. "Document".
        val attach = awaitElement("attach button") { WhatsAppScreen.attachButton(it) } ?: return done(FlowOutcome.STUCK, "attach button not found", chat = chat.name)
        if (!hands.tap(attach)) return done(FlowOutcome.STUCK, "attach button did not react", chat = chat.name)
        step("tapped attach")
        val document = awaitElement("Document option") { WhatsAppScreen.documentOption(it) } ?: return done(FlowOutcome.STUCK, "Document option not found", chat = chat.name)
        if (!hands.tap(document)) return done(FlowOutcome.STUCK, "Document option did not react", chat = chat.name)
        step("tapped Document")

        // 3. The one matching file in WhatsApp's document list (or the system file picker).
        var file: ScreenElement? = null
        for (i in 0 until patience) {
            pause(stepDelay(i))
            val s = readSafe() ?: continue
            if (s.isUnsafe()) return done(FlowOutcome.UNSAFE_SCREEN, "unsafe screen in the file list")
            if (!s.isWhatsApp() && s.packageName !in WhatsAppScreen.FILE_PICKERS) continue
            // Still the chat (a file sent earlier shows there too): wait for the document list.
            if (WhatsAppScreen.chatEntry(s) != null) continue
            val files = WhatsAppScreen.files(s)
            if (files.isEmpty()) continue
            val matches = DocumentMatch.pick(files, fileQuery)
            val names = matches.map { it.text.trim() }.distinct()
            if (names.size > 1) return done(FlowOutcome.SEVERAL_FILES, "several files: ${names.joinToString()}", chat = chat.name, choices = names)
            if (matches.isEmpty()) return done(FlowOutcome.FILE_NOT_FOUND, "no file like \"$fileQuery\" among ${files.size}", chat = chat.name)
            file = matches.first()
            break
        }
        val chosen = file ?: return done(FlowOutcome.FILE_NOT_FOUND, "the file list did not show", chat = chat.name)
        if (!hands.tap(chosen)) return done(FlowOutcome.STUCK, "the file did not react", chat = chat.name)
        step("picked \"${chosen.text}\"")

        // 4. "Send to …?" / the preview's Send button.
        val send = awaitElement("Send after picking") { WhatsAppScreen.sendButton(it) ?: WhatsAppScreen.confirmSend(it) }
            ?: return done(FlowOutcome.TYPED_NOT_SENT, "Send not found after picking the file", chat = chat.name)
        if (!hands.tap(send)) return done(FlowOutcome.TYPED_NOT_SENT, "Send could not be pressed", chat = chat.name)
        step("pressed Send")

        // 5. Back in the chat with the file in it.
        val fileName = DocumentMatch.normalize(chosen.text)
        fun inChat(s: ScreenSnapshot?) = s?.elements?.count { !it.editable && DocumentMatch.normalize(it.text).contains(fileName) } ?: 0
        val before = inChat(chatBefore)
        for (i in 0 until patience) {
            pause(stepDelay(i))
            val s = readSafe() ?: continue
            if (WhatsAppScreen.chatEntry(s) == null) continue
            if (inChat(s) > before) return done(FlowOutcome.SENT, "file is in the chat", chat = chat.name)
        }
        return done(FlowOutcome.TYPED_NOT_SENT, "the file did not show in the chat", chat = chat.name)
    }

    /** Waits up to patience × settleMs for [find] to see something on a WhatsApp screen. */
    private suspend fun awaitElement(what: String, find: (ScreenSnapshot) -> ScreenElement?): ScreenElement? {
        for (i in 0 until patience) {
            pause(stepDelay(i))
            val s = readSafe() ?: continue
            if (s.isUnsafe()) return null
            find(s)?.let { return it }
        }
        step("not found: $what")
        return null
    }

    /**
     * The screen, or null when it cannot be read. Something that is not part of the flow — a
     * permission prompt, a Play Store update, a system dialog, a WhatsApp update / permission dialog —
     * is backed out of; if it keeps coming back the flow stops as INTERRUPTED instead of waiting on.
     */
    private suspend fun readSafe(): ScreenSnapshot? {
        val s = runCatching { hands.read() }.getOrNull() ?: return null
        if (!WhatsAppScreen.isInterruption(s)) {
            interruptions = 0
            return s
        }
        interruptions++
        step("interrupted by ${s.appLabel} (${s.packageName})")
        if (interruptions > MAX_BACK_OUTS) throw FlowInterrupted(s.appLabel)
        hands.back()
        return null
    }

    private var interruptions = 0

    private class FlowInterrupted(val by: String) : Exception()

    /** Waits grow 200 → 300 → 400 → [settleMs] ms (0 in tests). */
    private fun stepDelay(i: Int): Long = if (settleMs <= 0) 0 else minOf(settleMs, 200L + 100L * i)

    private fun ScreenSnapshot.isWhatsApp() = packageName in WhatsAppScreen.PACKAGES

    private fun ScreenSnapshot.isUnsafe() = isWhatsApp() && SensitiveScreens.reason(this) != null

    private fun step(what: String) { log += what }

    private fun done(outcome: FlowOutcome, why: String, chat: String? = null, choices: List<String> = emptyList()): WhatsAppFlowResult {
        log += "${outcome.name}: $why"
        return WhatsAppFlowResult(outcome, chat, choices, log.toList())
    }
}

/** Reads per step: 200 + 300 + 400 ms, then 500 ms each — about 14 s before a step gives up. */
const val PATIENCE_FOR_14_S = 30
/** Dialogs backed out of before the flow stops as INTERRUPTED. */
const val MAX_BACK_OUTS = 3

/** How WhatsApp's screens are recognised: its view ids first, its visible words as a fallback. */
object WhatsAppScreen {
    val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    /** Apps that put prompts over WhatsApp: Android's permission dialog, Play Store updates, system dialogs. */
    private val INTERRUPTERS = setOf(
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.android.vending", "android", "com.android.systemui",
    )
    private val DIALOG_WORDS = Regex(
        "\\b(update|allow|permission|not now|later|restart|try again|isn.t responding)\\b|अपडेट|अनुमति|बाद में|আপডেট|অনুমতি",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A prompt that is not part of sending: another app's dialog over WhatsApp, or a WhatsApp dialog
     * (update / permission) with its own buttons and none of the chat or search boxes.
     */
    fun isInterruption(s: ScreenSnapshot): Boolean {
        if (s.packageName in INTERRUPTERS) return true
        if (s.packageName !in PACKAGES) return false
        val dialogButtons = s.elements.any { it.id == "android:id/button1" || it.id == "android:id/button2" }
        if (!dialogButtons) return false
        val partOfFlow = s.elements.any { it.editable } || confirmSend(s) != null
        return !partOfFlow && s.elements.any { DIALOG_WORDS.containsMatchIn(it.text) }
    }

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

    /** The system file picker WhatsApp may open for "Document". */
    val FILE_PICKERS = setOf("com.google.android.documentsui", "com.android.documentsui")

    private val ATTACH_WORDS = setOf("attach", "attachment", "attach file", "अटैच", "संलग्न", "সংযুক্ত").map { ScreenMatch.normalize(it) }
    private val DOCUMENT_WORDS = setOf("document", "documents", "डॉक्यूमेंट", "दस्तावेज़", "दस्तावेज", "ডকুমেন্ট", "নথি").map { ScreenMatch.normalize(it) }
    private val CONFIRM_WORDS = setOf("send", "ok", "भेजें", "ठीक है", "পাঠান").map { ScreenMatch.normalize(it) }
    private val FILE_NAME = Regex("\\.[A-Za-z0-9]{2,5}$")

    fun attachButton(s: ScreenSnapshot): ScreenElement? =
        s.elements.firstOrNull { it.clickable && it.id.endsWith(":id/input_attach_button") }
            ?: s.elements.firstOrNull { it.clickable && !it.editable && ScreenMatch.normalize(it.text) in ATTACH_WORDS }

    /** The Play button of the newest voice message on screen (the lowest one in the chat). */
    fun lastVoicePlay(s: ScreenSnapshot): ScreenElement? = s.elements.lastOrNull { e ->
        if (!e.clickable || e.editable) return@lastOrNull false
        val t = ScreenMatch.normalize(e.text)
        e.id.endsWith(":id/voice_note_btn") || e.id.endsWith(":id/audio_play_btn") ||
            (t.contains("play") || t.contains("चलाएं") || t.contains("चलाएँ") || t.contains("চালান")) &&
            (t.contains("voice") || t.contains("audio") || t.contains("वॉइस") || t.contains("वॉयस") || t.contains("ভয়েস"))
    }

    fun documentOption(s: ScreenSnapshot): ScreenElement? =
        s.elements.firstOrNull { it.clickable && (it.id.endsWith(":id/pick_document") || it.id.endsWith(":id/pickfiletype_document_holder")) }
            ?: s.elements.firstOrNull { it.clickable && !it.editable && ScreenMatch.normalize(it.text) in DOCUMENT_WORDS }

    /** Rows of a document list that are files ("IRCTC_ticket.pdf"). */
    fun files(s: ScreenSnapshot): List<ScreenElement> =
        s.elements.filter { it.clickable && !it.editable && !it.password && FILE_NAME.containsMatchIn(it.text.trim()) }

    /** The "Send to Riya?" dialog's own button. */
    fun confirmSend(s: ScreenSnapshot): ScreenElement? =
        s.elements.firstOrNull { it.clickable && it.id == "android:id/button1" && ScreenMatch.normalize(it.text) in CONFIRM_WORDS }

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

/** Which file the user means: "ticket" → "IRCTC_Ticket_12345.pdf"; "pdf" alone or nothing → the newest. */
object DocumentMatch {
    private val FILLER = setOf(
        "pdf", "file", "files", "document", "documents", "doc", "wali", "waali", "vali", "wala", "waala", "the", "my", "meri", "mera",
        "latest", "recent", "nayi", "naya", "new", "last", "aakhri", "akhri", "abhi", "wo", "woh", "vo", "ye", "yeh", "ek",
        "वाली", "वाला", "फाइल", "फ़ाइल", "पीडीएफ", "डॉक्यूमेंट", "मेरी", "मेरा", "नई", "नया", "आखिरी", "वो", "ये", "एक",
    )
    private val EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "jpg", "jpeg", "png", "zip", "apk")

    fun normalize(s: String): String = s.lowercase().replace(Regex("[_\\-.()\\[\\]]"), " ").replace(Regex("\\s+"), " ").trim()

    fun words(s: String): List<String> = normalize(s).split(' ').filter { it.isNotBlank() && it !in FILLER && it !in EXTENSIONS }

    /**
     * Files matching [query]: every meaningful word of it appears in the file name. A query with no
     * such word means the newest file (the list's first), of the named type when one was said.
     */
    fun pick(files: List<ScreenElement>, query: String): List<ScreenElement> {
        val q = normalize(query).split(' ').filter { it.isNotBlank() }
        val type = q.firstOrNull { it in EXTENSIONS }
        val ofType = if (type == null) files else files.filter { normalize(it.text).endsWith(" $type") }
        val wanted = q.filter { it !in FILLER && it !in EXTENSIONS }
        if (wanted.isEmpty()) return ofType.take(1)
        return ofType.filter { f -> val n = normalize(f.text); wanted.all { n.contains(it) } }
    }
}
