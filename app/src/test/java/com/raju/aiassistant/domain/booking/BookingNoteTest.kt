package com.raju.aiassistant.domain.booking

import com.raju.aiassistant.domain.tatkal.Passenger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BookingNoteTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun ask(d: BookingDraft, text: String): BookingStep = BookingNote.step(d, text, today)

    private fun draftOf(step: BookingStep): BookingDraft = when (step) {
        is BookingStep.Ask -> step.draft
        is BookingStep.Done -> step.draft
        BookingStep.Cancelled -> error("cancelled")
    }

    @Test
    fun aBookingNoteStartsFromThePhrasesPeopleUse() {
        assertTrue(BookingNote.isStart("सीमा एक बुकिंग आया है आज एसी का, नोट बनाओ".replace("आया है", "आया")))
        assertTrue(BookingNote.isStart("booking note banao"))
        assertTrue(BookingNote.isStart("booking aaya hai"))
        assertTrue(BookingNote.isStart("बुकिंग नोट"))
        assertFalse(BookingNote.isStart("booking karo mat"))
        assertFalse(BookingNote.isStart("ticket ka status"))
    }

    @Test
    fun theWholeNoteCanBeGivenInOneBreath() {
        val s = ask(BookingDraft(), "पुणे से हावड़ा AC कल, राजेश सिंह 1986")
        assertTrue(s is BookingStep.Ask)
        val d = (s as BookingStep.Ask).draft
        assertEquals("पुणे", d.from)
        assertEquals("हावड़ा", d.to)
        assertEquals(null, d.travelClass) // "AC" alone asks 3A or 2A
        assertTrue(d.classAmbiguous)
        assertEquals(LocalDate.of(2026, 11, 12), d.date)
        assertTrue(s.prompt, s.prompt.contains("3A या 2A"))
    }

    @Test
    fun theStepByStepFlowWritesTheNoteWithAges() {
        var d = draftOf(ask(BookingDraft(), "pune se howrah ac kal"))
        d = draftOf(ask(d, "3A"))
        assertEquals("3A", d.travelClass)
        val done = ask(d, "Rajesh Singh")
        assertTrue(done is BookingStep.Ask)
        assertEquals("Rajesh Singh", (done as BookingStep.Ask).draft.pendingName)
        assertTrue(done.prompt.contains("जन्म का साल"))
        val after = ask(done.draft, "1986")
        val withPassenger = draftOf(after)
        assertEquals(listOf(Passenger("Rajesh Singh", 1986, 40, null)), withPassenger.passengers)
        assertTrue(withPassenger.askingMore)
        val end = ask(withPassenger, "बस")
        assertTrue(end is BookingStep.Done)
        end as BookingStep.Done
        assertTrue(end.note, end.note.contains("रूट: pune → howrah") || end.note.contains("रूट: पुणे → हावड़ा") || end.note.contains("→"))
        assertTrue(end.note.contains("Rajesh Singh — उम्र 40 साल (जन्म 1986)"))
        assertTrue(end.note.contains("3A"))
        assertTrue(end.note.contains("12 नवंबर 2026"))
        assertTrue(end.note.contains("टिकट और PNR की पुष्टि"))
        assertEquals("booking-note-2026-11-12.txt", BookingNote.fileName(end.draft))
    }

    @Test
    fun acAloneIsSettledByThreeOrTwo() {
        var d = draftOf(ask(BookingDraft(), "mumbai se delhi ac aaj"))
        assertTrue(d.classAmbiguous)
        d = draftOf(ask(d, "दो"))
        assertEquals("2A", d.travelClass)
        assertFalse(d.classAmbiguous)
        assertEquals(today, d.date)
    }

    @Test
    fun aSpecificClassIsNotAskedAgain() {
        val d = draftOf(ask(BookingDraft(), "katihar se delhi sleeper parso"))
        assertEquals("SL", d.travelClass)
        assertEquals(LocalDate.of(2026, 11, 13), d.date)
        assertEquals("katihar", d.from)
        assertEquals("delhi", d.to)
    }

    @Test
    fun aDateWithAMonthNameIsThatDay() {
        val d = draftOf(ask(BookingDraft(from = "Pune", to = "Howrah", travelClass = "3A"), "12 नवंबर"))
        assertEquals(LocalDate.of(2026, 11, 12), d.date)
        val past = draftOf(ask(BookingDraft(from = "Pune", to = "Howrah", travelClass = "3A"), "3 jan"))
        assertEquals(LocalDate.of(2027, 1, 3), past.date) // a day already gone means next year
    }

    @Test
    fun aMissingPieceIsAskedFor() {
        val s = ask(BookingDraft(), "AC")
        assertTrue((s as BookingStep.Ask).prompt.contains("कहाँ से"))
        val t = ask(BookingDraft(from = "Pune"), "कल")
        assertTrue((t as BookingStep.Ask).prompt.contains("कहाँ तक"))
    }

    @Test
    fun aSecondPassengerIsAdded() {
        var d = BookingDraft(from = "Pune", to = "Howrah", travelClass = "3A", date = today.plusDays(1), passengers = listOf(Passenger("Rajesh", 1986, 40, null)))
        d = draftOf(ask(d, "और यात्री Sunita 1990"))
        assertEquals(2, d.passengers.size)
        assertEquals(36, d.passengers[1].age)
        val done = ask(d, "नहीं, बस")
        assertTrue(done is BookingStep.Done)
    }

    @Test
    fun cancelLeavesNoNote() {
        assertEquals(BookingStep.Cancelled, ask(BookingDraft(from = "Pune"), "रहने दो"))
    }

    @Test
    fun aFullDateOfBirthGivesTheBirthYear() {
        var d = BookingDraft(from = "Pune", to = "Howrah", travelClass = "3A", date = today.plusDays(1))
        d = draftOf(ask(d, "Amit Kumar"))
        d = draftOf(ask(d, "15/08/2001"))
        assertEquals(25, d.passengers.single().age) // the year on the date; this year minus it
    }

    @Test
    fun theNoteNeverInventsAPassenger() {
        val s = ask(BookingDraft(from = "Pune", to = "Howrah", travelClass = "3A", date = today), "बस")
        assertTrue(s is BookingStep.Ask)
        assertTrue((s as BookingStep.Ask).prompt.contains("यात्री"))
        assertNotNull(s.prompt)
    }
}
