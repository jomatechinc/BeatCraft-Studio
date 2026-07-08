package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val format: String, // WAV, MP3, FLAC, AIFF
    val durationSec: Int,
    val tag: String, // e.g. "Main Mix", "Draft Loop", "Vibe Out"
    val genre: String,
    val createdAt: Long = System.currentTimeMillis()
)
