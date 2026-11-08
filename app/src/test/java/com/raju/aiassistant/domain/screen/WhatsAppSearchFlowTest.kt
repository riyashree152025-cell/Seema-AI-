package com.raju.aiassistant.domain.screen

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The WhatsApp search-and-send flow against a fake WhatsApp that changes screens like the real one. */
class WhatsAppSearchFlowTest {

    private val wa = "com.whatsapp"

    private fun el(text: String, id: String = "", clickable: Boolean = true, editable: Boolean = false, hint: String = "", kind: ElementKind = ElementKind.BUTTON) =
        ScreenElement(text, if (editable) ElementKind.INPUT else kind, clickable = clickable, editable = editable, hint = hint, id = if (id.isEmpty()) "" else "$wa:id/$id")

    /** A small WhatsApp: home → search → results → chat. */
    private inner class FakeWhatsApp(
        val chats: List<String>,
        var startInChat: String? = null,
        val sendWorks: Boolean = true,
        val titleOverride: String? = null,
        val ids: Boolean = true,
    ) : ScreenHands {
        var screen = "closed"
        var query = ""
        var openChat = ""
        var box = ""
        val sent = mutableListOf<Pair<String, String>>()
        val actions = mutableListOf<String>()
        var unreadable = 0

        private fun id(x: String) = if (ids) x else ""

        override suspend fun openWhatsApp(): Boolean {
            screen = if (startInChat != null) { openChat = startInChat!!; "chat" } else "home"
            return true
        }

        override suspend fun read(): ScreenSnapshot? {
            if (unreadable > 0) { unreadable--; return null }
            val els = when (screen) {
                "home" -> listOf(el("WhatsApp", clickable = false), el("Search", id("menuitem_search")), el("Chats"), el("Riya", id("conversations_row_contact_name")))
                "search" -> listOf(el(query, id("search_input"), editable = true, hint = "Search…")) +
                    (if (query.isEmpty()) emptyList() else listOf(el("Chats")) + chats.filter { ScreenMatch.normalize(it).contains(ScreenMatch.normalize(query)) }
                        .map { el(it, id("conversations_row_contact_name")) } + el("Messages") + el("$query aa raha hai kal", id("message_text")))
                "chat" -> listOf(
                    el(titleOverride ?: openChat, id("conversation_contact_name"), clickable = false, kind = ElementKind.TEXT),
                    el("purana message", clickable = false, kind = ElementKind.TEXT),
                    el(box, id("entry"), editable = true, hint = "Message"),
                ) + if (box.isNotEmpty()) listOf(el("Send", id("send"))) else emptyList()
                else -> return ScreenSnapshot("com.android.launcher", "Home", emptyList())
            }
            return ScreenSnapshot(wa, "WhatsApp", els)
        }

        override suspend fun tap(element: ScreenElement): Boolean {
            actions += "tap ${element.text}"
            when {
                screen == "home" && element.text == "Search" -> { screen = "search"; query = "" }
                screen == "search" && element.text in chats -> { screen = "chat"; openChat = element.text; box = "" }
                screen == "chat" && element.text == "Send" -> if (sendWorks) { sent += openChat to box; box = "" }
                else -> return false
            }
            return true
        }

        override suspend fun type(element: ScreenElement, text: String): Boolean {
            actions += "type $text"
            when (screen) {
                "search" -> query = text
                "chat" -> box = text
                else -> return false
            }
            return true
        }

        override suspend fun back(): Boolean {
            actions += "back"
            if (screen == "chat") screen = "home"
            return true
        }
    }

    private fun flow(h: ScreenHands) = WhatsAppSearchFlow(h, pause = {}, settleMs = 0, patience = 5)

    @Test
    fun aGroupNotInContactsIsFoundTypedAndSent() = runTest {
        val w = FakeWhatsApp(listOf("Family Group", "Office"))
        val r = flow(w).send("family group", "main 8 baje aaunga")
        assertEquals(r.log.joinToString("\n"), FlowOutcome.SENT, r.outcome)
        assertEquals("Family Group", r.chat)
        assertEquals(listOf("Family Group" to "main 8 baje aaunga"), w.sent)
    }

    @Test
    fun aChatWhatsAppReopenedIsLeftFirst() = runTest {
        val w = FakeWhatsApp(listOf("Family Group"), startInChat = "Riya")
        val r = flow(w).send("Family Group", "hello")
        assertEquals(FlowOutcome.SENT, r.outcome)
        assertTrue(w.actions.first() == "back")
        assertEquals(listOf("Family Group" to "hello"), w.sent) // never typed into Riya's chat
    }

    @Test
    fun severalMatchingChatsAreAskedNotGuessed() = runTest {
        val w = FakeWhatsApp(listOf("Ramesh Kumar", "Ramesh Office"))
        val r = flow(w).send("Ramesh", "hi")
        assertEquals(FlowOutcome.SEVERAL_CHATS, r.outcome)
        assertEquals(listOf("Ramesh Kumar", "Ramesh Office"), r.choices)
        assertTrue(w.sent.isEmpty())
        assertTrue(w.actions.none { it.startsWith("tap Ramesh") })
    }

    @Test
    fun anExactNameWinsOverLongerOnes() = runTest {
        val w = FakeWhatsApp(listOf("Ramesh", "Ramesh Office"))
        val r = flow(w).send("ramesh", "hi")
        assertEquals(FlowOutcome.SENT, r.outcome)
        assertEquals(listOf("Ramesh" to "hi"), w.sent)
    }

    @Test
    fun noChatMeansNotFoundAndAMessageMentioningTheNameIsNeverTapped() = runTest {
        val w = FakeWhatsApp(listOf("Office"))
        val r = flow(w).send("Suresh", "hi")
        assertEquals(FlowOutcome.CHAT_NOT_FOUND, r.outcome)
        assertTrue(w.actions.none { it.contains("aa raha hai") })
        assertTrue(w.sent.isEmpty())
    }

    @Test
    fun theWrongChatIsNeverTypedInto() = runTest {
        val w = FakeWhatsApp(listOf("Family Group"), titleOverride = "Someone Else")
        val r = flow(w).send("Family Group", "hello")
        assertEquals(FlowOutcome.WRONG_CHAT, r.outcome)
        assertTrue(w.actions.none { it == "type hello" })
    }

    @Test
    fun sendThatDoesNotTakeIsReportedAsTypedNotSent() = runTest {
        val w = FakeWhatsApp(listOf("Office"), sendWorks = false)
        val r = flow(w).send("Office", "late hoga")
        assertEquals(FlowOutcome.TYPED_NOT_SENT, r.outcome)
        assertEquals("Office", r.chat)
    }

    @Test
    fun withoutViewIdsTheVisibleWordsAreEnough() = runTest {
        val w = FakeWhatsApp(listOf("Office"), ids = false)
        val r = flow(w).send("Office", "ok")
        assertEquals(r.log.joinToString("\n"), FlowOutcome.SENT, r.outcome)
    }

    @Test
    fun anUnreadableOrStuckAppEndsWithStuckNotAHang() = runTest {
        val w = object : ScreenHands {
            override suspend fun openWhatsApp() = true
            override suspend fun read(): ScreenSnapshot? = null
            override suspend fun tap(element: ScreenElement) = false
            override suspend fun type(element: ScreenElement, text: String) = false
            override suspend fun back() = false
        }
        assertEquals(FlowOutcome.STUCK, flow(w).send("Office", "ok").outcome)
        val throwing = object : ScreenHands {
            override suspend fun openWhatsApp() = true
            override suspend fun read(): ScreenSnapshot? = error("node gone")
            override suspend fun tap(element: ScreenElement) = false
            override suspend fun type(element: ScreenElement, text: String) = false
            override suspend fun back() = false
        }
        assertEquals(FlowOutcome.STUCK, flow(throwing).send("Office", "ok").outcome)
    }

    @Test
    fun aPaymentScreenStopsEverything() = runTest {
        val w = object : ScreenHands {
            override suspend fun openWhatsApp() = true
            override suspend fun read() = ScreenSnapshot(wa, "WhatsApp", listOf(ScreenElement("Enter UPI PIN", ElementKind.TEXT)))
            override suspend fun tap(element: ScreenElement) = error("must not tap")
            override suspend fun type(element: ScreenElement, text: String) = error("must not type")
            override suspend fun back() = false
        }
        assertEquals(FlowOutcome.UNSAFE_SCREEN, flow(w).send("Office", "ok").outcome)
    }

    @Test
    fun aPermissionPromptIsBackedOutOfAndAPersistentOneStopsTheFlow() = runTest {
        // A permission dialog shows once over WhatsApp: backed out of, and the message still goes.
        val once = FakeWhatsApp(listOf("Office"))
        var shown = false
        val hands = object : ScreenHands by once {
            override suspend fun read(): ScreenSnapshot? {
                if (once.screen == "search" && !shown) {
                    shown = true
                    return ScreenSnapshot("com.google.android.permissioncontroller", "Permissions", listOf(ScreenElement("Allow WhatsApp to access contacts?", ElementKind.TEXT)))
                }
                return once.read()
            }
        }
        assertEquals(FlowOutcome.SENT, flow(hands).send("Office", "ok").outcome)
        assertTrue(once.actions.contains("back"))

        // An update screen that keeps coming back: the flow stops as INTERRUPTED, nothing is sent.
        val stuck = object : ScreenHands {
            var backs = 0
            override suspend fun openWhatsApp() = true
            override suspend fun read() = ScreenSnapshot("com.android.vending", "Play Store", listOf(ScreenElement("Update WhatsApp", ElementKind.BUTTON, clickable = true)))
            override suspend fun tap(element: ScreenElement) = error("must not tap")
            override suspend fun type(element: ScreenElement, text: String) = error("must not type")
            override suspend fun back(): Boolean { backs++; return true }
        }
        val r = flow(stuck).send("Office", "ok")
        assertEquals(FlowOutcome.INTERRUPTED, r.outcome)
        assertEquals(MAX_BACK_OUTS, stuck.backs)
    }

    @Test
    fun whatsAppsOwnUpdateDialogIsAnInterruptionButTheSendDialogIsNot() {
        val update = ScreenSnapshot(wa, "WhatsApp", listOf(
            ScreenElement("This version of WhatsApp is out of date. Please update.", ElementKind.TEXT),
            ScreenElement("UPDATE", ElementKind.BUTTON, clickable = true, id = "android:id/button1"),
            ScreenElement("Not now", ElementKind.BUTTON, clickable = true, id = "android:id/button2"),
        ))
        val sendTo = ScreenSnapshot(wa, "WhatsApp", listOf(
            ScreenElement("Send ticket.pdf to Riya?", ElementKind.TEXT),
            ScreenElement("SEND", ElementKind.BUTTON, clickable = true, id = "android:id/button1"),
        ))
        assertTrue(WhatsAppScreen.isInterruption(update))
        assertFalse(WhatsAppScreen.isInterruption(sendTo))
        assertFalse(WhatsAppScreen.isInterruption(ScreenSnapshot(wa, "WhatsApp", listOf(ScreenElement("Riya", ElementKind.TEXT)))))
    }
}
