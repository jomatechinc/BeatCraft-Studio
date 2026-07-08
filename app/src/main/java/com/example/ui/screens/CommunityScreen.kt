package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.BeatViewModel

data class SoundPack(
    val title: String,
    val creator: String,
    val genre: String,
    val description: String,
    val downloads: Int,
    var likes: Int,
    val bpm: Int,
    val presetName: String // Matches ViewModel midi file presets
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(viewModel: BeatViewModel, modifier: Modifier = Modifier) {
    val isOffline by viewModel.isOfflineMode.collectAsState()
    var statusMessage by remember { mutableStateOf("") }

    // Hardcoded community loop suggestions with dynamic likes count
    val communitySoundPacks = remember {
        mutableStateListOf(
            SoundPack("Lagos Afro-Fusion Vol 1", "Sola_Beatz", "Afrobeat", "Essential highly syncopated shakers and bouncy sub-bass elements to get the crowd dancing.", 4820, 240, 115, "Syncopated Afro-Groove"),
            SoundPack("UK Drill & Sliding Grime", "D_Drill_99", "Drill", "Heavy glissando sub-bass sliding, skittering triplet hats, and sharp rimshots.", 3120, 189, 140, "Trap Double-Time Roll"),
            SoundPack("Lush Late-Night R&B Stems", "Mellowness", "Modern R&B", "Lush velvet major & minor 7th synth progressions with slow snap clap loops.", 1850, 95, 85, "R&B Smooth Chord Glide"),
            SoundPack("Yoruba Fuji Polyrhythms", "Baba_Fuji", "Afro Fuji", "Fuji metal bell ring modulators, authentic talking drum patterns, and dynamic tempo.", 940, 52, 120, "Syncopated Afro-Groove")
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Share currently active project to Community
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(20.dp)),
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
                        Icon(Icons.Default.Share, contentDescription = null, tint = NeonMagenta)
                        Text(
                            text = "SHARE ACTIVE BEAT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "Publish your currently programmed MIDI grid patterns and FX parameters to the global community database.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Button(
                        onClick = {
                            if (isOffline) {
                                statusMessage = "Cannot share beats while in offline mode."
                            } else {
                                statusMessage = "Success! Active beat pattern published to the global stream."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("publish_beat_button")
                    ) {
                        Text("PUBLISH ACTIVE TEMPLATE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }

                    AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                        Text(
                            text = statusMessage,
                            color = if (statusMessage.startsWith("Cannot")) NeonMagenta else NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "COMMUNITY HOT SOUNDPACKS",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = NeonCyan,
                letterSpacing = 1.sp
            )
        }

        // Display shared loops
        itemsIndexed(communitySoundPacks) { index, pack ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(14.dp))
                    .testTag("soundpack_card_$index"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(text = pack.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            Text(text = "Shared by @${pack.creator} • ${pack.genre.uppercase()}", fontSize = 11.sp, color = TextSecondary)
                        }

                        // Downloads / Likes counts
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("${pack.downloads}", fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    val updated = pack.copy(likes = pack.likes + 1)
                                    communitySoundPacks[index] = updated
                                }
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = "Like", tint = NeonMagenta, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("${pack.likes}", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = pack.description, fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(containerColor = CosmicSurfaceVariant) {
                            Text("${pack.bpm} BPM", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }

                        // Import pack trigger
                        Button(
                            onClick = {
                                viewModel.loadMidiFilePreset(pack.presetName)
                                statusMessage = "Imported community template: ${pack.title}!"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.SettingsInputComponent, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LOAD STEMS", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
