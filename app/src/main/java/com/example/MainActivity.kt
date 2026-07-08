package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.CommunityScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.BeatViewModel

enum class StudioTab {
    STUDIO, ARCHIVES, EXPLORE, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainContent() {
    val viewModel: BeatViewModel = viewModel()
    var currentTab by remember { mutableStateOf(StudioTab.STUDIO) }
    val currentProjectName by viewModel.currentProjectName.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "STUDIO SESSION",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = NeonCyan,
                            letterSpacing = 1.8.sp
                        )
                        Text(
                            text = currentProjectName,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            letterSpacing = (-0.2).sp
                        )
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 12.dp)) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Powered Status",
                            tint = NeonMagenta
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CosmicBackground,
                    titleContentColor = NeonCyan
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CosmicSurface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                // Tab 1: Studio
                NavigationBarItem(
                    selected = currentTab == StudioTab.STUDIO,
                    onClick = { currentTab = StudioTab.STUDIO },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "Studio") },
                    label = { Text("Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted,
                        indicatorColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("nav_tab_studio")
                )

                // Tab 2: Archives Dashboard
                NavigationBarItem(
                    selected = currentTab == StudioTab.ARCHIVES,
                    onClick = { currentTab = StudioTab.ARCHIVES },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Archives") },
                    label = { Text("Archives", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted,
                        indicatorColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("nav_tab_archives")
                )

                // Tab 3: Explore Community
                NavigationBarItem(
                    selected = currentTab == StudioTab.EXPLORE,
                    onClick = { currentTab = StudioTab.EXPLORE },
                    icon = { Icon(Icons.Default.People, contentDescription = "Explore") },
                    label = { Text("Explore", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted,
                        indicatorColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("nav_tab_explore")
                )

                // Tab 4: Settings
                NavigationBarItem(
                    selected = currentTab == StudioTab.SETTINGS,
                    onClick = { currentTab = StudioTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted,
                        indicatorColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } with
                            slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it }
                } else {
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it } with
                            slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it }
                }
            }
        ) { tab ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(CosmicBackground)
            ) {
                when (tab) {
                    StudioTab.STUDIO -> StudioScreen(viewModel = viewModel)
                    StudioTab.ARCHIVES -> DashboardScreen(viewModel = viewModel)
                    StudioTab.EXPLORE -> CommunityScreen(viewModel = viewModel)
                    StudioTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
