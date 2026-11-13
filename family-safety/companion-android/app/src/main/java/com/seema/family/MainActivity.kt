package com.seema.family

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * The whole app on one screen: join the family once (admin's code), a big switch to share or stop
 * sharing, a big red SOS button (always asks to confirm), and an optional shake-to-SOS. Text follows the
 * phone's language (Hindi / Bengali / English).
 */
class MainActivity : AppCompatActivity(), SensorEventListener {
    private val api by lazy { FamilyApi(this) }
    private val io = Executors.newSingleThreadExecutor()
    private var shakes = 0
    private var firstShakeAt = 0L
    private var sosDialogOpen = false

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) setSharing(true)
        else Toast.makeText(this, R.string.need_permission, Toast.LENGTH_LONG).show()
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        val sm = getSystemService(SensorManager::class.java)
        if (api.shakeSos) sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        getSystemService(SensorManager::class.java)?.unregisterListener(this)
        super.onPause()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        if (!api.joined) renderJoin(root) else renderHome(root)
        setContentView(root)
    }

    private fun renderJoin(root: LinearLayout) {
        root.addView(title(getString(R.string.setup)))
        val server = field(getString(R.string.server_url), InputType.TYPE_TEXT_VARIATION_URI)
        val id = field(getString(R.string.user_id), InputType.TYPE_CLASS_TEXT)
        val code = field(getString(R.string.pairing_code), InputType.TYPE_CLASS_NUMBER)
        listOf(server, id, code).forEach(root::addView)
        root.addView(Button(this).apply {
            text = getString(R.string.setup)
            setOnClickListener {
                isEnabled = false
                io.execute {
                    val ok = api.join(server.text.toString(), id.text.toString(), code.text.toString())
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, if (ok) R.string.joined else R.string.join_failed, Toast.LENGTH_LONG).show()
                        render()
                    }
                }
            }
        })
    }

    private fun renderHome(root: LinearLayout) {
        root.addView(title(getString(R.string.app_name)))
        val status = TextView(this).apply {
            textSize = 18f
            text = getString(if (api.sharing) R.string.sharing_on else R.string.sharing_off)
            setTextColor(if (api.sharing) Color.rgb(0, 128, 0) else Color.DKGRAY)
            setPadding(0, 24, 0, 24)
        }
        root.addView(MaterialSwitch(this).apply {
            text = getString(R.string.share_location)
            textSize = 22f
            isChecked = api.sharing
            setOnCheckedChangeListener { _, on -> if (on) startSharingWithPermission() else setSharing(false); status.text = getString(if (on) R.string.sharing_on else R.string.sharing_off) }
        })
        root.addView(status)
        root.addView(Button(this).apply {
            text = getString(R.string.sos)
            textSize = 40f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(211, 47, 47))
            minHeight = 420
            gravity = Gravity.CENTER
            setOnClickListener { confirmSos() }
        })
        root.addView(CheckBox(this).apply {
            text = getString(R.string.shake_sos)
            isChecked = api.shakeSos
            setPadding(0, 32, 0, 32)
            setOnCheckedChangeListener { _, on -> api.shakeSos = on; onPause(); onResume() }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.leave_family)
            setOnClickListener {
                setSharing(false)
                api.leave()
                render()
            }
        })
    }

    private fun startSharingWithPermission() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) setSharing(true)
        else askPermissions.launch(needed.toTypedArray())
    }

    /** The user's switch: ON starts the visible service; OFF tells the service to send "off" and stop. */
    private fun setSharing(on: Boolean) {
        api.sharing = on
        val i = Intent(this, LocationService::class.java)
        if (on) ContextCompat.startForegroundService(this, i)
        else if (api.joined) startService(i.setAction(LocationService.ACTION_STOP))
    }

    private fun confirmSos() {
        if (sosDialogOpen) return
        sosDialogOpen = true
        AlertDialog.Builder(this)
            .setTitle(R.string.sos_confirm_title)
            .setMessage(R.string.sos_confirm_text)
            .setPositiveButton(R.string.sos_send) { _, _ -> sendSos() }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { sosDialogOpen = false }
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun sendSos() {
        val battery = (getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val send = { lat: Double?, lng: Double? ->
            io.execute {
                val ok = api.sendSos(lat, lng, battery)
                runOnUiThread { Toast.makeText(this, if (ok) R.string.sos_sent else R.string.sos_failed, Toast.LENGTH_LONG).show() }
            }
        }
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) { send(null, null); return }
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc -> send(loc?.latitude, loc?.longitude) }
            .addOnFailureListener { send(null, null) }
    }

    // Shake-to-SOS (only when the user ticked it, only while this screen is open): three strong shakes
    // within 2 s open the same confirmation as the button — nothing is sent without "Send SOS".
    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
        if (g < 2.7f) return
        val now = System.currentTimeMillis()
        if (now - firstShakeAt > 2_000) { firstShakeAt = now; shakes = 0 }
        if (++shakes >= 3) { shakes = 0; confirmSos() }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun title(t: String) = TextView(this).apply { text = t; textSize = 28f; setPadding(0, 0, 0, 32) }

    private fun field(hint: String, type: Int) = EditText(this).apply { this.hint = hint; inputType = type; isSingleLine = true }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
