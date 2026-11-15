package com.raju.aiassistant.domain.assistant

class SendTicketCommandTest {
    @org.junit.Test
    fun sendingTheTicketGoesToTheChatAsADocument() {
        org.junit.Assert.assertEquals(
            AssistantAction.WhatsAppFile(contact = "Anand", file = "ticket"),
            LocalCommands.parse("ye ticket Anand ko WhatsApp par bhej do"),
        )
        org.junit.Assert.assertEquals(
            AssistantAction.WhatsAppFile(contact = "रिया", file = "टिकट"),
            LocalCommands.parse("ये टिकट रिया को व्हाट्सएप पर भेज दो"),
        )
    }
}
