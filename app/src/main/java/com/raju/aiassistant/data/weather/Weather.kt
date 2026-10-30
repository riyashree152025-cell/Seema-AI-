package com.raju.aiassistant.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.raju.aiassistant.data.ai.JsonFormat
import com.raju.aiassistant.data.ai.fetch
import com.raju.aiassistant.domain.ai.AiResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

/** Current weather as reported by Open-Meteo for the phone's location. */
data class CurrentWeather(val temperatureC: Double, val code: Int, val isDay: Boolean, val fetchedAt: Long)

/** What the weather panel shows. Never invented: no data → a clear reason. */
sealed interface WeatherState {
    data object Loading : WeatherState
    data object NeedsPermission : WeatherState
    data class Unavailable(val reason: String) : WeatherState
    data class Ready(val weather: CurrentWeather) : WeatherState
}

/** WMO weather code (as used by Open-Meteo) → short label and emoji. */
fun weatherLabel(code: Int, isDay: Boolean = true): Pair<String, String> = when (code) {
    0 -> "Clear" to if (isDay) "☀️" else "🌙"
    1, 2 -> "Partly cloudy" to if (isDay) "⛅" else "☁️"
    3 -> "Cloudy" to "☁️"
    45, 48 -> "Fog" to "🌫️"
    in 51..57 -> "Drizzle" to "🌦️"
    in 61..67 -> "Rain" to "🌧️"
    in 71..77 -> "Snow" to "❄️"
    in 80..82 -> "Showers" to "🌧️"
    85, 86 -> "Snow showers" to "🌨️"
    in 95..99 -> "Thunderstorm" to "⛈️"
    else -> "Weather" to "🌡️"
}

/** Open-Meteo forecast API (free, no key): https://open-meteo.com/en/docs */
class OpenMeteoClient(private val http: OkHttpClient, private val baseUrl: String = "https://api.open-meteo.com") {
    suspend fun current(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): AiResult<CurrentWeather> {
        val url = "$baseUrl/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", "%.3f".format(java.util.Locale.ROOT, lat))
            .addQueryParameter("longitude", "%.3f".format(java.util.Locale.ROOT, lon))
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("timezone", "auto")
            .build()
        return when (val r = http.fetch(Request.Builder().url(url).get().build())) {
            is AiResult.Failure -> r
            is AiResult.Success -> {
                val (code, body) = r.value
                if (code !in 200..299) AiResult.Failure(com.raju.aiassistant.domain.ai.FailureKind.SERVER, "Weather service error (HTTP $code)")
                else parseCurrent(body, now)?.let { AiResult.Success(it) }
                    ?: AiResult.Failure(com.raju.aiassistant.domain.ai.FailureKind.UNKNOWN, "Weather service sent unreadable data")
            }
        }
    }

    internal fun parseCurrent(body: String, now: Long): CurrentWeather? {
        val cur = runCatching { (JsonFormat.parseToJsonElement(body) as JsonObject)["current"] as JsonObject }.getOrNull() ?: return null
        val temp = (cur["temperature_2m"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val wmo = (cur["weather_code"] as? JsonPrimitive)?.intOrNull ?: return null
        val day = (cur["is_day"] as? JsonPrimitive)?.intOrNull != 0
        return CurrentWeather(temp, wmo, day, now)
    }
}

/**
 * Coarse location for the weather only (city level). Uses the last known position when it is
 * recent; otherwise asks once for a fresh network position (Android 11+), with a time limit.
 */
class CoarseLocation(private val context: Context) {
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission()
    suspend fun locate(): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        // Android 12+ has the fused provider (Wi-Fi, cell and GPS combined); older phones use network/GPS.
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER); add(LocationManager.GPS_PROVIDER); add(LocationManager.PASSIVE_PROVIDER)
        }
        val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 6 * 60 * 60_000L) return last
        if (Build.VERSION.SDK_INT >= 30) {
            for (provider in providers.filter { it != LocationManager.PASSIVE_PROVIDER && runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }) {
                val fresh = withTimeoutOrNull(10_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = android.os.CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        runCatching {
                            lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                        }.onFailure { if (cont.isActive) cont.resume(null) }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return last
    }
}

/** Weather for the dashboard, cached for 30 minutes (no repeated calls while the screen stays open). */
class WeatherRepository(private val client: OpenMeteoClient, private val location: CoarseLocation, private val online: () -> Boolean) {
    @Volatile private var cached: CurrentWeather? = null

    suspend fun load(force: Boolean = false): WeatherState {
        if (!location.hasPermission()) return WeatherState.NeedsPermission
        cached?.takeIf { !force && System.currentTimeMillis() - it.fetchedAt < 30 * 60_000L }?.let { return WeatherState.Ready(it) }
        if (!online()) return cached?.let { WeatherState.Ready(it) } ?: WeatherState.Unavailable("offline")
        val loc = location.locate() ?: return WeatherState.Unavailable("location unavailable — turn on Location")
        return when (val r = client.current(loc.latitude, loc.longitude)) {
            is AiResult.Success -> WeatherState.Ready(r.value.also { cached = it })
            is AiResult.Failure -> cached?.let { WeatherState.Ready(it) } ?: WeatherState.Unavailable("weather service unreachable")
        }
    }
}
