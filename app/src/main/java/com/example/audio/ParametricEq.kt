package com.example.audio

import kotlin.math.*

class BiquadFilter(
    var type: Type,
    var f0: Float,
    var gainDb: Float,
    var q: Float = 1.0f,
    val sampleRate: Float = 44100f
) {
    enum class Type {
        LOW_SHELF, PEAKING, HIGH_SHELF
    }

    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a0 = 1f
    private var a1 = 0f
    private var a2 = 0f

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    init {
        updateCoefficients()
    }

    fun update(f0: Float, gainDb: Float, q: Float = 1.0f) {
        this.f0 = f0.coerceIn(20f, 20000f)
        this.gainDb = gainDb.coerceIn(-12f, 12f)
        this.q = q.coerceIn(0.1f, 10f)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0f * q)
        val a = 10.0f.pow(gainDb / 40.0f)

        when (type) {
            Type.LOW_SHELF -> {
                val aPlus1 = a + 1f
                val aMinus1 = a - 1f
                val beta = 2.0f * sqrt(a) * alpha

                b0 = a * (aPlus1 - aMinus1 * cosW0 + beta)
                b1 = 2f * a * (aMinus1 - aPlus1 * cosW0)
                b2 = a * (aPlus1 - aMinus1 * cosW0 - beta)
                a0 = aPlus1 + aMinus1 * cosW0 + beta
                a1 = -2f * (aMinus1 + aPlus1 * cosW0)
                a2 = aPlus1 + aMinus1 * cosW0 - beta
            }
            Type.PEAKING -> {
                b0 = 1f + alpha * a
                b1 = -2f * cosW0
                b2 = 1f - alpha * a
                a0 = 1f + alpha / a
                a1 = -2f * cosW0
                a2 = 1f - alpha / a
            }
            Type.HIGH_SHELF -> {
                val aPlus1 = a + 1f
                val aMinus1 = a - 1f
                val beta = 2.0f * sqrt(a) * alpha

                b0 = a * (aPlus1 + aMinus1 * cosW0 + beta)
                b1 = -2f * a * (aMinus1 + aPlus1 * cosW0)
                b2 = a * (aPlus1 + aMinus1 * cosW0 - beta)
                a0 = aPlus1 - aMinus1 * cosW0 + beta
                a1 = 2f * (aMinus1 - aPlus1 * cosW0)
                a2 = aPlus1 - aMinus1 * cosW0 - beta
            }
        }
    }

    fun process(sample: Float): Float {
        if (a0 == 0f) return sample
        val out = (b0 / a0) * sample + (b1 / a0) * x1 + (b2 / a0) * x2 - (a1 / a0) * y1 - (a2 / a0) * y2
        
        // Update states
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = if (out.isNaN() || out.isInfinite()) 0f else out.coerceIn(-2.0f, 2.0f)
        
        return y1
    }

    fun getMagnitudeAtFrequency(f: Float): Float {
        val w = (2.0 * PI * f / sampleRate).toFloat()
        val cosW = cos(w)
        val cos2W = cos(2f * w)
        val sinW = sin(w)
        val sin2W = sin(2f * w)

        // Numerator
        val numReal = b0 + b1 * cosW + b2 * cos2W
        val numImag = -(b1 * sinW + b2 * sin2W)
        val numMag2 = numReal * numReal + numImag * numImag

        // Denominator
        val denReal = a0 + a1 * cosW + a2 * cos2W
        val denImag = -(a1 * sinW + a2 * sin2W)
        val denMag2 = denReal * denReal + denImag * denImag

        if (denMag2 <= 0.0001f) return 1f
        return sqrt(numMag2 / denMag2)
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }
}

class TrackEq(val trackName: String) {
    val lowBand = BiquadFilter(BiquadFilter.Type.LOW_SHELF, f0 = 150f, gainDb = 0f, q = 1.0f)
    val midBand = BiquadFilter(BiquadFilter.Type.PEAKING, f0 = 1000f, gainDb = 0f, q = 1.0f)
    val highBand = BiquadFilter(BiquadFilter.Type.HIGH_SHELF, f0 = 5000f, gainDb = 0f, q = 1.0f)

    var isBypassed: Boolean = false

    fun process(sample: Float): Float {
        if (isBypassed) return sample
        
        var out = sample
        out = lowBand.process(out)
        out = midBand.process(out)
        out = highBand.process(out)
        return out
    }

    fun getCombinedMagnitudeDb(f: Float): Float {
        if (isBypassed) return 0f
        val mLow = lowBand.getMagnitudeAtFrequency(f)
        val mMid = midBand.getMagnitudeAtFrequency(f)
        val mHigh = highBand.getMagnitudeAtFrequency(f)
        val combined = mLow * mMid * mHigh
        return 20f * log10(combined.coerceAtLeast(0.0001f))
    }

    fun reset() {
        lowBand.reset()
        midBand.reset()
        highBand.reset()
    }
}
