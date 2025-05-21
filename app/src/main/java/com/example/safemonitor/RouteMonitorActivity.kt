// src/main/java/com/example/safemonitor/RouteMonitorActivity.kt
// Activity file to monitor the user's route and detect unhamful deviations with the TFLite model
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
        // request code for identifying SMS permission request
        private const val REQ_SMS = 2001
    }
    // TensorFlow Lite Interpreter for route deviation detection model
    private lateinit var interpreter: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load model from assets
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
        // set up the UI using the Compose
        setContent {
            SafeMonitorTheme {
                // Create surface for UI content
                Surface(modifier = Modifier.fillMaxSize()) {
                    // load the screen to monitor route deviations
                    RouteMonitorScreen(
                        interpreter = interpreter,
                        // trigger SMS alert if deviation detected
                        onDeviation  = { location -> sendDeviationSms(location) }
                    )
                }
            }
        }
    }

     // send SMS alert when the deviation is detected
    private fun sendDeviationSms(location: LatLng) {
        val locText = "Lat:${location.latitude},Lng:${location.longitude}"
        val message = "ALERT! Route deviation detected at $locText"
        // number of trusted contact to send SMS when deviation detected
        val contacts = listOf("+233545581475")  
        // send SMS to the trusted contact
        val sms = SmsManager.getDefault()
        for (num in contacts) {
            sms.sendTextMessage(num, null, message, null, null)
        }
        // user feedback using toast when SMS is sent
        Toast.makeText(this, "Deviation SMS sent", Toast.LENGTH_SHORT).show()
    }

    // callback for handling permission request results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // confirm the SMS permission was granted
        if (requestCode == REQ_SMS &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    // load the model file from the assets 
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
