package com.example.data.gemini

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    
    // Using gemini-3.5-flash as default for basic text and structured tasks
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Suppress("LongMethod")
    suspend fun generateBeatPreset(userPrompt: String): BeatPreset? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return@withContext null
        }

        // Construct System Instructions to ensure we receive a clean JSON adhering strictly to the schema
        val systemInstruction = """
            You are a professional AI Music Producer and Beat Designer.
            Given the user's creative prompt, design a studio-grade beat.
            You must return a raw JSON object containing:
            1. "name": A catchy, trendy title for the beat.
            2. "genre": Must be exactly one of: "Afrobeat", "Afro Fuji", "Afro Blues", "Afro Pop", "Modern R&B", "Pop", "Hip-Hop", "Drill", "Trap Rap".
            3. "bpm": Recommended tempo as integer (between 60 and 160).
            4. "kick": Binary string of exactly 16 characters (only '0' or '1') representing the active steps.
            5. "snare": Binary string of exactly 16 characters.
            6. "hihat": Binary string of exactly 16 characters.
            7. "percussion": Binary string of exactly 16 characters.
            8. "bass": Comma-separated array of 16 elements. Each element must be a note like "C1", "C2", "E2", "G2" or "-" for silent steps.
            9. "synth": Comma-separated array of 16 elements. Each element must be a note like "C3", "E3", "G3", "C4" or "-" for silent steps.
            10. "description": A short, trendy 2-sentence description of the beat vibe.

            Do not wrap the JSON in Markdown backticks. Output raw JSON only.
        """.trimIndent()

        // Build request body JSON using standard org.json
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", userPrompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            // Add generation config for JSON output
            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.75)
            }
            put("generationConfig", generationConfig)

            // Add system instruction
            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    val partObj = JSONObject().apply {
                        put("text", systemInstruction)
                    }
                    put(partObj)
                }
                put("parts", partsArray)
            }
            put("systemInstruction", systemInstructionObj)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error: ${response.code} - $responseString")
                return@withContext null
            }

            Log.d(TAG, "Raw Response: $responseString")

            // Parse response
            val root = JSONObject(responseString)
            val candidates = root.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val textJson = parts.getJSONObject(0).getString("text")

            val beatJson = JSONObject(textJson)
            return@withContext BeatPreset(
                name = beatJson.getString("name"),
                genre = beatJson.getString("genre"),
                bpm = beatJson.getInt("bpm"),
                kick = beatJson.getString("kick"),
                snare = beatJson.getString("snare"),
                hihat = beatJson.getString("hihat"),
                percussion = beatJson.getString("percussion"),
                bass = beatJson.getString("bass"),
                synth = beatJson.getString("synth"),
                description = beatJson.getString("description")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during call", e)
            return@withContext null
        }
    }

    @Suppress("LongMethod", "MaxLineLength")
    suspend fun analyzeMix(
        genre: String,
        bpm: Int,
        kickVolume: Float,
        snareVolume: Float,
        hihatVolume: Float,
        percVolume: Float,
        bassVolume: Float,
        synthVolume: Float,
        delayLevel: Float,
        reverbLevel: Float,
        filterCutoff: Float,
        masteringPreset: String,
        stereoWidth: Float,
        loudnessNormalizer: Boolean,
        kickStepsCount: Int,
        snareStepsCount: Int,
        hihatStepsCount: Int,
        percStepsCount: Int,
        bassStepsCount: Int,
        synthStepsCount: Int,
        eqLevels: Map<String, Triple<Float, Float, Float>>
    ): MasteringSuggestions? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return@withContext null
        }

        val eqLevelsString = eqLevels.entries.joinToString("\n") { (track, bands) ->
            "- $track: Low Shelf: ${bands.first}dB, Mid Bell: ${bands.second}dB, High Shelf: ${bands.third}dB"
        }

        val prompt = """
            Target Genre: $genre
            BPM: $bpm
            
            Current Channel Fader Levels:
            - Kick: ${(kickVolume * 100).toInt()}%
            - Snare: ${(snareVolume * 100).toInt()}%
            - Hi-hat: ${(hihatVolume * 100).toInt()}%
            - Percussion: ${(percVolume * 100).toInt()}%
            - Bass: ${(bassVolume * 100).toInt()}%
            - Synth: ${(synthVolume * 100).toInt()}%
            
            Current FX Settings:
            - Delay Feedback/Mix: ${(delayLevel * 100).toInt()}%
            - Reverb Mix: ${(reverbLevel * 100).toInt()}%
            - Filter Cutoff (Sweeping lowpass): ${(filterCutoff * 100).toInt()}%
            
            Current Master Console settings:
            - Active Console Preset: $masteringPreset
            - Stereo Width factor: $stereoWidth
            - Peak Limiter Enabled: $loudnessNormalizer
            
            Active Step Counts (indicating grid rhythmic density out of 16 steps):
            - Kick: $kickStepsCount/16
            - Snare: $snareStepsCount/16
            - Hi-hat: $hihatStepsCount/16
            - Percussion: $percStepsCount/16
            - Bass: $bassStepsCount/16
            - Synth: $synthStepsCount/16
            
            Current Channel Equalizer Levels:
            $eqLevelsString
        """.trimIndent()

        val systemInstruction = """
            You are an elite, GRAMMY-winning Audio Mastering Engineer, DSP Specialist, and Music Producer.
            Your job is to analyze the user's current mix settings, active channel step density, EQ gains, and target genre, then suggest highly professional, specific adjustments for EQ, compression, and loudness to match professional industry standards for their chosen genre.
            
            You must return a raw JSON object containing exactly the following keys:
            1. "sonic_grade": A string representing their current mix balance grade (e.g. "B+", "A-", "C", "B"). Be realistic based on their current channel volumes and EQ.
            2. "analysis_summary": A professional, 2-3 sentence overview of their mix's current sonic profile, pointing out any imbalances or strengths.
            3. "adjustments": A JSON object containing specific, professional target values:
               - "kick_volume": Suggested float fader level (0.0 to 1.0).
               - "snare_volume": Suggested float fader level (0.0 to 1.0).
               - "hihat_volume": Suggested float fader level (0.0 to 1.0).
               - "perc_volume": Suggested float fader level (0.0 to 1.0).
               - "bass_volume": Suggested float fader level (0.0 to 1.0).
               - "synth_volume": Suggested float fader level (0.0 to 1.0).
               - "mastering_preset": Suggested console flavor (Must be exactly one of: "Club Punch", "Radio-Ready", "Lo-Fi Chill", "Flat Bypass").
               - "stereo_width": Suggested float width factor (0.0 to 2.0).
               - "loudness_normalizer": Suggested boolean for peak limiter (usually true).
               - "eq_adjustments": A JSON array of exactly 6 objects, one for each track ("KICK", "SNARE", "HIHAT", "PERC", "BASS", "SYNTH"). Each object must have:
                 - "track": Name of the track.
                 - "low_gain": Suggested low shelf gain adjustment in dB (-12.0 to 12.0).
                 - "mid_gain": Suggested mid bell gain adjustment in dB (-12.0 to 12.0).
                 - "high_gain": Suggested high shelf gain adjustment in dB (-12.0 to 12.0).
            4. "explanation_eq": A professional 2-sentence explanation of the suggested EQ cuts or boosts.
            5. "explanation_compression": A professional 2-sentence explanation of the dynamics/compression suggestions and why they glue the mix.
            6. "explanation_loudness": A professional 2-sentence explanation of the loudness/limiting suggestions and targeting industry loudness standards.

            Ensure all volume levels, EQ gains, and stereo widths are realistic. Keep suggested fader volumes relative and cohesive. For instance, do not set all faders to 1.0 or 0.0.
            Do not wrap the JSON in Markdown backticks. Output raw JSON only.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
            }
            put("generationConfig", generationConfig)

            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    val partObj = JSONObject().apply {
                        put("text", systemInstruction)
                    }
                    put(partObj)
                }
                put("parts", partsArray)
            }
            put("systemInstruction", systemInstructionObj)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error in analyzeMix: ${response.code} - $responseString")
                return@withContext null
            }

            Log.d(TAG, "Raw Mastering Response: $responseString")

            val root = JSONObject(responseString)
            val candidates = root.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val textJson = parts.getJSONObject(0).getString("text")

            val masterJson = JSONObject(textJson)
            val grade = masterJson.optString("sonic_grade", "B")
            val summary = masterJson.optString("analysis_summary", "")
            
            val adjObj = masterJson.getJSONObject("adjustments")
            val eqList = mutableListOf<EqAdjustment>()
            val eqArray = adjObj.optJSONArray("eq_adjustments")
            if (eqArray != null) {
                for (i in 0 until eqArray.length()) {
                    val item = eqArray.getJSONObject(i)
                    eqList.add(
                        EqAdjustment(
                            track = item.optString("track", ""),
                            lowGain = item.optDouble("low_gain", 0.0).toFloat(),
                            midGain = item.optDouble("mid_gain", 0.0).toFloat(),
                            highGain = item.optDouble("high_gain", 0.0).toFloat()
                        )
                    )
                }
            }
            
            val adjustments = MasteringAdjustment(
                kickVolume = adjObj.optDouble("kick_volume", 0.8).toFloat(),
                snareVolume = adjObj.optDouble("snare_volume", 0.7).toFloat(),
                hihatVolume = adjObj.optDouble("hihat_volume", 0.5).toFloat(),
                percVolume = adjObj.optDouble("perc_volume", 0.5).toFloat(),
                bassVolume = adjObj.optDouble("bass_volume", 0.7).toFloat(),
                synthVolume = adjObj.optDouble("synth_volume", 0.4).toFloat(),
                masteringPreset = adjObj.optString("mastering_preset", "Flat Bypass"),
                stereoWidth = adjObj.optDouble("stereo_width", 1.0).toFloat(),
                loudnessNormalizer = adjObj.optBoolean("loudness_normalizer", true),
                eqAdjustments = eqList
            )
            
            return@withContext MasteringSuggestions(
                sonicGrade = grade,
                analysisSummary = summary,
                adjustments = adjustments,
                explanationEq = masterJson.optString("explanation_eq", ""),
                explanationCompression = masterJson.optString("explanation_compression", ""),
                explanationLoudness = masterJson.optString("explanation_loudness", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during analyzeMix", e)
            return@withContext null
        }
    }

    suspend fun generateChordProgression(
        genre: String,
        musicalKey: String,
        style: String
    ): ChordProgression? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return@withContext null
        }

        val prompt = """
            Genre: $genre
            Key: $musicalKey
            Style: $style
        """.trimIndent()

        val systemInstruction = """
            You are a master of music theory, jazz harmony, and beat composition.
            Generate a professional, catchy chord progression and note arrangement for the given genre, key, and style.
            Your output must be a raw JSON object containing:
            1. "title": A descriptive title of the progression (e.g., "Ambitious Neo-Soul Turnaround", "Summer Afro-Reggae Loop").
            2. "roman_numerals": The standard Roman numeral analysis (e.g., "ii7 - V7 - Imaj7 - VI7").
            3. "description": A professional, 1-sentence description explaining why this progression works for the selected genre.
            4. "bass_steps": A JSON array of exactly 16 elements. Each element must be a note like "C1", "D1", "E1", "F1", "G1", "A1", "B1" (with pitch/octave 1 or 2) representing 16 sequencer steps. Silent steps should be "-".
            5. "synth_steps": A JSON array of exactly 16 elements. Each element must be a note like "C3", "E3", "G3", "C4" (with pitch/octave 3, 4, or 5) representing 16 sequencer steps. Silent steps should be "-".

            Ensure notes are in key and sound harmonically coherent when played together. Make sure there are a reasonable number of silent steps (e.g. 4-8 silent steps marked as "-") so the groove is not overcrowded.
            Do not wrap the JSON in Markdown backticks. Output raw JSON only.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.6)
            }
            put("generationConfig", generationConfig)

            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    val partObj = JSONObject().apply {
                        put("text", systemInstruction)
                    }
                    put(partObj)
                }
                put("parts", partsArray)
            }
            put("systemInstruction", systemInstructionObj)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error in generateChordProgression: ${response.code} - $responseString")
                return@withContext null
            }

            val root = JSONObject(responseString)
            val candidates = root.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val textJson = parts.getJSONObject(0).getString("text")

            val json = JSONObject(textJson)
            val bassArray = json.getJSONArray("bass_steps")
            val bassList = mutableListOf<String>()
            for (i in 0 until bassArray.length()) {
                bassList.add(bassArray.getString(i))
            }
            while (bassList.size < 16) bassList.add("-")

            val synthArray = json.getJSONArray("synth_steps")
            val synthList = mutableListOf<String>()
            for (i in 0 until synthArray.length()) {
                synthList.add(synthArray.getString(i))
            }
            while (synthList.size < 16) synthList.add("-")

            return@withContext ChordProgression(
                title = json.optString("title", "AI Chord Progression"),
                romanNumerals = json.optString("roman_numerals", "I - IV - V - I"),
                description = json.optString("description", ""),
                bassSteps = bassList.take(16),
                synthSteps = synthList.take(16)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generateChordProgression", e)
            return@withContext null
        }
    }
}

data class ChordProgression(
    val title: String,
    val romanNumerals: String,
    val description: String,
    val bassSteps: List<String>,
    val synthSteps: List<String>
)

data class BeatPreset(
    val name: String,
    val genre: String,
    val bpm: Int,
    val kick: String,
    val snare: String,
    val hihat: String,
    val percussion: String,
    val bass: String,
    val synth: String,
    val description: String
)

data class MasteringSuggestions(
    val sonicGrade: String,
    val analysisSummary: String,
    val adjustments: MasteringAdjustment,
    val explanationEq: String,
    val explanationCompression: String,
    val explanationLoudness: String
)

data class MasteringAdjustment(
    val kickVolume: Float,
    val snareVolume: Float,
    val hihatVolume: Float,
    val percVolume: Float,
    val bassVolume: Float,
    val synthVolume: Float,
    val masteringPreset: String,
    val stereoWidth: Float,
    val loudnessNormalizer: Boolean,
    val eqAdjustments: List<EqAdjustment>
)

data class EqAdjustment(
    val track: String,
    val lowGain: Float,
    val midGain: Float,
    val highGain: Float
)
