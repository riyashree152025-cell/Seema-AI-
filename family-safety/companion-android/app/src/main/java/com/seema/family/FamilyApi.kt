package com.seema.family

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Settings (token encrypted on the phone) and the three calls the app makes. HTTPS only. */
class FamilyApi(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "family_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val server: String get() = prefs.getString("server", "").orEmpty()
    val userId: String get() = prefs.getString("user_id", "").orEmpty()
    private val token: String get() = prefs.getString("token", "").orEmpty()
    val joined: Boolean get() = server.isNotEmpty() && token.isNotEmpty()

    var shakeSos: Boolean
        get() = prefs.getBoolean("shake_sos", false)
        set(v) = prefs.edit().putBoolean("shake_sos", v).apply()

    /** The user's own choice; the service runs only while this is true. */
    var sharing: Boolean
        get() = prefs.getBoolean("sharing", false)
        set(v) = prefs.edit().putBoolean("sharing", v).apply()

    /** Exchanges the one-time code the family admin gave for this phone's token. */
    fun join(serverUrl: String, id: String, code: String): Boolean {
        val base = serverUrl.trim().trimEnd('/')
        if (!base.startsWith("https://")) return false
        val (status, body) = post("$base/api/pair", JSONObject().put("user_id", id.trim()).put("code", code.trim()).toString(), null)
        if (status != 200) return false
        val t = JSONObject(body).optString("token")
        if (t.isEmpty()) return false
        prefs.edit().putString("server", base).putString("user_id", id.trim()).putString("token", t).apply()
        return true
    }

    fun leave() = prefs.edit().clear().apply()

    fun sendLocation(lat: Double, lng: Double, battery: Int?, speed: Float?, active: Boolean): Boolean {
        val json = JSONObject().put("user_id", userId).put("lat", lat).put("lng", lng)
            .put("is_sharing_active", active).put("timestamp", System.currentTimeMillis() / 1000.0)
        battery?.let { json.put("battery_pct", it) }
        speed?.let { json.put("speed", it.toDouble()) }
        return post("$server/api/location/update", json.toString(), token).first == 200
    }

    fun sendSos(lat: Double?, lng: Double?, battery: Int?): Boolean {
        val boundary = "----seema${System.nanoTime()}"
        val fields = buildMap {
            put("user_id", userId)
            lat?.let { put("lat", it.toString()) }
            lng?.let { put("lng", it.toString()) }
            battery?.let { put("battery_pct", it.toString()) }
        }
        val body = buildString {
            fields.forEach { (k, v) -> append("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n") }
            append("--$boundary--\r\n")
        }
        return post("$server/api/sos/trigger", body, token, "multipart/form-data; boundary=$boundary").first == 200
    }

    private fun post(url: String, body: String, bearer: String?, type: String = "application/json"): Pair<Int, String> = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", type)
        bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        DataOutputStream(c.outputStream).use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        code to text
    }.getOrDefault(-1 to "")
}
