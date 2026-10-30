package com.raju.aiassistant.data.tts.local

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class VoiceModelFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tarBz2(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(bytes)).use { tar ->
            files.forEach { (name, text) ->
                val data = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = data.size.toLong() })
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test fun extractsTheModelFolder() {
        val dest = tmp.newFolder("out")
        VoiceModelManager.extractTarBz2(
            ByteArrayInputStream(tarBz2("voice/model.onnx" to "onnx", "voice/espeak-ng-data/hi_dict" to "dict", "voice/tokens.txt" to "a 1")),
            dest,
        )
        assertEquals("onnx", File(dest, "voice/model.onnx").readText())
        assertEquals("dict", File(dest, "voice/espeak-ng-data/hi_dict").readText())
        assertEquals("a 1", File(dest, "voice/tokens.txt").readText())
    }

    @Test fun refusesFilesOutsideTheTargetFolder() {
        val dest = tmp.newFolder("out")
        try {
            VoiceModelManager.extractTarBz2(ByteArrayInputStream(tarBz2("../evil.txt" to "x")), dest)
            fail("path traversal was accepted")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(File(dest.parentFile, "evil.txt").exists())
    }

    @Test fun sha256MatchesTheKnownValue() {
        val f = tmp.newFile("abc").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", VoiceModelManager.sha256(f))
    }

    @Test fun floatSamplesBecomeLittleEndian16Bit() {
        val pcm = floatToPcm16(floatArrayOf(0f, 1f, -1f, 2f))
        assertArrayEquals(
            byteArrayOf(0, 0, 0xFF.toByte(), 0x7F, 0x01, 0x80.toByte(), 0xFF.toByte(), 0x7F),
            pcm,
        )
        assertTrue(floatToPcm16(FloatArray(0)).isEmpty())
    }
}
