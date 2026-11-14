package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every action the chat AI's instructions (PromptBuilder) describe, with exactly the fields the instructions
 * name, must be understood by the parser. A field name that differs between the two (once "type: gave|got"
 * for the udhaar book, which clashed with the action's own "type") made a whole feature silently do nothing.
 */
class ActionContractTest {
    private val samples = listOf(
        """{"type":"call","contact":"Mom"}""", """{"type":"call","number":"9876543210"}""", """{"type":"end_call"}""", """{"type":"answer_call"}""",
        """{"type":"find_contact","name":"Rahul"}""", """{"type":"last_call"}""", """{"type":"read_notification"}""",
        """{"type":"whatsapp","contact":"Rahul","text":"hi"}""", """{"type":"whatsapp_file","contact":"Rahul","file":"ticket"}""",
        """{"type":"whatsapp_peek","contact":"Anand"}""", """{"type":"whatsapp_play_voice","contact":"Rizwan"}""",
        """{"type":"whatsapp_call","contact":"Rahul"}""", """{"type":"whatsapp_video_call","contact":"Rahul"}""", """{"type":"sms","contact":"Rahul","text":"hi"}""",
        """{"type":"open_app","name":"Chrome"}""", """{"type":"open_settings","page":"wifi"}""", """{"type":"go_home"}""", """{"type":"open_seema"}""",
        """{"type":"search_web","query":"mausam"}""", """{"type":"play_music","query":"Kishore"}""", """{"type":"media","command":"pause"}""",
        """{"type":"volume","percent":40}""", """{"type":"flashlight","state":"on"}""", """{"type":"battery"}""", """{"type":"find_phone"}""",
        """{"type":"set_timer","minutes":5}""", """{"type":"set_timer","seconds":90}""", """{"type":"set_alarm","hour":6,"minute":30}""",
        """{"type":"add_event","title":"Meeting","start":"2026-11-20T10:00"}""",
        """{"type":"take_photo","camera":"back"}""", """{"type":"look","camera":"front","question":"kya hai"}""", """{"type":"screenshot"}""",
        """{"type":"navigate","destination":"Howrah"}""", """{"type":"copy","text":"abc"}""", """{"type":"remember","text":"dukan ka naam Raju Travels"}""",
        """{"type":"ledger_add","name":"Rahul","amount":"500","kind":"got"}""", """{"type":"ledger_add","name":"Rahul","amount":"500","kind":"gave"}""",
        """{"type":"ledger_balance","name":"Rahul"}""", """{"type":"ledger_undo"}""",
        """{"type":"tatkal_set","time":"09:58","class":"AC"}""", """{"type":"tatkal_list"}""", """{"type":"tatkal_cancel","all":true}""", """{"type":"lock_screen"}""",
        """{"type":"reminder_add","text":"dawai","in_minutes":30}""", """{"type":"reminder_list"}""",
        """{"type":"notification_summary"}""", """{"type":"notification_reply","text":"aa raha hoon"}""", """{"type":"calendar_read","day_offset":1}""",
        """{"type":"read_screen","question":"kya likha hai"}""", """{"type":"look_at_screen","question":"error kya hai"}""",
        """{"type":"tap","target":"Send"}""", """{"type":"go_back"}""", """{"type":"scroll","direction":"down"}""", """{"type":"type_text","text":"hello"}""",
        """{"type":"expense_add","amount":"200","note":"chai"}""", """{"type":"expense_undo"}""", """{"type":"expense_summary","period":"today"}""",
        """{"type":"pnr_add","pnr":"4512367890","date":"2026-11-20"}""", """{"type":"pnr_list"}""", """{"type":"pnr_status","pnr":"4512367890"}""",
        """{"type":"app_search","app":"youtube","query":"kishore kumar"}""",
    )

    @Test
    fun everyDescribedActionIsUnderstood() {
        val missing = samples.filter { parseReply("<action>$it</action>").actions.isEmpty() }
        assertTrue("Not understood: $missing", missing.isEmpty())
    }
}
