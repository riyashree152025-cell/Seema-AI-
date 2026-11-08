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
}
