package com.raju.aiassistant.domain.assistant

/**
 * Simple phone commands understood on the phone itself, without asking an AI: torch, volume,
 * timer, alarm, photo / selfie, camera, call, answer / end a call, battery, home screen and
 * opening an app. They cost no tokens, answer at once and also work without internet.
 *
 * Only a sentence that is nothing but such a command matches ("torch on karo", "Mummy ko call
 * karo", "6:30 baje subah ka alarm"). Anything more ("torch on karo aur gaana chalao"), anything
 * unclear ("6 baje ka alarm" — morning or evening?) and Bengali go to the AI as before.
 */
object LocalCommands {

    fun parse(text: String): AssistantAction? {
        val t = normalize(text)
        if (t.isEmpty() || t.length > 80) return null
        return readMessage(t) ?: torch(t) ?: volume(t) ?: timer(t) ?: alarm(t) ?: photo(t) ?: endCall(t) ?: answerCall(t) ?: call(t)
            ?: battery(t) ?: home(t) ?: openApp(t)
    }

    // ------------------------------------------------------------------ text clean-up

    private val FILLER_START = Regex("^(please|plz|pls|zara|jara|जरा|ज़रा|seema|सीमा|hey|हे)\\s+")
    private val FILLER_END = Regex("\\s+(please|plz|pls|na|ना|ji|जी|jaldi|जल्दी)$")

    /** Lower case, local digits as 0-9, no punctuation, single spaces, polite words at the ends removed. */
    internal fun normalize(text: String): String {
        var t = buildString {
            for (ch in text.lowercase()) {
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
            "^(?:kiska|kis ka|kya|किसका|किस का|क्या) $MSG (?:aaya|aya|aaya hai|aya hai|आया|आया है)$",
    )

    private fun readMessage(t: String): AssistantAction? = if (READ_MSG.matches(t)) AssistantAction.ReadNotification else null

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
    private val NOT_A_NAME = Regex("(?:^| )(?:whatsapp|व्हाट्सएप|व्हाट्सऐप|video|वीडियो|aur|और|and|phir|फिर|then|mujhe|मुझे|kisi|किसी|usko|उसको|isko|इसको|unko|उनको|sabko|सबको|ise|use|उसे|इसे|karo|करो|lagao|लगाओ|kar|कर|back|me|him|her)(?: |$)")

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
    private val HOME = Regex("^(?:home|होम)(?: (?:screen|स्क्रीन))?(?: (?:par|pe|पर|पे))? (?:jao|chalo|aao|aa jao|जाओ|चलो|आओ|आ जाओ)$")

    private fun battery(t: String): AssistantAction? = if (BATTERY.matches(t)) AssistantAction.Battery else null

    private fun home(t: String): AssistantAction? = if (HOME.matches(t)) AssistantAction.GoHome else null

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
