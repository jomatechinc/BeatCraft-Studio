package com.example.audio

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

object FftAnalyser {

    /**
     * In-place Radix-2 FFT computation.
     * @param real Real part of the input signal (size must be a power of 2)
     * @param imag Imaginary part of the input signal (size must match real)
     */
    fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        // Bit-reversal permutation
        var i = 0
        for (j in 1 until n - 1) {
            var bit = n shr 1
            while (i and bit != 0) {
                i = i xor bit
                bit = bit shr 1
            }
            i = i xor bit
            if (j < i) {
                val tempX = real[j]
                real[j] = real[i]
                real[i] = tempX
                val tempY = imag[j]
                imag[j] = imag[i]
                imag[i] = tempY
            }
        }

        // Cooley-Tukey decimation-in-time
        var size = 2
        while (size <= n) {
            val halfSize = size shr 1
            for (k in 0 until n step size) {
                for (j in 0 until halfSize) {
                    val angle = -2.0 * Math.PI * j / size
                    val wr = cos(angle).toFloat()
                    val wi = kotlin.math.sin(angle).toFloat()

                    val targetIndex = k + j
                    val buddyIndex = targetIndex + halfSize

                    // Complex multiplication: twiddle * odd
                    val tr = real[buddyIndex] * wr - imag[buddyIndex] * wi
                    val ti = real[buddyIndex] * wi + imag[buddyIndex] * wr

                    real[buddyIndex] = real[targetIndex] - tr
                    imag[buddyIndex] = imag[targetIndex] - ti
                    real[targetIndex] += tr
                    imag[targetIndex] += ti
                }
            }
            size = size shl 1
        }
    }

    /**
     * Analyses 512 PCM samples and returns 32 logarithmic frequency bands.
     * @param samples 512 normalized mono float samples
     */
    fun analyze(samples: FloatArray): FloatArray {
        val n = samples.size
        if (n != 512) return FloatArray(32)

        val real = FloatArray(512)
        val imag = FloatArray(512)

        // Apply Hanning Window to reduce spectral leakage
        for (i in 0 until 512) {
            val window = 0.5f * (1.0f - cos(2.0 * Math.PI * i / 511.0).toFloat())
            real[i] = samples[i] * window
            imag[i] = 0f
        }

        // Perform FFT
        fft(real, imag)

        // Calculate magnitude for first 256 bins
        val magnitudes = FloatArray(256)
        for (i in 0 until 256) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        // Group into 32 bands using logarithmic scaling (more resolution at bass/low-mids)
        val bands = FloatArray(32)
        for (i in 0 until 32) {
            // Map index (0..31) logarithmically to FFT bins (1..240)
            val startRatio = (i / 32.0).pow(1.6)
            val endRatio = ((i + 1) / 32.0).pow(1.6)
            val startBin = (1 + startRatio * 220).toInt().coerceIn(1, 255)
            val endBin = (1 + endRatio * 220).toInt().coerceIn(startBin, 255)

            var maxVal = 0f
            for (bin in startBin..endBin) {
                if (magnitudes[bin] > maxVal) {
                    maxVal = magnitudes[bin]
                }
            }
            // Logarithmic scaling for visual display aesthetic
            val dbScale = if (maxVal > 0f) {
                val db = 20.0f * kotlin.math.log10(maxVal + 0.001f)
                // Normalize dB range -60 to 0 into 0f..1f
                ((db + 60.0f) / 60.0f).coerceIn(0f, 1f)
            } else {
                0f
            }
            bands[i] = dbScale
        }

        return bands
    }
}
