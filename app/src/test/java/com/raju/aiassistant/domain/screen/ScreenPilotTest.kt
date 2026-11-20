package com.raju.aiassistant.domain.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenPilotTest {
    private fun button(t: String) = ScreenElement(t, ElementKind.BUTTON, clickable = true)
    private val screen = ScreenSnapshot(
        "com.example", "Example",
        listOf(
            ScreenElement("Title", ElementKind.TEXT),
            button("Cancel"),
            ScreenElement("", ElementKind.INPUT, clickable = true, editable = true),
            button("Later"),
            button("Continue"),
        ),
    )

    @Test
    fun positionsFromWords() {
        assertEquals(ScreenPilot.Position.Last, ScreenPilot.positionOf("neeche wala button"))
        assertEquals(ScreenPilot.Position.First, ScreenPilot.positionOf("pehla button"))
        assertEquals(ScreenPilot.Position.Nth(1), ScreenPilot.positionOf("doosra wala"))
        assertEquals(ScreenPilot.Position.Last, ScreenPilot.positionOf("নিচের বোতাম"))
        assertNull(ScreenPilot.positionOf("Top up"))
        assertNull(ScreenPilot.positionOf("First name"))
        assertNull(ScreenPilot.positionOf("Send"))
    }

    @Test
    fun resolvesOnlyLabelledButtonsInReadingOrder() {
        assertEquals("Continue", ScreenPilot.resolve("neeche wala button", screen)?.text)
        assertEquals("Cancel", ScreenPilot.resolve("upar wala", screen)?.text)
        assertEquals("Later", ScreenPilot.resolve("doosra button", screen)?.text)
        assertNull(ScreenPilot.resolve("Continue", screen))
    }

    @Test
    fun aDuplicatedLabelIsNotGuessed() {
        val twice = screen.copy(elements = screen.elements + button("Cancel"))
        assertNull(ScreenPilot.resolve("neeche wala", twice))
    }

    @Test
    fun typeHere() {
        assertEquals("kal milte hain", ScreenPilot.typeHere("isme kal milte hain likh do"))
        assertEquals("ঠিক আছে", ScreenPilot.typeHere("এখানে ঠিক আছে লিখে দাও"))
        assertNull(ScreenPilot.typeHere("Rahul ko likh do"))
    }
}
