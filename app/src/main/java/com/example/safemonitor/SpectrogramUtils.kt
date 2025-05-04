package com.example.safemonitor

import kotlin.math.*
import org.jtransforms.fft.FloatFFT_1D

object SpectrogramUtils {
    private fun computeInternal(
        signal: FloatArray,
        sampleRate: Int,
        nMels: Int,
        frames: Int,
        fftSize: Int,
        hopSize: Int,
        maxValFn: (FloatArray)->Float
    ): Array<FloatArray> {
        val spec = Array(nMels){ FloatArray(frames) }
        val fft  = FloatFFT_1D(fftSize.toLong())
        val bank = MelFilterbank(nMels, fftSize, sampleRate)
        val window = FloatArray(fftSize){ i ->
            0.5f*(1f - cos(2f*PI.toFloat()*i/(fftSize-1)))
        }

        for (i in 0 until frames) {
            val start = i*hopSize
            if (start+fftSize > signal.size) break

            val buf = FloatArray(2*fftSize).apply {
                for (n in 0 until fftSize) this[n] = signal[start+n]*window[n]
            }
            fft.realForwardFull(buf)

            val power = FloatArray(fftSize/2){ k ->
                val re = buf[2*k]; val im = buf[2*k+1]
                (re*re + im*im)/fftSize
            }

            val melBands = bank.apply(power)
            val mval     = maxValFn(melBands)

            for (j in melBands.indices) {
                spec[j][i] = 10f * log10(max(1e-10f, melBands[j]/mval))
            }
        }
        return spec
    }

    fun computeScreamMelSpectrogram(
        signal: FloatArray,
        sampleRate: Int,
        nMels: Int,
        timeSteps: Int,
        fftSize: Int = 512,
        hopSize: Int = 256
    ) = computeInternal(
        signal, sampleRate, nMels, timeSteps,
        fftSize, hopSize
    ) { bands -> bands.maxOrNull() ?: 1e-10f }

    fun computeAngerMelSpectrogram(
        signal: FloatArray,
        sampleRate: Int,
        nMels: Int,
        fftSize: Int = 512,
        hopSize: Int = 256
    ): Array<FloatArray> {
        val frames = 1 + (signal.size - fftSize)/hopSize
        return computeInternal(
            signal, sampleRate, nMels, frames, fftSize, hopSize
        ) { bands ->
            val rm = bands.maxOrNull() ?: 0f
            if (rm<1e-10f) 1e-10f else rm
        }
    }
}
