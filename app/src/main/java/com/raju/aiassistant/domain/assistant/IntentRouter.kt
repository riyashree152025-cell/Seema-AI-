package com.raju.aiassistant.domain.assistant

import java.time.LocalDate

/**
 * "AI understands, the phone does": a sentence that sounds like a phone task but matched no fixed phone
 * rule (other words, mishearings: "3 a", "उन्नीस सौ छियासी", half sentences) goes to the AI with only
 * this short list of what the phone can do (~450 tokens instead of the ~2,500 of the chat prompt). The
 * AI answers one JSON object; the phone checks it and does the task (calls, messages and money still ask
 * first). Questions and chat ({"type":"chat"}) go on to the normal conversation.
 */
object IntentRouter {

    fun system(today: LocalDate): String = """
Turn ONE spoken Hindi/Hinglish/Bengali phone command into ONE JSON object, nothing else. Speech recognition makes mistakes: fix obvious mishearings ("3 a"=3A, "उन्नीस सौ छियासी"=1986, numbers in words = digits). Keep names as said. Leave out fields that were not said. If it is a question, chat or anything not in the list: {"type":"chat"}.
Types:
call{contact} · sms{contact,text} · whatsapp{contact,text?} ("What's up" = WhatsApp; text only if said) · whatsapp_peek{contact} (what came in their chat: photos, documents, voice notes) · whatsapp_play_voice{contact} · open_app{name} · flashlight{state:on|off} · volume{percent} · set_alarm{hour,minute} · set_timer{minutes} · reminder_add{text,in_minutes | at:"YYYY-MM-DDTHH:MM"} · tatkal_reminder{name,class:ac|sleeper} (remind to book someone's Tatkal ticket) · booking_note{from,to,class:1A|2A|3A|SL|CC|2S,date:"YYYY-MM-DD",passenger,birth_year} (note down a ticket booking someone asked for) · look_camera{camera:back|front,question} · live_camera{on:true|false} · take_photo{camera} · inbox_check{period:today|yesterday|week|month,kind:all|credit|debit} · payment_check{amount} · ledger_add{name,amount,kind:gave|got} · ledger_balance{name} · expense_add{amount,note} · expense_summary{period:today|week|month} · pnr_status{pnr} · pnr_add{pnr,date:"YYYY-MM-DD",passenger} · remember{text} (save a note / something to remember, no time) · reminder_list · app_search{app,query} (search inside Chrome/YouTube/Maps/Play Store/Amazon/Flipkart) · play_music{query} · navigate{destination} · battery · find_phone · lock_screen
Today: $today
""".trim()

    private val TASK_WORDS = Regex(
        "\\b(?:kar|karo|kardo|karna|kijiye|laga|lagao|set|bhej|bhejo|send|call|phone|fon|yaad|remind|alarm|timer|booking|book|tatkal|ticket|pnr|" +
            "camera|photo|selfie|torch|volume|khol|kholo|open|band|sms|message|msg|inbox|credit|debit|refund|udhaar|udhar|khata|hisaab|hisab|diye|mile|" +
            "kharch|kharcha|note|likh|likho|gaana|song|bajao|chalao|rasta|battery|lock|whatsapp)\\w*|" +
            "करो|कर दो|लगाओ|भेजो|कॉल|फ़ोन|फोन|याद|अलार्म|टाइमर|बुकिंग|तत्काल|टिकट|कैमरा|फोटो|फ़ोटो|सेल्फी|टॉर्च|आवाज़|खोलो|बंद|मैसेज|" +
            "उधार|खाता|हिसाब|दिए|मिले|खर्च|नोट|लिखो|गाना|बजाओ|चलाओ|रास्ता|बैटरी|लॉक",
        RegexOption.IGNORE_CASE,
    )

    /** Only these sentences cost the extra short request; plain chat and questions go straight on. */
    fun looksLikeTask(text: String): Boolean = text.length in 4..300 && TASK_WORDS.containsMatchIn(text)

    /** The model's answer as a phone action, or null for chat / anything unreadable or incomplete. */
    fun toAction(raw: String): AssistantAction? {
        val json = Regex("\\{[^{}]*\\}").find(raw)?.value ?: return null
        val obj = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject }.getOrNull() ?: return null
        fun str(k: String) = (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        return when (str("type")?.lowercase()) {
            null, "chat" -> null
            "whatsapp" -> str("contact")?.let { c -> str("text")?.let { AssistantAction.WhatsApp(c, it) } ?: AssistantAction.WhatsAppAskText(c) }
            "tatkal_reminder" -> AssistantAction.TatkalReminder(str("name"), str("class")?.lowercase()?.startsWith("s") == true)
            "booking_note" -> AssistantAction.BookingNoteWith(
                listOfNotNull(
                    str("from")?.let { f -> str("to")?.let { "$f se $it" } ?: f },
                    str("to")?.takeIf { str("from") == null },
                    str("class"),
                    // The note reads "15/11/2026", not the ISO form.
                    str("date")?.let { d -> Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(d)?.destructured?.let { (y, m, dd) -> "$dd/$m/$y" } ?: d },
                    str("passenger"), str("birth_year"),
                ).joinToString(" "),
            )
            "live_camera" -> AssistantAction.LiveCamera(on = str("on")?.lowercase() != "false", front = str("camera")?.lowercase() == "front")
            "inbox_check" -> AssistantAction.InboxCheck(
                when (str("period")?.lowercase()) {
                    "today" -> com.raju.aiassistant.domain.sms.InboxPeriod.TODAY
                    "yesterday" -> com.raju.aiassistant.domain.sms.InboxPeriod.YESTERDAY
                    "month" -> com.raju.aiassistant.domain.sms.InboxPeriod.MONTH
                    else -> com.raju.aiassistant.domain.sms.InboxPeriod.WEEK
                },
                when (str("kind")?.lowercase()) {
                    "credit" -> com.raju.aiassistant.domain.sms.InboxKind.CREDIT
                    "debit" -> com.raju.aiassistant.domain.sms.InboxKind.DEBIT
                    else -> com.raju.aiassistant.domain.sms.InboxKind.ALL
                },
            )
            "payment_check" -> str("amount")?.let { com.raju.aiassistant.domain.business.rupeesToPaise(it) }?.let { AssistantAction.PaymentCheck(it) }
            "ledger_add" -> {
                val paise = str("amount")?.let { com.raju.aiassistant.domain.business.rupeesToPaise(it) }
                val kind = when (str("kind")?.lowercase()) {
                    "got", "mile", "received" -> com.raju.aiassistant.domain.ledger.LedgerKind.GOT
                    "gave", "diye", "given" -> com.raju.aiassistant.domain.ledger.LedgerKind.GAVE
                    else -> null
                }
                val name = str("name")
                if (name == null || paise == null || kind == null) null else AssistantAction.LedgerRecord(name, paise, kind)
            }
            // Every other type has the same fields as the chat AI's actions: one parser for both.
            else -> parseReply("<action>$json</action>").actions.firstOrNull()
        }
    }
}
