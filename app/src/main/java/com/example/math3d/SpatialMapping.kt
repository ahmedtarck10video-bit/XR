package com.example.math3d

import kotlin.math.sqrt

data class SpatialFeaturePoint(
    val id: Int,
    val pos: Vec3,
    val confidence: Float,
    val isAnchorCandidate: Boolean = false,
    val clusterId: Int = 0
)

enum class PointCloudVisualizationMode(val label: String) {
    FULL_POINT_CLOUD("Points & Mesh"),
    POINTS_ONLY("Feature Dots"),
    CONFIDENCE_HEATMAP("Heatmap"),
    OFF("Hidden")
}

data class SpatialDiagnostics(
    val trackedPointsCount: Int = 112,
    val pointDensityPerSqM: Float = 42.5f,
    val planeConfidence: Float = 0.94f,
    val trackingStatus: String = "VIO Locked",
    val surfaceTiltDeg: Float = 0.4f,
    val mappedAreaSqM: Float = 5.2f,
    val isPlacementFeasible: Boolean = true,
    val distanceToAnchorM: Float = 2.1f,
    val surfaceType: String = "Horizontal Ground Plane"
)

object SpatialMappingGenerator {
    fun generateInitialPointCloud(groundY: Float = -1.0f): List<SpatialFeaturePoint> {
        val points = mutableListOf<SpatialFeaturePoint>()
        var idCounter = 0

        // Dense inner grid near origin (Anchor zone)
        for (gx in -6..6) {
            for (gz in -6..6) {
                val jitterX = ((gx * 17 + gz * 31) % 100) / 400.0f
                val jitterZ = ((gx * 23 + gz * 13) % 100) / 400.0f
                val posX = gx * 0.35f + jitterX
                val posZ = gz * 0.35f + jitterZ
                val distFromCenter = sqrt(posX * posX + posZ * posZ)
                
                // Confidence higher near center, tapering outwards
                val conf = (1.0f - (distFromCenter / 3.2f) * 0.45f).coerceIn(0.42f, 0.98f)
                val isCandidate = distFromCenter < 0.9f && conf > 0.82f

                points.add(
                    SpatialFeaturePoint(
                        id = idCounter++,
                        pos = Vec3(posX, groundY + ((gx * gz) % 5) * 0.002f, posZ),
                        confidence = conf,
                        isAnchorCandidate = isCandidate,
                        clusterId = if (distFromCenter < 1.2f) 0 else (gx + 10) % 3
                    )
                )
            }
        }
        return points
    }
}
