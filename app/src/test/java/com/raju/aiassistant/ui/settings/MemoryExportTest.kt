package com.raju.aiassistant.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryExportTest {
    @Test
    fun onlyTextEntriesAreImported() {
        val json = """[{"text":"Mera naam Raju hai","createdAt":1},{"text":"  "},{"x":1},{"text":5},{"text":"Mera naam Raju hai"},{"text":"আমি কলকাতায় থাকি"}]"""
        assertEquals(listOf("Mera naam Raju hai", "আমি কলকাতায় থাকি"), parseMemoryExport(json))
        assertEquals(emptyList<String>(), parseMemoryExport("not json"))
        assertEquals(emptyList<String>(), parseMemoryExport("""{"text":"object, not array"}"""))
    }
}
