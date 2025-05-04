package com.example.safemonitor

import kotlin.math.*

class MelFilterbank(
    private val numMelBands: Int,
    fftSize: Int,
    sampleRate: Int
) {
    private val filterBank: Array<FloatArray>
    private val fftBins = fftSize / 2

    init {
        val melMin = hzToMel(0f)
        val melMax = hzToMel(sampleRate / 2f)
        val melPoints = FloatArray(numMelBands + 2) { i ->
            melMin + i * (melMax - melMin) / (numMelBands + 1)
        }
        val hzPoints  = melPoints.map { melToHz(it) }
        val binPoints = hzPoints.map { ((fftSize + 1) * it / sampleRate).toInt() }

        filterBank = Array(numMelBands) { band ->
            FloatArray(fftBins).apply {
                val start  = binPoints[band]
                val center = binPoints[band+1]
                val end    = binPoints[band+2]
                for (k in start until center) if (k in indices)
                    this[k] = (k - start).toFloat() / (center - start)
                for (k in center until end) if (k in indices)
                    this[k] = (end - k).toFloat() / (end - center)
            }
        }
    }

    fun apply(power: FloatArray): FloatArray {
        return FloatArray(numMelBands) { band ->
            var sum = 0f; var wsum = 0f
            for (k in filterBank[band].indices) {
                sum  += filterBank[band][k] * power[k]
                wsum += filterBank[band][k]
            }
            if (wsum>0) sum/wsum else 0f
        }
    }

    private fun hzToMel(hz: Float) = 2595f * log10(1f + hz/700f)
    private fun melToHz(mel: Float) = 700f * (10f.pow(mel/2595f) - 1f)
}
