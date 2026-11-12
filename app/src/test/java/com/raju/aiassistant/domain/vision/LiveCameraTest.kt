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
}
