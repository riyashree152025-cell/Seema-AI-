package com.raju.aiassistant.domain.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBridgeTest {
    @Test
    fun openAnAppOnThePc() {
        assertEquals(RemoteCommand.OpenApp(RemoteTarget.PC, "cursor"), RemoteBridge.parse("PC mein Cursor kholo"))
        assertEquals(RemoteCommand.OpenApp(RemoteTarget.PC, "notepad"), RemoteBridge.parse("पीसी में नोटपैड खोलो"))
        assertEquals(RemoteCommand.OpenApp(RemoteTarget.PC, "calculator"), RemoteBridge.parse("পিসিতে calculator খোলো"))
        assertEquals(RemoteCommand.OpenApp(RemoteTarget.PC, "chrome"), RemoteBridge.parse("PC par Chrome kholo"))
        assertEquals(RemoteCommand.OpenApp(RemoteTarget.PC, "vscode"), RemoteBridge.parse("PC mein VS Code kholo"))
        assertEquals(RemoteCommand.OpenApp(RemoteTarget.PC, "spotify"), RemoteBridge.parse("PC par Spotify kholo"))
        assertEquals("open_app" to mapOf("name" to "cursor"), RemoteBridge.request(RemoteCommand.OpenApp(RemoteTarget.PC, "cursor")))
    }

    @Test
    fun anUnknownAppIsRefusedWithoutSending() {
        val c = RemoteBridge.parse("PC mein photoshop kholo") as RemoteCommand.OpenApp
        assertEquals("photoshop", c.app)
        assertTrue(RemoteBridge.unknownApp(c, bengali = false)!!.contains("Cursor"))
        assertNull(RemoteBridge.unknownApp(RemoteCommand.OpenApp(RemoteTarget.PC, "cursor"), bengali = false))
        assertNull(RemoteBridge.unknownApp(RemoteCommand.OpenApp(RemoteTarget.PC, "chrome"), bengali = false))
    }

    @Test
    fun statusOfVpsAndPc() {
        assertEquals(RemoteCommand.Status(RemoteTarget.VPS), RemoteBridge.parse("VPS status check karo"))
        assertEquals(RemoteCommand.Status(RemoteTarget.PC), RemoteBridge.parse("PC ka status batao"))
        assertEquals(RemoteCommand.Status(RemoteTarget.VPS), RemoteBridge.parse("server chalu hai kya?"))
        assertEquals("system_info", RemoteBridge.request(RemoteCommand.Status(RemoteTarget.VPS)).first)
    }

    @Test
    fun instructionsKeepTheirTextAndTheFirstMachineNamed() {
        assertEquals(RemoteCommand.Instruction(RemoteTarget.PC, "server restart karo"), RemoteBridge.parse("PC ko command bhejo: server restart karo"))
        assertEquals(RemoteCommand.Instruction(RemoteTarget.VPS, "backup chala do"), RemoteBridge.parse("VPS ko command bhejo backup chala do"))
        assertEquals(RemoteCommand.Instruction(RemoteTarget.PC, "रिपोर्ट बना दो"), RemoteBridge.parse("पीसी को कमांड भेजो: रिपोर्ट बना दो"))
        // Direct action execution
        assertEquals(RemoteCommand.Instruction(RemoteTarget.PC, "backup"), RemoteBridge.parse("PC par backup chalao"))
        assertEquals(RemoteCommand.Instruction(RemoteTarget.PC, "git pull"), RemoteBridge.parse("PC par git pull karo"))
        val (action, params) = RemoteBridge.request(RemoteCommand.Instruction(RemoteTarget.PC, "x"))
        assertEquals("instruction", action)
        assertEquals("x", params["text"])
    }

    @Test
    fun ordinaryTalkIsNotABridgeCommand() {
        assertNull(RemoteBridge.parse("mera PC kharab ho gaya yaar"))
        assertNull(RemoteBridge.parse("command bhejo"))
    }

    @Test
    fun spokenResults() {
        val status = RemoteCommand.Status(RemoteTarget.VPS)
        assertEquals("VPS चालू है (box), लोड 0.5, डिस्क में 21 GB खाली।", RemoteBridge.spoken(status, RemoteResult.Done(mapOf("hostname" to "box", "load_1m" to "0.5", "disk_free_gb" to "20.6")), bengali = false))
        assertTrue(RemoteBridge.spoken(status, RemoteResult.NotConfigured, bengali = false).contains("Settings"))
        assertTrue(RemoteBridge.spoken(status, RemoteResult.Unreachable, bengali = true).contains("সার্ভার"))
        assertEquals("PC पर Cursor खोल दिया।", RemoteBridge.spoken(RemoteCommand.OpenApp(RemoteTarget.PC, "cursor"), RemoteResult.Done(), bengali = false))
    }
}
