package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.*
import com.example.ui.theme.*
import com.example.viewmodel.BeatViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(viewModel: BeatViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Viewmodel States
    val isPlaying by viewModel.synthesizer.isPlaying.collectAsState()
    val fftData by viewModel.synthesizer.fftData.collectAsState()
    val currentStep by viewModel.synthesizer.currentStep.collectAsState()
    val activeGenre by viewModel.synthesizer.genre.collectAsState()
    val baseBpm by viewModel.synthesizer.tempoBpm.collectAsState()
    val effectiveBpm by viewModel.synthesizer.effectiveBpm.collectAsState()
    val durationMin by viewModel.synthesizer.durationMin.collectAsState()
    val timeRemaining by viewModel.synthesizer.timeRemainingSec.collectAsState()
    val activeVst by viewModel.synthesizer.activeVst.collectAsState()

    val aiLoading by viewModel.aiGenerationLoading.collectAsState()
    val aiStatusMsg by viewModel.aiStatusMessage.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()

    val swingLevel by viewModel.synthesizer.swingLevel.collectAsState()
    val humanizeLevel by viewModel.synthesizer.humanizeLevel.collectAsState()

    // Mute, Solo, Metronome, Mastering, Automation states
    val trackMutes by viewModel.synthesizer.trackMutesState.collectAsState()
    val trackSolos by viewModel.synthesizer.trackSolosState.collectAsState()
    val metronomeEnabled by viewModel.synthesizer.metronomeEnabled.collectAsState()
    val metronomeVolume by viewModel.synthesizer.metronomeVolume.collectAsState()
    
    val lfoTarget by viewModel.synthesizer.lfoTarget.collectAsState()
    val lfoWaveform by viewModel.synthesizer.lfoWaveform.collectAsState()
    val lfoRate by viewModel.synthesizer.lfoRate.collectAsState()
    val lfoDepth by viewModel.synthesizer.lfoDepth.collectAsState()

    val stereoWidth by viewModel.synthesizer.stereoWidth.collectAsState()
    val masteringPreset by viewModel.synthesizer.masteringPreset.collectAsState()
    val loudnessNormalizer by viewModel.synthesizer.loudnessNormalizer.collectAsState()
    val automationCurves by viewModel.synthesizer.automationCurves.collectAsState()

    // AI Mastering Assistant States
    val masteringLoading by viewModel.masteringLoading.collectAsState()
    val masteringSuggestions by viewModel.masteringSuggestions.collectAsState()
    val masteringError by viewModel.masteringError.collectAsState()

    // Local UI States
    var masteringTargetGenre by remember(activeGenre) { mutableStateOf(activeGenre) }
    var showMasteringGenreMenu by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf("") }

    var selectedChannelText by remember { mutableStateOf("KICK") }
    var showPresetDrawer by remember { mutableStateOf(false) }
    var quantizeResolution by remember { mutableStateOf("1/8") }
    var quantizeStrength by remember { mutableStateOf(100f) }
    var quantizeTargetAll by remember { mutableStateOf(true) }

    var selectedEqTrack by remember { mutableStateOf("KICK") }
    var eqUpdateTrigger by remember { mutableStateOf(0) }

    // List of channel options
    val channels = listOf("KICK", "SNARE", "HIHAT", "PERC", "BASS", "SYNTH")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // --- 1. AI Beat Architect prompt input ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .testTag("ai_generator_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "AI BEAT ARCHITECT",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                        if (isOffline) {
                            Spacer(modifier = Modifier.weight(1f))
                            Badge(containerColor = NeonMagenta.copy(alpha = 0.2f)) {
                                Text("OFFLINE MODE", color = NeonMagenta, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }

                    Text(
                        text = "Describe your style or aesthetic (tempo, vibes, genre). Gemini will synthesize a custom pattern & tempo.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_prompt_input"),
                        placeholder = { Text("e.g., Upbeat Summer Afrobeat with syncopated logs and minor sub bass", fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CosmicSurfaceVariant,
                            focusedContainerColor = CosmicBackground,
                            unfocusedContainerColor = CosmicBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !aiLoading && !isOffline
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (aiLoading) aiStatusMsg else "Status: Ready",
                            fontSize = 11.sp,
                            color = if (aiLoading) NeonCyan else TextMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Button(
                            onClick = {
                                if (promptText.isNotEmpty()) {
                                    viewModel.generateAIBeat(promptText)
                                }
                            },
                            enabled = !aiLoading && promptText.isNotEmpty() && !isOffline,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("ai_generate_button")
                        ) {
                            if (aiLoading) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("GENERATE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Live BPM Meter and Waveform Visualizer ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "NOW PLAYING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonMagenta,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = activeGenre.uppercase() + " BEAT",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }

                        // BPM Dial display
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "ACTIVE BPM",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$effectiveBpm",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (effectiveBpm < baseBpm) NeonAmber else NeonCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Simulated Real-Time Audio Waveform
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        ElectricViolet.copy(alpha = 0.25f),
                                        CosmicBackground
                                    )
                                )
                            )
                            .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(24.dp))
                            .padding(top = 16.dp, bottom = 10.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                val bars = 32
                                val gap = 3.dp.toPx()
                                val barWidth = (size.width - (bars - 1) * gap) / bars
                                val isPlayingNow = isPlaying

                                for (i in 0 until bars) {
                                    // Use actual real-time FFT frequency amplitude or a subtle resting state
                                    val multiplier = if (isPlayingNow && i < fftData.size) {
                                        (fftData[i] * 1.3f).coerceIn(0.08f, 1.0f)
                                    } else {
                                        0.08f
                                    }
                                    val barHeight = size.height * multiplier
                                    val x = i * (barWidth + gap)
                                    val y = (size.height - barHeight) / 2f

                                    val color = if (i == currentStep * 2) NeonMagenta else NeonCyan
                                    drawRoundRect(
                                        color = color.copy(alpha = if (isPlayingNow) 1.0f else 0.4f),
                                        topLeft = Offset(x, y),
                                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Metadata status tags matching the Immersive UI Design HTML
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "44.1 kHz / 24-bit",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(TextMuted, CircleShape)
                                )
                                Text(
                                    text = if (isOffline) "Offline Draft" else "Cloud Synced",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 10.sp,
                                    color = if (isOffline) NeonMagenta.copy(alpha = 0.8f) else NeonCyan.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Playback Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.randomizeGrid() },
                            modifier = Modifier
                                .background(CosmicSurfaceVariant, CircleShape)
                                .size(48.dp)
                                .testTag("random_pattern_button")
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Randomize", tint = TextPrimary)
                        }

                        IconButton(
                            onClick = { viewModel.synthesizer.togglePlayback() },
                            modifier = Modifier
                                .background(if (isPlaying) NeonMagenta else NeonCyan, CircleShape)
                                .size(64.dp)
                                .testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { showPresetDrawer = true },
                            modifier = Modifier
                                .background(CosmicSurfaceVariant, CircleShape)
                                .size(48.dp)
                                .testTag("midi_preset_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = "MIDI presets", tint = TextPrimary)
                        }
                    }
                }
            }
        }

        // --- 3. Step Sequencer Sequencer Grid ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SEQUENCER MATRIX",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )

                        // Channel select tabs
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(channels) { _, channel ->
                                val selected = selectedChannelText == channel
                                val color by animateColorAsState(if (selected) NeonCyan else CosmicSurfaceVariant)
                                val textColor by animateColorAsState(if (selected) Color.Black else TextSecondary)

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color)
                                        .clickable { selectedChannelText = channel }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("channel_tab_$channel")
                                ) {
                                    Text(
                                        text = channel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }

                    // The 16 Step grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Selected Channel: $selectedChannelText Pattern",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        // Display the steps! We arrange them as 4 blocks of 4 steps for optimal visual scanning
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (block in 0 until 4) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (stepOffset in 0 until 4) {
                                        val step = block * 4 + stepOffset
                                        val isCurrent = step == currentStep && isPlaying

                                        // Check if step is active in active channel
                                        val isActive = when (selectedChannelText) {
                                            "KICK" -> viewModel.synthesizer.kickSteps[step]
                                            "SNARE" -> viewModel.synthesizer.snareSteps[step]
                                            "HIHAT" -> viewModel.synthesizer.hihatSteps[step]
                                            "PERC" -> viewModel.synthesizer.percussionSteps[step]
                                            "BASS" -> viewModel.synthesizer.bassSteps[step].isNotEmpty() && viewModel.synthesizer.bassSteps[step] != "-"
                                            "SYNTH" -> viewModel.synthesizer.synthSteps[step].isNotEmpty() && viewModel.synthesizer.synthSteps[step] != "-"
                                            else -> false
                                        }

                                        val padColor = when {
                                            isCurrent && isActive -> NeonMagenta
                                            isCurrent -> TextMuted
                                            isActive -> NeonCyan
                                            else -> CosmicSurfaceVariant
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(padColor)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isCurrent) NeonCyan else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    // Toggle step
                                                    when (selectedChannelText) {
                                                        "KICK" -> viewModel.synthesizer.kickSteps[step] = !viewModel.synthesizer.kickSteps[step]
                                                        "SNARE" -> viewModel.synthesizer.snareSteps[step] = !viewModel.synthesizer.snareSteps[step]
                                                        "HIHAT" -> viewModel.synthesizer.hihatSteps[step] = !viewModel.synthesizer.hihatSteps[step]
                                                        "PERC" -> viewModel.synthesizer.percussionSteps[step] = !viewModel.synthesizer.percussionSteps[step]
                                                        "BASS" -> {
                                                            viewModel.synthesizer.bassSteps[step] = if (viewModel.synthesizer.bassSteps[step].isEmpty() || viewModel.synthesizer.bassSteps[step] == "-") "C2" else "-"
                                                        }
                                                        "SYNTH" -> {
                                                            viewModel.synthesizer.synthSteps[step] = if (viewModel.synthesizer.synthSteps[step].isEmpty() || viewModel.synthesizer.synthSteps[step] == "-") "C4" else "-"
                                                        }
                                                    }
                                                    viewModel.autoSave()
                                                }
                                                .testTag("sequencer_step_$step"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Show note info on melodic channels
                                            if (selectedChannelText == "BASS" && isActive) {
                                                Text(viewModel.synthesizer.bassSteps[step], fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                            } else if (selectedChannelText == "SYNTH" && isActive) {
                                                Text(viewModel.synthesizer.synthSteps[step], fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("${step + 1}", fontSize = 10.sp, color = if (isActive || isCurrent) Color.Black else TextSecondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Pitch Roll for Melodic Channels
                        if (selectedChannelText == "BASS" || selectedChannelText == "SYNTH") {
                            Divider(color = CosmicSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                            Text("Note Pitch Selector (Tap to set values)", fontSize = 11.sp, color = TextSecondary)
                            val notes = if (selectedChannelText == "BASS") {
                                listOf("C1", "D1", "E1", "F1", "G1", "A1", "B1", "C2", "E2", "G2")
                            } else {
                                listOf("C3", "E3", "G3", "A3", "C4", "Eb4", "E4", "G4", "A4", "C5")
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(notes) { _, note ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CosmicSurfaceVariant)
                                            .clickable {
                                                // Apply note to the first empty or active slot
                                                val emptyIndex = if (selectedChannelText == "BASS") {
                                                    viewModel.synthesizer.bassSteps.indexOfFirst { it == "" || it == "-" }.coerceAtLeast(0)
                                                } else {
                                                    viewModel.synthesizer.synthSteps.indexOfFirst { it == "" || it == "-" }.coerceAtLeast(0)
                                                }
                                                if (selectedChannelText == "BASS") {
                                                    viewModel.synthesizer.bassSteps[emptyIndex] = note
                                                } else {
                                                    viewModel.synthesizer.synthSteps[emptyIndex] = note
                                                }
                                                viewModel.autoSave()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(note, fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- MIDI Quantization & Groove Control Card ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("midi_quantize_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with Sparkles / Settings icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Groove Control",
                            tint = NeonMagenta,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "MIDI QUANTIZATION & GROOVE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    // Grid Resolution Selection (1/4, 1/8, 1/16)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Grid Resolution",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val resolutions = listOf("1/4", "1/8", "1/16")
                            resolutions.forEach { res ->
                                val selected = quantizeResolution == res
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) NeonMagenta else CosmicSurfaceVariant)
                                        .clickable { quantizeResolution = res }
                                        .padding(vertical = 10.dp)
                                        .testTag("quantize_res_$res"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when(res) {
                                            "1/4" -> "1/4 Note (Beat)"
                                            "1/8" -> "1/8 Note"
                                            "1/16" -> "1/16 Note"
                                            else -> res
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (selected) Color.Black else TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Quantization Strength Slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Quantization Strength", fontSize = 11.sp, color = TextSecondary)
                            Text("${quantizeStrength.toInt()}%", fontSize = 11.sp, color = NeonMagenta, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = quantizeStrength,
                            onValueChange = { quantizeStrength = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = NeonMagenta, activeTrackColor = NeonMagenta, inactiveTrackColor = CosmicSurfaceVariant)
                        )
                    }

                    // Target Selector (Current Track vs All Tracks)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Target Tracks", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (quantizeTargetAll) "Quantize entire pattern" else "Quantize '$selectedChannelText' only",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { quantizeTargetAll = false },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!quantizeTargetAll) NeonCyan else CosmicSurfaceVariant,
                                    contentColor = if (!quantizeTargetAll) Color.Black else TextSecondary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Selected", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { quantizeTargetAll = true },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (quantizeTargetAll) NeonCyan else CosmicSurfaceVariant,
                                    contentColor = if (quantizeTargetAll) Color.Black else TextSecondary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("All Tracks", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Apply Quantization Button
                    Button(
                        onClick = {
                            val target = if (quantizeTargetAll) "ALL" else selectedChannelText
                            viewModel.quantizeMidiNotes(
                                channel = target,
                                resolution = quantizeResolution,
                                strength = quantizeStrength.toInt()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apply_quantize_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Text("APPLY SNAP-TO-GRID", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Divider(color = CosmicSurfaceVariant, thickness = 1.dp)

                    // --- Advanced Groove Controls: MPC Swing & Humanize ---
                    Text(
                        text = "ADVANCED GROOVE FEEL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    // MPC Swing Level Slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("MPC Swing Grid Offset", fontSize = 11.sp, color = TextSecondary)
                                Text("Delays even-numbered sixteenth notes for MPC pocket", fontSize = 9.sp, color = TextMuted)
                            }
                            Text(
                                text = if (swingLevel == 50) "50% (Straight)" else "$swingLevel% (Swing)",
                                fontSize = 11.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = swingLevel.toFloat(),
                            onValueChange = { viewModel.synthesizer.setSwing(it.toInt()); viewModel.autoSave() },
                            valueRange = 50f..75f,
                            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan, inactiveTrackColor = CosmicSurfaceVariant)
                        )
                    }

                    // Humanize Loose Timing Slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Humanize Loose Timing", fontSize = 11.sp, color = TextSecondary)
                                Text("Adds organic micro-timing delay & velocity variations", fontSize = 9.sp, color = TextMuted)
                            }
                            Text(
                                text = if (humanizeLevel == 0) "0% (Mechanical)" else "$humanizeLevel% (Human Feel)",
                                fontSize = 11.sp,
                                color = NeonAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = humanizeLevel.toFloat(),
                            onValueChange = { viewModel.synthesizer.setHumanize(it.toInt()); viewModel.autoSave() },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber, inactiveTrackColor = CosmicSurfaceVariant)
                        )
                    }
                }
            }
        }

        // --- 4. Custom VST Rack & Mixer Panel ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "VIRTUAL INSTRUMENT VST RACK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    // VST Plugins Select
                    val vsts = listOf("Afro-Synth VST", "Classic 808", "Fuji Bell Generator")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vsts.forEach { vst ->
                            val active = activeVst == vst
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) ElectricViolet else CosmicSurfaceVariant)
                                    .clickable { viewModel.setVstPlugin(vst) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = vst,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Divider(color = CosmicSurfaceVariant)

                    // Mixer controls (Faders)
                    Text(
                        text = "MIXER LEVELS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MixerSlider(
                            label = "Kick Vol",
                            trackKey = "KICK",
                            value = viewModel.synthesizer.kickVolume,
                            isMuted = trackMutes["KICK"] ?: false,
                            isSoloed = trackSolos["KICK"] ?: false,
                            onMuteToggle = { viewModel.synthesizer.toggleMute("KICK") },
                            onSoloToggle = { viewModel.synthesizer.toggleSolo("KICK") },
                            onValueChange = { viewModel.synthesizer.kickVolume = it; viewModel.autoSave() },
                            automationCurve = automationCurves["KICK"] ?: FloatArray(16) { 1.0f },
                            currentStep = currentStep,
                            onAutomationValueChange = { step, valFloat ->
                                viewModel.synthesizer.setAutomationValue("KICK", step, valFloat)
                            }
                        )
                        MixerSlider(
                            label = "Snare Vol",
                            trackKey = "SNARE",
                            value = viewModel.synthesizer.snareVolume,
                            isMuted = trackMutes["SNARE"] ?: false,
                            isSoloed = trackSolos["SNARE"] ?: false,
                            onMuteToggle = { viewModel.synthesizer.toggleMute("SNARE") },
                            onSoloToggle = { viewModel.synthesizer.toggleSolo("SNARE") },
                            onValueChange = { viewModel.synthesizer.snareVolume = it; viewModel.autoSave() },
                            automationCurve = automationCurves["SNARE"] ?: FloatArray(16) { 1.0f },
                            currentStep = currentStep,
                            onAutomationValueChange = { step, valFloat ->
                                viewModel.synthesizer.setAutomationValue("SNARE", step, valFloat)
                            }
                        )
                        MixerSlider(
                            label = "Hihat Vol",
                            trackKey = "HIHAT",
                            value = viewModel.synthesizer.hihatVolume,
                            isMuted = trackMutes["HIHAT"] ?: false,
                            isSoloed = trackSolos["HIHAT"] ?: false,
                            onMuteToggle = { viewModel.synthesizer.toggleMute("HIHAT") },
                            onSoloToggle = { viewModel.synthesizer.toggleSolo("HIHAT") },
                            onValueChange = { viewModel.synthesizer.hihatVolume = it; viewModel.autoSave() },
                            automationCurve = automationCurves["HIHAT"] ?: FloatArray(16) { 1.0f },
                            currentStep = currentStep,
                            onAutomationValueChange = { step, valFloat ->
                                viewModel.synthesizer.setAutomationValue("HIHAT", step, valFloat)
                            }
                        )
                        MixerSlider(
                            label = "Perc Vol",
                            trackKey = "PERC",
                            value = viewModel.synthesizer.percussionVolume,
                            isMuted = trackMutes["PERC"] ?: false,
                            isSoloed = trackSolos["PERC"] ?: false,
                            onMuteToggle = { viewModel.synthesizer.toggleMute("PERC") },
                            onSoloToggle = { viewModel.synthesizer.toggleSolo("PERC") },
                            onValueChange = { viewModel.synthesizer.percussionVolume = it; viewModel.autoSave() },
                            automationCurve = automationCurves["PERC"] ?: FloatArray(16) { 1.0f },
                            currentStep = currentStep,
                            onAutomationValueChange = { step, valFloat ->
                                viewModel.synthesizer.setAutomationValue("PERC", step, valFloat)
                            }
                        )
                        MixerSlider(
                            label = "Bass Vol",
                            trackKey = "BASS",
                            value = viewModel.synthesizer.bassVolume,
                            isMuted = trackMutes["BASS"] ?: false,
                            isSoloed = trackSolos["BASS"] ?: false,
                            onMuteToggle = { viewModel.synthesizer.toggleMute("BASS") },
                            onSoloToggle = { viewModel.synthesizer.toggleSolo("BASS") },
                            onValueChange = { viewModel.synthesizer.bassVolume = it; viewModel.autoSave() },
                            automationCurve = automationCurves["BASS"] ?: FloatArray(16) { 1.0f },
                            currentStep = currentStep,
                            onAutomationValueChange = { step, valFloat ->
                                viewModel.synthesizer.setAutomationValue("BASS", step, valFloat)
                            }
                        )
                        MixerSlider(
                            label = "Synth Vol",
                            trackKey = "SYNTH",
                            value = viewModel.synthesizer.synthVolume,
                            isMuted = trackMutes["SYNTH"] ?: false,
                            isSoloed = trackSolos["SYNTH"] ?: false,
                            onMuteToggle = { viewModel.synthesizer.toggleMute("SYNTH") },
                            onSoloToggle = { viewModel.synthesizer.toggleSolo("SYNTH") },
                            onValueChange = { viewModel.synthesizer.synthVolume = it; viewModel.autoSave() },
                            automationCurve = automationCurves["SYNTH"] ?: FloatArray(16) { 1.0f },
                            currentStep = currentStep,
                            onAutomationValueChange = { step, valFloat ->
                                viewModel.synthesizer.setAutomationValue("SYNTH", step, valFloat)
                            }
                        )
                    }

                    Divider(color = CosmicSurfaceVariant)

                    // Metronome section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Metronome", tint = NeonAmber, modifier = Modifier.size(16.dp))
                            Text(
                                text = "METRONOME CLICK",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                        }

                        Switch(
                            checked = metronomeEnabled,
                            onCheckedChange = { viewModel.synthesizer.setMetronomeEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonAmber,
                                checkedTrackColor = NeonAmber.copy(alpha = 0.4f),
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = CosmicSurfaceVariant
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    if (metronomeEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "Click Vol", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(64.dp))
                            Slider(
                                value = metronomeVolume,
                                onValueChange = { viewModel.synthesizer.setMetronomeVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber, inactiveTrackColor = CosmicSurfaceVariant)
                            )
                            Text(text = "${(metronomeVolume * 100).toInt()}%", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }

        // --- Multi-Band Parametric EQ & Visualizer Panel ---
        item {
            val trackEq = viewModel.synthesizer.trackEqs[selectedEqTrack]
            val trigger = eqUpdateTrigger // Read local state to force recomposition

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parametric_eq_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title and Bypass Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Parametric EQ",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "STUDIO PARAMETRIC EQ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                        }

                        if (trackEq != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (trackEq.isBypassed) "BYPASSED" else "ACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (trackEq.isBypassed) TextMuted else NeonCyan
                                )
                                Switch(
                                    checked = !trackEq.isBypassed,
                                    onCheckedChange = {
                                        trackEq.isBypassed = !it
                                        eqUpdateTrigger++
                                        viewModel.autoSave()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonCyan,
                                        checkedTrackColor = NeonCyan.copy(alpha = 0.4f),
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = CosmicSurfaceVariant
                                    ),
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }

                    // Track selector scrollable tab row
                    ScrollableTabRow(
                        selectedTabIndex = channels.indexOf(selectedEqTrack).coerceAtLeast(0),
                        containerColor = Color.Transparent,
                        contentColor = NeonCyan,
                        edgePadding = 0.dp,
                        divider = {},
                        indicator = { tabPositions ->
                            if (tabPositions.isNotEmpty()) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[channels.indexOf(selectedEqTrack).coerceAtLeast(0)]),
                                    color = NeonCyan,
                                    height = 2.dp
                                )
                            }
                        }
                    ) {
                        channels.forEach { ch ->
                            val selected = selectedEqTrack == ch
                            Tab(
                                selected = selected,
                                onClick = { selectedEqTrack = ch },
                                text = {
                                    Text(
                                        text = ch,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) NeonCyan else TextSecondary
                                    )
                                }
                            )
                        }
                    }

                    if (trackEq != null) {
                        // Graph Curve Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CosmicSurfaceVariant)
                                .padding(8.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val centerY = height / 2f
                                val dbMax = 15f
                                val dbMin = -15f

                                // Draw center reference line (0 dB)
                                drawLine(
                                    color = TextMuted.copy(alpha = 0.3f),
                                    start = Offset(0f, centerY),
                                    end = Offset(width, centerY),
                                    strokeWidth = 1f
                                )

                                // Draw grid lines for frequency intervals (Logarithmic scale)
                                val gridFreqs = listOf(100f, 1000f, 10000f)
                                gridFreqs.forEach { f ->
                                    val logMin = ln(40f)
                                    val logMax = ln(16000f)
                                    val pct = (ln(f) - logMin) / (logMax - logMin)
                                    val x = pct * width
                                    drawLine(
                                        color = TextMuted.copy(alpha = 0.2f),
                                        start = Offset(x, 0f),
                                        end = Offset(x, height),
                                        strokeWidth = 1f
                                    )
                                    // Labels
                                    drawContext.canvas.nativeCanvas.drawText(
                                        when(f) {
                                            100f -> "100Hz"
                                            1000f -> "1kHz"
                                            10000f -> "10kHz"
                                            else -> ""
                                        },
                                        x + 4f,
                                        height - 4f,
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.GRAY
                                            textSize = 24f
                                        }
                                    )
                                }

                                // Draw vertical dB reference ticks (+12dB, -12dB)
                                val dbTicks = listOf(12f, -12f)
                                dbTicks.forEach { db ->
                                    val pct = (db - dbMin) / (dbMax - dbMin)
                                    // Invert Y because canvas coordinates go from top to bottom
                                    val y = height - (pct * height)
                                    drawLine(
                                        color = TextMuted.copy(alpha = 0.15f),
                                        start = Offset(0f, y),
                                        end = Offset(width, y),
                                        strokeWidth = 0.5f
                                    )
                                }

                                // Draw the active EQ curve
                                val path = Path()
                                val pointsCount = 100
                                val logMin = ln(40f)
                                val logMax = ln(16000f)

                                for (i in 0..pointsCount) {
                                    val pct = i.toFloat() / pointsCount
                                    val logFreq = logMin + pct * (logMax - logMin)
                                    val freq = exp(logFreq)
                                    val db = trackEq.getCombinedMagnitudeDb(freq)

                                    val x = pct * width
                                    val dbPct = (db - dbMin) / (dbMax - dbMin)
                                    val y = (height - (dbPct * height)).coerceIn(0f, height)

                                    if (i == 0) {
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }

                                drawPath(
                                    path = path,
                                    color = if (trackEq.isBypassed) TextMuted else NeonCyan,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // Draw band nodes/interactive dots
                                val bands = listOf(
                                    trackEq.lowBand to NeonMagenta,
                                    trackEq.midBand to NeonCyan,
                                    trackEq.highBand to NeonAmber
                                )
                                bands.forEach { (band, color) ->
                                    val pct = (ln(band.f0) - logMin) / (logMax - logMin)
                                    val x = pct * width
                                    val dbPct = (band.gainDb - dbMin) / (dbMax - dbMin)
                                    val y = height - (dbPct * height)

                                    drawCircle(
                                        color = if (trackEq.isBypassed) TextMuted else color,
                                        radius = 6.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                    drawCircle(
                                        color = CosmicSurface,
                                        radius = 2.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                }
                            }
                        }

                        // Controls for Low, Mid, and High Bands
                        var activeEqBandTab by remember { mutableStateOf(0) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("LOW SHELF", "MID PEAKING", "HIGH SHELF").forEachIndexed { index, title ->
                                val active = activeEqBandTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) CosmicSurfaceVariant else Color.Transparent)
                                        .clickable { activeEqBandTab = index }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) NeonCyan else TextSecondary
                                    )
                                }
                            }
                        }

                        val activeBand = when (activeEqBandTab) {
                            0 -> trackEq.lowBand
                            1 -> trackEq.midBand
                            else -> trackEq.highBand
                        }

                        val bandColor = when (activeEqBandTab) {
                            0 -> NeonMagenta
                            1 -> NeonCyan
                            else -> NeonAmber
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Frequency Slider
                            val freqRange = when (activeEqBandTab) {
                                0 -> 30f..400f
                                1 -> 200f..5000f
                                else -> 2000f..16000f
                            }
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Center / Cutoff Frequency", fontSize = 11.sp, color = TextSecondary)
                                    Text("${activeBand.f0.toInt()} Hz", fontSize = 11.sp, color = bandColor, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = activeBand.f0,
                                    onValueChange = {
                                        activeBand.update(f0 = it, gainDb = activeBand.gainDb, q = activeBand.q)
                                        eqUpdateTrigger++
                                    },
                                    onValueChangeFinished = { viewModel.autoSave() },
                                    valueRange = freqRange,
                                    colors = SliderDefaults.colors(
                                        thumbColor = bandColor,
                                        activeTrackColor = bandColor,
                                        inactiveTrackColor = CosmicSurfaceVariant
                                    )
                                )
                            }

                            // Gain Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gain (Cut / Boost)", fontSize = 11.sp, color = TextSecondary)
                                    Text(
                                        text = if (activeBand.gainDb >= 0) "+${"%.1f".format(activeBand.gainDb)} dB" else "${"%.1f".format(activeBand.gainDb)} dB",
                                        fontSize = 11.sp,
                                        color = bandColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = activeBand.gainDb,
                                    onValueChange = {
                                        activeBand.update(f0 = activeBand.f0, gainDb = it, q = activeBand.q)
                                        eqUpdateTrigger++
                                    },
                                    onValueChangeFinished = { viewModel.autoSave() },
                                    valueRange = -12f..12f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = bandColor,
                                        activeTrackColor = bandColor,
                                        inactiveTrackColor = CosmicSurfaceVariant
                                    )
                                )
                            }

                            // Q-Factor Slider (Only for Mid Band peaking filter)
                            if (activeEqBandTab == 1) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Q-Factor (Bandwidth Resonance)", fontSize = 11.sp, color = TextSecondary)
                                        Text("%.2f".format(activeBand.q), fontSize = 11.sp, color = bandColor, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = activeBand.q,
                                        onValueChange = {
                                            activeBand.update(f0 = activeBand.f0, gainDb = activeBand.gainDb, q = it)
                                            eqUpdateTrigger++
                                        },
                                        onValueChangeFinished = { viewModel.autoSave() },
                                        valueRange = 0.2f..4.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = bandColor,
                                            activeTrackColor = bandColor,
                                            inactiveTrackColor = CosmicSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        // Reset current track EQ button
                        OutlinedButton(
                            onClick = {
                                trackEq.reset()
                                eqUpdateTrigger++
                                viewModel.autoSave()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, CosmicSurfaceVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("RESET EQ TO FLAT", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }

        // --- 5. Studio Effects & Master Filters ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "STUDIO FX & ANALOG FILTER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    // Effects sliders
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ping-Pong Delay Feedback", fontSize = 11.sp, color = TextSecondary)
                                Text("${(viewModel.synthesizer.delayLevel * 100).toInt()}%", fontSize = 11.sp, color = NeonCyan)
                            }
                            Slider(
                                value = viewModel.synthesizer.delayLevel,
                                onValueChange = { viewModel.synthesizer.delayLevel = it; viewModel.autoSave() },
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                            )
                        }

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reverb Wet Mix", fontSize = 11.sp, color = TextSecondary)
                                Text("${(viewModel.synthesizer.reverbLevel * 100).toInt()}%", fontSize = 11.sp, color = NeonMagenta)
                            }
                            Slider(
                                value = viewModel.synthesizer.reverbLevel,
                                onValueChange = { viewModel.synthesizer.reverbLevel = it; viewModel.autoSave() },
                                colors = SliderDefaults.colors(thumbColor = NeonMagenta, activeTrackColor = NeonMagenta)
                            )
                        }

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Master Cutoff Frequency", fontSize = 11.sp, color = TextSecondary)
                                Text("${(viewModel.synthesizer.filterCutoff * 20000).toInt()} Hz", fontSize = 11.sp, color = NeonAmber)
                            }
                            Slider(
                                value = viewModel.synthesizer.filterCutoff,
                                onValueChange = { viewModel.synthesizer.filterCutoff = it; viewModel.autoSave() },
                                colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber)
                            )
                        }
                    }
                }
            }
        }

        // --- LFO Automation Engine Panel ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LFO AUTOMATION ENGINE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (lfoWaveform != "Off") NeonCyan.copy(alpha = 0.15f) else CosmicSurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (lfoWaveform != "Off") "MODULATOR ACTIVE" else "BYPASS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lfoWaveform != "Off") NeonCyan else TextSecondary
                            )
                        }
                    }

                    // Waveform shape selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("LFO Waveform", fontSize = 11.sp, color = TextSecondary)
                        val waves = listOf("Off", "Sine", "Triangle", "Square")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            waves.forEach { wave ->
                                val active = lfoWaveform == wave
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) NeonCyan else CosmicSurfaceVariant)
                                        .clickable { 
                                            // Back up original baseline settings before modulating
                                            viewModel.synthesizer.baseFilterCutoff = viewModel.synthesizer.filterCutoff
                                            viewModel.synthesizer.baseSynthVolume = viewModel.synthesizer.synthVolume
                                            viewModel.synthesizer.baseDelayLevel = viewModel.synthesizer.delayLevel
                                            viewModel.synthesizer.setLfoWaveform(wave)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = wave.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.Black else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (lfoWaveform != "Off") {
                        // Modulation Target Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Modulation Target", fontSize = 11.sp, color = TextSecondary)
                            val targets = listOf("None", "Master Filter", "Synth Volume", "Delay Feedback")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                targets.forEach { target ->
                                    val active = lfoTarget == target
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) NeonMagenta else CosmicSurfaceVariant)
                                            .clickable { viewModel.synthesizer.setLfoTarget(target) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = target.uppercase(),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.White else TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // LFO Modulation Rate (musical sync)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Tempo-Synced Frequency Rate", fontSize = 11.sp, color = TextSecondary)
                            val rates = listOf("1/1", "1/2", "1/4", "1/8")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rates.forEach { rate ->
                                    val active = lfoRate == rate
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) NeonAmber else CosmicSurfaceVariant)
                                            .clickable { viewModel.synthesizer.setLfoRate(rate) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = rate,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.Black else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // LFO depth slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Modulation Intensity (Depth)", fontSize = 11.sp, color = TextSecondary)
                                Text("${lfoDepth.toInt()}%", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = lfoDepth,
                                onValueChange = { viewModel.synthesizer.setLfoDepth(it) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan, inactiveTrackColor = CosmicSurfaceVariant)
                            )
                        }
                    }
                }
            }
        }

        // --- Mastering Engine & Stereo Imaging Panel ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "ANALOG MASTERING & IMAGING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    // Stereo Width Expander
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Stereo Width", tint = NeonCyan, modifier = Modifier.size(14.dp))
                                Text("Stereo Image Expansion Width", fontSize = 11.sp, color = TextSecondary)
                            }
                            val widthPercent = (stereoWidth * 100).toInt()
                            val label = when {
                                stereoWidth == 0.0f -> "MONO"
                                stereoWidth == 1.0f -> "STEREO (100%)"
                                stereoWidth > 1.0f -> "EXPANDED ($widthPercent%)"
                                else -> "NARROW ($widthPercent%)"
                            }
                            Text(label, fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = stereoWidth,
                            onValueChange = { viewModel.synthesizer.setStereoWidth(it) },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan, inactiveTrackColor = CosmicSurfaceVariant)
                        )
                    }

                    // Mastering character preset select
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Master Console Flavor / Saturation", fontSize = 11.sp, color = TextSecondary)
                        val masteringPresets = listOf("Flat Bypass", "Club Punch", "Radio-Ready", "Lo-Fi Chill")
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                masteringPresets.take(2).forEach { preset ->
                                    val active = masteringPreset == preset
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) NeonMagenta else CosmicSurfaceVariant)
                                            .clickable { viewModel.synthesizer.setMasteringPreset(preset) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.White else TextSecondary
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                masteringPresets.drop(2).forEach { preset ->
                                    val active = masteringPreset == preset
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) NeonMagenta else CosmicSurfaceVariant)
                                            .clickable { viewModel.synthesizer.setMasteringPreset(preset) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.White else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = CosmicSurfaceVariant)

                    // Loudness Normalization switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Peak Limiter & Loudness Normalization", fontSize = 12.sp, color = TextPrimary)
                            Text("Ensures industry standard loudness without digital distortion", fontSize = 9.sp, color = TextMuted)
                        }
                        Switch(
                            checked = loudnessNormalizer,
                            onCheckedChange = { viewModel.synthesizer.setLoudnessNormalizer(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonMagenta,
                                checkedTrackColor = NeonMagenta.copy(alpha = 0.4f),
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = CosmicSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // --- 5B. AI-Powered Mastering Assistant Panel ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_mastering_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Mastering",
                            tint = NeonMagenta,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "AI MASTERING ASSISTANT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Badge(containerColor = NeonMagenta.copy(alpha = 0.15f)) {
                            Text(
                                "PRO DSP",
                                color = NeonMagenta,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Let our AI mastering engine analyze your mix's volume levels, EQ, and track density. Choose a target industry reference style to get expert tips and automatically apply professional settings.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    // Target Genre Selector dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Target Mastering Style:", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        
                        Box {
                            Button(
                                onClick = { showMasteringGenreMenu = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(masteringTargetGenre, fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                            
                            DropdownMenu(
                                expanded = showMasteringGenreMenu,
                                onDismissRequest = { showMasteringGenreMenu = false },
                                modifier = Modifier.background(CosmicSurface)
                            ) {
                                val styles = listOf("Afrobeat", "Hip-Hop", "Trap Rap", "Pop", "Modern R&B", "Drill", "Lo-Fi Chill", "Afro Fuji", "Afro Blues")
                                styles.forEach { style ->
                                    DropdownMenuItem(
                                        text = { Text(style, color = TextPrimary, fontSize = 12.sp) },
                                        onClick = {
                                            masteringTargetGenre = style
                                            showMasteringGenreMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Error Box if any
                    masteringError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x33E91E63)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Main Action / Loading state
                    if (masteringLoading) {
                        // Custom animated status
                        var loadingPhraseIndex by remember { mutableStateOf(0) }
                        val phrases = listOf(
                            "Exporting snapshot mix...",
                            "Measuring spectral headroom...",
                            "Analyzing kick vs sub bass balance...",
                            "Evaluating high-frequency hi-hat energy...",
                            "Measuring peak levels (dBFS)...",
                            "Calibrating glue compressor settings...",
                            "Calculating optimal target LUFS...",
                            "Simulating dynamic range curves..."
                        )

                        // Change phrase periodically
                        LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(1800)
                                loadingPhraseIndex = (loadingPhraseIndex + 1) % phrases.size
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = NeonMagenta,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                                Column {
                                    Text("Mastering analysis in progress...", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(phrases[loadingPhraseIndex], fontSize = 11.sp, color = NeonMagenta)
                                }
                            }
                        }
                    } else if (masteringSuggestions == null) {
                        Button(
                            onClick = { viewModel.runMasteringAnalysis(masteringTargetGenre) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("analyze_mix_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("ANALYZE CURRENT MIX & SUGGEST MASTERING", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Suggestions Layout
                    masteringSuggestions?.let { suggestions ->
                        Divider(color = CosmicSurfaceVariant)

                        // Grade & Score Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(
                                        brush = Brush.radialGradient(listOf(NeonMagenta, Color.Transparent)),
                                        shape = CircleShape
                                    )
                                    .border(BorderStroke(2.dp, NeonMagenta), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = suggestions.sonicGrade,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }

                            Column {
                                Text("CURRENT SONIC PROFILE GRADE", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                Text("Suggested Style: $masteringTargetGenre", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Summary
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Sonic Feedback", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                Text(suggestions.analysisSummary, fontSize = 11.sp, color = TextPrimary)
                            }
                        }

                        // Segmented suggestion views
                        var selectedSection by remember { mutableStateOf("Adjustments") }
                        val sections = listOf("Adjustments", "Channel EQ", "Explanations")

                        TabRow(
                            selectedTabIndex = sections.indexOf(selectedSection),
                            containerColor = Color.Transparent,
                            contentColor = NeonCyan,
                            indicator = { tabPositions ->
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[sections.indexOf(selectedSection)]),
                                    color = NeonCyan
                                )
                            },
                            divider = {}
                        ) {
                            sections.forEach { section ->
                                Tab(
                                    selected = selectedSection == section,
                                    onClick = { selectedSection = section },
                                    text = { Text(section, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        when (selectedSection) {
                            "Adjustments" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Suggested Mix Settings:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                    
                                    val currentAdj = suggestions.adjustments
                                    
                                    // Let's draw a compact faders list
                                    val faders = listOf(
                                        Triple("Kick Vol", viewModel.synthesizer.kickVolume, currentAdj.kickVolume),
                                        Triple("Snare Vol", viewModel.synthesizer.snareVolume, currentAdj.snareVolume),
                                        Triple("Hihat Vol", viewModel.synthesizer.hihatVolume, currentAdj.hihatVolume),
                                        Triple("Perc Vol", viewModel.synthesizer.percussionVolume, currentAdj.percVolume),
                                        Triple("Bass Vol", viewModel.synthesizer.bassVolume, currentAdj.bassVolume),
                                        Triple("Synth Vol", viewModel.synthesizer.synthVolume, currentAdj.synthVolume)
                                    )

                                    faders.forEach { (label, current, target) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(label, fontSize = 11.sp, color = TextPrimary, modifier = Modifier.width(60.dp))
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Current value bar
                                                Box(modifier = Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .background(CosmicSurfaceVariant, shape = RoundedCornerShape(3.dp))) {
                                                    Box(modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(current.coerceIn(0f, 1f))
                                                        .background(TextSecondary.copy(alpha = 0.5f), shape = RoundedCornerShape(3.dp)))
                                                }
                                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                                                // Target value bar
                                                Box(modifier = Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .background(CosmicSurfaceVariant, shape = RoundedCornerShape(3.dp))) {
                                                    Box(modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(target.coerceIn(0f, 1f))
                                                        .background(NeonCyan, shape = RoundedCornerShape(3.dp)))
                                                }
                                            }
                                            Text(
                                                text = "${(current * 100).toInt()}% -> ${(target * 100).toInt()}%",
                                                fontSize = 10.sp,
                                                color = TextSecondary,
                                                modifier = Modifier.width(72.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Mastering console recommendation
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Console Preset:", fontSize = 11.sp, color = TextSecondary)
                                        Text(
                                            text = "$masteringPreset -> ${currentAdj.masteringPreset}",
                                            fontSize = 11.sp,
                                            color = NeonMagenta,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Stereo Width:", fontSize = 11.sp, color = TextSecondary)
                                        Text(
                                            text = "${String.format("%.2f", stereoWidth)}x -> ${String.format("%.2f", currentAdj.stereoWidth)}x",
                                            fontSize = 11.sp,
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            "Channel EQ" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Suggested Parametric EQ Gains:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                    
                                    suggestions.adjustments.eqAdjustments.forEach { eq ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CosmicSurfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp))
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = eq.track,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonCyan,
                                                modifier = Modifier.width(54.dp)
                                            )

                                            Row(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Low", fontSize = 8.sp, color = TextMuted)
                                                    Text(
                                                        text = "${if (eq.lowGain >= 0) "+" else ""}${String.format("%.1f", eq.lowGain)} dB",
                                                        fontSize = 10.sp,
                                                        color = if (eq.lowGain > 0) NeonCyan else if (eq.lowGain < 0) NeonMagenta else TextSecondary
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Mid", fontSize = 8.sp, color = TextMuted)
                                                    Text(
                                                        text = "${if (eq.midGain >= 0) "+" else ""}${String.format("%.1f", eq.midGain)} dB",
                                                        fontSize = 10.sp,
                                                        color = if (eq.midGain > 0) NeonCyan else if (eq.midGain < 0) NeonMagenta else TextSecondary
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("High", fontSize = 8.sp, color = TextMuted)
                                                    Text(
                                                        text = "${if (eq.highGain >= 0) "+" else ""}${String.format("%.1f", eq.highGain)} dB",
                                                        fontSize = 10.sp,
                                                        color = if (eq.highGain > 0) NeonCyan else if (eq.highGain < 0) NeonMagenta else TextSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "Explanations" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Equalizer Adjustments", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                        Text(suggestions.explanationEq, fontSize = 11.sp, color = TextSecondary)
                                    }
                                    Divider(color = CosmicSurfaceVariant)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Glue Compression", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                        Text(suggestions.explanationCompression, fontSize = 11.sp, color = TextSecondary)
                                    }
                                    Divider(color = CosmicSurfaceVariant)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Loudness & Limiting", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                        Text(suggestions.explanationLoudness, fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        Divider(color = CosmicSurfaceVariant)

                        // Apply / Cancel Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.clearMasteringState() },
                                modifier = Modifier.weight(0.4f),
                                border = BorderStroke(1.dp, CosmicSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) {
                                Text("CLEAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.applyMasteringSuggestions(suggestions)
                                    viewModel.clearMasteringState()
                                },
                                modifier = Modifier
                                    .weight(0.6f)
                                    .testTag("apply_suggestions_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                    Text("APPLY SETTINGS", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5C. Live Performance Tap Pad Controller ---
        item {
            val isRecording by viewModel.isLiveRecording.collectAsState()
            val noteRepeat by viewModel.noteRepeatRate.collectAsState()
            val padMode by viewModel.activePadMode.collectAsState()
            val sampleAssignments by viewModel.sampleAssignments.collectAsState()
            val currentTempo by viewModel.synthesizer.tempoBpm.collectAsState()
            
            val coroutineScope = rememberCoroutineScope()
            
            // Calculate note repeat interval in ms
            val repeatIntervalMs = remember(currentTempo, noteRepeat) {
                val beatMs = 60000.0 / currentTempo.coerceAtLeast(1)
                when (noteRepeat) {
                    "1/8" -> (beatMs / 2.0).toLong()
                    "1/16" -> (beatMs / 4.0).toLong()
                    "1/32" -> (beatMs / 8.0).toLong()
                    else -> 0L
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tap_pads_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Tap Pads",
                                    tint = NeonMagenta,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "LIVE PERFORMANCE TAP PADS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Trigger drums, sound effects & loops in real-time.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        // Live Recording Toggle Button
                        Button(
                            onClick = { viewModel.toggleLiveRecording() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFFE57373) else CosmicSurfaceVariant,
                                contentColor = if (isRecording) Color.Black else TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("live_record_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Record",
                                tint = if (isRecording) Color.Black else Color.Red,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRecording) "RECORDING" else "LIVE REC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Mode Selection & Note Repeat Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pad Mode Selection Row
                        Row(
                            modifier = Modifier
                                .weight(1.3f)
                                .background(CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf("Drums", "Samples").forEach { mode ->
                                val active = padMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (active) ElectricViolet else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel.setPadMode(mode) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) NeonCyan else TextSecondary
                                    )
                                }
                            }
                        }

                        // Note Repeat Selector
                        Row(
                            modifier = Modifier
                                .weight(1.7f)
                                .background(CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf("Off", "1/8", "1/16", "1/32").forEach { rate ->
                                val active = noteRepeat == rate
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (active) ElectricViolet else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel.setNoteRepeatRate(rate) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = rate,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) NeonCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (noteRepeat != "Off") {
                        Text(
                            text = "Note Repeat Active at $noteRepeat rate (${repeatIntervalMs}ms intervals). Hold pad to roll/arpeggiate.",
                            fontSize = 10.sp,
                            color = NeonAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 3x2 Performance Pad Grid
                    val pads = if (padMode == "Drums") {
                        listOf(
                            Triple("KICK", NeonCyan, "C2"),
                            Triple("SNARE", NeonMagenta, "C2"),
                            Triple("HI-HAT", NeonAmber, "C2"),
                            Triple("PERCUSSION", NeonCyan, "C2"),
                            Triple("SUB BASS", NeonMagenta, "C2"),
                            Triple("LEAD SYNTH", NeonAmber, "C4")
                        )
                    } else {
                        listOf(
                            Triple("VOX HEY", NeonCyan, "VOX_HEY"),
                            Triple("VOX AHH", NeonMagenta, "VOX_AHH"),
                            Triple("SIREN FX", NeonAmber, "SIREN_FX"),
                            Triple("LASER FX", NeonCyan, "LASER_FX"),
                            Triple("SUB DROP", NeonMagenta, "SUB_DROP"),
                            Triple("CHIP RISER", NeonAmber, "CHIP_RISER")
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (row in 0..1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (col in 0..2) {
                                    val index = row * 3 + col
                                    val pad = pads[index]
                                    val label = pad.first
                                    val color = pad.second
                                    val soundId = pad.third

                                    var isPadPressed by remember { mutableStateOf(false) }

                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(85.dp)
                                            .testTag("performance_pad_$index")
                                            .pointerInput(padMode, soundId, repeatIntervalMs) {
                                                detectTapGestures(
                                                    onPress = {
                                                        isPadPressed = true
                                                        viewModel.triggerLivePad(index)
                                                        
                                                        if (repeatIntervalMs > 0) {
                                                            val job = coroutineScope.launch {
                                                                while (isPadPressed) {
                                                                    delay(repeatIntervalMs)
                                                                    viewModel.triggerLivePad(index)
                                                                }
                                                            }
                                                            try {
                                                                awaitRelease()
                                                            } finally {
                                                                isPadPressed = false
                                                                job.cancel()
                                                            }
                                                        } else {
                                                            awaitRelease()
                                                            isPadPressed = false
                                                        }
                                                    }
                                                )
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isPadPressed) color.copy(alpha = 0.35f) else CosmicSurfaceVariant
                                        ),
                                        border = BorderStroke(
                                            width = 1.5.dp,
                                            color = if (isPadPressed) color else color.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "PAD 0${index + 1}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextMuted
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(
                                                            if (isPadPressed) color else color.copy(alpha = 0.3f),
                                                            CircleShape
                                                        )
                                                )
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = label,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 11.sp,
                                                    color = if (isPadPressed) color else TextPrimary
                                                )
                                                Text(
                                                    text = if (padMode == "Drums") "TRIGGER" else "FX SYNTH",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Sample reassignment panel description
                    if (padMode == "Samples") {
                        Text(
                            text = "Sample triggering uses real-time dynamic sound-design algorithms to synthesize unique organic vocal formants and effects instantly on touch.",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            style = TextStyle(fontStyle = FontStyle.Italic)
                        )
                    }
                }
            }
        }

        // --- 6. Custom Outro and Export Options ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "TIME LENGTH & HIGH-QUALITY EXPORT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    // Duration Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Playback Duration", fontSize = 12.sp, color = TextSecondary)
                            Text("$durationMin Minutes", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }

                        Slider(
                            value = durationMin.toFloat(),
                            onValueChange = { viewModel.synthesizer.setDuration(it.toInt()); viewModel.autoSave() },
                            valueRange = 3f..15f,
                            steps = 11,
                            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                        )

                        Text(
                            text = "Note: Beat automatically slows down (rallentando) over the final 30 seconds for an organic, studio-grade outro fade.",
                            fontSize = 11.sp,
                            color = NeonAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Format Selection Panel
                    val formats = listOf("WAV", "MP3", "FLAC", "AIFF")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        formats.forEach { format ->
                            Button(
                                onClick = { viewModel.exportCurrentBeat(format) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_${format.lowercase()}_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(format, fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- MIDI File Drag & Drop Sim Drawer Dialog ---
    if (showPresetDrawer) {
        AlertDialog(
            onDismissRequest = { showPresetDrawer = false },
            confirmButton = {
                TextButton(onClick = { showPresetDrawer = false }) { Text("CLOSE", color = NeonCyan) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = NeonCyan)
                    Text("MIDI ARRANGEMENT DRAG-N-DROP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Drop a MIDI file or select a MIDI preset to quickly seed complex polyrhythms and arrangements.", fontSize = 13.sp, color = TextSecondary)
                    
                    val presets = listOf("Syncopated Afro-Groove", "Trap Double-Time Roll", "R&B Smooth Chord Glide")
                    presets.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CosmicSurfaceVariant)
                                .clickable {
                                    viewModel.loadMidiFilePreset(preset)
                                    showPresetDrawer = false
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(preset, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text(".midi loop metadata template", fontSize = 10.sp, color = TextMuted)
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            containerColor = CosmicSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun MixerSlider(
    label: String,
    trackKey: String,
    value: Float,
    isMuted: Boolean,
    isSoloed: Boolean,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onValueChange: (Float) -> Unit,
    automationCurve: FloatArray,
    currentStep: Int,
    onAutomationValueChange: (Int, Float) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CosmicSurface.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMuted) TextSecondary.copy(alpha = 0.5f) else TextPrimary,
                modifier = Modifier.width(64.dp)
            )
            
            // Mute button (M)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isMuted) Color(0xFFE91E63) else CosmicSurfaceVariant)
                    .clickable { onMuteToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMuted) Color.White else TextSecondary
                )
            }

            // Solo button (S)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSoloed) Color(0xFFFFEB3B) else CosmicSurfaceVariant)
                    .clickable { onSoloToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSoloed) Color.Black else TextSecondary
                )
            }

            // Automation toggle button (A)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isExpanded) NeonCyan.copy(alpha = 0.2f) else CosmicSurfaceVariant)
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isExpanded) NeonCyan else Color.Transparent
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpanded) NeonCyan else TextSecondary
                )
            }

            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = if (isMuted) TextSecondary else NeonCyan,
                    activeTrackColor = if (isMuted) TextSecondary.copy(alpha = 0.4f) else NeonCyan,
                    inactiveTrackColor = CosmicSurfaceVariant
                )
            )
            
            Text(
                text = "${(value * 100).toInt()}%",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 8.dp, end = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step Volume Automation Lane",
                        fontSize = 10.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Swipe to draw volume curve",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(CosmicSurface, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, CosmicSurfaceVariant, shape = RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val stepWidth = size.width / 16f
                                    val step = (offset.x / stepWidth).toInt().coerceIn(0, 15)
                                    val newVal = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                                    onAutomationValueChange(step, newVal)
                                }
                            ) { change, _ ->
                                change.consume()
                                val stepWidth = size.width / 16f
                                val step = (change.position.x / stepWidth).toInt().coerceIn(0, 15)
                                val newVal = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                onAutomationValueChange(step, newVal)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val stepWidth = size.width / 16f
                                val step = (offset.x / stepWidth).toInt().coerceIn(0, 15)
                                val newVal = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                                onAutomationValueChange(step, newVal)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val stepWidth = width / 16f

                        // Draw grid lines and column separators
                        for (i in 0..16) {
                            val x = i * stepWidth
                            drawLine(
                                color = CosmicSurfaceVariant.copy(alpha = 0.5f),
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = 1f
                            )
                        }

                        // Draw horizontal grid lines (0.25, 0.5, 0.75)
                        for (f in listOf(0.25f, 0.5f, 0.75f)) {
                            val y = height * (1f - f)
                            drawLine(
                                color = CosmicSurfaceVariant.copy(alpha = 0.3f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1f
                            )
                        }

                        // Draw bar chart / filled curve
                        val path = Path()
                        path.moveTo(0f, height)
                        for (i in 0 until 16) {
                            val x = i * stepWidth + stepWidth / 2f
                            val y = height * (1f - automationCurve[i])
                            if (i == 0) {
                                path.moveTo(0f, y)
                            }
                            path.lineTo(x, y)
                            if (i == 15) {
                                path.lineTo(width, y)
                            }
                        }
                        path.lineTo(width, height)
                        path.lineTo(0f, height)
                        path.close()

                        // Fill with subtle neon gradient
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    NeonCyan.copy(alpha = 0.35f),
                                    NeonCyan.copy(alpha = 0.05f)
                                )
                            )
                        )

                        // Draw curve lines connecting points
                        for (i in 0 until 15) {
                            val x1 = i * stepWidth + stepWidth / 2f
                            val y1 = height * (1f - automationCurve[i])
                            val x2 = (i + 1) * stepWidth + stepWidth / 2f
                            val y2 = height * (1f - automationCurve[i + 1])

                            drawLine(
                                color = NeonCyan,
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        // Draw step point indicators
                        for (i in 0 until 16) {
                            val x = i * stepWidth + stepWidth / 2f
                            val y = height * (1f - automationCurve[i])
                            drawCircle(
                                color = NeonCyan,
                                radius = 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }

                        // Draw active step playhead highlight
                        if (currentStep in 0..15) {
                            val playheadX = currentStep * stepWidth
                            // Draw filled step rectangle highlight
                            drawRect(
                                color = NeonMagenta.copy(alpha = 0.15f),
                                topLeft = Offset(playheadX, 0f),
                                size = Size(stepWidth, height)
                            )
                            // Draw a vertical glowing playhead line
                            drawLine(
                                color = NeonMagenta,
                                start = Offset(playheadX + stepWidth / 2f, 0f),
                                end = Offset(playheadX + stepWidth / 2f, height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
    }
}
