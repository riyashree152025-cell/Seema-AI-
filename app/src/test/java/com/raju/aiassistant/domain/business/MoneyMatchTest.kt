package com.raju.aiassistant.domain.business

class MoneyMatchTest {
    private val balances = listOf(
        com.raju.aiassistant.domain.ledger.ClientBalance("Rahul Kumar", 500_000, 2),
        com.raju.aiassistant.domain.ledger.ClientBalance("Amit", -10_000, 1),
        com.raju.aiassistant.domain.ledger.ClientBalance("Mitu", 300_000, 1),
    )

    @org.junit.Test
    fun thePayerIsMatchedToTheKhata() {
        org.junit.Assert.assertEquals("Rahul Kumar", MoneyMatch.client("RAHUL KUMAR", balances)?.name)
        org.junit.Assert.assertEquals("Rahul Kumar", MoneyMatch.client("Rahul", balances)?.name)
        org.junit.Assert.assertNull(MoneyMatch.client("Amit", balances)) // owes nothing
        org.junit.Assert.assertNull(MoneyMatch.client(null, balances))
        org.junit.Assert.assertTrue(MoneyMatch.question(balances[0], 200_000).contains("घटा दूँ"))
        org.junit.Assert.assertEquals("हो गया, Mitu का पूरा हिसाब चुकता।", MoneyMatch.done(balances[2], 300_000))
    }

    @org.junit.Test
    fun watchedAmounts() {
        org.junit.Assert.assertEquals(AmountWatch(375_000, null), AmountWatches.parse("3750 ka refund aaye to batana"))
        org.junit.Assert.assertEquals(AmountWatch(500_000, "Rahul"), AmountWatches.parse("Rahul ke 5000 aaye to batana"))
        org.junit.Assert.assertEquals(AmountWatch(375_000, null), AmountWatches.parse("৩৭৫০ টাকা এলে বলবে"))
        org.junit.Assert.assertNull(AmountWatches.parse("3750 aaya kya"))
        val w = AmountWatch(375_000, null)
        org.junit.Assert.assertEquals(w, AmountWatches.match(listOf(w), 375_050))
        org.junit.Assert.assertNull(AmountWatches.match(listOf(w), 400_000))
        org.junit.Assert.assertTrue(com.raju.aiassistant.domain.assistant.LocalCommands.parse("3750 ka refund aaye to batana") is com.raju.aiassistant.domain.assistant.AssistantAction.WatchAmount)
    }
}
