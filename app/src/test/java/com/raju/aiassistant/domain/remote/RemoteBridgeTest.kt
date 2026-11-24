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

    @Test
    fun autonomousTasksParsingAndSpoken() {
        // Screen check
        assertEquals(
            RemoteCommand.AutonomousTask(RemoteTarget.PC, "PC screen check karo"),
            RemoteBridge.parse("PC screen check karo")
        )
        assertEquals(
            RemoteCommand.AutonomousTask(RemoteTarget.PC, "PC screen dekho"),
            RemoteBridge.parse("PC screen dekho")
        )

        // Open-ended task
        val task = RemoteBridge.parse("PC par aaj ke ticket dhoondh kar WhatsApp par bhej do")
        assertTrue(task is RemoteCommand.AutonomousTask)
        val autoTask = task as RemoteCommand.AutonomousTask
        assertEquals(RemoteTarget.PC, autoTask.target)
        assertTrue(autoTask.goal.contains("ticket"))
        assertTrue(autoTask.goal.contains("WhatsApp"))

        // Explicit task
        val explicitTask = RemoteBridge.parse("PC par task: tickets find karke WhatsApp par bhejo")
        assertTrue(explicitTask is RemoteCommand.AutonomousTask)
        assertEquals("tickets find karke WhatsApp par bhejo", (explicitTask as RemoteCommand.AutonomousTask).goal)

        // NeedsInput spoken
        val needsInput = RemoteResult.NeedsInput("WhatsApp खुला है, किस नाम या नंबर पर भेजना है?", "sess_123")
        assertEquals(
            "WhatsApp खुला है, किस नाम या नंबर पर भेजना है?",
            RemoteBridge.spoken(autoTask, needsInput, bengali = false)
        )

        // Done spoken
        val done = RemoteResult.Done(mapOf("message" to "टिकट भेज दिया गया है।"))
        assertEquals(
            "टिकट भेज दिया गया है।",
            RemoteBridge.spoken(autoTask, done, bengali = false)
        )
    }

    @Test
    fun autonomousAbortParsingAndRequest() {
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("PC task roko"))
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("PC task stop karo"))
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("टास्क रोको"))
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("task roko"))

        val abortCmd = RemoteCommand.AutonomousAbort(RemoteTarget.PC, sessionId = "sess_456")
        val (action, params) = RemoteBridge.request(abortCmd)
        assertEquals("abort_autonomous_task", action)
        assertEquals("sess_456", params["session_id"])
        assertEquals("pc", params["target"])

        val doneSpoken = RemoteBridge.spoken(abortCmd, RemoteResult.Done(), bengali = false)
        assertEquals("PC पर टास्क रोक दिया गया है।", doneSpoken)
    }
}
