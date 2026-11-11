package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.ledger.LedgerCommand
import com.raju.aiassistant.domain.ledger.parseLedger
import com.raju.aiassistant.domain.tatkal.TatkalCommand
import com.raju.aiassistant.domain.tatkal.parseTatkalCommand

import com.raju.aiassistant.domain.tools.MediaCommand

/**
 * Simple phone commands understood on the phone itself, without asking an AI: torch, volume,
 * timer, alarm, photo / selfie, camera, call, answer / end a call, battery, home screen, music
 * (pause / play / next / previous), screenshot, find my phone, last call, a settings page, back,
 * Seema's own screen, the time and the date, and opening an app. They cost no tokens, answer at once and also work without internet.
 *
 * Only a sentence that is nothing but such a command matches ("torch on karo", "Mummy ko call
 * karo", "6:30 baje subah ka alarm"). Anything more ("torch on karo aur gaana chalao"), anything
 * unclear ("6 baje ka alarm" — morning or evening?) and Bengali go to the AI as before.
 */
object LocalCommands {

    fun parse(text: String): AssistantAction? {
        whatsApp(text)?.let { return it }
        sms(text)?.let { return it }
        typeOnScreen(text)?.let { return it }
        // "Hey Seema, Rahul se 500 mile": the name and polite words in front are not part of the client's name.
        val plain = withoutLeadingFillers(text)
        parseLedger(plain)?.let { return when (it) {
            is LedgerCommand.Record -> AssistantAction.LedgerRecord(it.name, it.amountPaise, it.kind)
            is LedgerCommand.Balance -> AssistantAction.LedgerBalance(it.name)
            LedgerCommand.Undo -> AssistantAction.LedgerUndo
        } }
        com.raju.aiassistant.domain.notify.parseReplyCommand(plain)?.let { return AssistantAction.NotificationReply(it.who, it.text) }
        com.raju.aiassistant.domain.calendar.parseCalendarQuestion(plain)?.let { return AssistantAction.CalendarRead(it) }
        com.raju.aiassistant.domain.reminder.parseReminder(plain, java.time.LocalDateTime.now())?.let { return when (it) {
            is com.raju.aiassistant.domain.reminder.ReminderCommand.Add ->
                AssistantAction.ReminderAdd(it.at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), it.text)
            com.raju.aiassistant.domain.reminder.ReminderCommand.List -> AssistantAction.ReminderList
            com.raju.aiassistant.domain.reminder.ReminderCommand.CancelLast -> AssistantAction.ReminderCancel(all = false)
            com.raju.aiassistant.domain.reminder.ReminderCommand.CancelAll -> AssistantAction.ReminderCancel(all = true)
        } }
        com.raju.aiassistant.domain.tatkal.parsePassengers(plain, java.time.LocalDate.now())?.let { return AssistantAction.PassengerAges(it) }
        com.raju.aiassistant.domain.rail.parseRailCommand(plain, java.time.LocalDate.now())?.let { return when (it) {
            is com.raju.aiassistant.domain.rail.RailCommand.Between -> AssistantAction.TrainsBetween(it.from, it.to, it.date)
            is com.raju.aiassistant.domain.rail.RailCommand.Live -> AssistantAction.TrainLive(it.train, null)
        } }
        parseTatkalCommand(plain)?.let { return when (it) {
            is TatkalCommand.Set -> AssistantAction.SetTatkalAlarm(it.alarm.hour, it.alarm.minute, it.alarm.label)
            TatkalCommand.List -> AssistantAction.TatkalList
            is TatkalCommand.Cancel -> AssistantAction.TatkalCancel(it.hour, it.minute, it.kind)
            TatkalCommand.CancelAll -> AssistantAction.TatkalCancelAll
        } }
        val t = normalize(text)
        if (t.isEmpty() || t.length > 80) return null
        return readMessage(t) ?: torch(t) ?: volume(t) ?: timer(t) ?: alarm(t) ?: photo(t) ?: endCall(t) ?: answerCall(t) ?: call(t)
            ?: battery(t) ?: home(t) ?: lock(t) ?: media(t) ?: screenshot(t) ?: findPhone(t) ?: lastCall(t) ?: openSettings(t) ?: back(t)
            ?: openSeema(t) ?: clock(t) ?: readScreen(t) ?: pnr(t) ?: scroll(t) ?: tap(t) ?: openApp(t)
    }

    // ------------------------------------------------------------------ text clean-up

    private val LEAD_FILLER = Regex("^\\s*(?:please|plz|pls|zara|jara|जरा|ज़रा|seema|सीमा|hey|hi|हे|हाय)(?:[\\s,.!।]+|$)", RegexOption.IGNORE_CASE)

    /** The sentence without "Hey Seema", "please", "zara" in front; the rest keeps its own spelling and case. */
    internal fun withoutLeadingFillers(text: String): String {
        var t = text.trim()
        repeat(3) { t = t.replace(LEAD_FILLER, "") }
        return t.trim()
    }

    private val FILLER_START = Regex("^(please|plz|pls|zara|jara|जरा|ज़रा|seema|सीमा|hey|हे)\\s+")
    private val FILLER_END = Regex("\\s+(please|plz|pls|na|ना|ji|जी|jaldi|जल्दी)$")

    /** Lower case, local digits as 0-9, no punctuation, single spaces, polite words at the ends removed. */
    internal fun normalize(text: String): String {
        // "6.30" from speech recognition is a time: keep it as "6:30" before dots become spaces
        // (it became "6 30", and "6.30 baje subah ka alarm" went to the AI).
        val timed = text.lowercase().replace(Regex("(?<![\\d.])(\\d{1,2})\\.(\\d{2})(?![\\d.])"), "$1:$2")
        var t = buildString {
            for (ch in timed) {
                append(
                    when (ch) {
                        in '०'..'९' -> '0' + (ch - '०')
                        '.', ',', '!', '?', '।', '|', '"', '\'' -> ' '
                        else -> ch
                    },
                )
            }
        }.replace(Regex("\\s+"), " ").trim()
        // "6 : 30" / "6.30" written by speech recognition → "6:30"
        t = t.replace(Regex("(\\d{1,2}) ?: ?(\\d{2})"), "$1:$2")
        repeat(3) {
            t = t.replace(FILLER_START, "").replace(FILLER_END, "").trim()
        }
        return t
    }

    /** "karo", "kar do", "करो", "कर दीजिए" … at the end of a command. */
    private const val DO = "(?: (?:karo|kar do|kar de|kardo|karde|kar dijiye|kar dijie|kijiye|kijie|do|de|dijiye|dijie|करो|कर दो|कर दे|करदो|करदे|कर दीजिए|कीजिए|दो|दे|दीजिए))?"

    // ------------------------------------------------------------------ messages

    private const val MSG = "(?:message|messages|msg|notification|notifications|मैसेज|मेसेज|संदेश|नोटिफिकेशन)"
    private val READ_MSG = Regex(
        "^(?:(?:wo|vo|woh|naya|nayi|last|latest|वो|नया|आखिरी) )?$MSG(?: (?:ko|को))? (?:padho|padh do|padhke sunao|padh ke sunao|sunao|suna do|batao|bata do|पढ़ो|पढ़ दो|पढ़के सुनाओ|पढ़ के सुनाओ|सुनाओ|सुना दो|बताओ|बता दो)$|" +
            "^(?:kiska|kis ka|kya|किसका|किस का|क्या) $MSG (?:aaya|aya|aaya hai|aya hai|aaya tha|aya tha|आया|आया है|आया था)$|" +
            "^(?:last|aakhri|akhri|aakhiri|pichla|आखिरी|आख़िरी|पिछला) $MSG (?:kiska|kis ka|किसका|किस का)(?: (?:tha|aaya tha|था|आया था))?$|" +
            "^(?:kisne|किसने) $MSG (?:kiya tha|bheja tha|kiya|bheja|किया था|भेजा था|किया|भेजा)$",
    )

    private fun readMessage(t: String): AssistantAction? = when {
        MSG_SUMMARY.matches(t) -> AssistantAction.NotificationSummary
        READ_MSG.matches(t) -> AssistantAction.ReadNotification
        else -> null
    }

    // "Notification ka saar batao", "kitne message aaye", "kya kya message aaye hain", "sab message batao".
    private val MSG_SUMMARY = Regex(
        "^$MSG (?:ka|ki|का|की) (?:saar|summary|सार|समरी)(?: (?:batao|bata do|sunao|बताओ|सुनाओ))?$|" +
            "^(?:aaj )?(?:kitne|kitni|कितने|कितनी) $MSG (?:aaye|aaye hain|aayi|aayi hain|aaya|आए|आए हैं|आई|आई हैं)$|" +
            "^(?:kya kya|kaun kaun se|क्या क्या|कौन कौन से) $MSG (?:aaye|aaye hain|आए|आए हैं)$|" +
            "^(?:sab|saare|sabhi|सब|सारे|सभी) $MSG (?:batao|bata do|sunao|suna do|बताओ|सुनाओ)$",
    )

    // ------------------------------------------------------------------ torch

    private const val TORCH = "(?:torch|tourch|torchlight|flash ?light|flash|टॉर्च|टार्च|टोर्च|फ्लैश ?लाइट|फ्लैश)"
    private val TORCH_ON = Regex("^$TORCH(?: (?:ko|को))? (?:on|chalu|chaalu|jalao|jala|start|चालू|जलाओ|जला|ऑन|ओन)$DO$")
    private val TORCH_OFF = Regex("^$TORCH(?: (?:ko|को))? (?:off|band|bujhao|bujha|बंद|बुझाओ|बुझा|ऑफ)$DO$")

    private fun torch(t: String): AssistantAction? = when {
        TORCH_ON.matches(t) -> AssistantAction.Flashlight(on = true)
        TORCH_OFF.matches(t) -> AssistantAction.Flashlight(on = false)
        else -> null
    }

    // ------------------------------------------------------------------ volume

    private const val VOL = "(?:volume|awaz|aawaz|awaaz|aawaaz|आवाज़|आवाज|वॉल्यूम|वोल्यूम)"
    private val VOLUME_NUMBER = Regex("^$VOL (\\d{1,3})(?: ?(?:percent|pratishat|%|प्रतिशत|परसेंट))?(?: (?:par|pe|पर|पे))?$DO$")
    private val VOLUME_FULL = Regex("^$VOL (?:full|max|maximum|poori|puri|पूरी|फुल)$DO$")

    private fun volume(t: String): AssistantAction? {
        VOLUME_NUMBER.matchEntire(t)?.let { m ->
            val p = m.groupValues[1].toInt()
            return if (p in 0..100) AssistantAction.Volume(p) else null
        }
        return if (VOLUME_FULL.matches(t)) AssistantAction.Volume(100) else null
    }

    // ------------------------------------------------------------------ timer

    private const val UNIT = "(minute|minutes|min|mins|mint|मिनट|second|seconds|sec|secs|सेकंड|सेकेंड|hour|hours|ghanta|ghante|घंटा|घंटे)"
    private const val SET = "(?: (?:lagao|laga do|laga de|lagado|set karo|set kar do|start karo|chalu karo|लगाओ|लगा दो|लगा दे|सेट करो|सेट कर दो|चालू करो|शुरू करो))?"
    private val TIMER_A = Regex("^(\\d{1,3}) $UNIT(?: (?:ka|ki|का|की))? (?:timer|टाइमर)$SET$")
    private val TIMER_B = Regex("^(?:timer|टाइमर) (\\d{1,3}) $UNIT(?: (?:ka|ki|का|की))?$SET$")

    private fun timer(t: String): AssistantAction? {
        val m = TIMER_A.matchEntire(t) ?: TIMER_B.matchEntire(t) ?: return null
        val n = m.groupValues[1].toInt()
        val unit = m.groupValues[2]
        val seconds = when {
            unit.startsWith("s") || unit.startsWith("से") -> n
            unit.startsWith("h") || unit.startsWith("g") || unit.startsWith("घं") -> n * 3600
            else -> n * 60
        }
        return if (seconds in 1..24 * 3600) AssistantAction.SetTimer(seconds, "") else null
    }

    // ------------------------------------------------------------------ alarm

    private const val PART = "(subah|subha|savere|sawere|morning|सुबह|सवेरे|dopahar|dopehar|दोपहर|shaam|sham|evening|शाम|raat|rat|night|रात)"
    private const val TIME = "(\\d{1,2})(?::(\\d{2}))?(?: (?:baje|bje|बजे))?"
    private val ALARM_A = Regex("^(?:$PART )?$TIME(?: $PART)?(?: (?:ka|ki|का|की))? (?:alarm|अलार्म)$SET$")
    private val ALARM_B = Regex("^(?:alarm|अलार्म) (?:$PART )?$TIME(?: $PART)?(?: (?:ka|ki|का|की))?$SET$")

    private fun alarm(t: String): AssistantAction? {
        val m = ALARM_A.matchEntire(t) ?: ALARM_B.matchEntire(t) ?: return null
        val g = m.groupValues
        val part = g[1].ifEmpty { g[4] }
        var hour = g[2].toInt()
        val minute = g[3].ifEmpty { "0" }.toInt()
        if (minute !in 0..59) return null
        hour = when {
            part.isEmpty() -> if (hour in 13..23 || (hour == 0 && g[3].isNotEmpty())) hour else return null // "6 baje": morning or evening? ask the AI
            part.startsWith("s") && !part.startsWith("sh") || part.startsWith("सु") || part.startsWith("सव") || part == "morning" ->
                if (hour in 1..12) hour % 12 else return null
            part.startsWith("d") || part.startsWith("दो") -> when (hour) { 12 -> 12; in 1..5 -> hour + 12; else -> return null }
            part.startsWith("sh") || part.startsWith("शा") || part == "evening" -> if (hour in 1..11) hour + 12 else return null
            else -> when (hour) { in 7..11 -> hour + 12; 12 -> 0; in 1..5 -> hour; else -> return null } // raat / night
        }
        return if (hour in 0..23) AssistantAction.SetAlarm(hour, minute, "") else null
    }

    // ------------------------------------------------------------------ camera

    private const val PHOTO = "(?:ek )?(?:photo|foto|picture|pic|tasveer|tasvir|फोटो|फ़ोटो|तस्वीर)"
    private const val TAKE = "(?:lo|le lo|lelo|khicho|kheencho|kheecho|khincho|khichiye|kheenchiye|click karo|लो|ले लो|लेलो|खींचो|खिंचो|खीचो|खींचिए|क्लिक करो|लीजिए|lijiye)"
    private val PHOTO_BACK = Regex("^(?:(?:back|peeche|piche|पीछे|बैक) (?:camera|कैमरा|कैमरे) (?:se|से) )?$PHOTO $TAKE$")
    private val PHOTO_FRONT = Regex("^(?:front|aage|age|आगे|फ्रंट|सामने) (?:camera|कैमरा|कैमरे) (?:se|से) $PHOTO $TAKE$")
    private val SELFIE = Regex("^(?:ek )?(?:selfie|सेल्फी|सेल्फ़ी)(?: $TAKE)?$")
    private val CAMERA_OPEN = Regex("^(?:camera|कैमरा)(?: (?:kholo|khol do|open karo|open kar do|open|chalu karo|खोलो|खोल दो|ओपन करो|ओपन कर दो|चालू करो))$")

    private fun photo(t: String): AssistantAction? = when {
        PHOTO_FRONT.matches(t) || SELFIE.matches(t) -> AssistantAction.TakePhoto(front = true)
        PHOTO_BACK.matches(t) -> AssistantAction.TakePhoto(front = false)
        CAMERA_OPEN.matches(t) -> AssistantAction.OpenApp("Camera")
        else -> null
    }

    // ------------------------------------------------------------------ calls

    private const val PHONE = "(?:call|phone|fone|कॉल|काल|फोन|फ़ोन)"
    private val END_CALL = Regex("^$PHONE (?:kato|kaato|kaat do|kat do|kaat de|cut karo|cut kar do|cut|end karo|end kar do|band karo|band kar do|rakh do|disconnect karo|काटो|काट दो|काट दे|कट करो|कट कर दो|बंद करो|बंद कर दो|रख दो|डिस्कनेक्ट करो)$")
    private val ANSWER_CALL = Regex("^$PHONE (?:uthao|utha lo|utha do|uthaiye|receive karo|receive kar lo|pick karo|उठाओ|उठा लो|उठा दो|उठाइए|रिसीव करो|रिसीव कर लो)$")
    private val CALL_TO = Regex("^(.{1,40}?) (?:ko|को) $PHONE (?:karo|kar do|kar de|lagao|laga do|laga de|milao|mila do|करो|कर दो|कर दे|लगाओ|लगा दो|लगा दे|मिलाओ|मिला दो)$")
    private val CALL_EN = Regex("^(?:call|dial) ([a-z0-9 +]{1,40})$")
    private val NOT_A_NAME = Regex("(?:^| )(?:whatsapp|व्हाट्सएप|व्हाट्सऐप|video|वीडियो|aur|और|and|phir|फिर|then|mujhe|मुझे|kisi|किसी|usko|उसको|isko|इसको|unko|उनको|sabko|सबको|ise|use|उसे|इसे|karo|करो|lagao|लगाओ|kar|कर|back|me|him|her|dabao|daba|press|click|दबाओ)(?: |$)")

    private fun endCall(t: String): AssistantAction? = if (END_CALL.matches(t)) AssistantAction.EndCall else null

    private fun answerCall(t: String): AssistantAction? = if (ANSWER_CALL.matches(t)) AssistantAction.AnswerCall else null

    private fun call(t: String): AssistantAction? {
        val who = (CALL_TO.matchEntire(t) ?: CALL_EN.matchEntire(t))?.groupValues?.get(1)?.trim() ?: return null
        if (who.isEmpty() || who.split(' ').size > 4 || NOT_A_NAME.containsMatchIn(who)) return null
        val digits = who.filter { it.isDigit() }
        return if (digits.length >= 7 && digits.length * 2 >= who.count { !it.isWhitespace() }) {
            AssistantAction.Call(contact = null, number = who)
        } else {
            AssistantAction.Call(contact = who, number = null)
        }
    }

    // ------------------------------------------------------------------ battery, home

    private val BATTERY = Regex(
        "^(?:(?:kitni|kitna|कितनी|कितना) )?(?:battery|बैटरी)(?: (?:kitni|kitna|कितनी|कितना|status|level|percent))?" +
            "(?: (?:hai|he|है|bachi hai|bachi he|बची है|baki hai|बाकी है|kitni hai|कितनी है))?$",
    )
    // "home pe aao", "home screen pe chalo", "ghar aao", "wapas home", "home" — leading fillers like
    // "chalo theek hai apne …" are stripped first (NAV_FILLER).
    private val HOME = Regex("^(?:(?:home ?screen|home|होम ?स्क्रीन|होम|ghar|घर)(?: (?:screen|स्क्रीन))?(?: (?:par|pe|पर|पे|me|में))?(?: (?:jao|chalo|aao|aa jao|le chalo|wapas aao|wapas chalo|जाओ|चलो|आओ|आ जाओ|ले चलो|वापस आओ|वापस चलो))?|(?:wapas|वापस)(?: (?:home ?screen|home|होम))?(?: (?:jao|chalo|aao|जाओ|चलो|आओ))?)$")

    private fun battery(t: String): AssistantAction? = if (BATTERY.matches(t)) AssistantAction.Battery else null

    // "chalo", "accha", "theek hai", "ab", "apne", "seema" said before a navigation command.
    private val NAV_FILLER = Regex("^(?:(?:chalo|chal|accha|acha|theek hai|thik hai|ab|abhi|apne|apni|seema|सीमा|चलो|अच्छा|ठीक है|अब|अभी|अपने|अपनी)[\\s,]+)+", RegexOption.IGNORE_CASE)
    private fun stripNav(t: String): String { var s = t; repeat(3) { s = NAV_FILLER.replace(s, "").trim() }; return s }
    private fun home(t: String): AssistantAction? = if (HOME.matches(stripNav(t))) AssistantAction.GoHome else null

    private val LOCK = Regex("^(?:(?:screen|phone|फ़ोन|फोन|स्क्रीन) )?(?:lock|लॉक) (?:karo|kar do|karna|करो|कर दो)$|^(?:screen|phone|फ़ोन|फोन|स्क्रीन) (?:lock|लॉक)$")
    private fun lock(t: String): AssistantAction? = if (LOCK.matches(t)) AssistantAction.LockScreen else null

    // ------------------------------------------------------------------ WhatsApp message

    private const val WA = "(?:whatsapp|whats app|watsapp|वॉट्सऐप|व्हाट्सएप|व्हाट्सऐप|वॉट्सएप)"
    private const val SEND = "(?:bhejo|bhej do|bhej de|bhejiye|send karo|send kar do|likh do|likh ke bhejo|likhkar bhejo|bolo|bol do|भेजो|भेज दो|भेज दे|भेजिए|सेंड करो|लिख दो|लिख के भेजो|लिखकर भेजो|बोलो|बोल दो)"
    private const val MSG_WORD = "(?:(?:ye|yeh|ek|यह|ये|एक) )?(?:message|msg|मैसेज|मेसेज|संदेश)"

    /**
     * "WhatsApp par Mummy ko 'main aa raha hoon' bhejo" / "Mummy ko WhatsApp par message bhejo ki …".
     * Read from the original words (not lower-cased) so the message goes out as it was said.
     */
    private val WHATSAPP_A = Regex(
        "^$WA (?:par|pe|पर|पे) (.{1,40}?) (?:ko|को) (?:$MSG_WORD )?(?:ki |कि )?(.{1,300}?)(?: (?:ka|का) $MSG_WORD)? $SEND$",
        RegexOption.IGNORE_CASE,
    )
    private val WHATSAPP_B = Regex(
        "^(.{1,40}?) (?:ko|को) $WA (?:par|pe|पर|पे) (?:$MSG_WORD )?(?:ki |कि )?(.{1,300}?) $SEND$",
        RegexOption.IGNORE_CASE,
    )
    private val WHATSAPP_C = Regex(
        "^$WA (?:par|pe|पर|पे) (.{1,40}?) (?:ko|को)(?: $MSG_WORD)? $SEND (?:ki|कि) (.{1,300})$",
        RegexOption.IGNORE_CASE,
    )
    private val WHATSAPP_D = Regex(
        "^(.{1,40}?) (?:ko|को) $WA (?:par|pe|पर|पे)(?: $MSG_WORD)? $SEND (?:ki|कि) (.{1,300})$",
        RegexOption.IGNORE_CASE,
    )
    private val FILE_REQUEST = Regex(
        "^(?:[\\p{L}\\p{M}\\p{N}_.-]+ ){0,4}(?:wali |waali |vali |wala |waala |वाली |वाला )?(?:pdf|file|document|doc|फाइल|फ़ाइल|पीडीएफ|डॉक्यूमेंट)$",
        RegexOption.IGNORE_CASE,
    )
    private val SEEMA_START = Regex("^(?:(?:hey|hi|हे) )?(?:seema|सीमा)[,\\s]+", RegexOption.IGNORE_CASE)

    private fun whatsApp(text: String): AssistantAction? {
        val t = text.trim().trimEnd('.', '!', '?', '।', ' ').replace(Regex("\\s+"), " ").replace(SEEMA_START, "").replace(Regex("^[,\\s]+"), "")
        if (t.length > 360) return null
        val m = WHATSAPP_A.matchEntire(t) ?: WHATSAPP_B.matchEntire(t) ?: WHATSAPP_C.matchEntire(t) ?: WHATSAPP_D.matchEntire(t) ?: return null
        val who = m.groupValues[1].trim().trim('"', '\'', '“', '”')
        val msg = m.groupValues[2].trim().trim('"', '\'', '“', '”').trim()
        if (who.isEmpty() || msg.isEmpty() || who.split(' ').size > 4 || NOT_A_NAME.containsMatchIn(normalize(who))) return null
        // "message bhejo" with nothing to say is not a message: the AI asks what to write.
        if (Regex("^$MSG_WORD$", RegexOption.IGNORE_CASE).matches(msg)) return null
        // "ticket wali PDF", "wo document", "latest file": a document, not words to type.
        if (FILE_REQUEST.matches(msg)) return AssistantAction.WhatsAppFile(contact = who, file = msg)
        return AssistantAction.WhatsApp(contact = who, text = msg)
    }

    // ------------------------------------------------------------------ SMS

    private const val SMS_WORD = "(?:sms|message|msg|massage|मैसेज|मेसेज|एसएमएस|संदेश)"
    /** "Firoz ko message karo ki abhi tak payment nahi mila" (no "WhatsApp" in it: that is WhatsApp). */
    private val SMS_TO = Regex(
        "^(.{1,40}?) (?:ko|को) $SMS_WORD(?: (?:karo|kar do|kar de|bhejo|bhej do|bhej de|करो|कर दो|कर दे|भेजो|भेज दो|भेज दे))? (?:ki|कि|ke|के) (.{2,300})$",
        RegexOption.IGNORE_CASE,
    )

    private fun sms(text: String): AssistantAction? {
        val t = text.trim().trimEnd('.', '!', '?', '।', ' ').replace(Regex("\\s+"), " ").replace(SEEMA_START, "").replace(Regex("^[,\\s]+"), "")
        if (t.length > 360 || Regex(WA, RegexOption.IGNORE_CASE).containsMatchIn(t)) return null
        val m = SMS_TO.matchEntire(t) ?: return null
        val who = m.groupValues[1].trim().trim('"', '\'', '“', '”')
        val msg = m.groupValues[2].trim().trim('"', '\'', '“', '”').trim()
        if (who.isEmpty() || msg.length < 2 || who.split(' ').size > 4 || NOT_A_NAME.containsMatchIn(normalize(who))) return null
        val digits = who.filter { it.isDigit() }
        return if (digits.length >= 7 && digits.length * 2 >= who.count { !it.isWhitespace() }) AssistantAction.Sms(contact = null, number = who, text = msg)
        else AssistantAction.Sms(contact = who, number = null, text = msg)
    }

    // ------------------------------------------------------------------ the app on screen

    /** "Send dabao", "OK button press karo", "Install pe click karo": tap that (Seema asks first). */
    private val TAP = Regex(
        "^(.{1,30}?)(?: (?:button|बटन))?(?: (?:par|pe|पर|पे|ko|को))? (?:dabao|daba do|daba de|press karo|press kar do|click karo|click kar do|tap karo|tap kar do|दबाओ|दबा दो|दबा दे|प्रेस करो|क्लिक करो|टैप करो)$",
    )
    private val NOT_A_BUTTON = Regex("(?:^| )(?:call|phone|फोन|कॉल|torch|टॉर्च|volume|आवाज़|aur|और|and|phir|फिर)(?: |$)")

    private fun tap(t: String): AssistantAction? {
        val what = TAP.matchEntire(t)?.groupValues?.get(1)?.trim() ?: return null
        if (what.isEmpty() || what.split(' ').size > 4 || NOT_A_BUTTON.containsMatchIn(what)) return null
        return AssistantAction.ScreenTap(what)
    }

    private val SCROLL_DOWN = Regex("^(?:(?:neeche|niche|नीचे) (?:scroll|स्क्रॉल)(?: (?:karo|kar do|करो|कर दो))?|scroll (?:down|neeche|niche)|(?:screen|स्क्रीन) (?:neeche|niche|नीचे) (?:karo|करो))$")
    private val SCROLL_UP = Regex("^(?:(?:upar|oopar|ऊपर) (?:scroll|स्क्रॉल)(?: (?:karo|kar do|करो|कर दो))?|scroll (?:up|upar|oopar)|(?:screen|स्क्रीन) (?:upar|ऊपर) (?:karo|करो))$")

    private fun scroll(t: String): AssistantAction? = when {
        SCROLL_DOWN.matches(t) -> AssistantAction.ScreenScroll(down = true)
        SCROLL_UP.matches(t) -> AssistantAction.ScreenScroll(down = false)
        else -> null
    }

    /** "Likho kal milte hain" / "type karo ki main aa raha hoon": typed into the app in front (asked first). */
    private val TYPE_FIRST = Regex("^(?:(?:ye|yeh|ये|यह) )?(?:likho|likh do|type karo|type kar do|लिखो|लिख दो|टाइप करो)(?: (?:ki|कि))? (.{1,200})$", RegexOption.IGNORE_CASE)

    private fun typeOnScreen(text: String): AssistantAction? {
        val t = text.trim().trimEnd('.', '!', '?', '।', ' ').replace(Regex("\\s+"), " ").replace(SEEMA_START, "")
        val words = TYPE_FIRST.matchEntire(t)?.groupValues?.get(1)?.trim()?.trim('"', '\'', '“', '”') ?: return null
        return if (words.isBlank()) null else AssistantAction.ScreenType(words, null)
    }

    // ------------------------------------------------------------------ music

    private const val SONG = "(?:gaana|gana|gaane|gane|music|song|गाना|गाने|म्यूजिक|म्यूज़िक|सॉन्ग)"
    private const val PLAY = "(?:chalao|chala do|chala de|bajao|baja do|baja de|play karo|play|चलाओ|चला दो|चला दे|बजाओ|बजा दो|प्ले करो)"
    private val MEDIA_PAUSE = Regex(
        "^(?:$SONG(?: (?:ko|को))? (?:band|rok|roko|rok do|ruko|pause|stop|बंद|रोको|रोक दो|रोक|पॉज़|पॉज|स्टॉप)|pause|पॉज़|पॉज)$DO$",
    )
    private val MEDIA_PLAY = Regex(
        "^(?:$SONG (?:phir se|fir se|phir|fir|wapas|dobara|फिर से|फिर|वापस|दोबारा) $PLAY|$SONG (?:resume|play)$DO|resume|play)$",
    )
    private val MEDIA_NEXT = Regex("^(?:(?:agla|next|अगला|नेक्स्ट) $SONG(?: $PLAY)?|$SONG (?:skip|badlo|बदलो)$DO|skip|next)$")
    private val MEDIA_PREVIOUS = Regex("^(?:pichla|pichhla|previous|पिछला|प्रीवियस) $SONG(?: $PLAY)?$")

    /** Pause / play / next / previous of whatever is playing. A song by name ("Arijit ka gaana chalao") goes to the AI. */
    private fun media(t: String): AssistantAction? = when {
        MEDIA_PAUSE.matches(t) -> AssistantAction.Media(MediaCommand.PAUSE)
        MEDIA_PLAY.matches(t) -> AssistantAction.Media(MediaCommand.PLAY)
        MEDIA_NEXT.matches(t) -> AssistantAction.Media(MediaCommand.NEXT)
        MEDIA_PREVIOUS.matches(t) -> AssistantAction.Media(MediaCommand.PREVIOUS)
        else -> null
    }

    // ------------------------------------------------------------------ screenshot, find phone, last call

    private val SCREENSHOT = Regex(
        "^(?:ek )?(?:screenshot|screen shot|स्क्रीनशॉट|स्क्रीन शॉट)(?: (?:lo|le lo|lelo|le|khincho|kheencho|khicho|lijiye|लो|ले लो|लेलो|ले|खींचो|लीजिए|लो ना))?$DO$",
    )
    private const val MOBILE = "(?:phone|fone|mobile|mobail|फोन|फ़ोन|मोबाइल)"
    private val FIND_PHONE = Regex(
        "^(?:mera |मेरा )?$MOBILE (?:dhundo|dhoondo|dhundho|dhoondho|khojo|dhund do|dhoond do|kahan hai|kaha hai|ki ghanti bajao|ki ring bajao|bajao|ढूंढो|ढूँढो|खोजो|ढूंढ दो|कहाँ है|कहां है|की घंटी बजाओ|बजाओ)$",
    )
    private val LAST_CALL = Regex(
        "^(?:(?:last|aakhri|akhri|aakhiri|pichla|आखिरी|आख़िरी|पिछला) $PHONE (?:kiska|kiski|kis ka|kis ki|किसका|किसकी|किस का|किस की)" +
            "(?: (?:tha|thi|aaya tha|aayi thi|aai thi|ayi thi|था|थी|आया था|आई थी))?|" +
            "(?:abhi |अभी )?(?:kiska|kiski|kis ka|kis ki|किसका|किसकी|किस का|किस की) $PHONE (?:aaya tha|aya tha|aayi thi|aai thi|ayi thi|tha|thi|आया था|आई थी|था|थी)|" +
            // "kisne" needs the word call/phone too: "kisne kiya tha" alone could be about anything.
            "(?:last|aakhri|आखिरी|आख़िरी) $PHONE (?:kisne|किसने) (?:kiya tha|ki thi|kiya|किया था|की थी|किया)|" +
            "(?:kisne|किसने) $PHONE (?:kiya tha|ki thi|kiya|किया था|की थी|किया))$",
    )

    private fun screenshot(t: String): AssistantAction? = if (SCREENSHOT.matches(t)) AssistantAction.Screenshot else null

    private val WHERE_ARE_YOU = Regex("^(?:(?:seema|सीमा) )?(?:kahan|kaha|kidhar|कहाँ|कहां|किधर) (?:ho|hai|हो|है)(?: (?:seema|सीमा))?$")

    private fun findPhone(t: String): AssistantAction? = if (FIND_PHONE.matches(t) || WHERE_ARE_YOU.matches(t)) AssistantAction.FindPhone else null

    private fun lastCall(t: String): AssistantAction? = if (LAST_CALL.matches(t)) AssistantAction.LastCall else null

    // ------------------------------------------------------------------ settings pages, back, Seema

    private val SETTINGS = Regex(
        "^(wifi|wi fi|wi-fi|वाईफाई|वाई फाई|bluetooth|ब्लूटूथ|display|डिस्प्ले|sound|साउंड|battery|बैटरी|notification|notifications|नोटिफिकेशन)" +
            "(?: (?:ki|ka|की|का))? (?:settings|setting|सेटिंग|सेटिंग्स)(?: (?:kholo|khol do|open karo|open kar do|open|dikhao|खोलो|खोल दो|ओपन करो|दिखाओ))?$|" +
            "^(?:settings|setting|सेटिंग|सेटिंग्स) (?:kholo|khol do|open karo|open kar do|खोलो|खोल दो|ओपन करो)$",
    )

    private fun openSettings(t: String): AssistantAction? {
        val m = SETTINGS.matchEntire(t) ?: return null
        val what = m.groupValues[1]
        val page = when {
            what.isEmpty() -> SettingsPage.GENERAL
            what.startsWith("w") || what.startsWith("वा") -> SettingsPage.WIFI
            what.startsWith("bl") || what.startsWith("ब्लू") -> SettingsPage.BLUETOOTH
            what.startsWith("d") || what.startsWith("डि") -> SettingsPage.DISPLAY
            what.startsWith("so") || what.startsWith("सा") -> SettingsPage.SOUND
            what.startsWith("ba") || what.startsWith("बै") -> SettingsPage.BATTERY
            else -> SettingsPage.NOTIFICATION_ACCESS
        }
        return AssistantAction.OpenSettings(page)
    }

    /** "peeche jao": the screen's back button (Seema still asks before pressing it). */
    private val BACK = Regex("^(?:(?:ek )?(?:peeche|piche|pichhe|peechhe|पीछे|back|बैक) (?:jao|chalo|jaao|chale jao|karo|aao|जाओ|चलो|करो|आओ)|go back|back|previous)$")
    private val SEEMA = Regex(
        "^(?:(?:apni|apne|अपनी|अपने) (?:screen|app|स्क्रीन|ऐप) (?:par|pe|पर|पे) (?:aao|aa jao|wapas aao|आओ|आ जाओ|वापस आओ)|(?:apna|apni|अपना|अपनी) (?:app|ऐप) (?:kholo|khol do|open karo|खोलो|खोल दो))$",
    )

    private fun back(t: String): AssistantAction? = if (BACK.matches(stripNav(t))) AssistantAction.ScreenBack else null

    private fun openSeema(t: String): AssistantAction? = if (SEEMA.matches(t)) AssistantAction.OpenSeema else null

    // ------------------------------------------------------------------ the screen

    private const val SCREEN = "(?:screen|स्क्रीन)"
    private val READ_SCREEN = Regex(
        "^(?:(?:ye|yeh|is|meri|ये|यह|इस|मेरी) )?$SCREEN(?: (?:ko|को))? (?:padho|padh do|padh ke sunao|padhke sunao|padh kar sunao|read karo|पढ़ो|पढ़ दो|पढ़ के सुनाओ|पढ़के सुनाओ|पढ़कर सुनाओ)$|" +
            "^$SCREEN (?:par|pe|पर|पे) (?:kya|क्या) (?:hai|he|likha hai|likha he|dikh raha hai|है|लिखा है|दिख रहा है)$",
    )

    private fun readScreen(t: String): AssistantAction? = when {
        READ_SCREEN.matches(t) -> AssistantAction.ReadScreen(null)
        LOOK_SCREEN.matches(t) -> AssistantAction.LookAtScreen(null)
        else -> null
    }

    // "Screen dekho", "screen dekh ke batao", "ye error kya hai", "screenshot samjhao": a picture, not only the text.
    private val LOOK_SCREEN = Regex(
        "^(?:(?:is|iss|ye|yeh|इस|ये|यह) )?$SCREEN(?: (?:ko|को))? (?:dekho|dekh lo|dekh ke batao|dekh kar batao|dekhkar batao|samjhao|samjha do|देखो|देख लो|देख के बताओ|देख कर बताओ|देखकर बताओ|समझाओ|समझा दो)$|" +
            "^(?:screenshot|स्क्रीनशॉट) (?:samjhao|samjha do|dekh ke batao|समझाओ|समझा दो|देख के बताओ)$|" +
            "^(?:ye|yeh|ये|यह) (?:error|एरर) (?:kya hai|kya he|samjhao|क्या है|समझाओ)$",
    )

    // ------------------------------------------------------------------ PNR

    private val PNR_MINE = Regex(
        "^(?:(?:mera|meri|mere|hamara|मेरा|मेरी|हमारा) )?(?:pnr|पीएनआर|ticket|टिकट)(?: (?:ka|ki|का|की))? (?:status|स्टेटस)(?: (?:kya hai|kya he|batao|bata do|क्या है|बताओ|बता दो))?$|" +
            "^(?:mera|meri|मेरा|मेरी) (?:ticket|टिकट) (?:confirm|कन्फर्म) (?:hua|hui|हुआ|हुई)(?: (?:kya|क्या))?$",
    )
    private val PNR_NUMBER = Regex("^(?:pnr|पीएनआर)? ?(\\d{10})(?: (?:ka|ki|का|की))? (?:status|स्टेटस)(?: (?:kya hai|batao|bata do|क्या है|बताओ))?$")

    private fun pnr(t: String): AssistantAction? {
        PNR_NUMBER.matchEntire(t.replace(Regex("(?<=\\d) (?=\\d)"), ""))?.let { return AssistantAction.PnrStatus(it.groupValues[1]) }
        return if (PNR_MINE.matches(t)) AssistantAction.PnrStatus("") else null
    }

    // ------------------------------------------------------------------ time and date

    private val TIME_Q = Regex(
        "^(?:abhi |अभी )?(?:(?:kitne|kitna|कितने|कितना) (?:baje|baj|बजे|बज)(?: (?:gaye|gye|rahe|गए|गये|रहे))?(?: (?:hain|hai|he|हैं|है))?|" +
            "(?:time|samay|टाइम|समय) (?:kya|kitna|क्या|कितना) (?:hua|hai|ho gaya|hua hai|हुआ|है|हो गया|हुआ है)|what time is it|time batao|टाइम बताओ|समय बताओ)$",
    )
    private val DATE_Q = Regex(
        "^(?:aaj|aj|आज) (?:kya|kaun si|kaunsi|kitni|क्या|कौन सी|कौनसी|कितनी) (?:date|tarikh|tareekh|तारीख|तारीख़|डेट)(?: (?:hai|he|है))?$|" +
            "^(?:aaj|aj|आज) (?:kaun sa|kaunsa|kya|कौन सा|कौनसा|क्या) (?:din|वार|दिन|day)(?: (?:hai|he|है))?$|" +
            "^(?:date|tarikh|तारीख|डेट) (?:batao|bata do|बताओ|बता दो)$",
    )

    private fun clock(t: String): AssistantAction? = when {
        TIME_Q.matches(t) -> AssistantAction.TellTime(date = false)
        DATE_Q.matches(t) -> AssistantAction.TellTime(date = true)
        else -> null
    }

    // ------------------------------------------------------------------ open an app

    private val OPEN_A = Regex("^(.{2,30}?)(?: (?:app|ऐप|एप))? (?:kholo|khol do|khol de|open karo|open kar do|open kardo|chalu karo|start karo|खोलो|खोल दो|खोल दे|ओपन करो|ओपन कर दो|चालू करो)$")
    private val OPEN_B = Regex("^open ([a-z0-9 ]{2,30}?)(?: app)?$")
    private val NOT_AN_APP = Regex("(?:^| )(?:darwaza|darwaja|door|window|khidki|दरवाज़ा|दरवाजा|खिड़की|aur|और|and|phir|फिर|ko|को|se|से|par|पर|pe|पे|mein|में|ka|का|ki|की)(?: |$)")

    private fun openApp(t: String): AssistantAction? {
        val name = (OPEN_A.matchEntire(t) ?: OPEN_B.matchEntire(t))?.groupValues?.get(1)?.trim() ?: return null
        if (name.split(' ').size > 3 || NOT_AN_APP.containsMatchIn(name) || name.any { it.isDigit() }) return null
        return AssistantAction.OpenApp(name)
    }
}
