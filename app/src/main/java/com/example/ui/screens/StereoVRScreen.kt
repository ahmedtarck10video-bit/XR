package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.unit.dp
import com.example.engine.CameraParams
import com.example.engine.Renderer3D
import com.example.ui.components.ModelHistoryCarouselOverlay
import com.example.viewmodel.MixedRealityUiState
import com.example.viewmodel.MixedRealityViewModel

@Composable
fun StereoVRScreen(
    uiState: MixedRealityUiState,
    viewModel: MixedRealityViewModel,
    modifier: Modifier = Modifier
) {
    val orientationData by viewModel.sensorTracker.orientation.collectAsState()

    var showIpdSettings by remember { mutableStateOf(false) }

    // Combine gyro tracking with touch yaw/pitch
    val effectiveYaw = if (uiState.isGyroHeadTracking && orientationData.isAvailable) {
        uiState.walkYaw + orientationData.yaw
    } else {
        uiState.walkYaw
    }

    val effectivePitch = if (uiState.isGyroHeadTracking && orientationData.isAvailable) {
        (uiState.walkPitch + orientationData.pitch).coerceIn(-85f, 85f)
    } else {
        uiState.walkPitch
    }

    val baseCamera = remember(uiState.walkPos, effectiveYaw, effectivePitch) {
        CameraParams(
            posX = uiState.walkPos.x,
            posY = uiState.walkPos.y,
            posZ = uiState.walkPos.z,
            yawDeg = effectiveYaw,
            pitchDeg = effectivePitch,
            isFirstPerson = true
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    viewModel.rotateLook(-dragAmount.x * 0.25f, -dragAmount.y * 0.25f)
                }
            }
    ) {
        // Dual Viewport (Left Eye | Right Eye)
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Left stereo camera offset (-IPD / 2)
                    Renderer3D.renderFloorGrid(
                        drawScope = this,
                        camera = baseCamera,
                        groundY = -1.0f,
                        gridSize = 5.0f,
                        gridSpacing = 0.5f,
                        stereoOffset = -uiState.stereoIPD * 0.5f
                    )
                    Renderer3D.renderMesh(
                        drawScope = this,
                        mesh = uiState.currentMesh,
                        camera = baseCamera,
                        light = uiState.lightParams,
                        renderStyle = uiState.renderStyle,
                        stereoOffset = -uiState.stereoIPD * 0.5f,
                        wireframeOnly = uiState.wireframeOnly
                    )
                }

                // Left Eye Label
                Surface(
                    color = Color(0x66000000),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "LEFT EYE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Reticle Center
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0x8800E5FF))
                        .align(Alignment.Center)
                )
            }

            // Center Divider for VR Headset Lenses
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF334155))
            )

            // Right Eye Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Right stereo camera offset (+IPD / 2)
                    Renderer3D.renderFloorGrid(
                        drawScope = this,
                        camera = baseCamera,
                        groundY = -1.0f,
                        gridSize = 5.0f,
                        gridSpacing = 0.5f,
                        stereoOffset = uiState.stereoIPD * 0.5f
                    )
                    Renderer3D.renderMesh(
                        drawScope = this,
                        mesh = uiState.currentMesh,
                        camera = baseCamera,
                        light = uiState.lightParams,
                        renderStyle = uiState.renderStyle,
                        stereoOffset = uiState.stereoIPD * 0.5f,
                        wireframeOnly = uiState.wireframeOnly
                    )
                }

                // Right Eye Label
                Surface(
                    color = Color(0x66000000),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "RIGHT EYE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA855F7),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Reticle Center
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0x88A855F7))
                        .align(Alignment.Center)
                )
            }
        }

        // Top Control Overlay Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            // Gyro Tracking Toggle
            AssistChip(
                onClick = { viewModel.toggleGyroHeadTracking() },
                label = {
                    Text(
                        if (uiState.isGyroHeadTracking) "Gyro Tracking: ON" else "Gyro: OFF"
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (uiState.isGyroHeadTracking) Icons.Default.ScreenRotation else Icons.Default.ScreenLockRotation,
                        contentDescription = null,
                        tint = if (uiState.isGyroHeadTracking) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xDD0F172A)
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // IPD Calibration
                IconButton(
                    onClick = { showIpdSettings = !showIpdSettings },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xDD0F172A), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "IPD Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Calibrate / Reset
                IconButton(
                    onClick = {
                        viewModel.sensorTracker.resetOrientation()
                        viewModel.resetARAnchor()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xDD0F172A), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Center View",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // IPD adjustment popover
        if (showIpdSettings) {
            Surface(
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp, start = 24.dp, end = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Interpupillary Distance (IPD): ${(uiState.stereoIPD * 1000).toInt()}mm",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showIpdSettings = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                    Slider(
                        value = uiState.stereoIPD,
                        onValueChange = { viewModel.updateStereoIPD(it) },
                        valueRange = 0.040f..0.090f
                    )
                    Text(
                        text = "Adjust to match your VR headset or Cardboard optics for zero eye strain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bottom VR Controls & Quick Model Swap Carousel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        ) {
            ModelHistoryCarouselOverlay(
                historyList = uiState.recentHistory,
                currentMesh = uiState.currentMesh,
                onSelectModel = { selectedMesh ->
                    viewModel.selectMesh(selectedMesh)
                }
            )

            Surface(
                color = Color(0x990F172A),
                shape = CircleShape,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "🥽 Insert phone into VR Goggles / Cardboard for stereoscopic 3D depth",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
