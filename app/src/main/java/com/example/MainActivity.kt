package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CompanionJokeCard
import com.example.ui.components.GlassPanelContainer
import com.example.ui.components.ModelInspectorSheet
import com.example.ui.components.ModelLibraryDialog
import com.example.ui.components.SnapshotDialog
import com.example.ui.screens.ARScreen
import com.example.ui.screens.Object3DScreen
import com.example.ui.screens.StereoVRScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppDisplayMode
import com.example.viewmodel.MixedRealityViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MixedRealityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SpatialStudioScreen(viewModel = viewModel)
            }
        }
    }
}

// =========================================================
// Complete Spatial Studio Screen with Apple Liquid Glass UI
// =========================================================
@Composable
fun SpatialStudioScreen(
    viewModel: MixedRealityViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    val currentModeStr = when (uiState.displayMode) {
        AppDisplayMode.MODE_STEREO -> "MR"
        AppDisplayMode.MODE_AR -> "AR"
        AppDisplayMode.MODE_3D -> "Object"
    }

    // Studio radial gradient background
    val studioGradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFF38383B), // Inner light center
            Color(0xFF1A1A1C), // Mid transition
            Color(0xFF0D0D0E)  // Outer dark background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(studioGradient)
    ) {
        // -----------------------------------------------------
        // Center Area: 3D Model View / AR / MR Dual Stream
        // -----------------------------------------------------
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState.displayMode) {
                AppDisplayMode.MODE_3D -> {
                    Object3DScreen(uiState = uiState, viewModel = viewModel)
                }
                AppDisplayMode.MODE_AR -> {
                    ARScreen(uiState = uiState, viewModel = viewModel)
                }
                AppDisplayMode.MODE_STEREO -> {
                    StereoVRScreen(uiState = uiState, viewModel = viewModel)
                }
            }
        }

        // -----------------------------------------------------
        // Top Section: Exit, Full Mode Switcher (MR, AR, Object), Share
        // -----------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exit Button (resets/clears scene or closes active overlays)
            GlassPanelContainer(modifier = Modifier.testTag("exit_glass_button")) {
                Button(
                    onClick = {
                        viewModel.clearCurrentScene()
                        if (uiState.showJokeCard) viewModel.toggleJokeCard(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Central Mode Switcher including MR, AR, and Object
            GlassPanelContainer(modifier = Modifier.testTag("mode_switcher_glass_panel")) {
                listOf("MR", "AR", "Object").forEach { mode ->
                    val isActive = currentModeStr == mode
                    Button(
                        onClick = {
                            val newMode = when (mode) {
                                "MR" -> AppDisplayMode.MODE_STEREO
                                "AR" -> AppDisplayMode.MODE_AR
                                else -> AppDisplayMode.MODE_3D
                            }
                            viewModel.setDisplayMode(newMode)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(22.dp),
                        elevation = null,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("nav_mode_${mode.lowercase()}")
                    ) {
                        Text(
                            text = mode,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Share / Inspector Button
            GlassPanelContainer(modifier = Modifier.testTag("share_glass_button")) {
                Button(
                    onClick = { viewModel.setInspectorVisible(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("⎘", color = Color.White, fontSize = 16.sp)
                }
            }
        }

        // -----------------------------------------------------
        // Bottom Section: Liquid Glass Action Bar
        // -----------------------------------------------------
        GlassPanelContainer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("bottom_glass_action_bar")
        ) {
            Button(
                onClick = { viewModel.captureSnapshot(1080, 1920) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("photo_action_button")
            ) {
                Text("PHOTO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.toggleRecording() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRecording) Color(0xFFFF3B30) else Color(0x33FF3B30),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("rec_action_button")
            ) {
                Text(
                    text = if (uiState.isRecording) "● REC ${uiState.recordingSeconds}s" else "● REC",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { viewModel.setModelLibraryVisible(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("open_action_button")
            ) {
                Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.clearCurrentScene() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("clear_action_button")
            ) {
                Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Floating HoloBot Joke Companion Overlay (if active)
        AnimatedVisibility(
            visible = uiState.showJokeCard,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            CompanionJokeCard(
                jokeText = uiState.jokeText,
                isLoading = uiState.isJokeLoading,
                onFetchJoke = { viewModel.fetchJoke() },
                onDismiss = { viewModel.toggleJokeCard(false) }
            )
        }
    }

    // 3D Asset Library Dialog
    if (uiState.showModelLibrary) {
        ModelLibraryDialog(
            selectedMeshId = uiState.currentMesh.id,
            recentHistory = uiState.recentHistory,
            onSelectMesh = { mesh ->
                viewModel.selectMesh(mesh)
            },
            onSelectMeshById = { meshId ->
                viewModel.selectMeshById(meshId)
            },
            onDeleteHistoryItem = { id ->
                viewModel.deleteHistoryItem(id)
            },
            onClearAllHistory = {
                viewModel.clearAllHistory()
            },
            onDismiss = { viewModel.setModelLibraryVisible(false) }
        )
    }

    // Inspector & Shaders Sheet
    if (uiState.showInspector) {
        ModelInspectorSheet(
            mesh = uiState.currentMesh,
            renderStyle = uiState.renderStyle,
            lightParams = uiState.lightParams,
            wireframeOnly = uiState.wireframeOnly,
            onRenderStyleChange = { viewModel.setRenderStyle(it) },
            onLightChange = { viewModel.updateLightParams(it) },
            onWireframeToggle = { viewModel.toggleWireframe(it) },
            onDismiss = { viewModel.setInspectorVisible(false) }
        )
    }

    // Snapshot Preview Dialog
    if (uiState.showSnapshotDialog) {
        SnapshotDialog(
            bitmap = uiState.capturedSnapshot,
            modelName = uiState.currentMesh.name,
            modeName = uiState.displayMode.title,
            onDismiss = { viewModel.dismissSnapshotDialog() }
        )
    }
}

