package com.example.audio

import android.content.Context
import com.example.data.database.ProjectEntity
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.exp

object AudioExporter {

    // Converts short array of PCM samples to standard WAV file
    fun exportBeat(
        context: Context,
        project: ProjectEntity,
        format: String = "WAV",
        durationSec: Int = 15 // Standard professional loop preview length
    ): File? {
        val sampleRate = 44100
        val numChannels = 2
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val blockAlign = numChannels * (bitsPerSample / 8)

        // Setup output folder
        val dir = File(context.getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) dir.mkdirs()

        // Construct unique file name
        val timestamp = System.currentTimeMillis()
        val safeName = project.name.replace("\\s+".toRegex(), "_")
        val fileExtension = format.lowercase()
        val file = File(dir, "${safeName}_${timestamp}.$fileExtension")

        try {
            val fos = FileOutputStream(file)

            // Write Placeholder WAV Header
            val header = ByteArray(44)
            fos.write(header)

            // Let's procedurally render the beat's PCM data based on project settings!
            // We want the WAV file to contain actual synthesized audio of the beat!
            val totalSamples = sampleRate * durationSec
            val bufferSize = 1024
            val shortBuffer = ShortArray(bufferSize * numChannels)

            val kickSteps = project.kickSteps.map { it == '1' }.toBooleanArray()
            val snareSteps = project.snareSteps.map { it == '1' }.toBooleanArray()
            val hihatSteps = project.hihatSteps.map { it == '1' }.toBooleanArray()
            val percussionSteps = project.percussionSteps.map { it == '1' }.toBooleanArray()
            val bassSteps = project.bassSteps.split(",")
            val synthSteps = project.synthSteps.split(",")

            val kickAuto = project.kickAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val snareAuto = project.snareAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val hihatAuto = project.hihatAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val percAuto = project.percussionAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val bassAuto = project.bassAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val synthAuto = project.synthAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }

            val bpm = project.bpm
            val samplesPerStep = (sampleRate * (60.0 / (bpm * 4.0))).toInt()

            var stepSampleCounter = 0
            var elapsedSampleCounter = 0
            var stepIndex = 0
            var currentPlayingStep = 0

            // Sound state trackers
            var kickPhase = 0.0
            var kickActive = false
            var kickTime = 0.0

            var snareActive = false
            var snareTime = 0.0

            var hihatActive = false
            var hihatTime = 0.0

            var percussionActive = false
            var percussionTime = 0.0
            var percussionFreq = 600.0

            var bassPhase = 0.0
            var bassActive = false
            var bassFreq = 0.0
            var bassEnv = 0.0

            var synthPhase = 0.0
            var synthActive = false
            var synthFreq = 0.0
            var synthEnv = 0.0

            val random = Random()

            // Main rendering loop
            while (elapsedSampleCounter < totalSamples) {
                val samplesToRender = Math.min(bufferSize, totalSamples - elapsedSampleCounter)

                for (s in 0 until samplesToRender) {
                    if (stepSampleCounter >= samplesPerStep || (elapsedSampleCounter == 0 && s == 0)) {
                        stepSampleCounter = 0
                        currentPlayingStep = stepIndex

                        // Trigger instruments based on steps
                        if (kickSteps.getOrElse(stepIndex) { false }) {
                            kickActive = true
                            kickTime = 0.0
                            kickPhase = 0.0
                        }
                        if (snareSteps.getOrElse(stepIndex) { false }) {
                            snareActive = true
                            snareTime = 0.0
                        }
                        if (hihatSteps.getOrElse(stepIndex) { false }) {
                            hihatActive = true
                            hihatTime = 0.0
                        }
                        if (percussionSteps.getOrElse(stepIndex) { false }) {
                            percussionActive = true
                            percussionTime = 0.0
                            percussionFreq = 500.0 + random.nextDouble() * 300.0
                        }
                        val bassNote = bassSteps.getOrNull(stepIndex) ?: ""
                        if (bassNote.isNotEmpty() && bassNote != "-") {
                            bassFreq = noteToFreq(bassNote)
                            if (bassFreq > 0.0) {
                                bassActive = true
                                bassPhase = 0.0
                                bassEnv = 1.0
                            }
                        }
                        val synthNote = synthSteps.getOrNull(stepIndex) ?: ""
                        if (synthNote.isNotEmpty() && synthNote != "-") {
                            val newFreq = noteToFreq(synthNote)
                            if (newFreq > 0.0) {
                                synthActive = true
                                synthPhase = 0.0
                                synthEnv = 1.0
                                synthFreq = newFreq
                            }
                        }

                        stepIndex = (stepIndex + 1) % 16
                    }

                    // Synth sounds
                    var sampleL = 0.0f
                    var sampleR = 0.0f

                    if (kickActive) {
                        val duration = 0.15
                        if (kickTime < duration) {
                            val currentFreq = 48.0 + (160.0 - 48.0) * exp(-kickTime / 0.02)
                            kickPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                            val amplitude = exp(-kickTime / 0.04)
                            val sample = (Math.sin(kickPhase) * amplitude * project.kickVolume * kickAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                            sampleL += sample
                            sampleR += sample
                            kickTime += (1.0 / sampleRate)
                        } else {
                            kickActive = false
                        }
                    }

                    if (snareActive) {
                        val duration = 0.22
                        if (snareTime < duration) {
                            val noise = (random.nextFloat() * 2.0f - 1.0f)
                            val env = exp(-snareTime / 0.05)
                            val bodyVal = Math.sin(2.0 * Math.PI * 180.0 * snareTime) * exp(-snareTime / 0.02)
                            val sample = ((noise * 0.7 + bodyVal * 0.3) * env * project.snareVolume * snareAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                            sampleL += sample
                            sampleR += sample
                            snareTime += (1.0 / sampleRate)
                        } else {
                            snareActive = false
                        }
                    }

                    if (hihatActive) {
                        val duration = 0.05
                        if (hihatTime < duration) {
                            val noise = (random.nextFloat() * 2.0f - 1.0f)
                            val env = exp(-hihatTime / 0.015)
                            val sample = (noise * env * project.hihatVolume * hihatAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                            sampleL += sample
                            sampleR += sample
                            hihatTime += (1.0 / sampleRate)
                        } else {
                            hihatActive = false
                        }
                    }

                    if (percussionActive) {
                        val duration = 0.18
                        if (percussionTime < duration) {
                            val car = Math.sin(2.0 * Math.PI * percussionFreq * percussionTime)
                            val mod = Math.sin(2.0 * Math.PI * (percussionFreq * 1.41) * percussionTime)
                            val env = exp(-percussionTime / 0.04)
                            val sample = (car * mod * env * project.percussionVolume * percAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                            sampleL += sample
                            sampleR += sample
                            percussionTime += (1.0 / sampleRate)
                        } else {
                            percussionActive = false
                        }
                    }

                    if (bassActive) {
                        if (bassEnv > 0.01) {
                            bassPhase += (2.0 * Math.PI * bassFreq / sampleRate)
                            val p = (bassPhase % (2.0 * Math.PI)) / (2.0 * Math.PI)
                            val bassWave = if (p < 0.5) (4.0 * p - 1.0) else (3.0 - 4.0 * p)
                            val sample = (bassWave * bassEnv * project.bassVolume * bassAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                            sampleL += sample
                            sampleR += sample
                            bassEnv *= 0.99992
                        } else {
                            bassActive = false
                        }
                    }

                    if (synthActive) {
                        if (synthEnv > 0.01) {
                            synthPhase += (2.0 * Math.PI * synthFreq / sampleRate)
                            val p = (synthPhase % (2.0 * Math.PI)) / (2.0 * Math.PI)
                            val rawSynth = (2.0 * p - 1.0).toFloat() // Sawtooth
                            val sample = (rawSynth * synthEnv * project.synthVolume * synthAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                            sampleL += sample
                            sampleR += sample
                            synthEnv *= 0.99994
                        } else {
                            synthActive = false
                        }
                    }

                    // Mix and limit
                    sampleL = sampleL.coerceIn(-1.0f, 1.0f)
                    sampleR = sampleR.coerceIn(-1.0f, 1.0f)

                    // Write to buffer
                    shortBuffer[s * 2] = (sampleL * 32767.0f).toInt().toShort()
                    shortBuffer[s * 2 + 1] = (sampleR * 32767.0f).toInt().toShort()

                    stepSampleCounter++
                    elapsedSampleCounter++
                }

                // Write short PCM buffer to WAV file (little-endian byte array)
                val byteBuffer = ByteBuffer.allocate(samplesToRender * numChannels * 2)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                for (b in 0 until samplesToRender * numChannels) {
                    byteBuffer.putShort(shortBuffer[b])
                }
                fos.write(byteBuffer.array())
            }

            fos.close()

            // Update WAV Header with exact lengths
            val totalDataLen = totalSamples * numChannels * 2
            val totalFileLen = 36 + totalDataLen

            val raf = RandomAccessFile(file, "rw")
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.write(intToByteArray(totalFileLen), 0, 4)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.write(intToByteArray(16), 0, 4) // Format chunk size
            raf.write(shortToByteArray(1), 0, 2) // Audio Format = 1 (PCM)
            raf.write(shortToByteArray(numChannels.toShort()), 0, 2)
            raf.write(intToByteArray(sampleRate), 0, 4)
            raf.write(intToByteArray(byteRate), 0, 4)
            raf.write(shortToByteArray(blockAlign.toShort()), 0, 2)
            raf.write(shortToByteArray(bitsPerSample.toShort()), 0, 2)
            raf.writeBytes("data")
            raf.write(intToByteArray(totalDataLen), 0, 4)
            raf.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportStems(
        context: Context,
        project: ProjectEntity,
        format: String = "WAV",
        durationSec: Int = 15
    ): List<File> {
        val stemsList = mutableListOf<File>()
        val stemsToRender = listOf(
            "Drums" to listOf("KICK", "SNARE", "HIHAT"),
            "Bass" to listOf("BASS"),
            "Synths" to listOf("SYNTH"),
            "Vocals_Perc" to listOf("PERC")
        )

        for ((stemName, activeTracks) in stemsToRender) {
            val sampleRate = 44100
            val numChannels = 2
            val bitsPerSample = 16
            val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
            val blockAlign = numChannels * (bitsPerSample / 8)

            val dir = File(context.getExternalFilesDir(null), "Recordings")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = System.currentTimeMillis()
            val safeName = project.name.replace("\\s+".toRegex(), "_")
            val fileExtension = format.lowercase()
            val file = File(dir, "${safeName}_Stem_${stemName}_${timestamp}.$fileExtension")

            try {
                val fos = FileOutputStream(file)
                val header = ByteArray(44)
                fos.write(header)

                val totalSamples = sampleRate * durationSec
                val bufferSize = 1024
                val shortBuffer = ShortArray(bufferSize * numChannels)

                val kickSteps = project.kickSteps.map { it == '1' }.toBooleanArray()
                val snareSteps = project.snareSteps.map { it == '1' }.toBooleanArray()
                val hihatSteps = project.hihatSteps.map { it == '1' }.toBooleanArray()
                val percussionSteps = project.percussionSteps.map { it == '1' }.toBooleanArray()
                val bassSteps = project.bassSteps.split(",")
                val synthSteps = project.synthSteps.split(",")

                val kickAuto = project.kickAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
                val snareAuto = project.snareAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
                val hihatAuto = project.hihatAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
                val percAuto = project.percussionAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
                val bassAuto = project.bassAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
                val synthAuto = project.synthAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }

                val bpm = project.bpm
                val samplesPerStep = (sampleRate * (60.0 / (bpm * 4.0))).toInt()

                var stepSampleCounter = 0
                var elapsedSampleCounter = 0
                var stepIndex = 0
                var currentPlayingStep = 0

                var kickPhase = 0.0
                var kickActive = false
                var kickTime = 0.0

                var snareActive = false
                var snareTime = 0.0

                var hihatActive = false
                var hihatTime = 0.0

                var percussionActive = false
                var percussionTime = 0.0
                var percussionFreq = 600.0

                var bassPhase = 0.0
                var bassActive = false
                var bassFreq = 0.0
                var bassEnv = 0.0

                var synthPhase = 0.0
                var synthActive = false
                var synthFreq = 0.0
                var synthEnv = 0.0

                val random = Random()

                while (elapsedSampleCounter < totalSamples) {
                    val samplesToRender = Math.min(bufferSize, totalSamples - elapsedSampleCounter)

                    for (s in 0 until samplesToRender) {
                        if (stepSampleCounter >= samplesPerStep || (elapsedSampleCounter == 0 && s == 0)) {
                            stepSampleCounter = 0
                            currentPlayingStep = stepIndex

                            if (activeTracks.contains("KICK") && kickSteps.getOrElse(stepIndex) { false }) {
                                kickActive = true
                                kickTime = 0.0
                                kickPhase = 0.0
                            }
                            if (activeTracks.contains("SNARE") && snareSteps.getOrElse(stepIndex) { false }) {
                                snareActive = true
                                snareTime = 0.0
                            }
                            if (activeTracks.contains("HIHAT") && hihatSteps.getOrElse(stepIndex) { false }) {
                                hihatActive = true
                                hihatTime = 0.0
                            }
                            if (activeTracks.contains("PERC") && percussionSteps.getOrElse(stepIndex) { false }) {
                                percussionActive = true
                                percussionTime = 0.0
                                percussionFreq = 500.0 + random.nextDouble() * 300.0
                            }
                            val bassNote = bassSteps.getOrNull(stepIndex) ?: ""
                            if (activeTracks.contains("BASS") && bassNote.isNotEmpty() && bassNote != "-") {
                                bassFreq = noteToFreq(bassNote)
                                if (bassFreq > 0.0) {
                                    bassActive = true
                                    bassPhase = 0.0
                                    bassEnv = 1.0
                                }
                            }
                            val synthNote = synthSteps.getOrNull(stepIndex) ?: ""
                            if (activeTracks.contains("SYNTH") && synthNote.isNotEmpty() && synthNote != "-") {
                                val newFreq = noteToFreq(synthNote)
                                if (newFreq > 0.0) {
                                    synthActive = true
                                    synthPhase = 0.0
                                    synthEnv = 1.0
                                    synthFreq = newFreq
                                }
                            }

                            stepIndex = (stepIndex + 1) % 16
                        }

                        var sampleL = 0.0f
                        var sampleR = 0.0f

                        if (kickActive) {
                            val duration = 0.15
                            if (kickTime < duration) {
                                val currentFreq = 48.0 + (160.0 - 48.0) * exp(-kickTime / 0.02)
                                kickPhase += (2.0 * Math.PI * currentFreq / sampleRate)
                                val amplitude = exp(-kickTime / 0.04)
                                val sample = (Math.sin(kickPhase) * amplitude * project.kickVolume * kickAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                                sampleL += sample
                                sampleR += sample
                                kickTime += (1.0 / sampleRate)
                            } else {
                                kickActive = false
                            }
                        }

                        if (snareActive) {
                            val duration = 0.22
                            if (snareTime < duration) {
                                val noise = (random.nextFloat() * 2.0f - 1.0f)
                                val env = exp(-snareTime / 0.05)
                                val bodyVal = Math.sin(2.0 * Math.PI * 180.0 * snareTime) * exp(-snareTime / 0.02)
                                val sample = ((noise * 0.7 + bodyVal * 0.3) * env * project.snareVolume * snareAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                                sampleL += sample
                                sampleR += sample
                                snareTime += (1.0 / sampleRate)
                            } else {
                                snareActive = false
                            }
                        }

                        if (hihatActive) {
                            val duration = 0.05
                            if (hihatTime < duration) {
                                val noise = (random.nextFloat() * 2.0f - 1.0f)
                                val env = exp(-hihatTime / 0.015)
                                val sample = (noise * env * project.hihatVolume * hihatAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                                sampleL += sample
                                sampleR += sample
                                hihatTime += (1.0 / sampleRate)
                            } else {
                                hihatActive = false
                            }
                        }

                        if (percussionActive) {
                            val duration = 0.18
                            if (percussionTime < duration) {
                                val car = Math.sin(2.0 * Math.PI * percussionFreq * percussionTime)
                                val mod = Math.sin(2.0 * Math.PI * (percussionFreq * 1.41) * percussionTime)
                                val env = exp(-percussionTime / 0.04)
                                val sample = (car * mod * env * project.percussionVolume * percAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                                sampleL += sample
                                sampleR += sample
                                percussionTime += (1.0 / sampleRate)
                            } else {
                                percussionActive = false
                            }
                        }

                        if (bassActive) {
                            if (bassEnv > 0.01) {
                                bassPhase += (2.0 * Math.PI * bassFreq / sampleRate)
                                val p = (bassPhase % (2.0 * Math.PI)) / (2.0 * Math.PI)
                                val bassWave = if (p < 0.5) (4.0 * p - 1.0) else (3.0 - 4.0 * p)
                                val sample = (bassWave * bassEnv * project.bassVolume * bassAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                                sampleL += sample
                                sampleR += sample
                                bassEnv *= 0.99992
                            } else {
                                bassActive = false
                            }
                        }

                        if (synthActive) {
                            if (synthEnv > 0.01) {
                                synthPhase += (2.0 * Math.PI * synthFreq / sampleRate)
                                val p = (synthPhase % (2.0 * Math.PI)) / (2.0 * Math.PI)
                                val rawSynth = (2.0 * p - 1.0).toFloat()
                                val sample = (rawSynth * synthEnv * project.synthVolume * synthAuto.getOrElse(currentPlayingStep) { 1.0f }).toFloat()
                                sampleL += sample
                                sampleR += sample
                                synthEnv *= 0.99994
                            } else {
                                synthActive = false
                            }
                        }

                        sampleL = sampleL.coerceIn(-1.0f, 1.0f)
                        sampleR = sampleR.coerceIn(-1.0f, 1.0f)

                        shortBuffer[s * 2] = (sampleL * 32767.0f).toInt().toShort()
                        shortBuffer[s * 2 + 1] = (sampleR * 32767.0f).toInt().toShort()

                        stepSampleCounter++
                        elapsedSampleCounter++
                    }

                    val byteBuffer = ByteBuffer.allocate(samplesToRender * numChannels * 2)
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    for (b in 0 until samplesToRender * numChannels) {
                        byteBuffer.putShort(shortBuffer[b])
                    }
                    fos.write(byteBuffer.array())
                }

                fos.close()

                val totalDataLen = totalSamples * numChannels * 2
                val totalFileLen = 36 + totalDataLen

                val raf = RandomAccessFile(file, "rw")
                raf.seek(0)
                raf.writeBytes("RIFF")
                raf.write(intToByteArray(totalFileLen), 0, 4)
                raf.writeBytes("WAVE")
                raf.writeBytes("fmt ")
                raf.write(intToByteArray(16), 0, 4)
                raf.write(shortToByteArray(1), 0, 2)
                raf.write(shortToByteArray(numChannels.toShort()), 0, 2)
                raf.write(intToByteArray(sampleRate), 0, 4)
                raf.write(intToByteArray(byteRate), 0, 4)
                raf.write(shortToByteArray(blockAlign.toShort()), 0, 2)
                raf.write(shortToByteArray(bitsPerSample.toShort()), 0, 2)
                raf.writeBytes("data")
                raf.write(intToByteArray(totalDataLen), 0, 4)
                raf.close()

                stemsList.add(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return stemsList
    }

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

    private fun intToByteArray(value: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = (value and 0xff).toByte()
        b[1] = ((value ushr 8) and 0xff).toByte()
        b[2] = ((value ushr 16) and 0xff).toByte()
        b[3] = ((value ushr 24) and 0xff).toByte()
        return b
    }

    private fun shortToByteArray(value: Short): ByteArray {
        val b = ByteArray(2)
        b[0] = (value.toInt() and 0xff).toByte()
        b[1] = ((value.toInt() ushr 8) and 0xff).toByte()
        return b
    }
}
