package com.raju.aiassistant.domain.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCameraTest {
    @Test
    fun liveCameraIsStartedAndStopped() {
        assertEquals(LiveCameraCommand.Start(front = false), parseLiveCamera("live camera chalu karo"))
        assertEquals(LiveCameraCommand.Start(front = false), parseLiveCamera("Camera live karo"))
        assertEquals(LiveCameraCommand.Start(front = true), parseLiveCamera("front wala live camera on karo"))
        assertEquals(LiveCameraCommand.Start(front = false), parseLiveCamera("लाइव कैमरा चालू करो"))
        assertEquals(LiveCameraCommand.Stop, parseLiveCamera("camera band karo"))
        assertEquals(LiveCameraCommand.Stop, parseLiveCamera("live camera band"))
        assertEquals(LiveCameraCommand.Stop, parseLiveCamera("कैमरा बंद करो"))
        assertNull(parseLiveCamera("camera kholo"))
        assertNull(parseLiveCamera("camera se dekh ke batao ye kya hai"))
        assertNull(parseLiveCamera("live cricket score batao"))
    }

    @Test
    fun aCameraQuestionIsAnsweredFromOnePhoto() {
        // What the user said on the phone.
        assertEquals(false, parseLookCamera("back camera chalu karo peeche kya dikh raha hai mere ko batao")?.first)
        assertEquals(true, parseLookCamera("front camera se dekh ke batao main kaisa lag raha hoon")?.first)
        assertEquals(false, parseLookCamera("कैमरा से देख के बताओ ये क्या है")?.first)
        assertNull(parseLookCamera("camera kholo"))
        assertNull(parseLookCamera("live camera chalu karo"))
    }
}
