package com.raju.aiassistant.data.weather

import com.raju.aiassistant.domain.ai.AiResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class WeatherTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
    private val client get() = OpenMeteoClient(http, server.url("/").toString().trimEnd('/'))

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun parsesCurrentWeatherAndSendsLocation() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"latitude":28.6,"current":{"time":"2026-10-30T12:00","temperature_2m":31.4,"weather_code":2,"is_day":1}}"""))
        val r = client.current(28.6139, 77.209, now = 42L)
        assertEquals(AiResult.Success(CurrentWeather(31.4, 2, true, 42L)), r)
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/v1/forecast?") && "latitude=28.614" in path && "longitude=77.209" in path)
        assertTrue(path, "temperature_2m" in path && "weather_code" in path)
    }

    @Test
    fun serverErrorIsAFailureNotInventedData() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        assertTrue(client.current(1.0, 2.0) is AiResult.Failure)
    }

    @Test
    fun unreadableBodyIsRejected() {
        assertNull(client.parseCurrent("""{"current":{"weather_code":1}}""", 0))
        assertNull(client.parseCurrent("not json", 0))
    }

    @Test
    fun weatherCodesMapToLabels() {
        assertEquals("Clear", weatherLabel(0).first)
        assertEquals("🌙", weatherLabel(0, isDay = false).second)
        assertEquals("Rain", weatherLabel(63).first)
        assertEquals("Thunderstorm", weatherLabel(95).first)
        assertEquals("Weather", weatherLabel(1234).first)
    }
}
