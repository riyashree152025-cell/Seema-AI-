package com.raju.aiassistant.domain.assistant

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
}
