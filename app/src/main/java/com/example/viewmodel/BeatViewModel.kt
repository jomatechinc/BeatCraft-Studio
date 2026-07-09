package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.audio.AudioExporter
import com.example.audio.BeatSynthesizer
import com.example.data.database.AppDatabase
import com.example.data.database.ProjectEntity
import com.example.data.database.RecordingEntity
import com.example.data.repository.BeatRepository
import com.example.data.gemini.GeminiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class BeatViewModel(application: Application) : AndroidViewModel(application) {
    
    // Database and Repository Setup
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "beatcraft_database"
        ).fallbackToDestructiveMigration().build()
    }
    
    val repository: BeatRepository by lazy {
        BeatRepository(database.projectDao(), database.recordingDao())
    }

    // Audio Engine
    val synthesizer = BeatSynthesizer()

    // Observed flows from Repository
    val savedProjects = lazy { repository.allProjects }.value.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val exportedRecordings = lazy { repository.allRecordings }.value.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current Project ID (0 if unsaved draft)
    private val _currentProjectId = MutableStateFlow<Long>(0)
    val currentProjectId: StateFlow<Long> = _currentProjectId

    private val _currentProjectName = MutableStateFlow("Lagos Sunset Draft")
    val currentProjectName: StateFlow<String> = _currentProjectName

    // AI Status state
    private val _aiGenerationLoading = MutableStateFlow(false)
    val aiGenerationLoading: StateFlow<Boolean> = _aiGenerationLoading

    private val _aiStatusMessage = MutableStateFlow("")
    val aiStatusMessage: StateFlow<String> = _aiStatusMessage

    // Cloud Sync Status state
    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing

    // Offline Mode Toggle
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode

    // Customizable Shortcuts Map (Key -> Description/Action)
    private val _shortcuts = MutableStateFlow(
        mapOf(
            "SPACE" to "Play / Pause Beat",
            "K" to "Toggle Kick Channel",
            "S" to "Toggle Snare Channel",
            "H" to "Toggle Hi-hat Channel",
            "P" to "Toggle Percussion",
            "B" to "Solo Bass Channel",
            "L" to "Solo Lead Synth",
            "R" to "Randomize Grid Steps"
        )
    )
    val shortcuts: StateFlow<Map<String, String>> = _shortcuts

    // Live Tap Pad Controller States
    private val _isLiveRecording = MutableStateFlow(false)
    val isLiveRecording: StateFlow<Boolean> = _isLiveRecording

    private val _noteRepeatRate = MutableStateFlow("Off") // "Off", "1/8", "1/16", "1/32"
    val noteRepeatRate: StateFlow<String> = _noteRepeatRate

    private val _activePadMode = MutableStateFlow("Drums") // "Drums", "Samples"
    val activePadMode: StateFlow<String> = _activePadMode

    private val _sampleAssignments = MutableStateFlow(
        mapOf(
            0 to "VOX_HEY",
            1 to "VOX_AHH",
            2 to "SIREN_FX",
            3 to "LASER_FX",
            4 to "SUB_DROP",
            5 to "CHIP_RISER"
        )
    )
    val sampleAssignments: StateFlow<Map<Int, String>> = _sampleAssignments

    fun toggleLiveRecording() {
        _isLiveRecording.value = !_isLiveRecording.value
    }

    fun setNoteRepeatRate(rate: String) {
        _noteRepeatRate.value = rate
    }

    fun setPadMode(mode: String) {
        _activePadMode.value = mode
    }

    fun assignSampleToPad(padIndex: Int, sampleType: String) {
        val current = _sampleAssignments.value.toMutableMap()
        current[padIndex] = sampleType
        _sampleAssignments.value = current
    }

    fun triggerLivePad(padIndex: Int) {
        val mode = _activePadMode.value
        if (mode == "Drums") {
            val track = when (padIndex) {
                0 -> "KICK"
                1 -> "SNARE"
                2 -> "HIHAT"
                3 -> "PERC"
                4 -> "BASS"
                5 -> "SYNTH"
                else -> "KICK"
            }
            // Trigger sound
            if (track == "BASS") {
                synthesizer.triggerLiveSound("BASS", "C2")
            } else if (track == "SYNTH") {
                synthesizer.triggerLiveSound("SYNTH", "C4")
            } else {
                synthesizer.triggerLiveSound(track)
            }

            // Record if recording is active
            if (_isLiveRecording.value) {
                val step = synthesizer.currentStep.value
                when (track) {
                    "KICK" -> synthesizer.kickSteps[step] = true
                    "SNARE" -> synthesizer.snareSteps[step] = true
                    "HIHAT" -> synthesizer.hihatSteps[step] = true
                    "PERC" -> synthesizer.percussionSteps[step] = true
                    "BASS" -> synthesizer.bassSteps[step] = "C2"
                    "SYNTH" -> synthesizer.synthSteps[step] = "C4"
                }
                autoSave()
            }
        } else {
            // Samples mode
            val sampleType = _sampleAssignments.value[padIndex] ?: "VOX_HEY"
            synthesizer.triggerSampleSound(sampleType)
        }
    }

    // AI Mastering Assistant States
    private val _masteringLoading = MutableStateFlow(false)
    val masteringLoading: StateFlow<Boolean> = _masteringLoading

    private val _masteringSuggestions = MutableStateFlow<com.example.data.gemini.MasteringSuggestions?>(null)
    val masteringSuggestions: StateFlow<com.example.data.gemini.MasteringSuggestions?> = _masteringSuggestions

    private val _masteringError = MutableStateFlow<String?>(null)
    val masteringError: StateFlow<String?> = _masteringError


    init {
        // Load default preset initially
        loadGenrePreset("Afrobeat")
    }

    // Load presets for genre
    fun loadGenrePreset(genreName: String) {
        synthesizer.loadGenrePreset(genreName)
        autoSave()
    }

    // Select custom VST Plugin instrument
    fun setVstPlugin(vstName: String) {
        synthesizer.setVst(vstName)
        autoSave()
    }

    // Toggle Offline Mode
    fun toggleOfflineMode() {
        _isOfflineMode.value = !_isOfflineMode.value
    }

    // Auto-saves current state of the sequencer grid into database
    fun autoSave() {
        viewModelScope.launch {
            val stepsDataString = ProjectEntity(
                id = _currentProjectId.value,
                name = _currentProjectName.value,
                genre = synthesizer.genre.value,
                bpm = synthesizer.tempoBpm.value,
                durationMin = synthesizer.durationMin.value,
                kickSteps = synthesizer.kickSteps.map { if (it) '1' else '0' }.joinToString(""),
                snareSteps = synthesizer.snareSteps.map { if (it) '1' else '0' }.joinToString(""),
                hihatSteps = synthesizer.hihatSteps.map { if (it) '1' else '0' }.joinToString(""),
                percussionSteps = synthesizer.percussionSteps.map { if (it) '1' else '0' }.joinToString(""),
                bassSteps = synthesizer.bassSteps.joinToString(","),
                synthSteps = synthesizer.synthSteps.joinToString(","),
                kickVolume = synthesizer.kickVolume,
                snareVolume = synthesizer.snareVolume,
                hihatVolume = synthesizer.hihatVolume,
                bassVolume = synthesizer.bassVolume,
                synthVolume = synthesizer.synthVolume,
                percussionVolume = synthesizer.percussionVolume,
                delayLevel = synthesizer.delayLevel,
                reverbLevel = synthesizer.reverbLevel,
                filterCutoff = synthesizer.filterCutoff,
                kickAutomation = synthesizer.automationCurves.value["KICK"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                snareAutomation = synthesizer.automationCurves.value["SNARE"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                hihatAutomation = synthesizer.automationCurves.value["HIHAT"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                percussionAutomation = synthesizer.automationCurves.value["PERC"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                bassAutomation = synthesizer.automationCurves.value["BASS"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                synthAutomation = synthesizer.automationCurves.value["SYNTH"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                isOfflineDraft = _isOfflineMode.value,
                isCloudSynced = !_isOfflineMode.value && _currentProjectId.value > 0
            )
            val newId = repository.saveProject(stepsDataString)
            if (_currentProjectId.value == 0L) {
                _currentProjectId.value = newId
            }
        }
    }

    // Save a brand new project
    fun createNewProject(name: String, genre: String) {
        viewModelScope.launch {
            _currentProjectName.value = name
            _currentProjectId.value = 0 // resets so Room generates new entry
            loadGenrePreset(genre)
        }
    }

    // Load an existing project
    fun loadProject(project: ProjectEntity) {
        viewModelScope.launch {
            synthesizer.stopPlayback()
            _currentProjectId.value = project.id
            _currentProjectName.value = project.name
            
            // Set Synth BPM, Genre, Duration
            synthesizer.loadGenrePreset(project.genre)
            synthesizer.setDuration(project.durationMin)
            
            // Load steps
            for (i in 0..15) {
                synthesizer.kickSteps[i] = project.kickSteps.getOrNull(i) == '1'
                synthesizer.snareSteps[i] = project.snareSteps.getOrNull(i) == '1'
                synthesizer.hihatSteps[i] = project.hihatSteps.getOrNull(i) == '1'
                synthesizer.percussionSteps[i] = project.percussionSteps.getOrNull(i) == '1'
            }
            
            val bassArray = project.bassSteps.split(",")
            val synthArray = project.synthSteps.split(",")
            for (i in 0..15) {
                synthesizer.bassSteps[i] = bassArray.getOrNull(i) ?: ""
                synthesizer.synthSteps[i] = synthArray.getOrNull(i) ?: ""
            }

            // Load mix
            synthesizer.kickVolume = project.kickVolume
            synthesizer.snareVolume = project.snareVolume
            synthesizer.hihatVolume = project.hihatVolume
            synthesizer.bassVolume = project.bassVolume
            synthesizer.synthVolume = project.synthVolume
            synthesizer.percussionVolume = project.percussionVolume

            // Load automation curves
            val kickAuto = project.kickAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val snareAuto = project.snareAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val hihatAuto = project.hihatAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val percAuto = project.percussionAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val bassAuto = project.bassAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }
            val synthAuto = project.synthAutomation.split(",").map { it.toFloatOrNull() ?: 1.0f }

            for (i in 0..15) {
                synthesizer.setAutomationValue("KICK", i, kickAuto.getOrElse(i) { 1.0f })
                synthesizer.setAutomationValue("SNARE", i, snareAuto.getOrElse(i) { 1.0f })
                synthesizer.setAutomationValue("HIHAT", i, hihatAuto.getOrElse(i) { 1.0f })
                synthesizer.setAutomationValue("PERC", i, percAuto.getOrElse(i) { 1.0f })
                synthesizer.setAutomationValue("BASS", i, bassAuto.getOrElse(i) { 1.0f })
                synthesizer.setAutomationValue("SYNTH", i, synthAuto.getOrElse(i) { 1.0f })
            }

            // Load FX
            synthesizer.delayLevel = project.delayLevel
            synthesizer.reverbLevel = project.reverbLevel
            synthesizer.filterCutoff = project.filterCutoff
        }
    }

    // Delete project
    fun deleteProject(id: Long) {
        viewModelScope.launch {
            if (_currentProjectId.value == id) {
                _currentProjectId.value = 0
                _currentProjectName.value = "New Project"
            }
            repository.deleteProject(id)
        }
    }

    // Cloud Backup Integration
    fun syncProjectToCloud(id: Long) {
        if (_isOfflineMode.value) {
            _aiStatusMessage.value = "Cannot sync in offline mode. Turn on online mode."
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _aiStatusMessage.value = "Uploading stems and project metadata to Cloud Storage..."
            val success = repository.syncProjectToCloud(id)
            _isCloudSyncing.value = false
            if (success) {
                _aiStatusMessage.value = "Project synced successfully to backup server!"
            } else {
                _aiStatusMessage.value = "Failed to sync project."
            }
        }
    }

    // Stem Export States
    private val _stemExportLoading = MutableStateFlow(false)
    val stemExportLoading: StateFlow<Boolean> = _stemExportLoading

    fun exportStems(format: String = "WAV") {
        viewModelScope.launch {
            _stemExportLoading.value = true
            _aiStatusMessage.value = "Rendering tracks as separate stems (Drums, Bass, Synths, Vocals)..."

            val currentSnapshot = ProjectEntity(
                name = _currentProjectName.value,
                genre = synthesizer.genre.value,
                bpm = synthesizer.tempoBpm.value,
                durationMin = synthesizer.durationMin.value,
                kickSteps = synthesizer.kickSteps.map { if (it) '1' else '0' }.joinToString(""),
                snareSteps = synthesizer.snareSteps.map { if (it) '1' else '0' }.joinToString(""),
                hihatSteps = synthesizer.hihatSteps.map { if (it) '1' else '0' }.joinToString(""),
                percussionSteps = synthesizer.percussionSteps.map { if (it) '1' else '0' }.joinToString(""),
                bassSteps = synthesizer.bassSteps.joinToString(","),
                synthSteps = synthesizer.synthSteps.joinToString(","),
                kickVolume = synthesizer.kickVolume,
                snareVolume = synthesizer.snareVolume,
                hihatVolume = synthesizer.hihatVolume,
                bassVolume = synthesizer.bassVolume,
                synthVolume = synthesizer.synthVolume,
                percussionVolume = synthesizer.percussionVolume,
                delayLevel = synthesizer.delayLevel,
                reverbLevel = synthesizer.reverbLevel,
                filterCutoff = synthesizer.filterCutoff,
                kickAutomation = synthesizer.automationCurves.value["KICK"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                snareAutomation = synthesizer.automationCurves.value["SNARE"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                hihatAutomation = synthesizer.automationCurves.value["HIHAT"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                percussionAutomation = synthesizer.automationCurves.value["PERC"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                bassAutomation = synthesizer.automationCurves.value["BASS"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                synthAutomation = synthesizer.automationCurves.value["SYNTH"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0"
            )

            try {
                val files = AudioExporter.exportStems(
                    context = getApplication(),
                    project = currentSnapshot,
                    format = format,
                    durationSec = 15
                )

                if (files.isNotEmpty()) {
                    files.forEach { file ->
                        val recording = RecordingEntity(
                            fileName = file.name,
                            filePath = file.absolutePath,
                            format = format,
                            durationSec = synthesizer.durationMin.value * 60,
                            tag = "Stem",
                            genre = synthesizer.genre.value
                        )
                        repository.saveRecording(recording)
                    }
                    _aiStatusMessage.value = "Stems rendered successfully! ${files.size} stems saved to directory."
                } else {
                    _aiStatusMessage.value = "Failed to render stems."
                }
            } catch (e: Exception) {
                Log.e("BeatViewModel", "Failed to export stems", e)
                _aiStatusMessage.value = "Error rendering stems: ${e.localizedMessage}"
            } finally {
                _stemExportLoading.value = false
            }
        }
    }

    // Chord Progression AI States
    private val _chordAiLoading = MutableStateFlow(false)
    val chordAiLoading: StateFlow<Boolean> = _chordAiLoading

    private val _generatedChordProgression = MutableStateFlow<com.example.data.gemini.ChordProgression?>(null)
    val generatedChordProgression: StateFlow<com.example.data.gemini.ChordProgression?> = _generatedChordProgression

    private val _chordAiError = MutableStateFlow<String?>(null)
    val chordAiError: StateFlow<String?> = _chordAiError

    fun generateChordProgression(genre: String, musicalKey: String, style: String) {
        if (_isOfflineMode.value) {
            _chordAiError.value = "Chord AI is not available in offline mode."
            return
        }
        _chordAiLoading.value = true
        _chordAiError.value = null
        _generatedChordProgression.value = null

        viewModelScope.launch {
            try {
                val progression = GeminiService.generateChordProgression(genre, musicalKey, style)
                if (progression != null) {
                    _generatedChordProgression.value = progression
                    _aiStatusMessage.value = "AI generated chord progression: ${progression.romanNumerals}"
                } else {
                    _chordAiError.value = "Failed to generate chord progression. Please verify API key."
                }
            } catch (e: Exception) {
                Log.e("BeatViewModel", "Chord AI generation failed", e)
                _chordAiError.value = "Error generating chord progression: ${e.localizedMessage}"
            } finally {
                _chordAiLoading.value = false
            }
        }
    }

    fun applyChordProgression(progression: com.example.data.gemini.ChordProgression) {
        viewModelScope.launch {
            for (i in 0..15) {
                synthesizer.bassSteps[i] = progression.bassSteps.getOrElse(i) { "-" }
                synthesizer.synthSteps[i] = progression.synthSteps.getOrElse(i) { "-" }
            }
            autoSave()
            _aiStatusMessage.value = "Applied '${progression.title}' to Bass and Synth channels!"
        }
    }

    fun clearChordAiState() {
        _generatedChordProgression.value = null
        _chordAiError.value = null
    }

    // Export Beat to standard files (.wav, .mp3, etc.)
    fun exportCurrentBeat(format: String, tag: String = "Main Mix") {
        viewModelScope.launch {
            _aiGenerationLoading.value = true
            _aiStatusMessage.value = "Rendering procedural high-fidelity stems to $format format..."
            
            // Construct a snapshot ProjectEntity
            val currentSnapshot = ProjectEntity(
                name = _currentProjectName.value,
                genre = synthesizer.genre.value,
                bpm = synthesizer.tempoBpm.value,
                durationMin = synthesizer.durationMin.value,
                kickSteps = synthesizer.kickSteps.map { if (it) '1' else '0' }.joinToString(""),
                snareSteps = synthesizer.snareSteps.map { if (it) '1' else '0' }.joinToString(""),
                hihatSteps = synthesizer.hihatSteps.map { if (it) '1' else '0' }.joinToString(""),
                percussionSteps = synthesizer.percussionSteps.map { if (it) '1' else '0' }.joinToString(""),
                bassSteps = synthesizer.bassSteps.joinToString(","),
                synthSteps = synthesizer.synthSteps.joinToString(","),
                kickVolume = synthesizer.kickVolume,
                snareVolume = synthesizer.snareVolume,
                hihatVolume = synthesizer.hihatVolume,
                bassVolume = synthesizer.bassVolume,
                synthVolume = synthesizer.synthVolume,
                percussionVolume = synthesizer.percussionVolume,
                delayLevel = synthesizer.delayLevel,
                reverbLevel = synthesizer.reverbLevel,
                filterCutoff = synthesizer.filterCutoff,
                kickAutomation = synthesizer.automationCurves.value["KICK"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                snareAutomation = synthesizer.automationCurves.value["SNARE"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                hihatAutomation = synthesizer.automationCurves.value["HIHAT"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                percussionAutomation = synthesizer.automationCurves.value["PERC"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                bassAutomation = synthesizer.automationCurves.value["BASS"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
                synthAutomation = synthesizer.automationCurves.value["SYNTH"]?.joinToString(",") ?: "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0"
            )

            // Perform file synthesis
            val renderedFile = AudioExporter.exportBeat(
                context = getApplication(),
                project = currentSnapshot,
                format = format,
                durationSec = 15 // Standard professional audio loop preview length
            )

            if (renderedFile != null && renderedFile.exists()) {
                val duration = synthesizer.durationMin.value * 60
                val recording = RecordingEntity(
                    fileName = renderedFile.name,
                    filePath = renderedFile.absolutePath,
                    format = format,
                    durationSec = duration,
                    tag = tag,
                    genre = synthesizer.genre.value
                )
                repository.saveRecording(recording)
                _aiStatusMessage.value = "Exported successfully! File saved at: ${renderedFile.name}"
            } else {
                _aiStatusMessage.value = "Export failed. Please try again."
            }
            _aiGenerationLoading.value = false
        }
    }

    // Delete Recording
    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            try {
                val file = File(recording.filePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                // Ignore file delete errors
            }
            repository.deleteRecording(recording.id)
        }
    }

    // Simulates MIDI file drag-and-drop or import templates
    fun loadMidiFilePreset(presetType: String) {
        viewModelScope.launch {
            _aiStatusMessage.value = "Importing MIDI Pattern: $presetType..."
            when (presetType) {
                "Syncopated Afro-Groove" -> {
                    synthesizer.kickSteps[0] = true; synthesizer.kickSteps[8] = true
                    synthesizer.snareSteps[4] = true; synthesizer.snareSteps[12] = true
                    for (i in listOf(2, 6, 10, 14)) synthesizer.hihatSteps[i] = true
                    synthesizer.bassSteps[0] = "D2"; synthesizer.bassSteps[8] = "A2"
                }
                "Trap Double-Time Roll" -> {
                    synthesizer.kickSteps[0] = true; synthesizer.kickSteps[11] = true
                    synthesizer.snareSteps[8] = true
                    for (i in 0..15 step 2) synthesizer.hihatSteps[i] = true
                    synthesizer.bassSteps[0] = "C1"; synthesizer.bassSteps[11] = "F1"
                }
                "R&B Smooth Chord Glide" -> {
                    synthesizer.kickSteps[0] = true
                    synthesizer.snareSteps[12] = true
                    synthesizer.synthSteps[0] = "F3"; synthesizer.synthSteps[8] = "Bb3"
                }
            }
            _aiStatusMessage.value = "MIDI Imported successfully! Custom MIDI grid configured."
            autoSave()
        }
    }

    // Gemini AI-powered Beat Generator
    fun generateAIBeat(prompt: String) {
        if (_isOfflineMode.value) {
            _aiStatusMessage.value = "AI generation is not available in offline mode."
            return
        }
        viewModelScope.launch {
            _aiGenerationLoading.value = true
            _aiStatusMessage.value = "Connecting to Gemini AI Beat Designer..."
            try {
                val preset = GeminiService.generateBeatPreset(prompt)
                if (preset != null) {
                    synthesizer.stopPlayback()
                    _currentProjectName.value = preset.name
                    
                    // Set BPM, Genre
                    val cleanGenre = when (preset.genre) {
                        "Afrobeat", "Afro Fuji", "Afro Blues", "Afro Pop", "Modern R&B", "Pop", "Hip-Hop", "Drill", "Trap Rap" -> preset.genre
                        else -> "Afrobeat"
                    }
                    synthesizer.loadGenrePreset(cleanGenre)
                    synthesizer.setDuration(3) // Default to 3 minutes
                    
                    // Apply AI BPM
                    // Make sure it matches within standard bounds
                    val bpm = preset.bpm.coerceIn(60, 160)
                    // Set steps
                    for (i in 0..15) {
                        synthesizer.kickSteps[i] = preset.kick.getOrNull(i) == '1'
                        synthesizer.snareSteps[i] = preset.snare.getOrNull(i) == '1'
                        synthesizer.hihatSteps[i] = preset.hihat.getOrNull(i) == '1'
                        synthesizer.percussionSteps[i] = preset.percussion.getOrNull(i) == '1'
                    }
                    
                    val bassArray = preset.bass.split(",")
                    val synthArray = preset.synth.split(",")
                    for (i in 0..15) {
                        synthesizer.bassSteps[i] = bassArray.getOrNull(i) ?: ""
                        synthesizer.synthSteps[i] = synthArray.getOrNull(i) ?: ""
                    }

                    _aiStatusMessage.value = "AI Beat '${preset.name}' generated! ${preset.description}"
                    autoSave()
                } else {
                    _aiStatusMessage.value = "API key missing or request failed. Loaded standard preset instead."
                    loadGenrePreset("Afrobeat")
                }
            } catch (e: Exception) {
                Log.e("BeatViewModel", "Failed to generate AI Beat", e)
                _aiStatusMessage.value = "Error connecting to AI Server. Loaded standard preset instead."
                loadGenrePreset("Afrobeat")
            } finally {
                _aiGenerationLoading.value = false
            }
        }
    }

    // Randomize sequencer grid for rapid sound discovery
    fun randomizeGrid() {
        for (i in 0..15) {
            synthesizer.kickSteps[i] = Math.random() > 0.6
            synthesizer.snareSteps[i] = Math.random() > 0.7
            synthesizer.hihatSteps[i] = Math.random() > 0.4
            synthesizer.percussionSteps[i] = Math.random() > 0.8
        }
        autoSave()
    }

    // Quantize active MIDI notes to resolution
    fun quantizeMidiNotes(channel: String, resolution: String, strength: Int) {
        synthesizer.quantize(channel, resolution, strength)
        _aiStatusMessage.value = "Quantized $channel track to $resolution at $strength% strength."
        autoSave()
    }

    // Run AI Mastering Analysis on the current mix
    fun runMasteringAnalysis(targetGenre: String) {
        if (_isOfflineMode.value) {
            _masteringError.value = "Mastering Assistant is not available in offline mode."
            return
        }
        _masteringLoading.value = true
        _masteringError.value = null
        _masteringSuggestions.value = null

        viewModelScope.launch {
            try {
                // Prepare current state snapshot for analysis
                val eqLevels = mapOf(
                    "KICK" to Triple(synthesizer.trackEqs["KICK"]?.lowBand?.gainDb ?: 0f, synthesizer.trackEqs["KICK"]?.midBand?.gainDb ?: 0f, synthesizer.trackEqs["KICK"]?.highBand?.gainDb ?: 0f),
                    "SNARE" to Triple(synthesizer.trackEqs["SNARE"]?.lowBand?.gainDb ?: 0f, synthesizer.trackEqs["SNARE"]?.midBand?.gainDb ?: 0f, synthesizer.trackEqs["SNARE"]?.highBand?.gainDb ?: 0f),
                    "HIHAT" to Triple(synthesizer.trackEqs["HIHAT"]?.lowBand?.gainDb ?: 0f, synthesizer.trackEqs["HIHAT"]?.midBand?.gainDb ?: 0f, synthesizer.trackEqs["HIHAT"]?.highBand?.gainDb ?: 0f),
                    "PERC" to Triple(synthesizer.trackEqs["PERC"]?.lowBand?.gainDb ?: 0f, synthesizer.trackEqs["PERC"]?.midBand?.gainDb ?: 0f, synthesizer.trackEqs["PERC"]?.highBand?.gainDb ?: 0f),
                    "BASS" to Triple(synthesizer.trackEqs["BASS"]?.lowBand?.gainDb ?: 0f, synthesizer.trackEqs["BASS"]?.midBand?.gainDb ?: 0f, synthesizer.trackEqs["BASS"]?.highBand?.gainDb ?: 0f),
                    "SYNTH" to Triple(synthesizer.trackEqs["SYNTH"]?.lowBand?.gainDb ?: 0f, synthesizer.trackEqs["SYNTH"]?.midBand?.gainDb ?: 0f, synthesizer.trackEqs["SYNTH"]?.highBand?.gainDb ?: 0f)
                )

                val suggestions = com.example.data.gemini.GeminiService.analyzeMix(
                    genre = targetGenre,
                    bpm = synthesizer.tempoBpm.value,
                    kickVolume = synthesizer.kickVolume,
                    snareVolume = synthesizer.snareVolume,
                    hihatVolume = synthesizer.hihatVolume,
                    percVolume = synthesizer.percussionVolume,
                    bassVolume = synthesizer.bassVolume,
                    synthVolume = synthesizer.synthVolume,
                    delayLevel = synthesizer.delayLevel,
                    reverbLevel = synthesizer.reverbLevel,
                    filterCutoff = synthesizer.filterCutoff,
                    masteringPreset = synthesizer.masteringPreset.value,
                    stereoWidth = synthesizer.stereoWidth.value,
                    loudnessNormalizer = synthesizer.loudnessNormalizer.value,
                    kickStepsCount = synthesizer.kickSteps.count { it },
                    snareStepsCount = synthesizer.snareSteps.count { it },
                    hihatStepsCount = synthesizer.hihatSteps.count { it },
                    percStepsCount = synthesizer.percussionSteps.count { it },
                    bassStepsCount = synthesizer.bassSteps.count { it.isNotEmpty() && it != "-" },
                    synthStepsCount = synthesizer.synthSteps.count { it.isNotEmpty() && it != "-" },
                    eqLevels = eqLevels
                )

                if (suggestions != null) {
                    _masteringSuggestions.value = suggestions
                } else {
                    _masteringError.value = "Gemini API failed to return suggestions. Please ensure API key is configured."
                }
            } catch (e: Exception) {
                Log.e("BeatViewModel", "Mastering Analysis Failed", e)
                _masteringError.value = "Failed to communicate with the Mastering Assistant: ${e.localizedMessage}"
            } finally {
                _masteringLoading.value = false
            }
        }
    }

    // Apply suggestions to the audio engine and save to database
    fun applyMasteringSuggestions(suggestions: com.example.data.gemini.MasteringSuggestions) {
        viewModelScope.launch {
            val adj = suggestions.adjustments
            
            // 1. Volumes
            synthesizer.kickVolume = adj.kickVolume
            synthesizer.snareVolume = adj.snareVolume
            synthesizer.hihatVolume = adj.hihatVolume
            synthesizer.percussionVolume = adj.percVolume
            synthesizer.bassVolume = adj.bassVolume
            synthesizer.synthVolume = adj.synthVolume
            
            // 2. Mastering Preset & Stereo Width & Limiter
            synthesizer.setMasteringPreset(adj.masteringPreset)
            synthesizer.setStereoWidth(adj.stereoWidth)
            synthesizer.setLoudnessNormalizer(adj.loudnessNormalizer)

            // 3. Channel EQs
            adj.eqAdjustments.forEach { eqAdj ->
                val trackEq = synthesizer.trackEqs[eqAdj.track]
                if (trackEq != null) {
                    trackEq.lowBand.update(trackEq.lowBand.f0, eqAdj.lowGain, trackEq.lowBand.q)
                    trackEq.midBand.update(trackEq.midBand.f0, eqAdj.midGain, trackEq.midBand.q)
                    trackEq.highBand.update(trackEq.highBand.f0, eqAdj.highGain, trackEq.highBand.q)
                }
            }

            // Trigger a recomposition and save state
            autoSave()
            _aiStatusMessage.value = "AI Mastering Suggestions applied! EQ curves and channel volumes optimized."
        }
    }

    // Clear mastering state
    fun clearMasteringState() {
        _masteringSuggestions.value = null
        _masteringError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        synthesizer.stopPlayback()
    }
}
