package com.example.safemonitor

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.safemonitor.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log


class MainActivity : AppCompatActivity(),
    DetectionManager.DetectionListener,
    AlertManager.AlertListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detectionManager: DetectionManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geoInterpreter: Interpreter

    // these come from your UI (hardcoded here for demo)
    private var startLoc: LatLng = LatLng(5.6500, -0.1807)
    private var endLoc:   LatLng = LatLng(5.6510, -0.1817)

    private var cachedRoute: List<LatLng>? = null
    private var isMonitoring = false

    // location monitoring
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    companion object {
        private const val REQ_SMS_LOC = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 1) Load your TFLite model
        interpreter = Interpreter(loadModelFile("deviation_nn_model.tflite"))

        // 2) Init location client
        fusedLocationClient = LocationServices
            .getFusedLocationProviderClient(this)

        // 3) Init Places
        if (!Places.isInitialized()) {
            Places.initialize(
                applicationContext,
                getString(R.string.google_maps_key)
            )
        }

        // 4) Ask location perms
        binding.composeView.post {   // defer until view ready
            requestLocationPermission()
        }

        // 5) Hook ComposeView to your existing layout
        binding.composeView.setContent {
            MainScreen(
                interpreter    = interpreter,
                fusedLocationClient = fusedLocationClient
            )
        }
    }

    // 1) Audio pipeline
        detectionManager = DetectionManager(this, this)

        // 2) Deviation-model
        geoInterpreter = Interpreter(loadModelFile("deviation_nn_model.tflite"))

        // 3) FusedLocation
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5_000L
        )
            .setMinUpdateIntervalMillis(3_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                val loc = res.lastLocation ?: return
                Log.d("GEOFENCE", "Got GPS update at ${loc.latitude},${loc.longitude}")
                cachedRoute?.let { route ->
                    if (checkForDeviation(route, loc)) {
                        AlertManager.onGeoDeviation(
                            LatLng(loc.latitude, loc.longitude)
                        )
                    }
                }
            }
        }

        // 4) Subscribe to aggregated alerts
        AlertManager.register(this)

        // 5) Start/Stop button
        binding.startStopButton.setOnClickListener {
            if (isMonitoring) stopFullMonitoring() else startFullMonitoring()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detectionManager.stop()
        stopLocationUpdates()
        AlertManager.unregister(this)
    }

    /** ← Aggregated callback from Audio/Face/Geo */
    override fun onEvent(event: AlertManager.Event) {
        when (event) {
            is AlertManager.Event.Geo ->
                sendEmergencySmsSafely(event.location)
            // TODO later: handle Audio or Face events here
            else -> { /* no-op */ }
        }
    }

    /** ← From DetectionManager (audio) */
    override fun onAudioTrigger(screamConfidence: Float, angerConfidence: Float) {
        // existing UI update…
        binding.statusText.text = buildString {
            if (screamConfidence > 0f) append("🚨 Scream: ${(screamConfidence*100).toInt()}%\n")
            if (angerConfidence  > 0f) append("😠 Anger:  ${(angerConfidence*100).toInt()}")
        }
        // and forward into AlertManager if you like:
        AlertManager.onAudio(screamConfidence, angerConfidence)
    }

    private fun startFullMonitoring() {
        // 1) Audio
        detectionManager.start()

        fetchRoute(startLoc, endLoc) { path ->
            cachedRoute = path
            saveRoute(path)
            startLocationUpdates()

            // ← ADD THIS TO LAUNCH YOUR COMPOSE UI
            val i = Intent(this, RouteMonitorActivity::class.java)
            startActivity(i)
        }

        binding.startStopButton.text = "Stop Monitoring"
        isMonitoring = true
    }

    private fun stopFullMonitoring() {
        detectionManager.stop()
        stopLocationUpdates()
        binding.startStopButton.text = "Start Monitoring"
        isMonitoring = false
    }

    // ——————— Permissions ———————

    private fun hasSendSmsPermission() =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestSmsAndLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            REQ_SMS_LOC
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SMS_LOC &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            // you could re-try the last pending SMS here
        }
    }

    // ——————— Location Updates ———————

    private fun startLocationUpdates() {
        if (hasLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
        } else {
            requestSmsAndLocationPermissions()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ——————— Route Fetch & Cache ———————

    private fun fetchRoute(
        origin: LatLng,
        destination: LatLng,
        onSuccess: (List<LatLng>) -> Unit
    ) {
        Thread {
            try {
                val url = ("https://maps.googleapis.com/maps/api/directions/json"
                        + "?origin=${origin.latitude},${origin.longitude}"
                        + "&destination=${destination.latitude},${destination.longitude}"
                        + "&key=YOUR_API_KEY")
                val resp = OkHttpClient()
                    .newCall(Request.Builder().url(url).build())
                    .execute()
                val body = resp.body?.string() ?: return@Thread
                val routes = JSONObject(body).getJSONArray("routes")
                if (routes.length() == 0) return@Thread
                val pts = routes
                    .getJSONObject(0)
                    .getJSONObject("overview_polyline")
                    .getString("points")
                val path = PolyUtil.decode(pts)
                runOnUiThread { onSuccess(path) }
            } catch (_: Exception) { /* handle errors */ }
        }.start()
    }

    private fun saveRoute(path: List<LatLng>) {
        val ss = getSharedPreferences("mlgeofence", MODE_PRIVATE)
        val str = path.joinToString("|") { "${it.latitude},${it.longitude}" }
        ss.edit().putString("saved_route", str).apply()
        Toast.makeText(this, "Route cached!", Toast.LENGTH_SHORT).show()
    }

    // ——————— Deviation Check ———————

    private fun checkForDeviation(route: List<LatLng>, loc: Location): Boolean {
        val user = LatLng(loc.latitude, loc.longitude)
        var minD = Double.MAX_VALUE
        for (i in 0 until route.size - 1) {
            minD = minD.coerceAtMost(
                PolyUtil.distanceToLine(user, route[i], route[i + 1])
            )
        }
        val speedKmh = loc.speed * 3.6f
        val tod = (loc.time % (24*60*60*1000)).let {
            when (it) {
                in 0..(12*60*60*1000)    -> 0f
                in (12*60*60*1000)..(18*60*60*1000) -> 1f
                else                     -> 2f
            }
        }
        val input = floatArrayOf(
            if (minD > 100) 1f else 0f,
            (speedKmh / 80f).coerceIn(0f, 1f),
            loc.bearing / 180f,
            0f,   // <-- insert your stopSecs logic if you have it
            tod / 2f
        )
        val output = Array(1) { FloatArray(1) }
        geoInterpreter.run(arrayOf(input), output)
        return output[0][0] < 0.5f
    }

    // ——————— SMS Sending ———————

    private fun sendEmergencySmsSafely(location: LatLng) {
        if (hasSendSmsPermission() && hasLocationPermission()) {
            sendEmergencySms(location)
        } else {
            requestSmsAndLocationPermissions()
        }
    }

    private fun sendEmergencySms(location: LatLng) {
        val msg = "ALERT! Deviation at Lat:${location.latitude},Lng:${location.longitude}"
        val sms = SmsManager.getDefault()
        listOf("+233545581475").forEach {
            sms.sendTextMessage(it, null, msg, null, null)
        }
        Toast.makeText(this, "Emergency SMS sent", Toast.LENGTH_SHORT).show()
    }

    // ——————— Utility ———————

    private fun loadModelFile(filename: String): MappedByteBuffer =
        assets.openFd(filename).let { afd ->
            FileInputStream(afd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }

}
