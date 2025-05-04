
package com.example.safemonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.safemonitor.AngerDetectionModel
import com.example.safemonitor.ScreamDetectionModel
import com.example.safemonitor.SpectrogramUtils
import kotlin.math.abs

class DetectionManager(
    private val context: Context,
    private val listener: DetectionListener
) {
    interface DetectionListener {
        fun onAudioTrigger(screamConfidence: Float, angerConfidence: Float)
    }

    private lateinit var sensorManager: SensorManager        // ADD: SensorManager field
    private var isMoving = true                             // ADD: movement flag
    private val MOVEMENT_THRESHOLD = 0.5f                   // experiment to tune

    // audio I/O / buffer
    private val sampleRate = 16_000
    private val windowSize = sampleRate * 2
    private var bufferSize = 0
    private lateinit var audioRecord: AudioRecord
    private val rollingBuffer = FloatArray(windowSize)
    private val shortBuffer: ShortArray
    private val handler = Handler(Looper.getMainLooper())


    // throttle / smoothing
    private var lastAngerTime = 0L
    private var lastScreamTime = 0L
    private val angerIntervalMs = 500L
    private val screamIntervalMs = 1000L

    private val angerHistory = ArrayDeque<String>()
    private val smoothingWindow = 3

    // models + thresholds
    private val angerModel = AngerDetectionModel(context)
    private val screamModel = ScreamDetectionModel(context)
    private val angerThreshold = 0.5f
    private val screamThreshold = 0.6f

    private var isRecording = false

    init {
        bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        shortBuffer = ShortArray(bufferSize / 2)

    }

    fun start() {
        if (isRecording) return

        // Check permission at runtime
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("DetectionManager", "RECORD_AUDIO permission not granted")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord.startRecording()
            isRecording = true
            Thread { captureLoop() }.start()
        } catch (e: SecurityException) {
            Log.e("DetectionManager", "Failed to start recording", e)
        } catch (e: Exception) {
            Log.e("DetectionManager", "AudioRecord initialization failed", e)
        }
    }

    fun stop() {
        if (!isRecording) return

        isRecording = false
        try {
            if (::audioRecord.isInitialized) {
                audioRecord.stop()
                audioRecord.release()
            }
        } catch (e: Exception) {
            Log.e("DetectionManager", "Error stopping AudioRecord", e)
        }
    }

    private fun captureLoop() {
        while (isRecording) {
            val read = try {
                audioRecord.read(shortBuffer, 0, shortBuffer.size)
            } catch (e: Exception) {
                Log.e("DetectionManager", "Error reading audio", e)
                break
            }

            if (read <= 0) continue

            System.arraycopy(rollingBuffer, read, rollingBuffer, 0, windowSize - read)
            for (i in 0 until read) {
                rollingBuffer[windowSize - read + i] = shortBuffer[i] / 32768f
            }

            val now = System.currentTimeMillis()
            val avgMag = rollingBuffer.fold(0f) { sum, v -> sum + abs(v) } / windowSize
            if (avgMag < 0.01f) continue  // skip silent

            // — Anger every 0.5s —
            if (now - lastAngerTime >= angerIntervalMs) {
                lastAngerTime = now

                val melSpecAnger = SpectrogramUtils.computeAngerMelSpectrogram(
                    signal = rollingBuffer,
                    sampleRate = sampleRate,
                    nMels = 128
                )
                val angerInput = convertToAngerInput(melSpecAnger)
                val rawAnger = angerModel.classify(angerInput).getOrNull(0) ?: 0f

                // smoothing
                val label = if (rawAnger > angerThreshold) "angry" else "neutral"
                angerHistory.addLast(label)
                if (angerHistory.size > smoothingWindow) angerHistory.removeFirst()
                val smoothLabel = angerHistory
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key ?: label
                val smoothAngerProb = if (smoothLabel == "angry") rawAnger else 0f

                handler.post {
                    listener.onAudioTrigger(
                        screamConfidence = 0f,
                        angerConfidence = smoothAngerProb
                    )
                }
            }

            // — Scream every 1s —
            if (now - lastScreamTime >= screamIntervalMs) {
                lastScreamTime = now

                val melSpecScream = SpectrogramUtils.computeScreamMelSpectrogram(
                    signal = rollingBuffer,
                    sampleRate = sampleRate,
                    nMels = 64,
                    timeSteps = 61
                )
                val screamInput = convertToScreamInput(melSpecScream)
                val screamProb = screamModel.classify(screamInput).getOrNull(1) ?: 0f

                handler.post {
                    listener.onAudioTrigger(
                        screamConfidence = screamProb,
                        angerConfidence = 0f
                    )
                }
            }
        }
    }

    private fun convertToAngerInput(
        melSpec: Array<FloatArray>
    ): Array<Array<Array<FloatArray>>> {
        val bands = melSpec.size
        val frames = 128
        val padded = Array(bands) { m ->
            FloatArray(frames) { t ->
                if (t < melSpec[m].size) melSpec[m][t] else -80f
            }
        }

        return Array(1) {
            Array(bands) { m ->
                Array(frames) { t ->
                    FloatArray(1).also { arr ->
                        // manual clamp
                        val v = padded[m][t]
                        val clipped = when {
                            v < -80f -> -80f
                            v > 0f -> 0f
                            else -> v
                        }
                        arr[0] = (clipped + 80f) / 80f
                    }
                }
            }
        }
    }

    private fun convertToScreamInput(
        melSpec: Array<FloatArray>
    ): Array<Array<Array<FloatArray>>> {
        return Array(1) {
            Array(64) { m ->
                Array(61) { t ->
                    FloatArray(1) { melSpec[m][t] }
                }
            }
        }
    }
}