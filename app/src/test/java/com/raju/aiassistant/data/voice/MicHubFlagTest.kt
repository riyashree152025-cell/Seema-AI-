package com.raju.aiassistant.data.voice

import com.raju.aiassistant.domain.model.UserSettings
import org.junit.Assert.assertFalse
import org.junit.Test

class MicHubFlagTest {
    /** Until the flag is switched on by hand, every listener keeps its original recorder. */
    @Test
    fun theSharedMicrophoneIsOffByDefault() {
        assertFalse(UserSettings().micHub)
    }
}
