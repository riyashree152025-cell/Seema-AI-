package com.raju.aiassistant.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.raju.aiassistant.RajuApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved places ("dukaan", "ghar") on the phone only, each watched with Android's own proximity alert: arriving within
 * 150 m wakes [PlaceArrivalReceiver], and Seema says the place and today's remaining tasks. Re-armed when the app starts.
 */
object PlaceAlerts {
    private const val PREFS = "seema_places"
    private const val KEY = "list"

    data class Place(val name: String, val lat: Double, val lon: Double)

    fun all(context: Context): List<Place> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Place(it.getString("name"), it.getDouble("lat"), it.getDouble("lon")) } }
    }.getOrDefault(emptyList())

    fun save(context: Context, place: Place) {
        val list = all(context).filterNot { it.name.equals(place.name, true) } + place
        val arr = JSONArray().apply { list.forEach { p -> put(JSONObject().put("name", p.name).put("lat", p.lat).put("lon", p.lon)) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
        arm(context)
    }

    fun canWatch(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** (Re)registers every saved place's proximity alert. */
    @SuppressLint("MissingPermission") // checked by canWatch()
    fun arm(context: Context) {
        if (!canWatch(context)) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        all(context).forEachIndexed { i, p ->
            runCatching {
                val pi = PendingIntent.getBroadcast(
                    context, 8100 + i,
                    Intent(context, PlaceArrivalReceiver::class.java).putExtra(EXTRA_NAME, p.name),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, // Android adds "entering" to the intent
                )
                lm.removeProximityAlert(pi)
                lm.addProximityAlert(p.lat, p.lon, com.raju.aiassistant.domain.automation.Places.RADIUS_M, -1, pi)
            }
        }
    }

    const val EXTRA_NAME = "place_name"
}

class PlaceArrivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val name = intent.getStringExtra(PlaceAlerts.EXTRA_NAME) ?: return
        val c = (context.applicationContext as? RajuApp)?.container ?: return
        // Not twice within an hour for the same place (GPS can wander across the edge).
        val prefs = context.getSharedPreferences("seema_places_seen", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(name, 0L) < 60 * 60_000L) return
        prefs.edit().putLong(name, now).apply()
        c.engine.onArrivedAt(name)
    }
}
