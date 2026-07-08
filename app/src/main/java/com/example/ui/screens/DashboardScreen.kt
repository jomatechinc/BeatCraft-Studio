package com.example.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ProjectEntity
import com.example.data.database.RecordingEntity
import com.example.ui.theme.*
import com.example.viewmodel.BeatViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: BeatViewModel, modifier: Modifier = Modifier) {
    val projects by viewModel.savedProjects.collectAsState()
    val recordings by viewModel.exportedRecordings.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf("ALL") }
    var sortBy by remember { mutableStateOf("DATE") } // DATE, GENRE, NAME

    // Media Player state for previewing exported files
    var activePlayingFilePath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current

    // Properly release mediaPlayer when leaving
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    fun playRecording(filePath: String) {
        try {
            if (activePlayingFilePath == filePath) {
                // Toggle pause/play
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                } else {
                    mediaPlayer?.start()
                }
            } else {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                }
                activePlayingFilePath = filePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // --- 1. Offline Mode and Cloud Backup Alert banner ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isOffline) NeonMagenta.copy(alpha = 0.3f) else NeonCyan.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOffline) NeonMagenta.copy(alpha = 0.12f) else ElectricViolet.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = if (isOffline) NeonMagenta else NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOffline) "OFFLINE STORAGE MODE" else "CLOUD AUTO-SYNC ENABLED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isOffline) NeonMagenta else NeonCyan
                        )
                        Text(
                            text = if (isOffline) "Drafts are locked locally. Sync queue will resume on connection." else "All projects and exported wavs are securely back-upped.",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                    Switch(
                        checked = !isOffline,
                        onCheckedChange = { viewModel.toggleOfflineMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = CosmicSurface
                        ),
                        modifier = Modifier.testTag("offline_mode_toggle")
                    )
                }
            }
        }

        // --- 2. Filter, Search, and Sort Panel ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_recordings_input"),
                    placeholder = { Text("Search by name or genre...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CosmicSurfaceVariant,
                        focusedContainerColor = CosmicSurface,
                        unfocusedContainerColor = CosmicSurface
                    ),
                    singleLine = true
                )

                // Tag Quick Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tags = listOf("ALL", "Main Mix", "Draft Loop", "Vibe Out")
                    tags.forEach { tag ->
                        val selected = selectedTagFilter == tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NeonCyan else CosmicSurface)
                                .clickable { selectedTagFilter = tag }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.Black else TextSecondary
                            )
                        }
                    }
                }

                // Sorting Toggle Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("STUDIO ARCHIVES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortChip("Date", sortBy == "DATE") { sortBy = "DATE" }
                        SortChip("Genre", sortBy == "GENRE") { sortBy = "GENRE" }
                        SortChip("Name", sortBy == "NAME") { sortBy = "NAME" }
                    }
                }
            }
        }

        // --- 3. Saved Project Drafts Section ---
        item {
            Text("PROJECT DRAFTS (AUTO-SAVED)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
        }

        if (projects.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No local project drafts found.", fontSize = 12.sp, color = TextMuted)
                }
            }
        } else {
            val filteredProjects = projects.filter {
                it.name.contains(searchQuery, true) || it.genre.contains(searchQuery, true)
            }.sortedWith { a, b ->
                when (sortBy) {
                    "GENRE" -> a.genre.compareTo(b.genre)
                    "NAME" -> a.name.compareTo(b.name)
                    else -> b.updatedAt.compareTo(a.updatedAt)
                }
            }

            items(filteredProjects) { project ->
                val isActive = project.id == currentProjectId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(12.dp))
                        .testTag("project_card_${project.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = project.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isActive) NeonCyan else TextPrimary
                                    )
                                    if (isActive) {
                                        Badge(containerColor = NeonCyan.copy(alpha = 0.2f)) {
                                            Text("ACTIVE", color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                                        }
                                    }
                                }
                                Text(
                                    text = "${project.genre.uppercase()} • ${project.bpm} BPM • ${project.durationMin} mins",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Load Button
                                IconButton(onClick = { viewModel.loadProject(project) }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Load", tint = NeonCyan)
                                }
                                // Cloud Backup Button
                                IconButton(
                                    onClick = { viewModel.syncProjectToCloud(project.id) },
                                    enabled = !isCloudSyncing
                                ) {
                                    Icon(
                                        imageVector = if (project.isCloudSynced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                        contentDescription = "Cloud backup",
                                        tint = if (project.isCloudSynced) NeonAmber else TextSecondary
                                    )
                                }
                                // Delete Button
                                IconButton(onClick = { viewModel.deleteProject(project.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonMagenta)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Exported Master Recordings Section ---
        item {
            Text("EXPORTED MASTERS & LOOPS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonMagenta)
        }

        val filteredRecordings = recordings.filter {
            (selectedTagFilter == "ALL" || it.tag == selectedTagFilter) &&
            (it.fileName.contains(searchQuery, true) || it.genre.contains(searchQuery, true))
        }.sortedWith { a, b ->
            when (sortBy) {
                "GENRE" -> a.genre.compareTo(b.genre)
                "NAME" -> a.fileName.compareTo(b.fileName)
                else -> b.createdAt.compareTo(a.createdAt)
            }
        }

        if (filteredRecordings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                        Text("No exported master stems yet.", fontSize = 12.sp, color = TextMuted)
                        Text("Finish your beat in Studio and click one of the export buttons (WAV, MP3, FLAC, AIFF) to listen here.", fontSize = 10.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        } else {
            items(filteredRecordings) { recording ->
                val isPlayingThis = activePlayingFilePath == recording.filePath && mediaPlayer?.isPlaying == true
                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(recording.createdAt))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(12.dp))
                        .testTag("recording_card_${recording.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Play/Pause trigger
                                IconButton(
                                    onClick = { playRecording(recording.filePath) },
                                    modifier = Modifier
                                        .background(if (isPlayingThis) NeonMagenta else CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                                        .size(44.dp)
                                        .testTag("play_recording_button_${recording.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Audition",
                                        tint = if (isPlayingThis) Color.Black else NeonCyan
                                    )
                                }

                                Column {
                                    Text(
                                        text = recording.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Badge(containerColor = CosmicSurfaceVariant) {
                                            Text(recording.format, color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                                        }
                                        Badge(containerColor = CosmicSurfaceVariant) {
                                            Text(recording.tag, color = NeonMagenta, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                                        }
                                        Text(
                                            text = "$dateStr",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }

                            // Info and Delete actions
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${recording.durationSec}s",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                IconButton(onClick = { viewModel.deleteRecording(recording) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete File", tint = NeonMagenta)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CosmicSurfaceVariant else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) NeonCyan else TextMuted
        )
    }
}
