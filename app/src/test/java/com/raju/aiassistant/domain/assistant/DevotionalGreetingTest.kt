package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevotionalGreetingTest {
    @Test
    fun theGreetingIsAnsweredWithTheSameGreeting() {
        mapOf(
            "राधे राधे" to "राधे राधे", "Radhe Radhe" to "राधे राधे", "राधे राधे राधे राधे" to "राधे राधे", "सीमा, राधे राधे जी" to "राधे राधे",
            "जय श्री कृष्णा" to "जय श्री कृष्णा", "jai shree krishna" to "जय श्री कृष्णा", "जय श्री कृष्ण" to "जय श्री कृष्णा",
            "जय श्री राम" to "जय श्री राम", "Jai Shri Ram" to "जय श्री राम", "राम राम" to "राम राम", "Ram Ram ji" to "राम राम",
            "हर हर महादेव" to "हर हर महादेव", "ॐ नमः शिवाय" to "ॐ नमः शिवाय", "जय माता दी" to "जय माता दी",
            "हरे कृष्णा हरे कृष्णा" to "हरे कृष्णा", "जय सिया राम" to "जय सिया राम", "जय बजरंगबली" to "जय बजरंगबली",
            "जय हनुमान" to "जय बजरंगबली", "गणपति बप्पा मोरया" to "गणपति बप्पा मोरया, मंगलमूर्ति मोरया",
            "Hey Seema, jai mata di" to "जय माता दी", "जय भोलेनाथ" to "जय भोलेनाथ", "जय श्री महाकाल" to "जय श्री महाकाल",
            "अस्सलाम वालेकुम" to "वालेकुम अस्सलाम", "Assalamu Alaikum" to "वालेकुम अस्सलाम", "सत श्री अकाल" to "सत श्री अकाल",
            "জয় শ্রী রাম" to "জয় শ্রী রাম", "রাধে রাধে" to "রাধে রাধে", "জয় মা কালী" to "জয় মা কালী", "হরে কৃষ্ণ" to "হরে কৃষ্ণ",
        ).forEach { (said, reply) -> assertEquals(said, reply, DevotionalGreeting.reply(said)) }
    }

    @Test
    fun aRequestThatOnlyStartsWithAGreetingGoesOnAsARequest() {
        assertNull(DevotionalGreeting.reply("राधे राधे, आज का मौसम बताओ"))
        assertNull(DevotionalGreeting.reply("राम को कॉल करो"))
        assertNull(DevotionalGreeting.reply("jai shree ram ka gana chalao"))
        assertNull(DevotionalGreeting.reply("राधा कौन थी"))
    }

    @Test
    fun theFastRouteTakesIt() {
        assertEquals(AssistantAction.Greeting("राधे राधे"), LocalCommands.parse("राधे राधे"))
        assertEquals(AssistantAction.Greeting("जय श्री कृष्णा"), LocalCommands.parse("Seema, jai shri krishna"))
    }
}
