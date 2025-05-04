package com.example.safemonitor

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ScreamDetectionModel(context: Context) {
    private val interpreter: Interpreter

    init {
        val options = Interpreter.Options().apply {
            setUseXNNPACK(false)
            setNumThreads(2)
        }
        interpreter = Interpreter(
            loadModelFile(context, "scream_cnn_augmented.tflite"),
            options
        )
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(modelName)
        FileInputStream(afd.fileDescriptor).use { stream ->
            val fc = stream.channel
            return fc.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    /** Expects [1][64][61][1] input */
    fun classify(input: Array<Array<Array<FloatArray>>>): FloatArray {
        return try {
            val output = Array(1) { FloatArray(2) }  // [non‑scream, scream]
            interpreter.run(input, output)
            output[0]
        } catch (e: Exception) {
            Log.e("ScreamModel", "Error: ${e.message}")
            floatArrayOf(0f,0f)
        }
    }
}
