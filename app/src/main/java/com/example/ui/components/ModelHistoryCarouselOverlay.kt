package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.entity.ModelHistoryEntity
import com.example.math3d.Mesh3D
import com.example.math3d.MeshPresets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModelHistoryCarouselOverlay(
    historyList: List<ModelHistoryEntity>,
    currentMesh: Mesh3D,
    onSelectModel: (Mesh3D) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Combine local Room history with all presets to ensure all available models are quickly selectable
    val historyMap = remember(historyList) {
        historyList.associateBy { it.meshId }
    }

    // Sort presets so that recently viewed models appear first based on history timestamp
    val sortedModels = remember(historyList) {
        MeshPresets.allPresets.sortedByDescending { preset ->
            historyList.filter { it.meshId == preset.id }.maxOfOrNull { it.timestamp } ?: 0L
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Animated Expandable Grid Carousel Sheet
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Surface(
                    color = Color(0xEE0A0F1D), // Cyber dark glass
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0x4000E5FF)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("model_history_grid_carousel")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        // Header Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x3300E5FF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Model Vault & History",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${sortedModels.size} Models Available • Tap to Swap",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Carousel",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Grid Carousel of 3D Models
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(sortedModels, key = { it.id }) { model ->
                                val isSelected = model.id == currentMesh.id
                                val historyCount = historyList.count { it.meshId == model.id }
                                val modelIcon = getModelIcon(model.id)

                                ModelGridItemCard(
                                    model = model,
                                    icon = modelIcon,
                                    isSelected = isSelected,
                                    viewCount = historyCount,
                                    onClick = {
                                        onSelectModel(model)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Collapsed Floating Quick-Bar / Toggle Trigger
            Surface(
                onClick = { isExpanded = !isExpanded },
                color = Color(0xDD0F172A),
                shape = CircleShape,
                border = BorderStroke(1.dp, if (isExpanded) Color(0xFF00E5FF) else Color(0x33FFFFFF)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .wrapContentWidth()
                    .testTag("quick_model_carousel_toggle")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.GridView,
                        contentDescription = "Toggle Models Carousel",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isExpanded) "Hide Model Vault" else "Quick Swap: ${currentMesh.name}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0x3300E5FF)
                    ) {
                        Text(
                            text = "${sortedModels.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelGridItemCard(
    model: Mesh3D,
    icon: ImageVector,
    isSelected: Boolean,
    viewCount: Int,
    onClick: () -> Unit
) {
    val cardBg = if (isSelected) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF0284C7), Color(0xFF0369A1))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0x331E293B), Color(0x220F172A))
        )
    }

    val borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0x22FFFFFF)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .testTag("model_grid_item_${model.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBg)
                .padding(6.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Icon
                Icon(
                    imageVector = icon,
                    contentDescription = model.name,
                    tint = if (isSelected) Color.White else Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                // Name
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                // Vertex / Poly count
                Text(
                    text = "${model.vertexCount}v • ${model.faceCount}f",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color(0xFF94A3B8),
                    fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                    textAlign = TextAlign.Center
                )
            }

            // Active Badge
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34D399))
                        .align(Alignment.TopEnd)
                )
            } else if (viewCount > 0) {
                // Has history view count tag
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0x44000000),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "${viewCount}x",
                        fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

private fun getModelIcon(meshId: String): ImageVector {
    return when (meshId) {
        "torus_knot" -> Icons.Default.AllInclusive
        "geodesic_sphere" -> Icons.Default.SportsBasketball
        "mobius_strip" -> Icons.Default.Loop
        "architectural_pavilion" -> Icons.Default.AccountBalance
        "hypercube_tesseract" -> Icons.Default.Category
        "cyber_drone" -> Icons.Default.AirplanemodeActive
        "dna_helix" -> Icons.Default.Biotech
        "teapot" -> Icons.Default.Coffee
        "crystal_cluster" -> Icons.Default.Diamond
        "klein_bottle" -> Icons.Default.Science
        "gyroscope" -> Icons.Default.Rotate90DegreesCcw
        "saturn_rings" -> Icons.Default.Public
        else -> Icons.Default.ViewInAr
    }
}
