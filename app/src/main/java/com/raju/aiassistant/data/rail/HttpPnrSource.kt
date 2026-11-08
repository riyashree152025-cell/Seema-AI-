package com.raju.aiassistant.data.rail

import com.raju.aiassistant.core.security.SecureKeyStore
import com.raju.aiassistant.domain.business.PnrFetch
import com.raju.aiassistant.domain.business.PnrSource
import com.raju.aiassistant.domain.business.parsePnrJson
import com.raju.aiassistant.domain.model.SecretId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * PNR status from the PNR service the user set up in Settings (their own backend or a provider they
 * subscribed to): GET the URL with "{pnr}" filled in (or "?pnr=" added), optional Bearer token.
 * There is no free official PNR API, so without a URL this reports NotConfigured.
 */
class HttpPnrSource(private val http: OkHttpClient, private val keys: SecureKeyStore) : PnrSource {
    override suspend fun status(pnr: String): PnrFetch = withContext(Dispatchers.IO) {
        val endpoint = keys.get(SecretId.PNR_STATUS_ENDPOINT)?.trim().orEmpty()
        if (endpoint.isEmpty()) return@withContext PnrFetch.NotConfigured
        val url = if ("{pnr}" in endpoint) endpoint.replace("{pnr}", pnr)
        else endpoint.toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("pnr", pnr)?.build()?.toString()
            ?: return@withContext PnrFetch.Failed("PNR service URL is not valid.")
        val token = keys.get(SecretId.PNR_STATUS_TOKEN)?.trim()
        val request = runCatching {
            Request.Builder().url(url).get().apply { if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token") }.build()
        }.getOrElse { return@withContext PnrFetch.Failed("PNR service URL is not valid.") }
        runCatching {
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use PnrFetch.Failed("PNR service answered HTTP ${r.code}.")
                val snapshot = parsePnrJson(pnr, r.body?.string().orEmpty())
                if (snapshot == null) PnrFetch.Failed("PNR service did not return a status for this PNR.") else PnrFetch.Ok(snapshot)
            }
        }.getOrElse { PnrFetch.Failed("PNR service not reachable: ${it.javaClass.simpleName}") }
    }
}
