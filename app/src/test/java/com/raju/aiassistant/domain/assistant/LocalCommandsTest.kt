package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.tools.MediaCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCommandsTest {
    private fun p(text: String) = LocalCommands.parse(text)

    @Test
    fun torchInHinglishAndHindi() {
        assertEquals(AssistantAction.Flashlight(true), p("torch on karo"))
        assertEquals(AssistantAction.Flashlight(true), p("Torch on kar do na"))
        assertEquals(AssistantAction.Flashlight(true), p("Hey Seema, torch on karo"))
        assertEquals(AssistantAction.Flashlight(true), p("टॉर्च जलाओ"))
        assertEquals(AssistantAction.Flashlight(false), p("टॉर्च बंद करो"))
        assertEquals(AssistantAction.Flashlight(false), p("flashlight off"))
    }

    @Test
    fun volumeNeedsANumberOrFull() {
        assertEquals(AssistantAction.Volume(50), p("volume 50"))
        assertEquals(AssistantAction.Volume(30), p("आवाज़ 30 प्रतिशत कर दो"))
        assertEquals(AssistantAction.Volume(100), p("volume full karo"))
        assertNull(p("volume 150"))
    }

    @Test
    fun timers() {
        assertEquals(AssistantAction.SetTimer(300, ""), p("5 minute ka timer lagao"))
        assertEquals(AssistantAction.SetTimer(600, ""), p("टाइमर 10 मिनट का लगाओ"))
        assertEquals(AssistantAction.SetTimer(7200, ""), p("2 ghante ka timer"))
        assertEquals(AssistantAction.SetTimer(30, ""), p("30 second ka timer"))
    }

    @Test
    fun alarmsOnlyWhenTheTimeIsClear() {
        assertEquals(AssistantAction.SetAlarm(6, 0, ""), p("subah 6 baje ka alarm lagao"))
        assertEquals(AssistantAction.SetAlarm(6, 30, ""), p("6:30 baje subah ka alarm"))
        assertEquals(AssistantAction.SetAlarm(19, 0, ""), p("alarm shaam 7 baje"))
        assertEquals(AssistantAction.SetAlarm(22, 0, ""), p("रात 10 बजे का अलार्म लगाओ"))
        assertEquals(AssistantAction.SetAlarm(18, 45, ""), p("18:45 ka alarm"))
        assertEquals(AssistantAction.SetTatkalAlarm(9, 58, "AC"), p("tatkal alarm 9:58 AC"))
        assertEquals(AssistantAction.SetTatkalAlarm(10, 58, "Sleeper"), p("10:58 baje sleeper ke liye tatkal yaad dilao"))
        assertEquals(null, p("6 baje ka alarm")) // still asked: morning or evening?
        assertEquals(AssistantAction.TatkalList, p("tatkal alarm list karo"))
        assertEquals(AssistantAction.TatkalCancel(9, 58, "AC"), p("9:58 AC tatkal cancel karo"))
        assertEquals(AssistantAction.TatkalCancelAll, p("sab tatkal alarm hata do"))
        assertEquals(AssistantAction.LedgerRecord("Rahul", 50_000, com.raju.aiassistant.domain.ledger.LedgerKind.GOT), p("Rahul se 500 rupaye mile"))
        assertEquals(AssistantAction.LedgerBalance("Rahul"), p("Rahul ka hisaab batao"))
        // "Hey Seema" in front is not part of the client's name.
        assertEquals(AssistantAction.LedgerRecord("राहुल", 50_000, com.raju.aiassistant.domain.ledger.LedgerKind.GOT), p("हे सीमा, राहुल से 500 मिले"))
        assertEquals(AssistantAction.LedgerRecord("Rahul", 50_000, com.raju.aiassistant.domain.ledger.LedgerKind.GOT), p("Seema, Rahul se 500 rupaye mile"))
        assertEquals(AssistantAction.TatkalList, p("Hey Seema tatkal alarm list karo"))
        assertEquals(AssistantAction.LockScreen, p("screen lock karo"))
        assertEquals(AssistantAction.LookAtScreen(null), p("screen dekh ke batao"))
        assertEquals(AssistantAction.LookAtScreen(null), p("ye error kya hai"))
        assertEquals(AssistantAction.LookAtScreen(null), p("स्क्रीन देखो"))
        assertEquals(AssistantAction.LockScreen, p("फोन लॉक करो"))
        assertEquals(AssistantAction.LedgerUndo, p("aakhri entry hatao"))
        assertEquals(AssistantAction.LedgerRecord("Rahul", 50_000, com.raju.aiassistant.domain.ledger.LedgerKind.GOT), p("Rahul ne mujhe 500 diye"))
        // A dotted time from speech recognition is a time.
        assertEquals(AssistantAction.SetAlarm(6, 30, ""), p("6.30 baje subah ka alarm"))
        assertNull(p("6 baje ka alarm")) // morning or evening? the AI asks
    }

    @Test
    fun cameraAndPhotos() {
        assertEquals(AssistantAction.TakePhoto(front = false), p("photo lo"))
        assertEquals(AssistantAction.TakePhoto(front = false), p("फोटो खींचो"))
        assertEquals(AssistantAction.TakePhoto(front = true), p("selfie lo"))
        assertEquals(AssistantAction.TakePhoto(front = true), p("front camera se photo lo"))
        assertEquals(AssistantAction.OpenApp("Camera"), p("camera kholo"))
        assertEquals(AssistantAction.OpenApp("Camera"), p("कैमरा खोलो"))
        assertNull(p("front camera se dekho main kaisa lag raha hoon")) // needs the AI to look
    }

    @Test
    fun calls() {
        assertEquals(AssistantAction.Call("mummy", null), p("Mummy ko call karo"))
        assertEquals(AssistantAction.Call("मम्मी", null), p("मम्मी को कॉल करो"))
        assertEquals(AssistantAction.Call("mom", null), p("call mom"))
        assertEquals(AssistantAction.Call(null, "98765 43210"), p("98765 43210 ko call karo"))
        assertNull(p("call karo"))
        assertNull(p("whatsapp par mummy ko call karo"))
        assertEquals(AssistantAction.EndCall, p("call kaat do"))
        assertEquals(AssistantAction.EndCall, p("फोन रख दो"))
        assertEquals(AssistantAction.AnswerCall, p("call uthao"))
    }

    @Test
    fun batteryHomeAndApps() {
        assertEquals(AssistantAction.Battery, p("battery kitni hai"))
        assertEquals(AssistantAction.Battery, p("कितनी बैटरी है"))
        assertEquals(AssistantAction.GoHome, p("home screen par jao"))
        assertEquals(AssistantAction.OpenApp("youtube"), p("youtube kholo"))
        assertEquals(AssistantAction.OpenApp("whatsapp"), p("WhatsApp kholo"))
        assertNull(p("darwaza kholo"))
        assertNull(p("mummy ka message kholo"))
    }

    @Test
    fun anythingMoreGoesToTheAi() {
        assertNull(p("torch on karo aur gaana chalao"))
        assertNull(p("bharat ki rajdhani kya hai"))
        assertNull(p("kaise ho"))
        assertNull(p("आज मौसम कैसा है"))
        assertNull(p(""))
    }

    @Test
    fun readingMessages() {
        assertEquals(AssistantAction.ReadNotification, p("message padho"))
        assertEquals(AssistantAction.ReadNotification, p("notification sunao"))
        assertEquals(AssistantAction.ReadNotification, p("kiska message aaya"))
        assertEquals(AssistantAction.ReadNotification, p("मैसेज पढ़ो"))
        assertNull(p("mummy ko message bhejo"))
    }

    // ---------------------------------------------------------------- Phase 2: fast intent router

    @Test
    fun musicKeys() {
        assertEquals(AssistantAction.Media(MediaCommand.PAUSE), p("gaana band karo"))
        assertEquals(AssistantAction.Media(MediaCommand.PAUSE), p("गाना रोको"))
        assertEquals(AssistantAction.Media(MediaCommand.PAUSE), p("music pause kar do"))
        assertEquals(AssistantAction.Media(MediaCommand.PAUSE), p("pause"))
        assertEquals(AssistantAction.Media(MediaCommand.PLAY), p("gaana phir se chalao"))
        assertEquals(AssistantAction.Media(MediaCommand.PLAY), p("गाना फिर से चलाओ"))
        assertEquals(AssistantAction.Media(MediaCommand.PLAY), p("resume"))
        assertEquals(AssistantAction.Media(MediaCommand.NEXT), p("agla gaana chalao"))
        assertEquals(AssistantAction.Media(MediaCommand.NEXT), p("अगला गाना"))
        assertEquals(AssistantAction.Media(MediaCommand.NEXT), p("next song"))
        assertEquals(AssistantAction.Media(MediaCommand.NEXT), p("gaana skip karo"))
        assertEquals(AssistantAction.Media(MediaCommand.PREVIOUS), p("pichla gaana"))
        // A song by name needs the AI (search + the user's music app).
        assertNull(p("arijit ka gaana chalao"))
        assertNull(p("play kesariya"))
        assertNull(p("gaana chalao"))
    }

    @Test
    fun screenshotFindPhoneAndLastCall() {
        assertEquals(AssistantAction.Screenshot, p("screenshot lo"))
        assertEquals(AssistantAction.Screenshot, p("ek screenshot le lo na"))
        assertEquals(AssistantAction.Screenshot, p("स्क्रीनशॉट लो"))
        assertEquals(AssistantAction.FindPhone, p("mera phone dhundo"))
        assertEquals(AssistantAction.FindPhone, p("kahan ho seema"))
        assertEquals(AssistantAction.FindPhone, p("Seema, kahan ho?"))
        assertEquals(AssistantAction.FindPhone, p("सीमा कहाँ हो"))
        assertEquals(AssistantAction.FindPhone, p("फोन की घंटी बजाओ"))
        assertEquals(AssistantAction.LastCall, p("last call kiska tha"))
        assertEquals(AssistantAction.LastCall, p("kiska call aaya tha"))
        assertEquals(AssistantAction.LastCall, p("आखिरी कॉल किसका था"))
        assertNull(p("mummy ka phone kahan hai")) // not my phone
    }

    @Test
    fun settingsPages() {
        assertEquals(AssistantAction.OpenSettings(SettingsPage.WIFI), p("wifi settings kholo"))
        assertEquals(AssistantAction.OpenSettings(SettingsPage.BLUETOOTH), p("ब्लूटूथ सेटिंग खोलो"))
        assertEquals(AssistantAction.OpenSettings(SettingsPage.BATTERY), p("battery settings"))
        assertEquals(AssistantAction.OpenSettings(SettingsPage.DISPLAY), p("display ki settings kholo"))
        assertEquals(AssistantAction.OpenSettings(SettingsPage.GENERAL), p("settings kholo"))
        assertEquals(AssistantAction.Battery, p("battery kitni hai")) // still the battery level, not its settings page
    }

    @Test
    fun backAndSeemasOwnScreen() {
        assertEquals(AssistantAction.ScreenBack, p("peeche jao"))
        assertEquals(AssistantAction.ScreenBack, p("पीछे जाओ"))
        assertEquals(AssistantAction.OpenSeema, p("apni screen par aao"))
        assertEquals(AssistantAction.OpenSeema, p("apna app kholo"))
        assertNull(p("peeche jao aur send dabao"))
    }

    @Test
    fun timeAndDate() {
        assertEquals(AssistantAction.TellTime(date = false), p("kitne baje hain"))
        assertEquals(AssistantAction.TellTime(date = false), p("abhi kitne baje hai?"))
        assertEquals(AssistantAction.TellTime(date = false), p("कितने बजे हैं"))
        assertEquals(AssistantAction.TellTime(date = false), p("time kya hua"))
        assertEquals(AssistantAction.TellTime(date = true), p("aaj kya date hai"))
        assertEquals(AssistantAction.TellTime(date = true), p("आज कौन सा दिन है"))
        assertEquals(AssistantAction.TellTime(date = true), p("aaj kaun si tarikh hai"))
        assertNull(p("delhi mein kitne baje hain")) // another city's time needs the AI
        assertNull(p("aaj kya khana banau"))
    }

    @Test
    fun conversationStillGoesToTheAi() {
        for (q in listOf("kaise ho", "gaane ke baare mein batao", "bharat ki rajdhani kya hai", "ek kahani sunao", "time management kaise karu")) {
            assertNull(q, p(q))
        }
    }

    // ---------------------------------------------------------------- Phase 3: WhatsApp message

    @Test
    fun whatsAppMessagesKeepTheWordsAsSaid() {
        val wa = { who: String, msg: String -> AssistantAction.WhatsApp(contact = who, text = msg) }
        assertEquals(wa("Mummy", "main 8 baje aaunga"), p("WhatsApp par Mummy ko main 8 baje aaunga bhejo"))
        assertEquals(wa("Family Group", "Kal Milte Hain"), p("Hey Seema, WhatsApp pe Family Group ko 'Kal Milte Hain' bhej do."))
        assertEquals(wa("Riya", "kal milte hain"), p("WhatsApp par Riya ko message bhejo ki kal milte hain"))
        assertEquals(wa("Papa", "main pahunch gaya"), p("Papa ko WhatsApp par bolo ki main pahunch gaya"))
        assertEquals(wa("Raju bhai", "late ho jaunga"), p("Raju bhai ko WhatsApp par late ho jaunga likh do"))
        assertEquals(wa("मम्मी", "मैं आ रहा हूँ"), p("व्हाट्सएप पर मम्मी को मैं आ रहा हूँ भेजो"))
        assertEquals(wa("दीदी", "खाना खा लिया"), p("दीदी को व्हाट्सएप पर बोलो कि खाना खा लिया"))
    }

    @Test
    fun aWhatsAppRequestWithoutWordsOrAPersonGoesToTheAi() {
        assertNull(p("WhatsApp par Riya ko message bhejo")) // what to write? the AI asks
        assertNull(p("WhatsApp kholo aur Riya ko message bhejo"))
        assertNull(p("WhatsApp par usko hello bhejo")) // who is "usko"? the AI knows the conversation
        assertEquals(AssistantAction.OpenApp("whatsapp"), p("WhatsApp kholo"))
    }

    @Test
    fun readingTheScreen() {
        for (q in listOf("screen padho", "Seema, screen par kya hai?", "ye screen padh ke sunao", "स्क्रीन पर क्या लिखा है", "स्क्रीन पढ़ो")) {
            assertEquals(q, AssistantAction.ReadScreen(null), p(q))
        }
        assertNull(p("screen par kya likha hai Riya ne")) // a question about the screen: the AI reads it with the question
    }

    @Test
    fun whatsAppDocuments() {
        assertEquals(AssistantAction.WhatsAppFile("Riya", "ticket wali PDF"), p("WhatsApp par Riya ko ticket wali PDF bhejo"))
        assertEquals(AssistantAction.WhatsAppFile("Sharma ji", "invoice file"), p("Sharma ji ko WhatsApp par invoice file bhej do"))
        assertEquals(AssistantAction.WhatsAppFile("मम्मी", "टिकट वाली फाइल"), p("व्हाट्सएप पर मम्मी को टिकट वाली फाइल भेजो"))
        assertEquals(AssistantAction.WhatsAppFile("Riya", "latest pdf"), p("WhatsApp par Riya ko latest pdf bhejo"))
        // A sentence that only mentions a file is a message.
        assertEquals(AssistantAction.WhatsApp("Riya", "file mil gayi kya"), p("WhatsApp par Riya ko file mil gayi kya bhejo"))
    }

    @Test
    fun pnrStatusByVoice() {
        assertEquals(AssistantAction.PnrStatus(""), p("mera PNR status kya hai"))
        assertEquals(AssistantAction.PnrStatus(""), p("PNR status batao"))
        assertEquals(AssistantAction.PnrStatus(""), p("meri ticket confirm hui kya"))
        assertEquals(AssistantAction.PnrStatus("4512345678"), p("PNR 4512345678 ka status"))
        assertEquals(AssistantAction.PnrStatus("4512345678"), p("45123 45678 ka status batao"))
        assertNull(p("PNR kaise check karte hain"))
    }

    @Test
    fun lastCallAndLastMessageAsPeopleAskThem() {
        listOf("last call kiski aayi thi", "aakhri call kiska tha", "kiski call aayi thi", "last call kisne kiya tha", "kisne call kiya tha", "आखिरी कॉल किसकी थी", "किसका फोन आया था")
            .forEach { assertEquals(it, AssistantAction.LastCall, p(it)) }
        listOf("kiska message aaya tha", "kiska message aaya", "last message kiska tha", "kisne message bheja tha", "किसका मैसेज आया था")
            .forEach { assertEquals(it, AssistantAction.ReadNotification, p(it)) }
        assertNull(p("kisne kiya tha")) // no call / message word: could be about anything
    }

    @Test
    fun smsAndScreenActionsAreUnderstoodOnThePhone() {
        assertEquals(AssistantAction.Sms("Firoz", null, "abhi tak payment nahi mila hai"), p("Firoz ko message karo ki abhi tak payment nahi mila hai"))
        assertEquals(AssistantAction.Sms("Mummy", null, "main 8 baje aaunga"), p("Seema, Mummy ko SMS bhejo ki main 8 baje aaunga."))
        assertEquals(AssistantAction.Sms(null, "98765 43210", "pahunch gaya"), p("98765 43210 ko message bhejo ki pahunch gaya"))
        assertEquals(AssistantAction.WhatsApp("Riya", "kal milte hain"), p("WhatsApp par Riya ko message bhejo ki kal milte hain")) // WhatsApp stays WhatsApp
        assertNull(p("mom ko sms karo")) // no words: the AI asks what to write

        assertEquals(AssistantAction.ScreenTap("send"), p("send dabao"))
        assertEquals(AssistantAction.ScreenTap("ok"), p("OK button press karo"))
        assertEquals(AssistantAction.ScreenTap("install"), p("install pe click karo"))
        assertNull(p("call dabao"))

        assertEquals(AssistantAction.ScreenScroll(down = true), p("neeche scroll karo"))
        assertEquals(AssistantAction.ScreenScroll(down = false), p("scroll up"))
        assertEquals(AssistantAction.ScreenType("kal milte hain", null), p("likho kal milte hain"))
        assertEquals(AssistantAction.ScreenType("main aa raha hoon", null), p("type karo ki main aa raha hoon"))
    }
}
