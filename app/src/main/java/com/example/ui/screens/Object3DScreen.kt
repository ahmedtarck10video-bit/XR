package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.CameraParams
import com.example.engine.Renderer3D
import com.example.ui.components.ModelHistoryCarouselOverlay
import com.example.viewmodel.MixedRealityUiState
import com.example.viewmodel.MixedRealityViewModel
import java.util.Locale

@Composable
fun Object3DScreen(
    uiState: MixedRealityUiState,
    viewModel: MixedRealityViewModel,
    modifier: Modifier = Modifier
) {
    val orientationData by viewModel.sensorTracker.orientation.collectAsState()
    var showStereoCalibration by remember { mutableStateOf(false) }

    // Combine gyro tracking with orbit angles when in headset VR split-screen mode
    val effectiveYaw = if (uiState.isSplitScreenStereo && uiState.isGyroHeadTracking && orientationData.isAvailable) {
        uiState.orbitYaw + orientationData.yaw
    } else {
        uiState.orbitYaw
    }

    val effectivePitch = if (uiState.isSplitScreenStereo && uiState.isGyroHeadTracking && orientationData.isAvailable) {
        (uiState.orbitPitch + orientationData.pitch).coerceIn(-85f, 85f)
    } else {
        uiState.orbitPitch
    }

    val camera = remember(effectiveYaw, effectivePitch, uiState.orbitDistance) {
        CameraParams(
            yawDeg = effectiveYaw,
            pitchDeg = effectivePitch,
            orbitDistance = uiState.orbitDistance,
            isFirstPerson = false
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E293B),
                        Color(0xFF0F172A),
                        Color(0xFF030712)
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        viewModel.resetOrbitCamera()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { _, pan, zoom, rotation ->
                    // 1. Pan / Drag -> Orbit Yaw and Pitch
                    if (pan.x != 0f || pan.y != 0f) {
                        viewModel.rotateOrbit(-pan.x * 0.35f, pan.y * 0.35f)
                    }

                    // 2. Multi-touch Pinch -> Multiplicative Smooth Zoom
                    if (zoom != 1.0f) {
                        viewModel.zoomOrbitFactor(zoom)
                    }

                    // 3. Two-finger Twist -> Angular Yaw Rotation
                    if (rotation != 0f) {
                        viewModel.rotateOrbit(-rotation * 0.85f, 0f)
                    }
                }
            }
    ) {
        if (uiState.isSplitScreenStereo) {
            // ==========================================
            // SPLIT-SCREEN STEREOSCOPIC 3D VIEW (DUAL EYE)
            // ==========================================
            Row(modifier = Modifier.fillMaxSize()) {
                // 1. Left Eye Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Left eye camera offset (-IPD / 2)
                        Renderer3D.renderFloorGrid(
                            drawScope = this,
                            camera = camera,
                            groundY = -1.0f,
                            gridSize = 4.0f,
                            gridSpacing = 0.5f,
                            isARMode = false,
                            stereoOffset = -uiState.stereoIPD * 0.5f
                        )

                        Renderer3D.renderMesh(
                            drawScope = this,
                            mesh = uiState.currentMesh,
                            camera = camera,
                            light = uiState.lightParams,
                            renderStyle = uiState.renderStyle,
                            stereoOffset = -uiState.stereoIPD * 0.5f,
                            wireframeOnly = uiState.wireframeOnly
                        )
                    }

                    // Left Eye Label & Alignment Guide
                    if (uiState.stereoLensGuide) {
                        Surface(
                            color = Color(0x66000000),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF))
                                )
                                Text(
                                    text = "LEFT EYE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }

                        // Optical Center Reticle
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0x6600E5FF))
                                .align(Alignment.Center)
                        )
                    }
                }

                // 2. Optical Center VR Divider Line (Prevents cross-eye optical bleed)
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x3338BDF8),
                                    Color(0xFF38BDF8),
                                    Color(0x3338BDF8)
                                )
                            )
                        )
                )

                // 3. Right Eye Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Right eye camera offset (+IPD / 2)
                        Renderer3D.renderFloorGrid(
                            drawScope = this,
                            camera = camera,
                            groundY = -1.0f,
                            gridSize = 4.0f,
                            gridSpacing = 0.5f,
                            isARMode = false,
                            stereoOffset = uiState.stereoIPD * 0.5f
                        )

                        Renderer3D.renderMesh(
                            drawScope = this,
                            mesh = uiState.currentMesh,
                            camera = camera,
                            light = uiState.lightParams,
                            renderStyle = uiState.renderStyle,
                            stereoOffset = uiState.stereoIPD * 0.5f,
                            wireframeOnly = uiState.wireframeOnly
                        )
                    }

                    // Right Eye Label & Alignment Guide
                    if (uiState.stereoLensGuide) {
                        Surface(
                            color = Color(0x66000000),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "RIGHT EYE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA855F7)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFA855F7))
                                )
                            }
                        }

                        // Optical Center Reticle
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0x66A855F7))
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        } else {
            // ==========================================
            // STANDARD SINGLE VIEWPORT 3D ORBIT VIEW
            // ==========================================
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 1. Draw floor grid
                Renderer3D.renderFloorGrid(
                    drawScope = this,
                    camera = camera,
                    groundY = -1.0f,
                    gridSize = 4.0f,
                    gridSpacing = 0.5f,
                    isARMode = false
                )

                // 2. Render 3D Model
                Renderer3D.renderMesh(
                    drawScope = this,
                    mesh = uiState.currentMesh,
                    camera = camera,
                    light = uiState.lightParams,
                    renderStyle = uiState.renderStyle,
                    wireframeOnly = uiState.wireframeOnly
                )
            }
        }

        // Top Model Quick Header / VR Split-Screen Mode Bar
        if (uiState.isSplitScreenStereo) {
            Surface(
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Vrpano,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Stereoscopic 3D Split-Screen",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0x3300E5FF)
                    ) {
                        Text(
                            text = "IPD ${String.format(Locale.US, "%.0f mm", uiState.stereoIPD * 1000f)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = { showStereoCalibration = !showStereoCalibration },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Tune VR Optics",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setSplitScreenStereo(false) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Split Screen",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            Surface(
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = uiState.currentMesh.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${uiState.currentMesh.vertexCount} Verts • ${uiState.currentMesh.faceCount} Polys",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Zoom multiplier badge
                            val zoomScale = 3.5f / uiState.orbitDistance
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1fx", zoomScale),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // VR Stereoscopic Optics Calibration Sheet
        if (showStereoCalibration && uiState.isSplitScreenStereo) {
            Surface(
                color = Color(0xEE0A0F1D),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4000E5FF)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 20.dp, end = 20.dp)
                    .widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "VR Headset Optics & IPD Calibration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = { showStereoCalibration = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // IPD Eye Separation Slider
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Interpupillary Distance (IPD)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f mm", uiState.stereoIPD * 1000f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color(0xFF00E5FF)
                            )
                        }
                        Slider(
                            value = uiState.stereoIPD,
                            onValueChange = { viewModel.updateStereoIPD(it) },
                            valueRange = 0.045f..0.085f,
                            steps = 40,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )
                    }

                    // Toggles for Gyro & Lens Alignment Reticles
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = uiState.isGyroHeadTracking,
                            onClick = { viewModel.toggleGyroHeadTracking() },
                            label = { Text("Gyro Head Track") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (uiState.isGyroHeadTracking) Icons.Default.ScreenRotation else Icons.Default.ScreenLockRotation,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = uiState.stereoLensGuide,
                            onClick = { viewModel.toggleStereoLensGuide() },
                            label = { Text("Lens Reticles") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusWeak,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Right-side Floating Action Toolbar
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            // Split-Screen Stereoscopic 3D VR Mode Toggle Button
            FilledTonalIconButton(
                onClick = { viewModel.toggleSplitScreenStereo() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (uiState.isSplitScreenStereo) Color(0xFF0284C7) else Color(0x991E293B)
                ),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("toggle_stereoscopic_split_screen")
            ) {
                Icon(
                    imageVector = Icons.Default.Vrpano,
                    contentDescription = "Toggle Stereoscopic Split-Screen 3D",
                    tint = if (uiState.isSplitScreenStereo) Color(0xFF00E5FF) else Color.White
                )
            }

            // Zoom In button
            FilledTonalIconButton(
                onClick = { viewModel.zoomOrbit(-0.4f) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x991E293B)),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In",
                    tint = Color.White
                )
            }

            // Zoom Out button
            FilledTonalIconButton(
                onClick = { viewModel.zoomOrbit(0.4f) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x991E293B)),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out",
                    tint = Color.White
                )
            }

            // Auto Rotate Turntable Toggle
            FilledTonalIconButton(
                onClick = { viewModel.toggleAutoRotate() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (uiState.isAutoRotating) MaterialTheme.colorScheme.primary else Color(0x991E293B)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Auto Rotate",
                    tint = if (uiState.isAutoRotating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
            }

            // Wireframe Toggle
            FilledTonalIconButton(
                onClick = { viewModel.toggleWireframe(!uiState.wireframeOnly) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (uiState.wireframeOnly) MaterialTheme.colorScheme.tertiary else Color(0x991E293B)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Grid4x4,
                    contentDescription = "Wireframe",
                    tint = if (uiState.wireframeOnly) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.tertiary
                )
            }

            // Dynamic Shadow Toggle
            FilledTonalIconButton(
                onClick = { viewModel.toggleShadows() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (uiState.lightParams.shadowsEnabled) MaterialTheme.colorScheme.secondary else Color(0x991E293B)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WbTwilight,
                    contentDescription = "Toggle Realistic Shadows",
                    tint = if (uiState.lightParams.shadowsEnabled) MaterialTheme.colorScheme.onSecondary else Color(0xFFFBBF24)
                )
            }

            // Reset Camera
            FilledTonalIconButton(
                onClick = { viewModel.resetOrbitCamera() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0x991E293B)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = "Reset View",
                    tint = Color.White
                )
            }

            // Snapshot Photo
            FilledTonalIconButton(
                onClick = { viewModel.captureSnapshot(1080, 1920) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0x991E293B)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture Snapshot",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Bottom Gesture Guidance Chip
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 86.dp)
        ) {
            Surface(
                color = Color(0x660F172A),
                shape = CircleShape,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isSplitScreenStereo) Icons.Default.Vrpano else Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (uiState.isSplitScreenStereo) "🥽 Dual Stereoscopic 3D • Place in VR Headset / Cardboard" else "Drag: Orbit • Pinch: Zoom • 2-Finger: Rotate • 2x Tap: Reset",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
