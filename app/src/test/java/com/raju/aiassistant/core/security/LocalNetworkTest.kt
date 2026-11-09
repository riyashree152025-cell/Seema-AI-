package com.raju.aiassistant.core.security

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
}
