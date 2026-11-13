package com.raju.aiassistant.domain.language

import org.junit.Assert.assertEquals
import org.junit.Test

class HindiNumbersTest {
    @Test
    fun spokenYearsAndNumbersBecomeDigits() {
        // What the phone's recogniser wrote.
        assertEquals("आनंद कुमार पटेल, 1986", hindiNumbersToDigits("आनंद कुमार पटेल, उन्नीस सौ छह छियासी"))
        assertEquals("1986 है, madam.", hindiNumbersToDigits("उन्नीस सौ छियासी है, madam."))
        assertEquals("2005", hindiNumbersToDigits("दो हज़ार पाँच"))
        assertEquals("2000", hindiNumbersToDigits("दो हजार"))
        assertEquals("उम्र 45 साल", hindiNumbersToDigits("उम्र पैंतालीस साल"))
        assertEquals("anand ka booking", hindiNumbersToDigits("anand ka booking"))
    }
}
