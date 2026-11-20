package com.raju.aiassistant.domain.camera

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraVoiceTest {
    @Test
    fun startPhrases() {
        assertEquals(false, CameraVoice.startRequest("Seema video recording chalu karo"))
        assertEquals(false, CameraVoice.startRequest("वीडियो रिकॉर्डिंग चालू करो"))
        assertEquals(false, CameraVoice.startRequest("वीडियो बनाओ"))
        assertEquals(false, CameraVoice.startRequest("recording shuru karo"))
        assertEquals(true, CameraVoice.startRequest("front camera se video banao"))
        assertEquals(false, CameraVoice.startRequest("ভিডিও রেকর্ডিং চালু করো"))
        assertNull(CameraVoice.startRequest("YouTube pe video chalao"))
        assertNull(CameraVoice.startRequest("video bhejo Rahul ko"))
    }

    @Test
    fun stopPhrases() {
        assertTrue(CameraVoice.isStop("ठीक है बंद कर दो"))
        assertTrue(CameraVoice.isStop("Seema, video band karo"))
        assertTrue(CameraVoice.isStop("recording rok do"))
        assertTrue(CameraVoice.isStop("বন্ধ করো"))
        assertFalse(CameraVoice.isStop("kal milte hain"))
        assertFalse(CameraVoice.isStop("light band karo"))
    }

    @Test
    fun durationWords() {
        assertEquals("1 मिनट 5 सेकंड", CameraVoice.duration(65, bengali = false))
        assertEquals("40 सेकंड", CameraVoice.duration(40, bengali = false))
        assertEquals("2 মিনিট", CameraVoice.duration(120, bengali = true))
    }

    @Test
    fun everyWayOfAskingForAPhotoTakesOne() {
        for (said in listOf("photo lo", "फ़ोटो क्लिक करो", "photo click kar do", "camera kholo aur photo click karo", "कैमरा खोल के फोटो लो", "একটা ছবি তোলো")) {
            assertEquals(said, AssistantAction.TakePhoto(front = false), LocalCommands.parse(said))
        }
        assertEquals(AssistantAction.TakePhoto(front = true), LocalCommands.parse("সেলফি তোলো"))
        assertEquals(AssistantAction.StartVideo(front = false), LocalCommands.parse("video recording chalu karo"))
    }
}
