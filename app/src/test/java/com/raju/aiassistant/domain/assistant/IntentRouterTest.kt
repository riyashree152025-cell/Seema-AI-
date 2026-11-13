package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {
    @Test
    fun onlyTaskLikeSentencesUseTheShortRequest() {
        assertTrue(IntentRouter.looksLikeTask("आनंद का booking आया है कल के लिए, note down करके रखो"))
        assertTrue(IntentRouter.looksLikeTask("peeche wala camera chalu kar ke dekho"))
        assertFalse(IntentRouter.looksLikeTask("aaj mausam kaisa hai"))
        assertFalse(IntentRouter.looksLikeTask("tum kaisi ho"))
    }

    @Test
    fun theModelsAnswerBecomesAPhoneAction() {
        assertNull(IntentRouter.toAction("""{"type":"chat"}"""))
        assertNull(IntentRouter.toAction("not json"))
        assertEquals(
            AssistantAction.BookingNoteWith("Pune se Howrah 3A 14/11/2026 Anand Kumar Patel 1986"),
            IntentRouter.toAction("""{"type":"booking_note","from":"Pune","to":"Howrah","class":"3A","date":"2026-11-14","passenger":"Anand Kumar Patel","birth_year":"1986"}"""),
        )
        assertEquals(AssistantAction.TatkalReminder("Anand", sleeper = true), IntentRouter.toAction("""```json
{"type":"tatkal_reminder","name":"Anand","class":"sleeper"}
```"""))
        assertEquals(AssistantAction.LookCamera(front = false, question = "peeche kya hai"), IntentRouter.toAction("""{"type":"look_camera","camera":"back","question":"peeche kya hai"}"""))
        assertEquals(AssistantAction.Flashlight(on = false), IntentRouter.toAction("""{"type":"flashlight","state":"off"}"""))
        assertEquals(
            AssistantAction.LedgerRecord("Rahul", 50000, com.raju.aiassistant.domain.ledger.LedgerKind.GOT),
            IntentRouter.toAction("""{"type":"ledger_add","name":"Rahul","amount":"500","kind":"got"}"""),
        )
        assertNull(IntentRouter.toAction("""{"type":"ledger_add","name":"Rahul"}""")) // incomplete: not done
    }

    @Test
    fun theInstructionsStaySmall() {
        val prompt = IntentRouter.system(java.time.LocalDate.of(2026, 11, 13))
        assertTrue("${prompt.length} chars", prompt.length < 2_000)
    }
}
