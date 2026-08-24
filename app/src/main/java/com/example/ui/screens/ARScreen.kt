package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.CameraParams
import com.example.engine.Renderer3D
import com.example.math3d.PointCloudVisualizationMode
import com.example.math3d.Vec3
import com.example.ui.components.CameraPreviewContainer
import com.example.ui.components.HeightControls
import com.example.ui.components.ModelHistoryCarouselOverlay
import com.example.ui.components.SpatialDiagnosticsOverlay
import com.example.ui.components.VirtualJoystick
import com.example.viewmodel.MixedRealityUiState
import com.example.viewmodel.MixedRealityViewModel
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun ARScreen(
    uiState: MixedRealityUiState,
    viewModel: MixedRealityViewModel,
    modifier: Modifier = Modifier
) {
    var showAnchorControls by remember { mutableStateOf(false) }

    CameraPreviewContainer(
        enableCamera = true,
        modifier = modifier
    ) {
        val camera = remember(uiState.walkPos, uiState.walkYaw, uiState.walkPitch) {
            CameraParams(
                posX = uiState.walkPos.x,
                posY = uiState.walkPos.y,
                posZ = uiState.walkPos.z,
                yawDeg = uiState.walkYaw,
                pitchDeg = uiState.walkPitch,
                isFirstPerson = true
            )
        }

        // 3D Canvas on top of Camera Stream with interactive world surface pinning
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(uiState.isARAnchorLocked) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                if (!uiState.isARAnchorLocked) {
                                    viewModel.pinModelAtScreenTap(
                                        screenX = tapOffset.x,
                                        screenY = tapOffset.y,
                                        screenWidth = screenWidth,
                                        screenHeight = screenHeight
                                    )
                                }
                            },
                            onDoubleTap = {
                                viewModel.toggleAnchorLock()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures(panZoomLock = false) { _, pan, zoom, rotation ->
                            // 1. Single finger pan / drag -> First-person camera look around
                            if (pan.x != 0f || pan.y != 0f) {
                                viewModel.rotateLook(-pan.x * 0.25f, -pan.y * 0.25f)
                            }
                            // 2. Pinch -> Scale pinned 3D model
                            if (zoom != 1.0f) {
                                viewModel.updateARModelScale(zoom)
                            }
                            // 3. Two-finger twist -> Rotate pinned 3D model on its anchor axis
                            if (rotation != 0f) {
                                viewModel.rotateARModel(-rotation * 1.2f)
                            }
                        }
                    }
            ) {
                // 1. Render AR Ground Plane Grid with animated sonar pulse centered at anchor
                Renderer3D.renderFloorGrid(
                    drawScope = this,
                    camera = camera,
                    groundY = uiState.arGroundY,
                    gridSize = 6.0f,
                    gridSpacing = 0.5f,
                    sonarAnimProgress = uiState.arSonarProgress,
                    isARMode = true,
                    isPlaced = uiState.isARPlaced,
                    anchorPos = uiState.arAnchorPos
                )

                // 2. Render Real-time Spatial Feature Point Cloud & Surface Tessellation
                Renderer3D.renderSpatialPointCloud(
                    drawScope = this,
                    camera = camera,
                    points = uiState.spatialPoints,
                    visualizationMode = uiState.pointCloudMode,
                    sonarAnimProgress = uiState.arSonarProgress,
                    isPlaced = uiState.isARPlaced
                )

                // 3. Render Pinned Surface Anchor Ground Rings & Holographic Pedestal
                if (uiState.isARPlaced) {
                    Renderer3D.renderSurfaceAnchor(
                        drawScope = this,
                        camera = camera,
                        anchorPos = uiState.arAnchorPos,
                        isLocked = uiState.isARAnchorLocked,
                        modelElevation = uiState.arModelElevation,
                        sonarAnimProgress = uiState.arSonarProgress
                    )

                    // 4. Render 3D Model firmly pinned at world coordinate space
                    val modelWorldPos = uiState.arAnchorPos + Vec3(0f, uiState.arModelElevation, 0f)
                    Renderer3D.renderMesh(
                        drawScope = this,
                        mesh = uiState.currentMesh,
                        camera = camera,
                        light = uiState.lightParams,
                        renderStyle = uiState.renderStyle,
                        modelTransform = modelWorldPos,
                        modelRotation = Vec3(0f, uiState.arModelRotationY, 0f),
                        modelScale = uiState.arModelScale,
                        wireframeOnly = uiState.wireframeOnly
                    )
                }
            }
        }

        // Top Spatial Mapping Diagnostics HUD Overlay
        if (uiState.showSpatialDiagnostics) {
            SpatialDiagnosticsOverlay(
                diagnostics = uiState.spatialDiagnostics,
                pointCloudMode = uiState.pointCloudMode,
                isScanning = uiState.isSurfaceScanning,
                isPlaced = uiState.isARPlaced,
                onPointCloudModeChange = { mode -> viewModel.setPointCloudMode(mode) },
                onRescanSurface = { viewModel.rescanSurface() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }

        // Top-Center Pinned World Space Anchor Status Bar
        if (uiState.isARPlaced) {
            val dist = (uiState.walkPos - uiState.arAnchorPos).length()
            Surface(
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (uiState.isARAnchorLocked) Color(0xFF10B981) else Color(0xFF00E5FF)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isARAnchorLocked) Icons.Default.Lock else Icons.Default.PinDrop,
                        contentDescription = null,
                        tint = if (uiState.isARAnchorLocked) Color(0xFF10B981) else Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (uiState.isARAnchorLocked) "Surface Anchor Locked" else "Surface Pinned",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0x3300E5FF)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1fm away", dist),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = { showAnchorControls = !showAnchorControls },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Tune Anchor",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Pinned Anchor Fine-Tuning Sheet
        if (showAnchorControls && uiState.isARPlaced) {
            Surface(
                color = Color(0xEE0A0F1D),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4000E5FF)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp, start = 20.dp, end = 20.dp)
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Surface Anchor Controls",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = { showAnchorControls = false },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // World Coordinate Readout
                    Text(
                        text = String.format(
                            Locale.US,
                            "Anchor World Pos: (X: %.2fm, Y: %.2fm, Z: %.2fm)",
                            uiState.arAnchorPos.x,
                            uiState.arAnchorPos.y,
                            uiState.arAnchorPos.z
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )

                    // Lock and Re-center Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.toggleAnchorLock() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isARAnchorLocked) Color(0xFF10B981) else Color(0xFF0284C7)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (uiState.isARAnchorLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (uiState.isARAnchorLocked) "Unlock Pin" else "Lock Pin", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetARAnchor() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Center Pin", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Surface Elevation Control
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Surface Elevation: ${String.format(Locale.US, "%.2fm", uiState.arModelElevation)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE2E8F0)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick = { viewModel.updateModelElevation(-0.05f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Lower", modifier = Modifier.size(14.dp))
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.updateModelElevation(0.05f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Raise", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Right-side Floating Action Toolbar
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            // Toggle Anchor Pin Lock
            FilledTonalIconButton(
                onClick = { viewModel.toggleAnchorLock() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (uiState.isARAnchorLocked) Color(0xFF10B981) else Color(0x991E293B)
                ),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("toggle_ar_anchor_lock")
            ) {
                Icon(
                    imageVector = if (uiState.isARAnchorLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Toggle Pin Lock",
                    tint = if (uiState.isARAnchorLocked) Color.White else Color(0xFF00E5FF)
                )
            }

            // Toggle Spatial Diagnostics HUD
            FilledTonalIconButton(
                onClick = { viewModel.toggleSpatialDiagnostics() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (uiState.showSpatialDiagnostics) MaterialTheme.colorScheme.primary else Color(0x991E293B)
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = "Toggle Spatial Diagnostics",
                    tint = if (uiState.showSpatialDiagnostics) MaterialTheme.colorScheme.onPrimary else Color(0xFF00E5FF)
                )
            }

            // Cycle Point Cloud Overlay Style
            FilledTonalIconButton(
                onClick = { viewModel.cyclePointCloudMode() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (uiState.pointCloudMode != PointCloudVisualizationMode.OFF) Color(0x990284C7) else Color(0x991E293B)
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Grain,
                    contentDescription = "Cycle Point Cloud Mode",
                    tint = Color.White
                )
            }

            // Rescan / Recalibrate Plane
            FilledTonalIconButton(
                onClick = { viewModel.rescanSurface() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x991E293B)),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = "Rescan Surface",
                    tint = Color(0xFFFBBF24)
                )
            }

            // Re-anchor to origin
            FilledTonalIconButton(
                onClick = { viewModel.resetARAnchor() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x991E293B)),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center Model",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Snapshot Photo
            FilledTonalIconButton(
                onClick = { viewModel.captureSnapshot(1080, 1920) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x991E293B)),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture Snapshot",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Height Controls (Elevate camera Up/Down)
        HeightControls(
            onHeightChange = { delta -> viewModel.changeWalkHeight(delta) },
            onReset = { viewModel.changeWalkHeight(-uiState.walkPos.y) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )

        // Bottom Left Walk Joystick
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 86.dp)
        ) {
            VirtualJoystick(
                onMove = { stickX, stickZ ->
                    viewModel.walkMove(stickX, stickZ)
                }
            )
        }

        // Bottom Center Guidance & Tap Pin Hint
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 86.dp)
        ) {
            if (!uiState.isARPlaced) {
                Button(
                    onClick = { viewModel.placeARModel() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xCC0284C7)
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PinDrop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tap Screen to Pin on Detected Surface")
                }
            }

            Surface(
                color = Color(0x660F172A),
                shape = CircleShape,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = if (uiState.isARAnchorLocked) "🔒 Anchor Locked • Joystick to walk 360° around object" else "Tap Surface: Pin Anchor • Joystick: Walk • Drag: Look Around",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

