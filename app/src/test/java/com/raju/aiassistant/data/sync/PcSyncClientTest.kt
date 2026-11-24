package com.raju.aiassistant.data.sync

import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.domain.remote.RemoteResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PcSyncClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
    private val serverUrl get() = server.url("").toString().trimEnd('/')

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun endpointCandidatesPrioritizeLocalAndAllowHttpsRemote() = runBlocking {
        val secrets = mapOf(
            SecretId.PC_SYNC_ENDPOINT to "192.168.1.15:11435",
            SecretId.PC_REMOTE_ENDPOINT to "https://mona-bridge.trycloudflare.com",
            SecretId.PC_SYNC_TOKEN to "test_token_123"
        )
        val client = PcSyncClient(http, secretLookup = { id -> secrets[id] })
        val candidates = client.getCandidateEndpoints()

        assertEquals(2, candidates.size)
        assertEquals("http://192.168.1.15:11435", candidates[0])
        assertEquals("https://mona-bridge.trycloudflare.com", candidates[1])
    }

    @Test
    fun insecureInternetHttpEndpointIsRejected() = runBlocking {
        val secrets = mapOf(
            SecretId.PC_SYNC_ENDPOINT to "http://insecure-public-site.com:11435",
            SecretId.PC_REMOTE_ENDPOINT to "http://another-insecure-site.com",
        )
        val client = PcSyncClient(http, secretLookup = { id -> secrets[id] })
        val candidates = client.getCandidateEndpoints()

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun checkStatusReturnsSuccessWithHostnameWhenServerReturns200() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"hostname":"MONA-PC-PRO","service":"SEEMA AI PC Companion"}""")
        )

        val secrets = mapOf(
            SecretId.PC_SYNC_ENDPOINT to serverUrl,
            SecretId.PC_SYNC_TOKEN to "mona_token_xyz"
        )
        val client = PcSyncClient(http, secretLookup = { id -> secrets[id] })
        val result = client.checkStatus()

        assertTrue(result is AiResult.Success)
        val msg = (result as AiResult.Success).value
        assertTrue(msg.contains("Connected to MONA PC ✅"))
        assertTrue(msg.contains("MONA-PC-PRO"))

        val req = server.takeRequest()
        assertEquals("Bearer mona_token_xyz", req.getHeader("Authorization"))
        assertEquals("mona_token_xyz", req.getHeader("X-Seema-Token"))
    }

    @Test
    fun checkStatusReturnsWrongTokenOn401() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))

        val secrets = mapOf(
            SecretId.PC_SYNC_ENDPOINT to serverUrl,
            SecretId.PC_SYNC_TOKEN to "wrong_token"
        )
        val client = PcSyncClient(http, secretLookup = { id -> secrets[id] })
        val result = client.checkStatus()

        assertTrue(result is AiResult.Failure)
        assertEquals("Offline / Wrong Token ❌", (result as AiResult.Failure).message)
    }

    @Test
    fun checkStatusFallsBackToRemoteWhenLocalIsUnreachable() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"hostname":"MONA-REMOTE-TUNNEL","service":"SEEMA AI PC Companion"}""")
        )

        val secrets = mapOf(
            // Unreachable port on localhost
            SecretId.PC_SYNC_ENDPOINT to "http://127.0.0.1:54321",
            SecretId.PC_REMOTE_ENDPOINT to serverUrl,
            SecretId.PC_SYNC_TOKEN to "my_auth_token"
        )
        val client = PcSyncClient(http, secretLookup = { id -> secrets[id] })
        val result = client.checkStatus()

        assertTrue(result is AiResult.Success)
        val msg = (result as AiResult.Success).value
        assertTrue(msg.contains("Connected to MONA PC ✅"))
        assertTrue(msg.contains("MONA-REMOTE-TUNNEL"))
    }

    @Test
    fun sendActionPostsPayloadWithBearerTokenAndReturnsDone() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"action":"open_app","app":"cursor","status":"launched"}""")
        )

        val secrets = mapOf(
            SecretId.PC_SYNC_ENDPOINT to serverUrl,
            SecretId.PC_SYNC_TOKEN to "action_token_456"
        )
        val client = PcSyncClient(http, secretLookup = { id -> secrets[id] })
        val result = client.sendAction("open_app", mapOf("name" to "cursor"))

        assertTrue(result is RemoteResult.Done)
        val fields = (result as RemoteResult.Done).fields
        assertEquals("cursor", fields["app"])
        assertEquals("launched", fields["status"])

        val req = server.takeRequest()
        assertEquals("Bearer action_token_456", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"action\":\"open_app\""))
        assertTrue(body.contains("\"name\":\"cursor\""))
    }

    @Test
    fun sendActionFallsBackToRemoteWhenLocalFails() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"action":"system_info","hostname":"MONA-PC","load_1m":"0.45"}""")
        )

        val secrets = mapOf(
            SecretId.PC_SYNC_ENDPOINT to "http://127.0.0.1:54321",
            SecretId.PC_REMOTE_ENDPOINT to serverUrl,
            SecretId.PC_SYNC_TOKEN to "action_token_456"
        )
        val client = PcSyncClient(http, secretLookup = { id -> secrets[id] })
        val result = client.sendAction("system_info", mapOf("target" to "pc"))

        assertTrue(result is RemoteResult.Done)
        val fields = (result as RemoteResult.Done).fields
        assertEquals("MONA-PC", fields["hostname"])
    }
}
