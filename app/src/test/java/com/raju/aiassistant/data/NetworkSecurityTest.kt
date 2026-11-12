package com.raju.aiassistant.data

import com.raju.aiassistant.data.ai.AiProviderFactory
import com.raju.aiassistant.data.ai.defaultHttpClient
import com.raju.aiassistant.domain.model.SecretId
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class NetworkSecurityTest {
    @Test
    fun plainHttpToTheInternetIsRefusedBeforeAnyConnection() {
        // 8.8.8.8:9 is never contacted: the guard refuses the request before DNS or a socket.
        val call = defaultHttpClient().newCall(Request.Builder().url("http://8.8.8.8:9/v1/chat").header("Authorization", "Bearer secret").build())
        try {
            call.execute().close()
            fail("plain http to an internet address must be refused")
        } catch (e: IOException) {
            assertTrue(e.message, e.message.orEmpty().contains("Blocked insecure http://"))
        }
    }

    @Test
    fun plainHttpOnThisPhoneStillWorks() {
        // The local case the guard keeps open (Ollama on the phone, a local test server).
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val url = server.url("/status").toString()
            assertTrue(url, url.startsWith("http://"))
            defaultHttpClient().newCall(Request.Builder().url(url).build()).execute().use { assertEquals("ok", it.body?.string()) }
        }
    }

    @Test
    fun ollamaWithoutASavedAddressStaysOnThePhone() {
        // Used to be a fixed LAN address (192.168.31.55), which could be any device on the Wi-Fi.
        assertEquals("http://127.0.0.1:11434/v1", AiProviderFactory.normalizeOllamaUrl(""))
        assertEquals("http://192.168.1.20:11434/v1", AiProviderFactory.normalizeOllamaUrl("192.168.1.20"))
    }

    @Test
    fun noSecretCarriesABuiltInPlainHttpAddress() {
        for (id in SecretId.entries) {
            assertTrue("${id.name}: ${id.helpUrl}", id.helpUrl.isEmpty() || id.helpUrl.startsWith("https://"))
        }
    }
}
