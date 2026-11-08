package com.raju.aiassistant.data.voice

import com.raju.aiassistant.domain.model.UserSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicHubFlagTest {
    /** Phase 1 sealed: the shared microphone is on unless the user switches it off in Settings. */
    @Test
    fun theSharedMicrophoneIsOnByDefault() {
        assertTrue(UserSettings().micHub)
        assertFalse(UserSettings(micHub = false).micHub) // the legacy recorders stay one toggle away
    }
}
