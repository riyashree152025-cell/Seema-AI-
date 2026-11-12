package com.raju.aiassistant.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {
    @Test
    fun theHomeNetworkMayUsePlainHttp() {
        for (h in listOf("192.168.31.55", "10.0.0.2", "172.20.1.1", "127.0.0.1", "localhost", "raju-pc.local", "[::1]", "fd12::1", "100.101.1.2", "raju-pc.tail1234.ts.net")) {
            assertTrue(h, isLocalNetworkHost(h))
        }
    }

    @Test
    fun theInternetMustUseHttps() {
        for (h in listOf("api.groq.com", "8.8.8.8", "172.32.0.1", "192.169.1.1", "example.local.com", "2001:4860::8888", "999.1.1.1", "")) {
            assertFalse(h, isLocalNetworkHost(h))
        }
    }

    @Test
    fun onlyHttpsThePhoneItselfOrATailscaleNameCountAsEncrypted() {
        for (u in listOf("https://pc.example.com:11435", "http://localhost:11434", "http://127.0.0.1:11434/v1", "http://[::1]:8080", "http://raju-pc.tail1234.ts.net:11435")) {
            assertTrue(u, isEncryptedOrOnDevice(u))
        }
        // Plain http on the Wi-Fi (or a bare 100.x address, also used by some routers) is readable on the network.
        for (u in listOf("http://192.168.31.55:11435", "http://10.0.0.2", "http://100.101.1.2:11435", "http://raju-pc.local", "http://api.groq.com", "ftp://x", "", "not a url")) {
            assertFalse(u, isEncryptedOrOnDevice(u))
        }
    }

    @Test
    fun pcSyncHasNoBuiltInAddressAndRefusesPlainHttpOnTheWifi() {
        assertEquals(PcSyncTarget.NotSet, pcSyncTarget(null))
        assertEquals(PcSyncTarget.NotSet, pcSyncTarget("   "))
        assertEquals(PcSyncTarget.Insecure("http://192.168.1.5:11435"), pcSyncTarget("192.168.1.5:11435"))
        assertEquals(PcSyncTarget.Insecure("http://192.168.1.5:11435"), pcSyncTarget("http://192.168.1.5:11435/"))
        assertEquals(PcSyncTarget.Ready("https://pc.home.arpa:11435"), pcSyncTarget("https://pc.home.arpa:11435"))
        assertEquals(PcSyncTarget.Ready("http://raju-pc.tail1234.ts.net:11435"), pcSyncTarget("raju-pc.tail1234.ts.net:11435"))
    }
}
