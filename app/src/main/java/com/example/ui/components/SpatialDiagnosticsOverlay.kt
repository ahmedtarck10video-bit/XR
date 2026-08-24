package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.math3d.PointCloudVisualizationMode
import com.example.math3d.SpatialDiagnostics
import java.util.Locale

@Composable
fun SpatialDiagnosticsOverlay(
    diagnostics: SpatialDiagnostics,
    pointCloudMode: PointCloudVisualizationMode,
    isScanning: Boolean,
    isPlaced: Boolean,
    onPointCloudModeChange: (PointCloudVisualizationMode) -> Unit,
    onRescanSurface: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xEB0A0F1D), // Dark cybernetic glass
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (diagnostics.isPlacementFeasible) Color(0x4000E5FF) else Color(0x40F59E0B)
        ),
        modifier = modifier
            .widthIn(max = 360.dp)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header: Status + Density + Minimize/Expand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsing Status Dot
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isScanning) Color(0x33F59E0B)
                                else if (diagnostics.isPlacementFeasible) Color(0x3310B981)
                                else Color(0x33EF4444)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isScanning) Color(0xFFF59E0B)
                                    else if (diagnostics.isPlacementFeasible) Color(0xFF10B981)
                                    else Color(0xFFEF4444)
                                )
                        )
                    }

                    Column {
                        Text(
                            text = if (isScanning) "Scanning Surface..." else "Spatial Mapping VIO",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${diagnostics.trackedPointsCount} Points • ${String.format(Locale.US, "%.1f", diagnostics.pointDensityPerSqM)} pts/m²",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick mode badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x3300E5FF)
                    ) {
                        Text(
                            text = pointCloudMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse Diagnostics" else "Expand Diagnostics",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick placement feasibility indicator bar
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (diagnostics.isPlacementFeasible) Color(0x2210B981) else Color(0x22F59E0B)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (diagnostics.isPlacementFeasible) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (diagnostics.isPlacementFeasible) Color(0xFF34D399) else Color(0xFFFBBF24),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (isPlaced) "Anchor Fixed • 3D Object Rendered"
                           else if (diagnostics.isPlacementFeasible) "Surface Suitable • Tap to Place Object"
                           else "Adjust Angle: Scan Floor Surface",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (diagnostics.isPlacementFeasible) Color(0xFF34D399) else Color(0xFFFBBF24),
                    fontWeight = FontWeight.Medium
                )
            }

            // Expanded Diagnostics Details
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(10.dp))

                // Density & Confidence Meters
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Density Gauge
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Point Cloud Density",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = if (diagnostics.pointDensityPerSqM > 40f) "Optimal (High)" else "Adequate",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { (diagnostics.pointDensityPerSqM / 60f).coerceIn(0.1f, 1.0f) },
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0x331E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                        )
                    }

                    // Plane Tracking Confidence
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Plane Confidence",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${(diagnostics.planeConfidence * 100).toInt()}% Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { diagnostics.planeConfidence },
                            color = Color(0xFF10B981),
                            trackColor = Color(0x331E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Telemetry Data Grid
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DiagnosticMetricItem(
                        label = "Surface Incline",
                        value = "${String.format(Locale.US, "%.1f", diagnostics.surfaceTiltDeg)}° (Flat)"
                    )
                    DiagnosticMetricItem(
                        label = "Mapped Area",
                        value = "${String.format(Locale.US, "%.1f", diagnostics.mappedAreaSqM)} m²"
                    )
                    DiagnosticMetricItem(
                        label = "Anchor Range",
                        value = "${String.format(Locale.US, "%.1f", diagnostics.distanceToAnchorM)} m"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Point Cloud Visualizer Selector
                Text(
                    text = "Point Cloud Overlay Style",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PointCloudVisualizationMode.values().forEach { mode ->
                        val isSelected = pointCloudMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPointCloudModeChange(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF),
                                selectedLabelColor = Color(0xFF0F172A),
                                containerColor = Color(0x331E293B),
                                labelColor = Color.White.copy(alpha = 0.8f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color(0x4000E5FF)
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rescan / Recalibrate button
                OutlinedButton(
                    onClick = onRescanSurface,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00E5FF)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6600E5FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Rescan & Calibrate Plane",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
