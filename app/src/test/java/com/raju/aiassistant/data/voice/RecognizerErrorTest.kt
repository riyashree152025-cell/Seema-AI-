package com.raju.aiassistant.data.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizerErrorTest {
    @Test fun wordsHeardBeforeANoMatchAreUsedNotThrownAway() {
        assertEquals(RecognizerOutcome.USE_PARTIAL, classifyRecognizerError(SpeechRecognizer.ERROR_NO_MATCH, "kal ka mausam", speechBegan = true, sinceReadyMs = 3_000))
        assertEquals(RecognizerOutcome.USE_PARTIAL, classifyRecognizerError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, "Seema", speechBegan = false, sinceReadyMs = 6_000))
    }

    @Test fun aNoMatchBeforeTheUserCouldSpeakIsRetriedNotReportedAsSilence() {
        assertEquals(RecognizerOutcome.EARLY_NO_MATCH, classifyRecognizerError(SpeechRecognizer.ERROR_NO_MATCH, "", speechBegan = false, sinceReadyMs = 300))
        // Long enough for the user to have spoken: real silence.
        assertEquals(RecognizerOutcome.ERROR, classifyRecognizerError(SpeechRecognizer.ERROR_NO_MATCH, "", speechBegan = false, sinceReadyMs = 5_000))
        // Error before the microphone was even ready: not "early no match".
        assertEquals(RecognizerOutcome.ERROR, classifyRecognizerError(SpeechRecognizer.ERROR_NO_MATCH, "", speechBegan = false, sinceReadyMs = -1))
    }

    @Test fun speechWithoutWordsIsUnclearSoSeemaAsksAgain() {
        assertEquals(RecognizerOutcome.UNCLEAR, classifyRecognizerError(SpeechRecognizer.ERROR_NO_MATCH, " ", speechBegan = true, sinceReadyMs = 4_000))
    }

    @Test fun otherErrorsKeepTheirOwnMessage() {
        assertEquals(RecognizerOutcome.ERROR, classifyRecognizerError(SpeechRecognizer.ERROR_NETWORK, "partial", speechBegan = true, sinceReadyMs = 2_000))
        assertEquals(RecognizerOutcome.ERROR, classifyRecognizerError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, "", speechBegan = false, sinceReadyMs = 100))
    }
}
