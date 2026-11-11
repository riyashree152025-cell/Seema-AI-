package com.raju.aiassistant.data.rail

import com.raju.aiassistant.core.security.SecureKeyStore
import com.raju.aiassistant.domain.business.PnrFetch
import com.raju.aiassistant.domain.business.PnrSource
import com.raju.aiassistant.domain.model.SecretId
import com.raju.aiassistant.domain.rail.Fare
import com.raju.aiassistant.domain.rail.LiveStatus
import com.raju.aiassistant.domain.rail.RailPnr
import com.raju.aiassistant.domain.rail.RailResult
import com.raju.aiassistant.domain.rail.RailSource
import com.raju.aiassistant.domain.rail.SeatAvailability
import com.raju.aiassistant.domain.rail.Station
import com.raju.aiassistant.domain.rail.TrainBetween
import com.raju.aiassistant.domain.rail.parseFare
import com.raju.aiassistant.domain.rail.parseLive
import com.raju.aiassistant.domain.rail.parseRailPnr
import com.raju.aiassistant.domain.rail.parseSeats
import com.raju.aiassistant.domain.rail.parseStations
import com.raju.aiassistant.domain.rail.parseTrainsBetween
import com.raju.aiassistant.domain.rail.railDate
import com.raju.aiassistant.domain.rail.railHttpError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate

/**
 * RailKit over HTTPS: GET https://api.railkit.in/api/v1/… with the user's key in "x-api-key".
 * The key is read from the encrypted store for each call and is never logged.
 */
class RailKitClient(private val http: OkHttpClient, private val keys: SecureKeyStore) : RailSource {

    fun configured(): Boolean = !keys.get(SecretId.RAILKIT_API_KEY).isNullOrBlank()

    private fun url(vararg path: String, query: Pair<String, String>? = null): HttpUrl =
        HttpUrl.Builder().scheme("https").host(HOST).addPathSegment("api").apply {
            path.forEach { addPathSegment(it) } // each part is escaped, so a spoken name cannot change the path
            query?.let { addQueryParameter(it.first, it.second) }
        }.build()

    private suspend fun <T> get(url: HttpUrl, parse: (String) -> RailResult<T>): RailResult<T> = withContext(Dispatchers.IO) {
        val key = keys.get(SecretId.RAILKIT_API_KEY)?.trim().orEmpty()
        if (key.isEmpty()) return@withContext RailResult.NotConfigured
        val request = Request.Builder().url(url).get().header("x-api-key", key).header("accept", "application/json").build()
        runCatching {
            http.newCall(request).execute().use { r ->
                val body = r.body?.string().orEmpty()
                // The service explains its own errors in the body ("PNR number must be exactly 10 digits").
                val result = if (body.isNotBlank()) parse(body) else null
                when {
                    r.isSuccessful && result != null -> result
                    result is RailResult.Failed && r.code !in setOf(401, 403, 429) -> result
                    else -> RailResult.Failed(railHttpError(r.code))
                }
            }
        }.getOrElse { RailResult.Failed("रेल सेवा तक नहीं पहुँच पाई — इंटरनेट देखिए।") }
    }

    suspend fun pnr(pnr: String): RailResult<RailPnr> = get(url("v1", "pnr", pnr)) { parseRailPnr(pnr, it) }

    override suspend fun stations(name: String): RailResult<List<Station>> =
        get(url("v1", "stations", "search", query = "name" to name), ::parseStations)

    override suspend fun trainsBetween(from: String, to: String, date: LocalDate?): RailResult<List<TrainBetween>> =
        get(url("v1", "trains", "between", from, to, query = date?.let { "date" to railDate(it) }), ::parseTrainsBetween)

    override suspend fun live(train: String, date: LocalDate): RailResult<LiveStatus> =
        get(url("v1", "trains", train, "live", railDate(date)), ::parseLive)

    override suspend fun fare(train: String, from: String, to: String, date: LocalDate, travelClass: String, quota: String): RailResult<Fare> =
        get(url("v1", "fare", train, railDate(date), from, to, travelClass, quota), ::parseFare)

    override suspend fun seats(train: String, from: String, to: String, date: LocalDate, travelClass: String, quota: String): RailResult<SeatAvailability> =
        get(url("v1", "seats", train, from, to, railDate(date), travelClass, quota), ::parseSeats)

    private companion object {
        const val HOST = "api.railkit.in"
    }
}

/**
 * PNR status: RailKit when its key is saved, otherwise the user's own PNR URL (the older setting).
 * [onRailPnr] hears every RailKit answer, so a waiting ticket can be put on the chart watch.
 */
class RailPnrSource(
    private val rail: RailKitClient,
    private val fallback: PnrSource,
    private val onRailPnr: suspend (RailPnr) -> Unit = {},
) : PnrSource {
    override suspend fun status(pnr: String): PnrFetch {
        if (!rail.configured()) return fallback.status(pnr)
        return when (val r = rail.pnr(pnr)) {
            is RailResult.Ok -> {
                runCatching { onRailPnr(r.value) }
                PnrFetch.Ok(r.value.snapshot)
            }
            is RailResult.Failed -> PnrFetch.Failed(r.message)
            RailResult.NotConfigured -> fallback.status(pnr)
        }
    }
}
