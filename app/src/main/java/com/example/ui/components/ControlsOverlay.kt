package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    onMove: (dx: Float, dz: Float) -> Unit,
    onRelease: () -> Unit = {}
) {
    var knobOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val maxRadius = 45f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(110.dp)
            .clip(CircleShape)
            .background(Color(0x660F172A))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        knobOffset = androidx.compose.ui.geometry.Offset.Zero
                        onRelease()
                    },
                    onDragCancel = {
                        knobOffset = androidx.compose.ui.geometry.Offset.Zero
                        onRelease()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = knobOffset + dragAmount
                        val dist = newOffset.getDistance()
                        knobOffset = if (dist > maxRadius) {
                            newOffset * (maxRadius / dist)
                        } else {
                            newOffset
                        }
                        // Normalize to [-1, 1]
                        val normX = knobOffset.x / maxRadius
                        val normZ = -knobOffset.y / maxRadius
                        onMove(normX, normZ)
                    }
                )
            }
    ) {
        // Outer ring indicator
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0x3338BDF8))
        )
        // Center knob
        Box(
            modifier = Modifier
                .offset(x = knobOffset.x.dp, y = knobOffset.y.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = "Walk Joystick",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun HeightControls(
    modifier: Modifier = Modifier,
    onHeightChange: (delta: Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x990F172A))
            .padding(6.dp)
    ) {
        IconButton(
            onClick = { onHeightChange(0.15f) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Elevate Up",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(
            onClick = onReset,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = "Reset Height",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        IconButton(
            onClick = { onHeightChange(-0.15f) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Elevate Down",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
