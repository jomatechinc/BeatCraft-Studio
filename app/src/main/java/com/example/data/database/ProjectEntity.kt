package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val genre: String,
    val bpm: Int,
    val durationMin: Int,
    // Sequencer steps (comma separated or binary strings)
    val kickSteps: String = "1000100010001000",
    val snareSteps: String = "0000100000001000",
    val hihatSteps: String = "1010101010101010",
    val percussionSteps: String = "0000000010000000",
    val bassSteps: String = "C2,-,-,-,E2,-,-,-,G2,-,-,-,C2,-,-,-",
    val synthSteps: String = "-,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-",
    // Mix and Effects
    val kickVolume: Float = 0.8f,
    val snareVolume: Float = 0.7f,
    val hihatVolume: Float = 0.6f,
    val bassVolume: Float = 0.7f,
    val synthVolume: Float = 0.5f,
    val percussionVolume: Float = 0.5f,
    val delayLevel: Float = 0.2f,
    val reverbLevel: Float = 0.3f,
    val filterCutoff: Float = 1.0f, // 0.0 to 1.0
    // Automation Curves
    val kickAutomation: String = "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
    val snareAutomation: String = "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
    val hihatAutomation: String = "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
    val percussionAutomation: String = "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
    val bassAutomation: String = "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
    val synthAutomation: String = "1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0",
    // State
    val isOfflineDraft: Boolean = true,
    val isCloudSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
