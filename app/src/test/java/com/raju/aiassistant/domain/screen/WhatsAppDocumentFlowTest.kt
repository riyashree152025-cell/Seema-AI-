package com.raju.aiassistant.domain.screen

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sending a document on WhatsApp: chat → 📎 → Document → the one matching file → Send. */
class WhatsAppDocumentFlowTest {
    private val wa = "com.whatsapp"

    private fun el(text: String, id: String = "", clickable: Boolean = true, editable: Boolean = false) =
        ScreenElement(text, if (editable) ElementKind.INPUT else ElementKind.BUTTON, clickable = clickable, editable = editable, id = if (id.isEmpty()) "" else if (id.startsWith("android:")) id else "$wa:id/$id")

    private inner class FakeWhatsApp(
        val files: List<String>,
        val attachShown: Boolean = true,
        val confirmDialog: Boolean = true,
        val sendWorks: Boolean = true,
        val systemPicker: Boolean = false,
    ) : ScreenHands {
        var screen = "closed"
        var query = ""
        var picked = ""
        val chatFiles = mutableListOf("old_ticket.pdf") // a file sent earlier is visible in the chat
        val sent = mutableListOf<String>()
        val actions = mutableListOf<String>()

        override suspend fun openWhatsApp(): Boolean { screen = "home"; return true }

        override suspend fun read(): ScreenSnapshot = when (screen) {
            "home" -> ScreenSnapshot(wa, "WhatsApp", listOf(el("Search", "menuitem_search")))
            "search" -> ScreenSnapshot(wa, "WhatsApp", listOf(el(query, "search_input", editable = true)) + if (query.isNotEmpty()) listOf(el("Riya", "conversations_row_contact_name")) else emptyList())
            "chat" -> ScreenSnapshot(wa, "WhatsApp", listOf(el("Riya", "conversation_contact_name", clickable = false)) + chatFiles.map { el(it) } +
                listOf(el("", "entry", editable = true)) + if (attachShown) listOf(el("Attach", "input_attach_button")) else emptyList())
            "attach" -> ScreenSnapshot(wa, "WhatsApp", listOf(el("Document", "pick_document"), el("Camera"), el("Gallery")))
            "files" -> ScreenSnapshot(if (systemPicker) "com.google.android.documentsui" else wa, "Files", files.map { el(it) })
            "confirm" -> ScreenSnapshot(wa, "WhatsApp", listOf(el("Send $picked to Riya?", clickable = false), el("CANCEL", "android:id/button2"), el("SEND", "android:id/button1")))
            else -> ScreenSnapshot("x", "x", emptyList())
        }

        override suspend fun tap(element: ScreenElement): Boolean {
            actions += "tap ${element.text}"
            when {
                screen == "home" && element.text == "Search" -> screen = "search"
                screen == "search" && element.text == "Riya" -> screen = "chat"
                screen == "chat" && element.text == "Attach" -> screen = "attach"
                screen == "chat" -> return false
                screen == "attach" && element.text == "Document" -> screen = "files"
                screen == "files" && element.text in files -> { picked = element.text; screen = if (confirmDialog) "confirm" else "confirm" }
                screen == "confirm" && element.text == "SEND" -> { if (sendWorks) { sent += picked; chatFiles += picked }; screen = "chat" }
                else -> return false
            }
            return true
        }

        override suspend fun type(element: ScreenElement, text: String): Boolean { if (screen == "search") { query = text; return true }; return false }
        override suspend fun back(): Boolean = true
    }

    private fun flow(h: ScreenHands) = WhatsAppSearchFlow(h, pause = {}, settleMs = 0, patience = 5)

    @Test
    fun theOneMatchingPdfIsAttachedAndSent() = runTest {
        val w = FakeWhatsApp(listOf("Invoice_March.pdf", "IRCTC_Ticket_4512.pdf", "notes.txt"))
        val r = flow(w).sendDocument("Riya", "ticket wali pdf")
        assertEquals(r.log.joinToString("\n"), FlowOutcome.SENT, r.outcome)
        assertEquals(listOf("IRCTC_Ticket_4512.pdf"), w.sent)
        assertTrue(w.actions.none { it == "tap old_ticket.pdf" }) // the chat's old file is never taken for the list
    }

    @Test
    fun theNewestPdfWhenNoNameIsSaid() = runTest {
        val w = FakeWhatsApp(listOf("photo.jpg", "Report.pdf", "Old.pdf"))
        val r = flow(w).sendDocument("Riya", "latest pdf")
        assertEquals(FlowOutcome.SENT, r.outcome)
        assertEquals(listOf("Report.pdf"), w.sent)
    }

    @Test
    fun severalOrNoMatchingFilesSendNothing() = runTest {
        val w = FakeWhatsApp(listOf("Ticket_Delhi.pdf", "Ticket_Patna.pdf"))
        val r = flow(w).sendDocument("Riya", "ticket")
        assertEquals(FlowOutcome.SEVERAL_FILES, r.outcome)
        assertEquals(listOf("Ticket_Delhi.pdf", "Ticket_Patna.pdf"), r.choices)
        assertTrue(w.sent.isEmpty())
        val none = FakeWhatsApp(listOf("a.pdf"))
        assertEquals(FlowOutcome.FILE_NOT_FOUND, flow(none).sendDocument("Riya", "aadhaar").outcome)
        assertTrue(none.sent.isEmpty())
    }

    @Test
    fun noAttachButtonOrASendThatDoesNotTakeIsSaidHonestly() = runTest {
        assertEquals(FlowOutcome.STUCK, flow(FakeWhatsApp(listOf("a.pdf"), attachShown = false)).sendDocument("Riya", "a").outcome)
        assertEquals(FlowOutcome.TYPED_NOT_SENT, flow(FakeWhatsApp(listOf("a.pdf"), sendWorks = false)).sendDocument("Riya", "a").outcome)
    }

    @Test
    fun theSystemFilePickerWorksToo() = runTest {
        val w = FakeWhatsApp(listOf("Client_Quote.pdf"), systemPicker = true)
        assertEquals(FlowOutcome.SENT, flow(w).sendDocument("Riya", "client quote").outcome)
    }

    @Test
    fun matchingFileNames() {
        val f = listOf("IRCTC_Ticket-4512.PDF", "Aadhaar Card.jpg", "aadhaar.pdf").map { ScreenElement(it, ElementKind.BUTTON, clickable = true) }
        assertEquals(listOf("IRCTC_Ticket-4512.PDF"), DocumentMatch.pick(f, "ticket").map { it.text })
        assertEquals(listOf("aadhaar.pdf"), DocumentMatch.pick(f, "aadhaar pdf").map { it.text })
        assertEquals(listOf("Aadhaar Card.jpg", "aadhaar.pdf"), DocumentMatch.pick(f, "aadhaar").map { it.text })
        assertEquals(listOf("IRCTC_Ticket-4512.PDF"), DocumentMatch.pick(f, "").map { it.text })
    }
}
