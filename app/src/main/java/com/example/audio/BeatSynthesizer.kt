package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class BeatSynthesizer {
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Real-time live triggers for MIDI/Keyboard mapping
    val liveKickTrigger = java.util.concurrent.atomic.AtomicBoolean(false)
    val liveSnareTrigger = java.util.concurrent.atomic.AtomicBoolean(false)
    val liveHihatTrigger = java.util.concurrent.atomic.AtomicBoolean(false)
    val livePercTrigger = java.util.concurrent.atomic.AtomicBoolean(false)
    val liveBassQueue = java.util.concurrent.ConcurrentLinkedQueue<Double>()
    val liveSynthQueue = java.util.concurrent.ConcurrentLinkedQueue<Double>()
    val liveSampleTrigger = java.util.concurrent.atomic.AtomicReference<String>(null)

    fun triggerSampleSound(sampleType: String) {
        if (_isPlaying.value) {
            liveSampleTrigger.set(sampleType)
        } else {
            playPreviewSample(sampleType)
        }
    }

    private fun playPreviewSample(sampleType: String) {
        scope.launch {
            val sampleRate = 44100
            val durationSec = when (sampleType) {
                "VOX_HEY" -> 0.25
                "VOX_AHH" -> 0.6
                "SIREN_FX" -> 1.0
                "LASER_FX" -> 0.2
                "SUB_DROP" -> 1.5
                "CHIP_RISER" -> 1.2
                else -> 0.5
            }
            val totalSamples = (sampleRate * durationSec).toInt()
            val shortBuffer = ShortArray(totalSamples * 2)
            val random = java.util.Random()
            var localPhase = 0.0

            for (s in 0 until totalSamples) {
                val time = s / sampleRate.toDouble()
                var sample = 0.0f
                when (sampleType) {
                    "VOX_HEY" -> {
                        val f1 = sin(2.0 * Math.PI * 220.0 * time) * exp(-time / 0.04)
                        val f2 = sin(2.0 * Math.PI * 880.0 * time) * exp(-time / 0.06)
                        val f3 = sin(2.0 * Math.PI * 1300.0 * time) * exp(-time / 0.08)
                        val noise = (random.nextFloat() * 2.0f - 1.0f) * exp(-time / 0.03) * 0.4f
                        sample = ((f1 * 0.4 + f2 * 0.3 + f3 * 0.2) * exp(-time / 0.1) * 0.6 + noise).toFloat() * 0.8f
                    }
                    "VOX_AHH" -> {
                        val f1 = sin(2.0 * Math.PI * 400.0 * time)
                        val f2 = sin(2.0 * Math.PI * 800.0 * time) * 0.5
                        val f3 = sin(2.0 * Math.PI * 1200.0 * time) * 0.25
                        val vibrato = 1.0 + 0.02 * sin(2.0 * Math.PI * 6.0 * time)
                        val wave = (f1 + f2 + f3) * vibrato
                        val env = exp(-time / 0.2).toFloat()
                        sample = (wave * env * 0.45f).toFloat()
                    }
                    "SIREN_FX" -> {
                        val sweepSpeed = 8.0
                        val currentFreq = 600.0 + 300.0 * sin(2.0 * Math.PI * sweepSpeed * time)
                        localPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                        val env = if (time < 0.8) 1.0 else (1.0 - (time - 0.8) / 0.2)
                        sample = (sin(localPhase) * env * 0.35f).toFloat()
                    }
                    "LASER_FX" -> {
                        val currentFreq = 80.0 + 2200.0 * exp(-time / 0.04)
                        localPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                        val env = exp(-time / 0.08).toFloat()
                        sample = (sin(localPhase) * env * 0.4f).toFloat()
                    }
                    "SUB_DROP" -> {
                        val currentFreq = 30.0 + 100.0 * exp(-time / 0.35)
                        localPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                        val env = exp(-time / 0.45).toFloat()
                        sample = (sin(localPhase) * env * 0.75f).toFloat()
                    }
                    "CHIP_RISER" -> {
                        val currentFreq = 200.0 + 1400.0 * (time / durationSec)
                        localPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                        val wave = if (sin(localPhase) > 0.0) 1.0 else -1.0
                        val env = (time / durationSec).toFloat() * (1f - (time / durationSec).toFloat()) * 4f
                        sample = (wave * env * 0.22f).toFloat()
                    }
                }
                
                sample = sample.coerceIn(-1.0f, 1.0f)
                val outShort = (sample * 32767.0f).toInt().toShort()
                shortBuffer[s * 2] = outShort
                shortBuffer[s * 2 + 1] = outShort
            }

            try {
                val tempTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    shortBuffer.size * 2,
                    AudioTrack.MODE_STATIC
                )
                tempTrack.write(shortBuffer, 0, shortBuffer.size)
                tempTrack.play()
                kotlinx.coroutines.delay((durationSec * 1000).toLong() + 100)
                tempTrack.stop()
                tempTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerLiveSound(track: String, note: String = "C4") {
        if (_isPlaying.value) {
            when (track) {
                "KICK" -> liveKickTrigger.set(true)
                "SNARE" -> liveSnareTrigger.set(true)
                "HIHAT" -> liveHihatTrigger.set(true)
                "PERC" -> livePercTrigger.set(true)
                "BASS" -> {
                    val freq = noteToFreq(note)
                    if (freq > 0.0) liveBassQueue.add(freq)
                }
                "SYNTH" -> {
                    val freq = noteToFreq(note)
                    if (freq > 0.0) liveSynthQueue.add(freq)
                }
            }
        } else {
            playPreviewSound(track, note)
        }
    }

    private fun playPreviewSound(track: String, note: String) {
        scope.launch {
            val sampleRate = 44100
            val durationSec = when (track) {
                "KICK" -> 0.15
                "SNARE" -> 0.22
                "HIHAT" -> 0.05
                "PERC" -> 0.18
                else -> 0.8
            }
            val totalSamples = (sampleRate * durationSec).toInt()
            val shortBuffer = ShortArray(totalSamples * 2)
            val random = java.util.Random()
            val freq = noteToFreq(note)
            
            var kickPhaseLocal = 0.0
            var bassPhaseLocal = 0.0
            var bassEnvLocal = 1.0
            var synthPhaseLocal = 0.0
            var synthEnvLocal = 1.0

            for (s in 0 until totalSamples) {
                val time = s / sampleRate.toDouble()
                var sample = 0.0f
                when (track) {
                    "KICK" -> {
                        val currentFreq = 48.0 + (160.0 - 48.0) * exp(-time / 0.02)
                        kickPhaseLocal += (2.0 * Math.PI * currentFreq / sampleRate)
                        val amplitude = exp(-time / 0.04)
                        sample = (sin(kickPhaseLocal) * amplitude * kickVolume).toFloat()
                    }
                    "SNARE" -> {
                        val noise = (random.nextFloat() * 2.0f - 1.0f)
                        val env = exp(-time / 0.05)
                        val bodyVal = sin(2.0 * Math.PI * 180.0 * time) * exp(-time / 0.02)
                        sample = ((noise * 0.7 + bodyVal * 0.3) * env * snareVolume).toFloat()
                    }
                    "HIHAT" -> {
                        val noise = (random.nextFloat() * 2.0f - 1.0f)
                        val env = exp(-time / 0.015)
                        sample = (noise * env * hihatVolume).toFloat()
                    }
                    "PERC" -> {
                        val percussionFreq = 600.0
                        val car = sin(2.0 * Math.PI * percussionFreq * time)
                        val mod = sin(2.0 * Math.PI * (percussionFreq * 1.41) * time)
                        val env = exp(-time / 0.04)
                        sample = (car * mod * env * percussionVolume).toFloat()
                    }
                    "BASS" -> {
                        if (freq > 0.0 && bassEnvLocal > 0.01) {
                            bassPhaseLocal += (2.0 * Math.PI * freq / sampleRate)
                            val p = (bassPhaseLocal % (2.0 * Math.PI)) / (2.0 * Math.PI)
                            val bassWave = if (p < 0.5) (4.0 * p - 1.0) else (3.0 - 4.0 * p)
                            sample = (bassWave * bassEnvLocal * bassVolume).toFloat()
                            bassEnvLocal *= 0.9998
                        }
                    }
                    "SYNTH" -> {
                        if (freq > 0.0 && synthEnvLocal > 0.01) {
                            synthPhaseLocal += (2.0 * Math.PI * freq / sampleRate)
                            val p = (synthPhaseLocal % (2.0 * Math.PI)) / (2.0 * Math.PI)
                            val rawSynth = (2.0 * p - 1.0).toFloat()
                            sample = (rawSynth * synthEnvLocal * synthVolume).toFloat()
                            synthEnvLocal *= 0.9998
                        }
                    }
                }
                
                sample = sample.coerceIn(-1.0f, 1.0f)
                val outShort = (sample * 32767.0f).toInt().toShort()
                shortBuffer[s * 2] = outShort
                shortBuffer[s * 2 + 1] = outShort
            }

            try {
                val tempTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    shortBuffer.size * 2,
                    AudioTrack.MODE_STATIC
                )
                tempTrack.write(shortBuffer, 0, shortBuffer.size)
                tempTrack.play()
                kotlinx.coroutines.delay((durationSec * 1000).toLong() + 100)
                tempTrack.stop()
                tempTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Synthesizer State Exposed to UI
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep

    private val _tempoBpm = MutableStateFlow(115)
    val tempoBpm: StateFlow<Int> = _tempoBpm

    private val _effectiveBpm = MutableStateFlow(115)
    val effectiveBpm: StateFlow<Int> = _effectiveBpm

    private val _genre = MutableStateFlow("Afrobeat")
    val genre: StateFlow<String> = _genre

    private val _durationMin = MutableStateFlow(3)
    val durationMin: StateFlow<Int> = _durationMin

    private val _timeRemainingSec = MutableStateFlow(180)
    val timeRemainingSec: StateFlow<Int> = _timeRemainingSec

    private val _elapsedSec = MutableStateFlow(0)
    val elapsedSec: StateFlow<Int> = _elapsedSec

    private val _fftData = MutableStateFlow(FloatArray(32))
    val fftData: StateFlow<FloatArray> = _fftData

    // MIDI Groove / Quantization States
    private val _swingLevel = MutableStateFlow(50) // 50% (no swing) to 75% (extreme swing)
    val swingLevel: StateFlow<Int> = _swingLevel

    private val _humanizeLevel = MutableStateFlow(0) // 0% (perfect timing) to 100% (loose timing)
    val humanizeLevel: StateFlow<Int> = _humanizeLevel

    fun setSwing(level: Int) {
        _swingLevel.value = level.coerceIn(50, 75)
    }

    fun setHumanize(level: Int) {
        _humanizeLevel.value = level.coerceIn(0, 100)
    }

    fun quantize(channel: String, resolution: String, strength: Int) {
        val targets = when (resolution) {
            "1/4" -> listOf(0, 4, 8, 12)
            "1/8" -> listOf(0, 2, 4, 6, 8, 10, 12, 14)
            else -> (0..15).toList()
        }

        fun quantizeBooleanArray(steps: BooleanArray) {
            val newSteps = BooleanArray(16) { false }
            for (s in 0..15) {
                if (steps[s]) {
                    var closestTarget = targets[0]
                    var minDistance = 999
                    for (target in targets) {
                        val dist1 = Math.abs(s - target)
                        val dist2 = Math.abs(s - target - 16)
                        val dist3 = Math.abs(s - target + 16)
                        val dist = minOf(dist1, minOf(dist2, dist3))
                        if (dist < minDistance) {
                            minDistance = dist
                            closestTarget = target
                        }
                    }
                    val shouldSnap = if (strength >= 100) true else (Math.random() * 100 < strength)
                    if (shouldSnap) {
                        newSteps[closestTarget] = true
                    } else {
                        newSteps[s] = true
                    }
                }
            }
            for (i in 0..15) {
                steps[i] = newSteps[i]
            }
        }

        fun quantizeStringArray(steps: Array<String>) {
            val newSteps = Array(16) { "" }
            for (s in 0..15) {
                val note = steps[s]
                if (note.isNotEmpty() && note != "-") {
                    var closestTarget = targets[0]
                    var minDistance = 999
                    for (target in targets) {
                        val dist1 = Math.abs(s - target)
                        val dist2 = Math.abs(s - target - 16)
                        val dist3 = Math.abs(s - target + 16)
                        val dist = minOf(dist1, minOf(dist2, dist3))
                        if (dist < minDistance) {
                            minDistance = dist
                            closestTarget = target
                        }
                    }
                    val shouldSnap = if (strength >= 100) true else (Math.random() * 100 < strength)
                    if (shouldSnap) {
                        newSteps[closestTarget] = note
                    } else {
                        newSteps[s] = note
                    }
                }
            }
            for (i in 0..15) {
                steps[i] = newSteps[i]
            }
        }

        if (channel == "ALL" || channel == "KICK") quantizeBooleanArray(kickSteps)
        if (channel == "ALL" || channel == "SNARE") quantizeBooleanArray(snareSteps)
        if (channel == "ALL" || channel == "HIHAT") quantizeBooleanArray(hihatSteps)
        if (channel == "ALL" || channel == "PERC") quantizeBooleanArray(percussionSteps)
        if (channel == "ALL" || channel == "BASS") quantizeStringArray(bassSteps)
        if (channel == "ALL" || channel == "SYNTH") quantizeStringArray(synthSteps)
    }

    // Sequencer Grid State (6 Tracks x 16 Steps)
    var kickSteps = BooleanArray(16) { false }
    var snareSteps = BooleanArray(16) { false }
    var hihatSteps = BooleanArray(16) { false }
    var percussionSteps = BooleanArray(16) { false }
    var bassSteps = Array(16) { "" } // e.g. "C2", "E2", "-", etc.
    var synthSteps = Array(16) { "" } // e.g. "C4", "G4", "-", etc.

    // Volumes (0.0f to 1.0f)
    var kickVolume = 0.8f
    var snareVolume = 0.7f
    var hihatVolume = 0.5f
    var bassVolume = 0.7f
    var synthVolume = 0.4f
    var percussionVolume = 0.5f

    // Mute & Solo States
    private val _trackMutesState = MutableStateFlow(mapOf(
        "KICK" to false,
        "SNARE" to false,
        "HIHAT" to false,
        "PERC" to false,
        "BASS" to false,
        "SYNTH" to false
    ))
    val trackMutesState: StateFlow<Map<String, Boolean>> = _trackMutesState

    private val _trackSolosState = MutableStateFlow(mapOf(
        "KICK" to false,
        "SNARE" to false,
        "HIHAT" to false,
        "PERC" to false,
        "BASS" to false,
        "SYNTH" to false
    ))
    val trackSolosState: StateFlow<Map<String, Boolean>> = _trackSolosState

    fun toggleMute(track: String) {
        val current = _trackMutesState.value.toMutableMap()
        current[track] = !(current[track] ?: false)
        _trackMutesState.value = current
    }

    fun toggleSolo(track: String) {
        val current = _trackSolosState.value.toMutableMap()
        current[track] = !(current[track] ?: false)
        _trackSolosState.value = current
    }

    // Metronome state
    private val _metronomeEnabled = MutableStateFlow(false)
    val metronomeEnabled: StateFlow<Boolean> = _metronomeEnabled

    private val _metronomeVolume = MutableStateFlow(0.3f)
    val metronomeVolume: StateFlow<Float> = _metronomeVolume

    fun setMetronomeEnabled(enabled: Boolean) {
        _metronomeEnabled.value = enabled
    }

    fun setMetronomeVolume(vol: Float) {
        _metronomeVolume.value = vol.coerceIn(0f, 1f)
    }

    // Mastering & Stereo Width States
    private val _stereoWidth = MutableStateFlow(1.0f) // 0.0 (mono) to 2.0 (super-wide)
    val stereoWidth: StateFlow<Float> = _stereoWidth

    private val _masteringPreset = MutableStateFlow("Flat Bypass")
    val masteringPreset: StateFlow<String> = _masteringPreset

    private val _loudnessNormalizer = MutableStateFlow(true)
    val loudnessNormalizer: StateFlow<Boolean> = _loudnessNormalizer

    fun setStereoWidth(width: Float) { _stereoWidth.value = width.coerceIn(0f, 2f) }
    fun setMasteringPreset(preset: String) { _masteringPreset.value = preset }
    fun setLoudnessNormalizer(enabled: Boolean) { _loudnessNormalizer.value = enabled }

    // Automation LFO State
    private val _lfoTarget = MutableStateFlow("None") // "None", "Master Filter", "Synth Volume", "Delay Feedback"
    val lfoTarget: StateFlow<String> = _lfoTarget

    private val _lfoWaveform = MutableStateFlow("Off") // "Off", "Sine", "Triangle", "Square"
    val lfoWaveform: StateFlow<String> = _lfoWaveform

    private val _lfoRate = MutableStateFlow("1/4") // "1/1", "1/2", "1/4", "1/8"
    val lfoRate: StateFlow<String> = _lfoRate

    private val _lfoDepth = MutableStateFlow(50f) // 0% to 100%
    val lfoDepth: StateFlow<Float> = _lfoDepth

    fun setLfoTarget(target: String) { _lfoTarget.value = target }
    fun setLfoWaveform(wave: String) { _lfoWaveform.value = wave }
    fun setLfoRate(rate: String) { _lfoRate.value = rate }
    fun setLfoDepth(depth: Float) { _lfoDepth.value = depth }

    // Volume Automation Curves (16 steps per track)
    private val _automationCurves = MutableStateFlow(mapOf(
        "KICK" to FloatArray(16) { 1.0f },
        "SNARE" to FloatArray(16) { 1.0f },
        "HIHAT" to FloatArray(16) { 1.0f },
        "PERC" to FloatArray(16) { 1.0f },
        "BASS" to FloatArray(16) { 1.0f },
        "SYNTH" to FloatArray(16) { 1.0f }
    ))
    val automationCurves: StateFlow<Map<String, FloatArray>> = _automationCurves

    fun setAutomationValue(track: String, step: Int, value: Float) {
        val current = _automationCurves.value
        val array = current[track]?.clone() ?: FloatArray(16) { 1.0f }
        array[step] = value.coerceIn(0.0f, 1.0f)
        val updated = current.toMutableMap()
        updated[track] = array
        _automationCurves.value = updated
    }

    // Backup original values to restore when LFO is off or targets change
    var baseFilterCutoff = 1.0f
    var baseSynthVolume = 0.4f
    var baseDelayLevel = 0.2f

    // Track Equalizers (Low, Mid, High parametric EQ per track)
    val trackEqs = mapOf(
        "KICK" to TrackEq("KICK"),
        "SNARE" to TrackEq("SNARE"),
        "HIHAT" to TrackEq("HIHAT"),
        "PERC" to TrackEq("PERC"),
        "BASS" to TrackEq("BASS"),
        "SYNTH" to TrackEq("SYNTH")
    )

    // Effects
    var delayLevel = 0.2f // feedback & mix
    var reverbLevel = 0.3f
    var filterCutoff = 1.0f // 1.0 = fully open, 0.05 = dark

    // Selected VST Plugin Type
    private val _activeVst = MutableStateFlow("Afro-Synth VST")
    val activeVst: StateFlow<String> = _activeVst

    fun setVst(vstName: String) {
        _activeVst.value = vstName
    }

    // Audio Engine Constants
    private val sampleRate = 44100
    private val random = Random()

    // Sound Generator States (Envelopes and Phase)
    private var kickPhase = 0.0
    private var kickActive = false
    private var kickTime = 0.0

    private var snareActive = false
    private var snareTime = 0.0

    private var hihatActive = false
    private var hihatTime = 0.0

    private var percussionActive = false
    private var percussionTime = 0.0
    private var percussionFreq = 600.0

    private var bassPhase = 0.0
    private var bassActive = false
    private var bassFreq = 0.0
    private var bassEnv = 0.0

    private var synthPhase = 0.0
    private var synthActive = false
    private var synthFreq = 0.0
    private var synthEnv = 0.0
    private var synthGlissandoFreq = 0.0

    // Sample SFX trigger states
    private var sampleSfxActive = false
    private var sampleSfxType = "" // "VOX_HEY", "VOX_AHH", "SIREN_FX", "LASER_FX", "SUB_DROP", "CHIP_RISER"
    private var sampleSfxTime = 0.0
    private var sampleSfxPhase = 0.0
    private var sampleSfxEnv = 0.0

    // Delay FX Buffer
    private val delayBufferSize = 22050 // ~0.5 second at 44.1kHz
    private val delayBufferL = FloatArray(delayBufferSize)
    private val delayBufferR = FloatArray(delayBufferSize)
    private var delayWriteIndex = 0

    // Lowpass Filter States
    private var filterStateL = 0.0f
    private var filterStateR = 0.0f

    fun resetAllEqsToFlat() {
        trackEqs.values.forEach { it.reset() }
    }

    // Configure the grid based on chosen genre presets
    fun loadGenrePreset(genreName: String) {
        _genre.value = genreName
        resetAllEqsToFlat()
        
        // Custom professional signature EQ starting points per genre
        when (genreName) {
            "Afrobeat" -> {
                // Fat sub-heavy kick drum
                trackEqs["KICK"]?.lowBand?.update(f0 = 80f, gainDb = 3.5f)
                // Crisp leads
                trackEqs["SYNTH"]?.highBand?.update(f0 = 6000f, gainDb = 2.0f)
            }
            "Afro Fuji" -> {
                // Highly resonant talking drums (mids)
                trackEqs["PERC"]?.midBand?.update(f0 = 700f, gainDb = 4.0f)
                trackEqs["HIHAT"]?.highBand?.update(f0 = 10000f, gainDb = 2.5f)
            }
            "Afro Blues" -> {
                // Warm bass and rounded treble
                trackEqs["BASS"]?.lowBand?.update(f0 = 120f, gainDb = 3.0f)
                trackEqs["SYNTH"]?.highBand?.update(f0 = 5000f, gainDb = -2.0f)
            }
        }

        // Clear grid
        kickSteps.fill(false)
        snareSteps.fill(false)
        hihatSteps.fill(false)
        percussionSteps.fill(false)
        bassSteps.fill("")
        synthSteps.fill("")

        when (genreName) {
            "Afrobeat" -> {
                _tempoBpm.value = 115
                _effectiveBpm.value = 115
                // Infectious 4-on-the-floor + offbeats
                kickSteps[0] = true; kickSteps[4] = true; kickSteps[8] = true; kickSteps[12] = true
                snareSteps[4] = true; snareSteps[12] = true; snareSteps[14] = true
                for (i in listOf(0, 2, 4, 6, 8, 10, 12, 14)) hihatSteps[i] = true
                percussionSteps[2] = true; percussionSteps[6] = true; percussionSteps[10] = true
                // Pentatonic groove
                bassSteps[0] = "A1"; bassSteps[3] = "C2"; bassSteps[6] = "D2"; bassSteps[10] = "E2"
                synthSteps[4] = "A3"; synthSteps[12] = "C4"
                kickVolume = 0.85f; snareVolume = 0.75f; hihatVolume = 0.5f; bassVolume = 0.8f; synthVolume = 0.45f
                delayLevel = 0.25f; reverbLevel = 0.35f; filterCutoff = 0.9f
            }
            "Afro Fuji" -> {
                _tempoBpm.value = 120
                _effectiveBpm.value = 120
                // Highly syncopated talking drums & bell patterns
                kickSteps[0] = true; kickSteps[6] = true; kickSteps[10] = true; kickSteps[12] = true
                snareSteps[2] = true; snareSteps[8] = true; snareSteps[14] = true
                for (i in 0..15 step 2) hihatSteps[i] = true
                percussionSteps[1] = true; percussionSteps[3] = true; percussionSteps[7] = true; percussionSteps[11] = true
                // Fuji bells chime on 1 and 3
                bassSteps[0] = "D2"; bassSteps[4] = "F2"; bassSteps[8] = "G2"; bassSteps[12] = "D2"
                synthSteps[2] = "A4"; synthSteps[10] = "G4"
                kickVolume = 0.8f; snareVolume = 0.7f; hihatVolume = 0.45f; bassVolume = 0.85f; synthVolume = 0.5f
                delayLevel = 0.15f; reverbLevel = 0.4f; filterCutoff = 0.85f
            }
            "Afro Blues" -> {
                _tempoBpm.value = 90
                _effectiveBpm.value = 90
                // Laid-back shuffle feel
                kickSteps[0] = true; kickSteps[8] = true
                snareSteps[4] = true; snareSteps[12] = true
                for (i in listOf(0, 3, 6, 8, 11, 14)) hihatSteps[i] = true
                percussionSteps[9] = true; percussionSteps[13] = true
                bassSteps[0] = "E2"; bassSteps[4] = "A2"; bassSteps[8] = "B2"; bassSteps[12] = "A2"
                synthSteps[3] = "G3"; synthSteps[7] = "E3"; synthSteps[11] = "A3"
                kickVolume = 0.75f; snareVolume = 0.65f; hihatVolume = 0.4f; bassVolume = 0.75f; synthVolume = 0.55f
                delayLevel = 0.35f; reverbLevel = 0.5f; filterCutoff = 0.75f
            }
            "Afro Pop" -> {
                _tempoBpm.value = 108
                _effectiveBpm.value = 108
                // Standard bouncy mainstream vibe
                kickSteps[0] = true; kickSteps[4] = true; kickSteps[8] = true; kickSteps[12] = true
                snareSteps[3] = true; snareSteps[10] = true; snareSteps[14] = true
                for (i in 0..15 step 2) hihatSteps[i] = true
                percussionSteps[5] = true; percussionSteps[13] = true
                bassSteps[0] = "C2"; bassSteps[5] = "G2"; bassSteps[10] = "F2"
                synthSteps[0] = "C4"; synthSteps[5] = "G4"; synthSteps[10] = "F4"
                kickVolume = 0.85f; snareVolume = 0.8f; hihatVolume = 0.55f; bassVolume = 0.8f; synthVolume = 0.4f
                delayLevel = 0.2f; reverbLevel = 0.3f; filterCutoff = 0.95f
            }
            "Modern R&B" -> {
                _tempoBpm.value = 85
                _effectiveBpm.value = 85
                // Lush minor sevenths, smooth laid back snaps
                kickSteps[0] = true; kickSteps[8] = true; kickSteps[11] = true
                snareSteps[4] = true; snareSteps[12] = true
                for (i in 0..15 step 4) hihatSteps[i] = true
                percussionSteps[6] = true; percussionSteps[14] = true
                bassSteps[0] = "F1"; bassSteps[8] = "C2"
                synthSteps[0] = "F3"; synthSteps[4] = "A3"; synthSteps[8] = "C3"; synthSteps[12] = "E3"
                kickVolume = 0.75f; snareVolume = 0.6f; hihatVolume = 0.5f; bassVolume = 0.85f; synthVolume = 0.6f
                delayLevel = 0.3f; reverbLevel = 0.6f; filterCutoff = 0.65f
            }
            "Pop" -> {
                _tempoBpm.value = 120
                _effectiveBpm.value = 120
                // 4-on-the-floor club groove
                kickSteps[0] = true; kickSteps[4] = true; kickSteps[8] = true; kickSteps[12] = true
                snareSteps[4] = true; snareSteps[12] = true
                for (i in 0..15) hihatSteps[i] = true
                percussionSteps[10] = true
                bassSteps[0] = "G1"; bassSteps[4] = "C2"; bassSteps[8] = "D2"; bassSteps[12] = "G1"
                synthSteps[0] = "G3"; synthSteps[4] = "C4"; synthSteps[8] = "D4"; synthSteps[12] = "G3"
                kickVolume = 0.9f; snareVolume = 0.8f; hihatVolume = 0.6f; bassVolume = 0.75f; synthVolume = 0.4f
                delayLevel = 0.15f; reverbLevel = 0.25f; filterCutoff = 1.0f
            }
            "Hip-Hop" -> {
                _tempoBpm.value = 90
                _effectiveBpm.value = 90
                // Boom bap, dusty acoustic drum sounds
                kickSteps[0] = true; kickSteps[5] = true; kickSteps[8] = true
                snareSteps[4] = true; snareSteps[12] = true
                for (i in 0..15 step 2) hihatSteps[i] = true
                hihatSteps[3] = true; hihatSteps[11] = true // swings
                bassSteps[0] = "G1"; bassSteps[5] = "Bb1"; bassSteps[8] = "C2"
                synthSteps[4] = "D3"; synthSteps[12] = "F3"
                kickVolume = 0.85f; snareVolume = 0.75f; hihatVolume = 0.5f; bassVolume = 0.8f; synthVolume = 0.35f
                delayLevel = 0.25f; reverbLevel = 0.45f; filterCutoff = 0.7f
            }
            "Drill" -> {
                _tempoBpm.value = 140
                _effectiveBpm.value = 140
                // Sliding sliding bass, rapid-fire hats
                kickSteps[0] = true; kickSteps[10] = true
                snareSteps[6] = true; snareSteps[14] = true
                for (i in 0..15) {
                    if (i % 3 == 0) hihatSteps[i] = true // skittering triplet feel
                }
                percussionSteps[12] = true
                bassSteps[0] = "E1"; bassSteps[4] = "E2"; bassSteps[10] = "B1" // Sliding sub!
                synthSteps[3] = "E3"; synthSteps[11] = "G3"
                kickVolume = 0.8f; snareVolume = 0.8f; hihatVolume = 0.65f; bassVolume = 0.95f; synthVolume = 0.3f
                delayLevel = 0.2f; reverbLevel = 0.5f; filterCutoff = 0.8f
            }
            "Trap Rap" -> {
                _tempoBpm.value = 130
                _effectiveBpm.value = 130
                // Double-time hats, massive 808s
                kickSteps[0] = true; kickSteps[8] = true; kickSteps[11] = true
                snareSteps[4] = true; snareSteps[12] = true
                for (i in 0..15) {
                    if (i % 2 == 0 || i % 3 == 0) hihatSteps[i] = true // Roll feel!
                }
                percussionSteps[14] = true
                bassSteps[0] = "C1"; bassSteps[8] = "F1"; bassSteps[11] = "G1"
                synthSteps[2] = "C4"; synthSteps[6] = "Eb4"; synthSteps[10] = "G4"
                kickVolume = 0.9f; snareVolume = 0.85f; hihatVolume = 0.7f; bassVolume = 0.95f; synthVolume = 0.4f
                delayLevel = 0.3f; reverbLevel = 0.4f; filterCutoff = 0.9f
            }
        }
    }

    // Set duration
    fun setDuration(minutes: Int) {
        _durationMin.value = minutes
        _timeRemainingSec.value = minutes * 60
    }

    // Convert note name to frequency (e.g. C2, E2, A3)
    private fun noteToFreq(note: String): Double {
        if (note.isEmpty() || note == "-") return 0.0
        val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        try {
            val name = note.substring(0, note.length - 1)
            val octave = note.substring(note.length - 1).toInt()
            val semitone = notes.indexOf(name)
            if (semitone == -1) return 0.0
            val stepsFromA4 = (semitone + (octave - 4) * 12) - 9
            return 440.0 * Math.pow(2.0, stepsFromA4 / 12.0)
        } catch (e: Exception) {
            return 0.0
        }
    }

    // Start Real-time Audio Preview
    fun togglePlayback() {
        if (_isPlaying.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        _elapsedSec.value = 0
        _timeRemainingSec.value = _durationMin.value * 60

        // Clear Delay Buffers
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)

        playbackJob = scope.launch(Dispatchers.Default) {
            // Setup AudioTrack
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
                AudioTrack.MODE_STREAM
            )
            audioTrack = track
            track.play()

            var stepSampleCounter = 0
            var elapsedSampleCounter = 0
            var stepIndex = 0

            var kickTriggered = false
            var snareTriggered = false
            var hihatTriggered = false
            var percTriggered = false
            var bassTriggered = false
            var synthTriggered = false

            var kickDelaySamples = 0
            var snareDelaySamples = 0
            var hihatDelaySamples = 0
            var percDelaySamples = 0
            var bassDelaySamples = 0
            var synthDelaySamples = 0

            var kickHumanVol = 1.0f
            var snareHumanVol = 1.0f
            var hihatHumanVol = 1.0f
            var percHumanVol = 1.0f
            var bassHumanVol = 1.0f
            var synthHumanVol = 1.0f

            var nextBassFreq = 0.0
            var nextSynthFreq = 0.0

            val bufferSize = 512
            val shortBuffer = ShortArray(bufferSize * 2) // Stereo (L, R)

            val totalDurationSeconds = _durationMin.value * 60.0
            val smoothedBands = FloatArray(32)

            var metronomeActive = false
            var metronomeTime = 0.0
            var metronomeFreq = 1000.0
            var lfoPhase = 0.0
            var compEnvL = 0.0f
            var compEnvR = 0.0f
            var lofiLastL = 0.0f
            var lofiLastR = 0.0f
            var currentPlayingStep = 0

            while (_isPlaying.value) {
                // Fetch current automation curves once per buffer
                val curves = _automationCurves.value
                val kickCurve = curves["KICK"] ?: FloatArray(16) { 1.0f }
                val snareCurve = curves["SNARE"] ?: FloatArray(16) { 1.0f }
                val hihatCurve = curves["HIHAT"] ?: FloatArray(16) { 1.0f }
                val percCurve = curves["PERC"] ?: FloatArray(16) { 1.0f }
                val bassCurve = curves["BASS"] ?: FloatArray(16) { 1.0f }
                val synthCurve = curves["SYNTH"] ?: FloatArray(16) { 1.0f }

                // Outro Rallentando calculations (last 30 seconds slow down)
                val elapsedSecValue = elapsedSampleCounter / sampleRate.toDouble()
                _elapsedSec.value = elapsedSecValue.toInt()
                _timeRemainingSec.value = (totalDurationSeconds - elapsedSecValue).toInt().coerceAtLeast(0)

                if (elapsedSecValue >= totalDurationSeconds) {
                    // Loop ended naturally
                    scope.launch(Dispatchers.Main) {
                        stopPlayback()
                    }
                    break
                }

                // Smooth deceleration over last 30s
                val baseBpm = _tempoBpm.value.toDouble()
                val activeBpm = if (totalDurationSeconds - elapsedSecValue < 30.0) {
                    val secondsIntoOutro = 30.0 - (totalDurationSeconds - elapsedSecValue)
                    // Decelerate BPM linearly by up to 50%
                    val scale = 1.0 - (secondsIntoOutro / 30.0) * 0.45
                    (baseBpm * scale).coerceAtLeast(40.0)
                } else {
                    baseBpm
                }
                _effectiveBpm.value = activeBpm.toInt()

                // Fetch current mute/solo maps
                val mutes = _trackMutesState.value
                val solos = _trackSolosState.value
                val hasSolo = solos.values.any { it }

                val playKick = if (hasSolo) solos["KICK"] == true else !(mutes["KICK"] ?: false)
                val playSnare = if (hasSolo) solos["SNARE"] == true else !(mutes["SNARE"] ?: false)
                val playHihat = if (hasSolo) solos["HIHAT"] == true else !(mutes["HIHAT"] ?: false)
                val playPerc = if (hasSolo) solos["PERC"] == true else !(mutes["PERC"] ?: false)
                val playBass = if (hasSolo) solos["BASS"] == true else !(mutes["BASS"] ?: false)
                val playSynth = if (hasSolo) solos["SYNTH"] == true else !(mutes["SYNTH"] ?: false)

                // Calculate samples per step with swing (BPM * 4.0 steps per beat)
                val baseSamplesPerStep = (sampleRate * (60.0 / (activeBpm * 4.0))).toInt()
                val swingVal = _swingLevel.value
                val samplesPerStep = if (swingVal > 50) {
                    val swingFactor = (swingVal - 50) / 100.0 // 0.0..0.25
                    // Even-numbered step indices (0, 2, 4...) represent on-beats. We lengthen them to delay the offbeats.
                    if (stepIndex % 2 == 0) {
                        baseSamplesPerStep + (baseSamplesPerStep * swingFactor).toInt()
                    } else {
                        // Odd-numbered step indices represent off-beats. We shorten them.
                        baseSamplesPerStep - (baseSamplesPerStep * swingFactor).toInt()
                    }
                } else {
                    baseSamplesPerStep
                }

                // Generate audio samples for buffer
                for (i in 0 until bufferSize) {
                    // Trigger note on step boundary
                    if (stepSampleCounter >= samplesPerStep || (elapsedSampleCounter == 0 && i == 0)) {
                        stepSampleCounter = 0
                        _currentStep.value = stepIndex
                        currentPlayingStep = stepIndex

                        // Trigger Metronome Click
                        if (_metronomeEnabled.value) {
                            if (stepIndex % 4 == 0) {
                                metronomeActive = true
                                metronomeTime = 0.0
                                metronomeFreq = if (stepIndex == 0) 1400.0 else 900.0
                            }
                        }

                        val humanVal = _humanizeLevel.value
                        val maxHumanDelayMs = 22.0 // Up to 22ms timing displacement
                        val maxHumanDelaySamples = (sampleRate * (maxHumanDelayMs / 1000.0) * (humanVal / 100.0)).toInt()

                        // Configure kick trigger
                        if (kickSteps[stepIndex]) {
                            kickTriggered = true
                            kickDelaySamples = if (humanVal > 0) random.nextInt(maxHumanDelaySamples + 1) else 0
                        } else {
                            kickTriggered = false
                        }

                        // Configure snare trigger
                        if (snareSteps[stepIndex]) {
                            snareTriggered = true
                            snareDelaySamples = if (humanVal > 0) random.nextInt(maxHumanDelaySamples + 1) else 0
                        } else {
                            snareTriggered = false
                        }

                        // Configure hihat trigger
                        if (hihatSteps[stepIndex]) {
                            hihatTriggered = true
                            hihatDelaySamples = if (humanVal > 0) random.nextInt(maxHumanDelaySamples + 1) else 0
                        } else {
                            hihatTriggered = false
                        }

                        // Configure percussion trigger
                        if (percussionSteps[stepIndex]) {
                            percTriggered = true
                            percDelaySamples = if (humanVal > 0) random.nextInt(maxHumanDelaySamples + 1) else 0
                        } else {
                            percTriggered = false
                        }

                        // Configure bass trigger
                        val bassNote = bassSteps[stepIndex]
                        if (bassNote.isNotEmpty() && bassNote != "-") {
                            val freq = noteToFreq(bassNote)
                            if (freq > 0.0) {
                                bassTriggered = true
                                nextBassFreq = freq
                                bassDelaySamples = if (humanVal > 0) random.nextInt(maxHumanDelaySamples + 1) else 0
                            } else {
                                bassTriggered = false
                            }
                        } else {
                            bassTriggered = false
                        }

                        // Configure synth trigger
                        val synthNote = synthSteps[stepIndex]
                        if (synthNote.isNotEmpty() && synthNote != "-") {
                            val freq = noteToFreq(synthNote)
                            if (freq > 0.0) {
                                synthTriggered = true
                                nextSynthFreq = freq
                                synthDelaySamples = if (humanVal > 0) random.nextInt(maxHumanDelaySamples + 1) else 0
                            } else {
                                synthTriggered = false
                            }
                        } else {
                            synthTriggered = false
                        }

                        // Move to next step
                        stepIndex = (stepIndex + 1) % 16
                    }

                    // Real-time Live Triggers
                    if (liveKickTrigger.compareAndSet(true, false)) {
                        kickActive = true
                        kickTime = 0.0
                        kickPhase = 0.0
                        kickHumanVol = 1.0f
                    }
                    if (liveSnareTrigger.compareAndSet(true, false)) {
                        snareActive = true
                        snareTime = 0.0
                        snareHumanVol = 1.0f
                    }
                    if (liveHihatTrigger.compareAndSet(true, false)) {
                        hihatActive = true
                        hihatTime = 0.0
                        hihatHumanVol = 1.0f
                    }
                    if (livePercTrigger.compareAndSet(true, false)) {
                        percussionActive = true
                        percussionTime = 0.0
                        percussionFreq = 500.0 + random.nextDouble() * 400.0
                        percHumanVol = 1.0f
                    }
                    val liveBassFreqVal = liveBassQueue.poll()
                    if (liveBassFreqVal != null) {
                        bassFreq = liveBassFreqVal
                        bassActive = true
                        bassPhase = 0.0
                        bassEnv = 1.0
                        bassHumanVol = 1.0f
                    }
                    val liveSynthFreqVal = liveSynthQueue.poll()
                    if (liveSynthFreqVal != null) {
                        synthActive = true
                        synthPhase = 0.0
                        synthEnv = 1.0
                        synthGlissandoFreq = if (synthFreq > 0.0) synthFreq else liveSynthFreqVal
                        synthFreq = liveSynthFreqVal
                        synthHumanVol = 1.0f
                    }
                    val liveSfx = liveSampleTrigger.getAndSet(null)
                    if (liveSfx != null) {
                        sampleSfxActive = true
                        sampleSfxType = liveSfx
                        sampleSfxTime = 0.0
                        sampleSfxPhase = 0.0
                        sampleSfxEnv = 1.0
                    }

                    val humanVal = _humanizeLevel.value

                    // Consume Triggers on their specific delay samples
                    if (kickTriggered && stepSampleCounter == kickDelaySamples) {
                        kickActive = true
                        kickTime = 0.0
                        kickPhase = 0.0
                        kickHumanVol = if (humanVal > 0) (1.0f - random.nextFloat() * (humanVal / 100.0f) * 0.18f) else 1.0f
                        kickTriggered = false
                    }
                    if (snareTriggered && stepSampleCounter == snareDelaySamples) {
                        snareActive = true
                        snareTime = 0.0
                        snareHumanVol = if (humanVal > 0) (1.0f - random.nextFloat() * (humanVal / 100.0f) * 0.18f) else 1.0f
                        snareTriggered = false
                    }
                    if (hihatTriggered && stepSampleCounter == hihatDelaySamples) {
                        hihatActive = true
                        hihatTime = 0.0
                        hihatHumanVol = if (humanVal > 0) (1.0f - random.nextFloat() * (humanVal / 100.0f) * 0.22f) else 1.0f
                        hihatTriggered = false
                    }
                    if (percTriggered && stepSampleCounter == percDelaySamples) {
                        percussionActive = true
                        percussionTime = 0.0
                        percussionFreq = 500.0 + random.nextDouble() * 400.0
                        percHumanVol = if (humanVal > 0) (1.0f - random.nextFloat() * (humanVal / 100.0f) * 0.20f) else 1.0f
                        percTriggered = false
                    }
                    if (bassTriggered && stepSampleCounter == bassDelaySamples) {
                        bassFreq = nextBassFreq
                        bassActive = true
                        bassPhase = 0.0
                        bassEnv = 1.0
                        bassHumanVol = if (humanVal > 0) (1.0f - random.nextFloat() * (humanVal / 100.0f) * 0.15f) else 1.0f
                        bassTriggered = false
                    }
                    if (synthTriggered && stepSampleCounter == synthDelaySamples) {
                        val newFreq = nextSynthFreq
                        synthActive = true
                        synthPhase = 0.0
                        synthEnv = 1.0
                        synthGlissandoFreq = if (synthFreq > 0.0) synthFreq else newFreq
                        synthFreq = newFreq
                        synthHumanVol = if (humanVal > 0) (1.0f - random.nextFloat() * (humanVal / 100.0f) * 0.20f) else 1.0f
                        synthTriggered = false
                    }

                    // --- Synthesize Sample ---
                    val t = stepSampleCounter / sampleRate.toDouble()

                    // --- Sample-rate LFO / Automation Modulator ---
                    val lfoWave = _lfoWaveform.value
                    val lfoTargetVal = _lfoTarget.value
                    if (lfoWave != "Off" && lfoTargetVal != "None") {
                        val bpm = activeBpm
                        val lfoFreq = when (_lfoRate.value) {
                            "1/1" -> (bpm / 60.0) / 4.0 // 1 cycle per 4 beats (whole bar)
                            "1/2" -> (bpm / 60.0) / 2.0 // 1 cycle per 2 beats (half bar)
                            "1/4" -> bpm / 60.0        // 1 cycle per beat (quarter beat)
                            "1/8" -> (bpm / 60.0) * 2.0 // 2 cycles per beat (eighth beat)
                            else -> bpm / 60.0
                        }
                        
                        lfoPhase += (2.0 * Math.PI * lfoFreq / sampleRate)
                        if (lfoPhase > 2.0 * Math.PI) {
                            lfoPhase -= 2.0 * Math.PI
                        }
                        
                        val lfoShapeValue = when (lfoWave) {
                            "Sine" -> sin(lfoPhase)
                            "Triangle" -> {
                                val p = (lfoPhase % (2.0 * Math.PI)) / (2.0 * Math.PI)
                                if (p < 0.5) (4.0 * p - 1.0) else (3.0 - 4.0 * p)
                            }
                            "Square" -> if (sin(lfoPhase) > 0.0) 1.0 else -1.0
                            else -> 0.0
                        }.toFloat()
                        
                        val lfoVal = (lfoShapeValue + 1.0f) / 2.0f // Scale to [0f, 1f]
                        val depth = _lfoDepth.value / 100f
                        
                        when (lfoTargetVal) {
                            "Master Filter" -> {
                                filterCutoff = (baseFilterCutoff * (1f - depth * (1f - lfoVal))).coerceIn(0.05f, 1.0f)
                            }
                            "Synth Volume" -> {
                                synthVolume = (baseSynthVolume * (1f - depth * (1f - lfoVal))).coerceIn(0.0f, 1.0f)
                            }
                            "Delay Feedback" -> {
                                delayLevel = (baseDelayLevel * (1f - depth * (1f - lfoVal))).coerceIn(0.0f, 0.95f)
                            }
                        }
                    } else {
                        // Restore base values when LFO is bypassed/off
                        filterCutoff = baseFilterCutoff
                        synthVolume = baseSynthVolume
                        delayLevel = baseDelayLevel
                    }

                    var mixedL = 0.0f
                    var mixedR = 0.0f

                    // 1. Kick Drum (Swept sine wave with exponential decay)
                    if (kickActive) {
                        val duration = 0.15
                        if (kickTime < duration) {
                            // Swift pitch slide
                            val startFreq = 160.0
                            val endFreq = 48.0
                            val currentFreq = endFreq + (startFreq - endFreq) * exp(-kickTime / 0.02)
                            kickPhase += (2.0 * PI * currentFreq / sampleRate)
                            val amplitude = exp(-kickTime / 0.04)
                            var sample = (sin(kickPhase) * amplitude * kickVolume * kickCurve[currentPlayingStep] * kickHumanVol).toFloat()
                            sample = trackEqs["KICK"]?.process(sample) ?: sample
                            if (playKick) {
                                mixedL += sample
                                mixedR += sample
                            }
                            kickTime += (1.0 / sampleRate)
                        } else {
                            kickActive = false
                        }
                    }

                    // 2. Snare Drum (White noise + short high-pass decayed envelope)
                    if (snareActive) {
                        val duration = 0.22
                        if (snareTime < duration) {
                            val noise = (random.nextFloat() * 2.0f - 1.0f)
                            val env = exp(-snareTime / 0.05).toFloat()
                            // Simple high-pass resonance: diffing noise
                            val bodyFreq = 180.0
                            val bodyVal = sin(2.0 * PI * bodyFreq * snareTime).toFloat() * exp(-snareTime / 0.02).toFloat()
                            var sample = (noise * 0.7f + bodyVal * 0.3f) * env * snareVolume * snareCurve[currentPlayingStep] * snareHumanVol
                            sample = trackEqs["SNARE"]?.process(sample) ?: sample
                            if (playSnare) {
                                mixedL += sample
                                mixedR += sample
                            }
                            snareTime += (1.0 / sampleRate)
                        } else {
                            snareActive = false
                        }
                    }

                    // 3. Hihat (Very short white noise burst)
                    if (hihatActive) {
                        val duration = 0.05
                        if (hihatTime < duration) {
                            val noise = (random.nextFloat() * 2.0f - 1.0f)
                            val env = exp(-hihatTime / 0.015).toFloat()
                            var sample = noise * env * hihatVolume * hihatCurve[currentPlayingStep] * hihatHumanVol
                            sample = trackEqs["HIHAT"]?.process(sample) ?: sample
                            if (playHihat) {
                                mixedL += sample
                                mixedR += sample
                            }
                            hihatTime += (1.0 / sampleRate)
                        } else {
                            hihatActive = false
                        }
                    }

                    // 4. Percussion (Metallic chime ring-modulator)
                    if (percussionActive) {
                        val duration = 0.18
                        if (percussionTime < duration) {
                            // Combination of two sine waves creating FM-bell sound
                            val car = sin(2.0 * PI * percussionFreq * percussionTime)
                            val mod = sin(2.0 * PI * (percussionFreq * 1.41) * percussionTime)
                            val env = exp(-percussionTime / 0.04).toFloat()
                            var sample = (car * mod).toFloat() * env * percussionVolume * percCurve[currentPlayingStep] * percHumanVol
                            sample = trackEqs["PERC"]?.process(sample) ?: sample
                            if (playPerc) {
                                mixedL += sample
                                mixedR += sample
                            }
                            percussionTime += (1.0 / sampleRate)
                        } else {
                            percussionActive = false
                        }
                    }

                    // 5. Sub-Bass (Deep clean triangle or sine wave)
                    if (bassActive) {
                        if (bassEnv > 0.01) {
                            bassPhase += (2.0 * PI * bassFreq / sampleRate)
                            // Bass synth type selection simulation (Sub-Bass triangle)
                            val bassWave = if (_activeVst.value == "Classic 808") {
                                // Swept Sine 808
                                sin(bassPhase)
                            } else {
                                // Smooth triangle
                                val p = (bassPhase % (2.0 * PI)) / (2.0 * PI)
                                if (p < 0.5) (4.0 * p - 1.0) else (3.0 - 4.0 * p)
                            }
                            var sample = (bassWave * bassEnv * bassVolume * bassCurve[currentPlayingStep] * bassHumanVol).toFloat()
                            sample = trackEqs["BASS"]?.process(sample) ?: sample
                            if (playBass) {
                                mixedL += sample
                                mixedR += sample
                            }
                            // Slow decay
                            bassEnv *= 0.99992
                        } else {
                            bassActive = false
                        }
                    }

                    // 6. Lead Synth VST (Sawtooth / Square with Resonant filter sweep)
                    if (synthActive) {
                        if (synthEnv > 0.01) {
                            // Glissando / Glide effect
                            synthGlissandoFreq += (synthFreq - synthGlissandoFreq) * 0.005
                            synthPhase += (2.0 * PI * synthGlissandoFreq / sampleRate)

                            val rawSynth = when (_activeVst.value) {
                                "Fuji Bell Generator" -> {
                                        // FM Metallic Bell style
                                        val car = sin(synthPhase)
                                        val mod = sin(synthPhase * 2.01)
                                        car * (0.6 + 0.4 * mod)
                                    }
                                "Afro-Synth VST" -> {
                                        // Buzzy Sawtooth
                                        val p = (synthPhase % (2.0 * PI)) / (2.0 * PI)
                                        (2.0 * p - 1.0)
                                    }
                                    else -> {
                                        // Fat Square Wave
                                        if (sin(synthPhase) > 0.0) 0.5 else -0.5
                                    }
                            }
                            var sample = (rawSynth * synthEnv * synthVolume * synthCurve[currentPlayingStep] * synthHumanVol).toFloat()
                            sample = trackEqs["SYNTH"]?.process(sample) ?: sample
                            if (playSynth) {
                                mixedL += sample
                                mixedR += sample
                            }
                            // Decay
                            synthEnv *= 0.99994
                        } else {
                            synthActive = false
                        }
                    }

                    // 7. Custom Sample SFX Trigger
                    if (sampleSfxActive) {
                        val duration = when (sampleSfxType) {
                            "VOX_HEY" -> 0.25
                            "VOX_AHH" -> 0.6
                            "SIREN_FX" -> 1.0
                            "LASER_FX" -> 0.2
                            "SUB_DROP" -> 1.5
                            "CHIP_RISER" -> 1.2
                            else -> 0.5
                        }
                        if (sampleSfxTime < duration) {
                            var sample = 0.0f
                            val time = sampleSfxTime
                            when (sampleSfxType) {
                                "VOX_HEY" -> {
                                    val f1 = sin(2.0 * Math.PI * 220.0 * time) * exp(-time / 0.04)
                                    val f2 = sin(2.0 * Math.PI * 880.0 * time) * exp(-time / 0.06)
                                    val f3 = sin(2.0 * Math.PI * 1300.0 * time) * exp(-time / 0.08)
                                    val noise = (random.nextFloat() * 2.0f - 1.0f) * exp(-time / 0.03) * 0.4f
                                    sample = ((f1 * 0.4 + f2 * 0.3 + f3 * 0.2) * exp(-time / 0.1) * 0.6 + noise).toFloat() * 0.8f
                                }
                                "VOX_AHH" -> {
                                    val f1 = sin(2.0 * Math.PI * 400.0 * time)
                                    val f2 = sin(2.0 * Math.PI * 800.0 * time) * 0.5
                                    val f3 = sin(2.0 * Math.PI * 1200.0 * time) * 0.25
                                    val vibrato = 1.0 + 0.02 * sin(2.0 * Math.PI * 6.0 * time)
                                    val wave = (f1 + f2 + f3) * vibrato
                                    val env = exp(-time / 0.2).toFloat()
                                    sample = (wave * env * 0.45f).toFloat()
                                }
                                "SIREN_FX" -> {
                                    val sweepSpeed = 8.0
                                    val currentFreq = 600.0 + 300.0 * sin(2.0 * Math.PI * sweepSpeed * time)
                                    sampleSfxPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                                    val env = if (time < 0.8) 1.0 else (1.0 - (time - 0.8) / 0.2)
                                    sample = (sin(sampleSfxPhase) * env * 0.35f).toFloat()
                                }
                                "LASER_FX" -> {
                                    val currentFreq = 80.0 + 2200.0 * exp(-time / 0.04)
                                    sampleSfxPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                                    val env = exp(-time / 0.08).toFloat()
                                    sample = (sin(sampleSfxPhase) * env * 0.4f).toFloat()
                                }
                                "SUB_DROP" -> {
                                    val currentFreq = 30.0 + 100.0 * exp(-time / 0.35)
                                    sampleSfxPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                                    val env = exp(-time / 0.45).toFloat()
                                    sample = (sin(sampleSfxPhase) * env * 0.75f).toFloat()
                                }
                                "CHIP_RISER" -> {
                                    val currentFreq = 200.0 + 1400.0 * (time / duration)
                                    sampleSfxPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                                    val wave = if (sin(sampleSfxPhase) > 0.0) 1.0 else -1.0
                                    val env = (time / duration).toFloat() * (1f - (time / duration).toFloat()) * 4f
                                    sample = (wave * env * 0.22f).toFloat()
                                }
                            }
                            mixedL += sample
                            mixedR += sample
                            sampleSfxTime += (1.0 / sampleRate)
                        } else {
                            sampleSfxActive = false
                        }
                    }

                    // Metronome sound generation
                    if (metronomeActive) {
                        val duration = 0.08
                        if (metronomeTime < duration) {
                            val metroPhase = 2.0 * Math.PI * metronomeFreq * metronomeTime
                            val metroEnv = exp(-metronomeTime / 0.012)
                            val metroSample = (sin(metroPhase) * metroEnv * _metronomeVolume.value).toFloat()
                            mixedL += metroSample
                            mixedR += metroSample
                            metronomeTime += (1.0 / sampleRate)
                        } else {
                            metronomeActive = false
                        }
                    }

                    // --- Real-Time Audio FX ---

                    // A. Low-pass filter sweeps
                    // Simple IIR pole filter
                    val fc = filterCutoff.coerceIn(0.01f, 1.0f) * 0.15f
                    filterStateL += fc * (mixedL - filterStateL)
                    filterStateR += fc * (mixedR - filterStateR)
                    mixedL = filterStateL
                    mixedR = filterStateR

                    // B. Stereo Ping-pong Delay
                    val wetL = delayBufferL[delayWriteIndex]
                    val wetR = delayBufferR[delayWriteIndex]

                    // Write dry and feed feedback back into the circular delay buffers
                    delayBufferL[delayWriteIndex] = mixedL + wetR * delayLevel
                    delayBufferR[delayWriteIndex] = mixedR + wetL * delayLevel

                    // Increment and wrap delay buffer indices
                    delayWriteIndex = (delayWriteIndex + 1) % delayBufferSize

                    // Mix dry + wet signal
                    var outputL = mixedL + wetL * delayLevel * 0.8f
                    var outputR = mixedR + wetR * delayLevel * 0.8f

                    // C. Simple Room Reverb / Spaciousness
                    val reverbWet = (outputL - outputR) * reverbLevel
                    outputL += reverbWet * 0.5f
                    outputR -= reverbWet * 0.5f

                    // --- MASTERING MODULES ---

                    // Apply different preset characteristics:
                    val activePreset = _masteringPreset.value
                    
                    when (activePreset) {
                        "Club Punch" -> {
                            // 1. Stereo Widening (1.4f)
                            val mid = (outputL + outputR) * 0.5f
                            val side = (outputL - outputR) * 0.5f
                            val widenedSide = side * 1.40f
                            outputL = mid + widenedSide
                            outputR = mid - widenedSide

                            // 2. Dynamic Compression (fast attack, medium release)
                            val absL = Math.abs(outputL)
                            val absR = Math.abs(outputR)
                            compEnvL += (absL - compEnvL) * (if (absL > compEnvL) 0.015f else 0.00045f)
                            compEnvR += (absR - compEnvR) * (if (absR > compEnvR) 0.015f else 0.00045f)

                            val thresh = 0.30f
                            val ratio = 4.5f
                            val makeup = 1.35f

                            if (compEnvL > thresh) {
                                val reduction = thresh + (compEnvL - thresh) / ratio
                                outputL = outputL * (reduction / compEnvL)
                            }
                            if (compEnvR > thresh) {
                                val reduction = thresh + (compEnvR - thresh) / ratio
                                outputR = outputR * (reduction / compEnvR)
                            }
                            outputL *= makeup
                            outputR *= makeup

                            // 3. Punchy Tube Saturation
                            outputL = (outputL * 0.9f + sin(outputL * 0.35f) * 0.25f).toFloat()
                            outputR = (outputR * 0.9f + sin(outputR * 0.35f) * 0.25f).toFloat()

                            // 4. Heavy Peak Limiting (0.95f ceiling)
                            val limit = 0.95f
                            if (Math.abs(outputL) > limit) {
                                outputL = Math.signum(outputL) * limit
                            }
                            if (Math.abs(outputR) > limit) {
                                outputR = Math.signum(outputR) * limit
                            }
                        }
                        "Radio-Ready" -> {
                            // 1. Bright Stereo Widening (1.25f)
                            val mid = (outputL + outputR) * 0.5f
                            val side = (outputL - outputR) * 0.5f
                            val widenedSide = side * 1.25f
                            outputL = mid + widenedSide
                            outputR = mid - widenedSide

                            // 2. Broadcast Style Compression (slower attack, faster release)
                            val absL = Math.abs(outputL)
                            val absR = Math.abs(outputR)
                            compEnvL += (absL - compEnvL) * (if (absL > compEnvL) 0.008f else 0.001f)
                            compEnvR += (absR - compEnvR) * (if (absR > compEnvR) 0.008f else 0.001f)

                            val thresh = 0.22f
                            val ratio = 3.0f
                            val makeup = 1.45f

                            if (compEnvL > thresh) {
                                val reduction = thresh + (compEnvL - thresh) / ratio
                                outputL = outputL * (reduction / compEnvL)
                            }
                            if (compEnvR > thresh) {
                                val reduction = thresh + (compEnvR - thresh) / ratio
                                outputR = outputR * (reduction / compEnvR)
                            }
                            outputL *= makeup
                            outputR *= makeup

                            // 3. High Excitement / Harmonic Saturation for Radio Brightness
                            outputL += (outputL * 0.08f * Math.abs(outputL))
                            outputR += (outputR * 0.08f * Math.abs(outputR))

                            // 4. Fast Peak Limiting (0.98f ceiling)
                            val limit = 0.98f
                            if (Math.abs(outputL) > limit) {
                                outputL = Math.signum(outputL) * limit
                            }
                            if (Math.abs(outputR) > limit) {
                                outputR = Math.signum(outputR) * limit
                            }
                        }
                        "Lo-Fi Chill" -> {
                            // 1. Narrowing Stereo Width for vintage feel (0.7f)
                            val mid = (outputL + outputR) * 0.5f
                            val side = (outputL - outputR) * 0.5f
                            val narrowedSide = side * 0.70f
                            outputL = mid + narrowedSide
                            outputR = mid - narrowedSide

                            // 2. Slow "Pumpy/Breathe" Compression
                            val absL = Math.abs(outputL)
                            val absR = Math.abs(outputR)
                            compEnvL += (absL - compEnvL) * (if (absL > compEnvL) 0.004f else 0.0002f)
                            compEnvR += (absR - compEnvR) * (if (absR > compEnvR) 0.004f else 0.0002f)

                            val thresh = 0.18f
                            val ratio = 3.5f
                            val makeup = 1.15f

                            if (compEnvL > thresh) {
                                val reduction = thresh + (compEnvL - thresh) / ratio
                                outputL = outputL * (reduction / compEnvL)
                            }
                            if (compEnvR > thresh) {
                                val reduction = thresh + (compEnvR - thresh) / ratio
                                outputR = outputR * (reduction / compEnvR)
                            }
                            outputL *= makeup
                            outputR *= makeup

                            // 3. Warm Analog Tape Saturation & roll-off high-cut filtering
                            outputL = (outputL - (outputL * outputL * outputL * 0.18f))
                            outputR = (outputR - (outputR * outputR * outputR * 0.18f))

                            // Apply low-pass filter
                            outputL = outputL * 0.70f + lofiLastL * 0.30f
                            outputR = outputR * 0.70f + lofiLastR * 0.30f
                            lofiLastL = outputL
                            lofiLastR = outputR

                            // Add a subtle 0.15% warm analog hiss and occasional vinyl crackle
                            val hiss = (Math.random().toFloat() * 2f - 1f) * 0.0015f
                            val pop = if (Math.random() < 0.00015) (Math.random().toFloat() * 2f - 1f) * 0.05f else 0f
                            outputL += hiss + pop
                            outputR += hiss + pop

                            // 4. Soft Ceiling Peak Limiter (0.90f ceiling)
                            val limit = 0.90f
                            if (Math.abs(outputL) > limit) {
                                outputL = Math.signum(outputL) * limit
                            }
                            if (Math.abs(outputR) > limit) {
                                outputR = Math.signum(outputR) * limit
                            }
                        }
                        else -> {
                            // "Flat Bypass" or any other fallback
                            // 1. Stereo Width Imaging (Mid/Side processing)
                            val width = _stereoWidth.value
                            if (width != 1.0f) {
                                val mid = (outputL + outputR) * 0.5f
                                val side = (outputL - outputR) * 0.5f
                                val scaledSide = side * width
                                outputL = mid + scaledSide
                                outputR = mid - scaledSide
                            }

                            // 2. Loudness Normalizer / Peak Limiter (if enabled)
                            if (_loudnessNormalizer.value) {
                                val peakLimit = 0.985f
                                if (Math.abs(outputL) > peakLimit) {
                                    outputL = Math.signum(outputL) * peakLimit
                                }
                                if (Math.abs(outputR) > peakLimit) {
                                    outputR = Math.signum(outputR) * peakLimit
                                }
                            }
                        }
                    }

                    // D. Soft clipping limiter to prevent digital crackling
                    outputL = outputL.coerceIn(-1.0f, 1.0f)
                    outputR = outputR.coerceIn(-1.0f, 1.0f)

                    // Convert floats to 16-bit PCM Short values
                    shortBuffer[i * 2] = (outputL * 32767.0f).toInt().toShort()
                    shortBuffer[i * 2 + 1] = (outputR * 32767.0f).toInt().toShort()

                    // Track sample progression
                    stepSampleCounter++
                    elapsedSampleCounter++
                }

                // Real-time FFT Frequency Analysis
                val analysisSamples = FloatArray(512)
                for (idx in 0 until 512) {
                    analysisSamples[idx] = shortBuffer[idx * 2].toFloat() / 32768.0f
                }
                val rawBands = FftAnalyser.analyze(analysisSamples)
                for (j in 0 until 32) {
                    val decay = 0.82f
                    smoothedBands[j] = smoothedBands[j] * decay + rawBands[j] * (1.0f - decay)
                }
                _fftData.value = smoothedBands.clone()

                // Write short PCM buffer to AudioTrack streaming output
                track.write(shortBuffer, 0, shortBuffer.size)
            }

            track.stop()
            track.release()
            audioTrack = null
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Safe release
        }
        audioTrack = null
        _currentStep.value = 0
        _effectiveBpm.value = _tempoBpm.value
        _fftData.value = FloatArray(32)
    }
}
