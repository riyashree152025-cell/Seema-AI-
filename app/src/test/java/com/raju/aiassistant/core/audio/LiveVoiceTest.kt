package com.raju.aiassistant.core.audio

import android.media.AudioManager
import com.raju.aiassistant.domain.model.AssistantState
import com.raju.aiassistant.domain.voice.WAKE_WORD_KEYWORDS
import com.raju.aiassistant.domain.voice.WAKE_WORD_MODEL
import com.raju.aiassistant.domain.voice.WAKE_WORD_THRESHOLD
import com.raju.aiassistant.notifications.VoiceSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVoiceTest {
    @Test fun seemasOwnHandsFreeModeIsNotMistakenForAPhoneCall() {
        assertEquals(CallPhase.NONE, AudioInterruptions.callPhaseForMode(AudioManager.MODE_IN_COMMUNICATION))
        assertEquals(CallPhase.NONE, AudioInterruptions.callPhaseForMode(AudioManager.MODE_NORMAL))
        assertEquals(CallPhase.RINGING, AudioInterruptions.callPhaseForMode(AudioManager.MODE_RINGTONE))
        assertEquals(CallPhase.ACTIVE, AudioInterruptions.callPhaseForMode(AudioManager.MODE_IN_CALL))
    }

    @Test fun wakeWordKeywordsAreOnlyHeySeemaVariants() {
        val lines = WAKE_WORD_KEYWORDS.split("/")
        assertEquals(6, lines.size)
        lines.forEach { line ->
            assertTrue(line, line.endsWith("@HEY_SEEMA"))
            assertTrue(line, line.contains(":2.0"))
            assertTrue(line, line.startsWith("▁")) // BPE tokens, not plain words
        }
        assertTrue(lines.any { it.startsWith("▁HE Y ▁SEE MA ") })
        assertTrue(WAKE_WORD_THRESHOLD in 0.05f..0.5f)
    }

    @Test fun wakeWordModelCatalogIsComplete() {
        val m = WAKE_WORD_MODEL
        assertTrue(Regex("[0-9a-f]{64}").matches(m.sha256))
        assertTrue(m.sizeBytes > 1_000_000)
        assertTrue(m.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/"))
        listOf(m.modelFile, m.decoderFile, m.joinerFile).forEach { assertTrue(it, it.endsWith(".int8.onnx")) }
    }

    @Test fun theNotificationAlwaysSaysTheMicrophoneIsOn() {
        listOf(AssistantState.Listening, AssistantState.Sleeping, AssistantState.Idle).forEach {
            assertTrue(VoiceSessionService.statusText(it).startsWith("Microphone ON"))
        }
        assertTrue(VoiceSessionService.statusText(AssistantState.Sleeping).contains("Hey Seema"))
    }
}
