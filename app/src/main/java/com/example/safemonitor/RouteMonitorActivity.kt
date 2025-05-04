// src/main/java/com/example/safemonitor/RouteMonitorActivity.kt
package com.example.safemonitor

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.safemonitor.ui.theme.SafeMonitorTheme
import com.google.android.gms.maps.model.LatLng
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class RouteMonitorActivity : ComponentActivity() {
    companion object {
        private const val REQ_SMS = 2001
    }
    private lateinit var interpreter: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load TFLite
        interpreter = Interpreter(loadModelFile("deviation_nn_model.tflite"))

        // Request SEND_SMS permission if not granted
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.SEND_SMS), REQ_SMS
            )
        }

        setContent {
            SafeMonitorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RouteMonitorScreen(
                        interpreter = interpreter,
                        onDeviation  = { location -> sendDeviationSms(location) }
                    )
                }
            }
        }
    }

    private fun sendDeviationSms(location: LatLng) {
        val locText = "Lat:${location.latitude},Lng:${location.longitude}"
        val message = "ALERT! Route deviation detected at $locText"
        val contacts = listOf("+233545581475")  // load or hardcode as needed
        val sms = SmsManager.getDefault()
        for (num in contacts) {
            sms.sendTextMessage(num, null, message, null, null)
        }
        Toast.makeText(this, "Deviation SMS sent", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SMS &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModelFile(filename: String): MappedByteBuffer {
        val afd = assets.openFd(filename)
        FileInputStream(afd.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }
}
