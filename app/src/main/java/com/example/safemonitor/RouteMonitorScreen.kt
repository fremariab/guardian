// src/main/java/com/example/safemonitor/RouteMonitorScreen.kt
// screen for monitoring the route in real time
package com.example.safemonitor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.tensorflow.lite.Interpreter



@SuppressLint("MissingPermission")
@Composable
fun RouteMonitorScreen(
    interpreter: Interpreter,
    onDeviation: (LatLng) -> Unit    // callback when deviation is detected
) {
    val context = LocalContext.current
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val cameraState = rememberCameraPositionState()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // state variables
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }
    var startLoc   by remember { mutableStateOf<LatLng?>(null) }
    var endLoc     by remember { mutableStateOf<LatLng?>(null) }
    var routePath  by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var running    by remember { mutableStateOf(false) }
    var stopSecs   by remember { mutableStateOf(0L) }       // time the vehicle has been stopped
    var alertFlag  by remember { mutableStateOf(false) }    // used to trigger alert when deviation detected 

    // high-accuracy location updates request
    val locReq = remember {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()
    }
    // Location callback for receiving periodic updates
    val locCb = remember {
        object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                res.lastLocation?.let { loc ->
                    val ll = LatLng(loc.latitude, loc.longitude)
                    currentLoc = ll
                    if (loc.speed * 3.6f < 1f) stopSecs += 5 else stopSecs = 0
                    // check for deviations if monitoring is active and a path is defined
                    if (running && routePath.isNotEmpty()) {
                        checkForDeviation(interpreter, loc, routePath, stopSecs) {
                            alertFlag = it
                        }
                    }
                }
            }
        }
    }

    // setup location updates and initialize camera view
    LaunchedEffect(Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { it?.let {
            currentLoc = LatLng(it.latitude, it.longitude)
            cameraState.position =
                CameraPosition.fromLatLngZoom(currentLoc!!, 16f)
        }}
        fusedLocationClient.requestLocationUpdates(
            locReq, locCb, Looper.getMainLooper()
        )
    }
    // stop location updates when the screen is closed
    DisposableEffect(Unit) {
        onDispose { fusedLocationClient.removeLocationUpdates(locCb) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            // top bar with start/stop monitoring button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    running = !running
                    // fetch the route if the monitoring is starting and the points are set
                    if (running && startLoc != null && endLoc != null) {
                        scope.launch {
                            fetchRoute(
                                origin     = startLoc!!,
                                destination= endLoc!!,
                                context    = context,
                                onSuccess  = { path ->
                                    routePath = path
                                    saveRoute(path, context)
                                },
                                onError    = {
                                    scope.launch {
                                        snackbarHost
                                            .showSnackbar("Failed to fetch route")
                                    }
                                }
                            )
                        }
                    }
                }) {
                    Text(if (running) "Stop Monitor" else "Start Monitor")
                }
            }

            GoogleMap(
                modifier = Modifier.weight(1f),
                cameraPositionState = cameraState
            ) {
                currentLoc?.let {
                    Marker(state = MarkerState(it), title = "You")
                }
                startLoc?.let {
                    Marker(state = MarkerState(it), title = "Start")
                }
                endLoc?.let {
                    Marker(state = MarkerState(it), title = "End")
                }
                if (routePath.isNotEmpty()) {
                    Polyline(
                        points = routePath,
                        color  = MaterialTheme.colorScheme.primary,
                        width  = 5f
                    )
                }
            }

            // WHEN A DEVIATION IS DETECTED:
            LaunchedEffect(alertFlag) {
                if (alertFlag) {
                    currentLoc?.let { onDeviation(it) }  // send SMS
                    snackbarHost.showSnackbar("Deviation Detected!")
                }
            }
        }
    }
}

private fun fetchRoute(
    origin: LatLng,
    destination: LatLng,
    context: Context,
    onSuccess: (List<LatLng>) -> Unit,
    onError: () -> Unit
) {
    val client = OkHttpClient()
    val apiKey = "AIzaSyDGYZL1gxoNLvEh3yYomdIliXnqJZH6_O8"
    val url = "https://maps.googleapis.com/maps/api/directions/json?" +
            "origin=${origin.latitude},${origin.longitude}" +
            "&destination=${destination.latitude},${destination.longitude}" +
            "&key=$apiKey"
    try {
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) return onError()
        val body = resp.body?.string() ?: return onError()
        val routes = JSONObject(body).getJSONArray("routes")
        if (routes.length()==0) return onError()
        val pts = routes
            .getJSONObject(0)
            .getJSONObject("overview_polyline")
            .getString("points")
        onSuccess(PolyUtil.decode(pts))
    } catch (_: Exception) {
        onError()
    }
}

private fun checkForDeviation(
    interpreter: Interpreter,
    loc: Location,
    path: List<LatLng>,
    stopSecs: Long,
    callback: (Boolean)->Unit
) {
    val user = LatLng(loc.latitude, loc.longitude)
    // find min dist to polyline
    var minD = Double.MAX_VALUE
    for (i in 0 until path.size-1) {
        val d = PolyUtil.distanceToLine(user, path[i], path[i+1])
        if (d<minD) minD=d
    }
    val speedKmh = loc.speed * 3.6f
    val tod = when (loc.time % (24*60*60*1000)) {
        in 0..(12*60*60*1000) -> 0f
        in (12*60*60*1000)..(18*60*60*1000) -> 1f
        else -> 2f
    }
    val input = floatArrayOf(
        if (minD>100) 1f else 0f,
        (speedKmh/80f).coerceIn(0f,1f),
        loc.bearing/180f,
        (stopSecs.coerceAtMost(900).toFloat()/900f),
        tod/2f
    )
    val output = Array(1){ FloatArray(1) }
    interpreter.run(arrayOf(input), output)
    callback(output[0][0] < 0.5f)
}

private fun saveRoute(path: List<LatLng>, ctx: Context) {
    val ss = ctx.getSharedPreferences("mlgeofence", Context.MODE_PRIVATE)
    val str = path.joinToString("|") { "${it.latitude},${it.longitude}" }
    ss.edit().putString("saved_route", str).apply()
    Toast.makeText(ctx, "Route cached successfully!", Toast.LENGTH_SHORT).show()
}
