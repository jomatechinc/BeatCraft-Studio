package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BeatViewModel, modifier: Modifier = Modifier) {
    val shortcuts by viewModel.shortcuts.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()

    // Sample rate & Depth parameters
    var activeSampleRate by remember { mutableStateOf("44.1 kHz") }
    var activeBitDepth by remember { mutableStateOf("16-Bit") }

    // User Profile simulation
    var userIsSignedIn by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("Guest Producer") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // --- 1. User Profile Sync ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (userIsSignedIn) "BC" else "G",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (userIsSignedIn) "BeatCraft Pro Account" else "Anonymous Session",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (userIsSignedIn) "Connected as @oghenekarojoseph" else "Sync drafts with cloud by signing in.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = {
                            if (userIsSignedIn) {
                                userIsSignedIn = false
                                userName = "Guest Producer"
                            } else {
                                userIsSignedIn = true
                                userName = "Joseph O."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (userIsSignedIn) TextMuted else NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("signin_toggle_button")
                    ) {
                        Text(
                            text = if (userIsSignedIn) "LOG OUT" else "SIGN IN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // --- 2. Professional Audio Quality parameter displays ---
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "STUDIO ENGINE AUDIO STANDARDS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    // Audio Sample Rate Selection
                    Column {
                        Text("Active Engine Sample Rate", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("44.1 kHz", "48.0 kHz").forEach { rate ->
                                val selected = rate == activeSampleRate
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) NeonCyan else CosmicSurfaceVariant)
                                        .clickable { activeSampleRate = rate }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(rate, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selected) Color.Black else TextSecondary)
                                }
                            }
                        }
                    }

                    // Audio Bit Depth Selection
                    Column {
                        Text("Render Encoding Bit Depth", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("16-Bit", "24-Bit").forEach { depth ->
                                val selected = depth == activeBitDepth
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) NeonCyan else CosmicSurfaceVariant)
                                        .clickable { activeBitDepth = depth }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(depth, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selected) Color.Black else TextSecondary)
                                }
                            }
                        }
                    }

                    Text(
                        text = "BeatCraft synthesizes native audio in real-time with zero buffer lag. Changing these parameters updates the hardware encoder immediately.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // --- 3. Keyboard Shortcut Key configurations ---
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
                    Text(
                        text = "HARDWARE SHORTCUT CONFIGURATION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Customize physical keyboard keys for fast navigation when using a Chromebook or hardware accessories.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Divider(color = CosmicSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

                    // Shortcut listings
                    shortcuts.forEach { (key, action) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = action, fontSize = 12.sp, color = TextPrimary)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CosmicSurfaceVariant)
                                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = key,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
