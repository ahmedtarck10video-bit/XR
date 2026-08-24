package com.example.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.math3d.*
import kotlin.math.*

enum class RenderStyle(val label: String) {
    SHADED_LIT("Solid Shaded"),
    HOLOGRAM("Hologram Matrix"),
    CYBER_WIREFRAME("Cyber Wireframe"),
    EMERALD_GLOW("Emerald Pulse"),
    RAINBOW_NORMAL("Normal Shading")
}

enum class EnvironmentType(val label: String) {
    DEEP_SPACE("Deep Space"),
    CYBER_GRID("Cyber Grid"),
    STUDIO_LIGHT("Minimalist Studio"),
    AR_PASSTHROUGH("AR Passthrough")
}

data class CameraParams(
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val fovDeg: Float = 60f,
    val orbitDistance: Float = 3.5f,
    val isFirstPerson: Boolean = false
)

data class LightParams(
    val azimuthDeg: Float = 45f,
    val elevationDeg: Float = 45f,
    val ambientIntensity: Float = 0.35f,
    val directionalIntensity: Float = 0.75f,
    val specularIntensity: Float = 0.4f,
    val shadowsEnabled: Boolean = true,
    val shadowIntensity: Float = 0.6f,
    val sceneIntensity: Float = 1.0f
) {
    val direction: Vec3 by lazy {
        val azRad = Math.toRadians(azimuthDeg.toDouble()).toFloat()
        val elRad = Math.toRadians(elevationDeg.toDouble()).toFloat()
        Vec3(
            cos(elRad) * sin(azRad),
            sin(elRad),
            cos(elRad) * cos(azRad)
        ).normalized()
    }
}

object Renderer3D {

    /**
     * Projects a 3D point to screen coordinates
     */
    fun projectPoint(
        point: Vec3,
        camera: CameraParams,
        screenWidth: Float,
        screenHeight: Float,
        stereoOffset: Float = 0f
    ): ProjectedVertex? {
        val radYaw = Math.toRadians(camera.yawDeg.toDouble()).toFloat()
        val radPitch = Math.toRadians(camera.pitchDeg.toDouble()).toFloat()

        // World to Camera space transform
        val relPos = if (camera.isFirstPerson) {
            // Camera position is at (posX + stereoOffset, posY, posZ)
            val camPos = Vec3(camera.posX + stereoOffset, camera.posY, camera.posZ)
            val translated = point - camPos
            // Rotate by camera pitch and yaw
            translated.rotateY(-radYaw).rotateX(-radPitch)
        } else {
            // Orbit camera around model center
            val rotated = point.rotateY(radYaw).rotateX(radPitch)
            Vec3(rotated.x - stereoOffset, rotated.y, rotated.z + camera.orbitDistance)
        }

        // Near clipping plane check
        if (relPos.z <= 0.15f) return null

        val fovRad = Math.toRadians(camera.fovDeg.toDouble()).toFloat()
        val f = 1.0f / tan(fovRad / 2.0f)
        val aspect = screenWidth / screenHeight

        val projX = (relPos.x * f / aspect) / relPos.z
        val projY = -(relPos.y * f) / relPos.z // Invert Y for screen space

        val screenX = (projX + 1.0f) * 0.5f * screenWidth
        val screenY = (projY + 1.0f) * 0.5f * screenHeight

        return ProjectedVertex(
            screenX = screenX,
            screenY = screenY,
            depth = relPos.z,
            worldPos = point
        )
    }

    /**
     * Renders a 3D mesh onto the Compose Canvas DrawScope
     */
    fun renderMesh(
        drawScope: DrawScope,
        mesh: Mesh3D,
        camera: CameraParams,
        light: LightParams,
        renderStyle: RenderStyle,
        modelTransform: Vec3 = Vec3(0f, 0f, 0f),
        modelRotation: Vec3 = Vec3(0f, 0f, 0f),
        modelScale: Float = 1.0f,
        stereoOffset: Float = 0f,
        wireframeOnly: Boolean = false
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height

        if (width <= 0 || height <= 0 || mesh.vertices.isEmpty()) return

        // 1. Transform mesh vertices to world space
        val rotXRad = Math.toRadians(modelRotation.x.toDouble()).toFloat()
        val rotYRad = Math.toRadians(modelRotation.y.toDouble()).toFloat()
        val rotZRad = Math.toRadians(modelRotation.z.toDouble()).toFloat()

        val worldVertices = mesh.vertices.map { v ->
            val scaled = v * modelScale
            val rotated = scaled.rotateX(rotXRad).rotateY(rotYRad).rotateZ(rotZRad)
            rotated + modelTransform
        }

        // 2. Project all vertices to screen
        val projectedVertices = worldVertices.map { v ->
            projectPoint(v, camera, width, height, stereoOffset)
        }

        // 3. Assemble and filter triangles
        val trianglesToDraw = mutableListOf<ProjectedTriangle>()
        val lightDir = light.direction

        for (face in mesh.faces) {
            if (face.v1 >= projectedVertices.size || face.v2 >= projectedVertices.size || face.v3 >= projectedVertices.size) continue

            val p1 = projectedVertices[face.v1] ?: continue
            val p2 = projectedVertices[face.v2] ?: continue
            val p3 = projectedVertices[face.v3] ?: continue

            val w1 = worldVertices[face.v1]
            val w2 = worldVertices[face.v2]
            val w3 = worldVertices[face.v3]

            // Calculate surface normal
            val edge1 = w2 - w1
            val edge2 = w3 - w1
            val normal = edge1.cross(edge2).normalized()

            // Backface culling in view space
            val camPos = if (camera.isFirstPerson) {
                Vec3(camera.posX + stereoOffset, camera.posY, camera.posZ)
            } else {
                val radYaw = Math.toRadians(camera.yawDeg.toDouble()).toFloat()
                val radPitch = Math.toRadians(camera.pitchDeg.toDouble()).toFloat()
                Vec3(0f, 0f, -camera.orbitDistance).rotateX(-radPitch).rotateY(-radYaw)
            }
            val viewDir = (camPos - (w1 + w2 + w3) / 3f).normalized()
            val dotView = normal.dot(viewDir)

            // Keep all faces in wireframe mode, or front-facing in shaded mode
            if (!wireframeOnly && dotView < -0.15f) continue

            // Lighting computation scaled by scene intensity
            val dotLight = max(0f, normal.dot(lightDir))
            val baseLight = (light.ambientIntensity + light.directionalIntensity * dotLight) * light.sceneIntensity
            val intensity = baseLight.coerceIn(0.05f, 2.0f)

            // Specular reflection
            val halfVector = (lightDir + viewDir).normalized()
            val specDot = max(0f, normal.dot(halfVector))
            val specular = (specDot.toDouble().pow(16.0).toFloat() * light.specularIntensity * light.sceneIntensity).coerceIn(0f, 1.0f)

            val baseCol = face.baseColor
            val r = (((baseCol shr 16) and 0xFF) / 255f)
            val g = (((baseCol shr 8) and 0xFF) / 255f)
            val b = ((baseCol and 0xFF) / 255f)

            val finalColorInt = when (renderStyle) {
                RenderStyle.SHADED_LIT -> {
                    val finalR = (r * intensity + specular).coerceIn(0f, 1f)
                    val finalG = (g * intensity + specular).coerceIn(0f, 1f)
                    val finalB = (b * intensity + specular).coerceIn(0f, 1f)
                    (0xFF shl 24) or
                            ((finalR * 255).toInt() shl 16) or
                            ((finalG * 255).toInt() shl 8) or
                            (finalB * 255).toInt()
                }
                RenderStyle.HOLOGRAM -> {
                    val pulse = (sin(p1.worldPos.y * 5f) * 0.2f + 0.8f).coerceIn(0.5f, 1.0f) * light.sceneIntensity.coerceIn(0.4f, 1.5f)
                    val alpha = if (wireframeOnly) 0xFF else 0x99
                    (alpha shl 24) or
                            (((0.1f * pulse) * 255).toInt().coerceIn(0, 255) shl 16) or
                            (((0.9f * pulse) * 255).toInt().coerceIn(0, 255) shl 8) or
                            (((1.0f * pulse) * 255).toInt().coerceIn(0, 255))
                }
                RenderStyle.CYBER_WIREFRAME -> {
                    (0xFF shl 24) or 0x00E5FF
                }
                RenderStyle.EMERALD_GLOW -> {
                    val alpha = if (wireframeOnly) 0xFF else 0xCC
                    (alpha shl 24) or
                            (((0.05f * intensity) * 255).toInt().coerceIn(0, 255) shl 16) or
                            (((0.95f * intensity + specular) * 255).toInt().coerceIn(0, 255) shl 8) or
                            (((0.45f * intensity) * 255).toInt().coerceIn(0, 255))
                }
                RenderStyle.RAINBOW_NORMAL -> {
                    val nr = (((normal.x + 1f) * 0.5f) * light.sceneIntensity.coerceIn(0.3f, 1.2f)).coerceIn(0f, 1f)
                    val ng = (((normal.y + 1f) * 0.5f) * light.sceneIntensity.coerceIn(0.3f, 1.2f)).coerceIn(0f, 1f)
                    val nb = (((normal.z + 1f) * 0.5f) * light.sceneIntensity.coerceIn(0.3f, 1.2f)).coerceIn(0f, 1f)
                    (0xFF shl 24) or
                            ((nr * 255).toInt() shl 16) or
                            ((ng * 255).toInt() shl 8) or
                            (nb * 255).toInt()
                }
            }

            val avgDepth = (p1.depth + p2.depth + p3.depth) / 3.0f
            trianglesToDraw.add(
                ProjectedTriangle(p1, p2, p3, normal, finalColorInt, avgDepth)
            )
        }

        // 3.5. Dynamic Planar Shadow Projection onto Ground Plane
        if (light.shadowsEnabled && renderStyle != RenderStyle.CYBER_WIREFRAME && !wireframeOnly) {
            val groundY = -1.0f
            val shadowColorAlpha = ((light.shadowIntensity * 0.75f * light.sceneIntensity.coerceIn(0.2f, 1.2f)).coerceIn(0.05f, 0.85f) * 255).toInt()
            val shadowColorInt = (shadowColorAlpha shl 24) or 0x0A0F1D // Rich dark shadow

            // Calculate directional shadow projection on plane y = groundY
            val ly = max(0.2f, lightDir.y)
            for (face in mesh.faces) {
                val w1 = worldVertices[face.v1]
                val w2 = worldVertices[face.v2]
                val w3 = worldVertices[face.v3]

                // Project each vertex along light direction onto ground plane
                val t1 = (groundY - w1.y) / ly
                val t2 = (groundY - w2.y) / ly
                val t3 = (groundY - w3.y) / ly

                val s1 = Vec3(w1.x - lightDir.x * t1, groundY + 0.005f, w1.z - lightDir.z * t1)
                val s2 = Vec3(w2.x - lightDir.x * t2, groundY + 0.005f, w2.z - lightDir.z * t2)
                val s3 = Vec3(w3.x - lightDir.x * t3, groundY + 0.005f, w3.z - lightDir.z * t3)

                val ps1 = projectPoint(s1, camera, width, height, stereoOffset) ?: continue
                val ps2 = projectPoint(s2, camera, width, height, stereoOffset) ?: continue
                val ps3 = projectPoint(s3, camera, width, height, stereoOffset) ?: continue

                val avgShadowDepth = (ps1.depth + ps2.depth + ps3.depth) / 3.0f + 0.01f
                trianglesToDraw.add(
                    ProjectedTriangle(ps1, ps2, ps3, Vec3(0f, 1f, 0f), shadowColorInt, avgShadowDepth)
                )
            }
        }

        // 4. Sort triangles from farthest to nearest (Painter's Algorithm)
        trianglesToDraw.sortByDescending { it.avgDepth }

        // 5. Draw triangles onto Compose Canvas
        val path = Path()
        for (tri in trianglesToDraw) {
            path.reset()
            path.moveTo(tri.p1.screenX, tri.p1.screenY)
            path.lineTo(tri.p2.screenX, tri.p2.screenY)
            path.lineTo(tri.p3.screenX, tri.p3.screenY)
            path.close()

            val c = Color(tri.color)

            if (!wireframeOnly && renderStyle != RenderStyle.CYBER_WIREFRAME) {
                drawScope.drawPath(path, color = c, style = Fill)
            }

            // Draw wireframe overlay
            if (wireframeOnly || renderStyle == RenderStyle.HOLOGRAM || renderStyle == RenderStyle.CYBER_WIREFRAME) {
                val wireColor = when (renderStyle) {
                    RenderStyle.HOLOGRAM -> Color(0xFF00E5FF).copy(alpha = 0.8f)
                    RenderStyle.EMERALD_GLOW -> Color(0xFF10B981).copy(alpha = 0.9f)
                    RenderStyle.CYBER_WIREFRAME -> Color(0xFF00E5FF)
                    else -> Color.White.copy(alpha = 0.35f)
                }
                drawScope.drawPath(
                    path,
                    color = wireColor,
                    style = Stroke(width = if (renderStyle == RenderStyle.CYBER_WIREFRAME) 1.8f else 1.0f)
                )
            }
        }
    }

    /**
     * Renders 3D perspective floor grid and AR ground plane tracker
     */
    fun renderFloorGrid(
        drawScope: DrawScope,
        camera: CameraParams,
        groundY: Float = -1.0f,
        gridSize: Float = 6.0f,
        gridSpacing: Float = 0.5f,
        sonarAnimProgress: Float = 0f,
        isARMode: Boolean = false,
        isPlaced: Boolean = true,
        anchorPos: Vec3 = Vec3(0f, groundY, 0f),
        stereoOffset: Float = 0f
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height
        val gridColor = if (isARMode) Color(0xFF00E5FF).copy(alpha = 0.45f) else Color(0xFF38BDF8).copy(alpha = 0.25f)

        var x = -gridSize
        while (x <= gridSize) {
            val pStart = projectPoint(Vec3(x, groundY, -gridSize), camera, width, height, stereoOffset)
            val pEnd = projectPoint(Vec3(x, groundY, gridSize), camera, width, height, stereoOffset)
            if (pStart != null && pEnd != null) {
                drawScope.drawLine(
                    color = gridColor,
                    start = Offset(pStart.screenX, pStart.screenY),
                    end = Offset(pEnd.screenX, pEnd.screenY),
                    strokeWidth = 1.0f
                )
            }
            x += gridSpacing
        }

        var z = -gridSize
        while (z <= gridSize) {
            val pStart = projectPoint(Vec3(-gridSize, groundY, z), camera, width, height, stereoOffset)
            val pEnd = projectPoint(Vec3(gridSize, groundY, z), camera, width, height, stereoOffset)
            if (pStart != null && pEnd != null) {
                drawScope.drawLine(
                    color = gridColor,
                    start = Offset(pStart.screenX, pStart.screenY),
                    end = Offset(pEnd.screenX, pEnd.screenY),
                    strokeWidth = 1.0f
                )
            }
            z += gridSpacing
        }

        // If in AR mode, draw animated ground scanning sonar pulse ring around anchor
        if (isARMode) {
            val ringRadius = 0.3f + sonarAnimProgress * 1.5f
            val ringAlpha = (1.0f - sonarAnimProgress).coerceIn(0f, 1f) * 0.7f
            val ringSegments = 16
            val ringPath = Path()
            var first = true

            for (i in 0..ringSegments) {
                val angle = (i * 2.0 * Math.PI / ringSegments).toFloat()
                val px = anchorPos.x + cos(angle) * ringRadius
                val pz = anchorPos.z + sin(angle) * ringRadius
                val pt = projectPoint(Vec3(px, groundY, pz), camera, width, height, stereoOffset)
                if (pt != null) {
                    if (first) {
                        ringPath.moveTo(pt.screenX, pt.screenY)
                        first = false
                    } else {
                        ringPath.lineTo(pt.screenX, pt.screenY)
                    }
                }
            }
            if (!first) {
                ringPath.close()
                drawScope.drawPath(
                    ringPath,
                    color = Color(0xFF00E5FF).copy(alpha = ringAlpha),
                    style = Stroke(width = 2.5f)
                )
            }

            // Anchor target reticle at anchorPos with spatial placement feasibility rings
            val centerPt = projectPoint(Vec3(anchorPos.x, groundY, anchorPos.z), camera, width, height, stereoOffset)
            if (centerPt != null) {
                val reticleColor = if (isPlaced) Color(0xFF10B981) else Color(0xFF00E5FF)
                // Outer targeting ring
                drawScope.drawCircle(
                    color = reticleColor.copy(alpha = 0.5f),
                    radius = 24f,
                    center = Offset(centerPt.screenX, centerPt.screenY),
                    style = Stroke(width = 1.5f)
                )
                // Inner solid bullseye
                drawScope.drawCircle(
                    color = if (isPlaced) Color(0xFF10B981) else Color(0xFFF59E0B),
                    radius = 7f,
                    center = Offset(centerPt.screenX, centerPt.screenY)
                )
            }
        }
    }

    /**
     * Renders real-time spatial point cloud, feature detection points, and triangulated spatial mesh
     */
    fun renderSpatialPointCloud(
        drawScope: DrawScope,
        camera: CameraParams,
        points: List<SpatialFeaturePoint>,
        visualizationMode: PointCloudVisualizationMode,
        sonarAnimProgress: Float = 0f,
        isPlaced: Boolean = false,
        stereoOffset: Float = 0f
    ) {
        if (visualizationMode == PointCloudVisualizationMode.OFF || points.isEmpty()) return

        val width = drawScope.size.width
        val height = drawScope.size.height

        // Project visible points
        val projectedPoints = points.mapNotNull { pt ->
            val proj = projectPoint(pt.pos, camera, width, height, stereoOffset)
            if (proj != null) Pair(pt, proj) else null
        }

        // Draw spatial mesh connections between adjacent feature points if FULL_POINT_CLOUD mode
        if (visualizationMode == PointCloudVisualizationMode.FULL_POINT_CLOUD) {
            val maxConnDistSq = 0.75f * 0.75f
            for (i in 0 until min(projectedPoints.size, 60)) {
                val (ptA, projA) = projectedPoints[i]
                for (j in i + 1 until min(projectedPoints.size, 60)) {
                    val (ptB, projB) = projectedPoints[j]
                    val dx = ptA.pos.x - ptB.pos.x
                    val dz = ptA.pos.z - ptB.pos.z
                    val distSq = dx * dx + dz * dz
                    if (distSq < maxConnDistSq) {
                        val meshAlpha = ((1.0f - distSq / maxConnDistSq) * 0.28f * min(ptA.confidence, ptB.confidence)).coerceIn(0.04f, 0.4f)
                        drawScope.drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = meshAlpha),
                            start = Offset(projA.screenX, projA.screenY),
                            end = Offset(projB.screenX, projB.screenY),
                            strokeWidth = 1.0f
                        )
                    }
                }
            }
        }

        // Render point cloud vertices / markers
        for ((pt, proj) in projectedPoints) {
            val pointColor = when (visualizationMode) {
                PointCloudVisualizationMode.CONFIDENCE_HEATMAP -> {
                    // Green for >0.85, Yellow for 0.65-0.85, Orange/Red for <0.65
                    if (pt.confidence > 0.82f) Color(0xFF10B981)
                    else if (pt.confidence > 0.65f) Color(0xFFFBBF24)
                    else Color(0xFFF97316)
                }
                PointCloudVisualizationMode.FULL_POINT_CLOUD -> {
                    if (pt.isAnchorCandidate) Color(0xFF34D399) else Color(0xFF00E5FF)
                }
                PointCloudVisualizationMode.POINTS_ONLY -> {
                    Color(0xFF38BDF8)
                }
                PointCloudVisualizationMode.OFF -> Color.Transparent
            }

            val pointRadius = if (pt.isAnchorCandidate) 4.5f else 3.0f
            val baseAlpha = (pt.confidence * 0.85f).coerceIn(0.3f, 0.95f)

            // Outer confidence pulse halo for anchor candidates
            if (pt.isAnchorCandidate) {
                val haloRadius = pointRadius + (sin(sonarAnimProgress * 6.28f + pt.id) * 0.5f + 0.5f) * 4f
                drawScope.drawCircle(
                    color = pointColor.copy(alpha = (baseAlpha * 0.35f)),
                    radius = haloRadius,
                    center = Offset(proj.screenX, proj.screenY)
                )
            }

            // Core feature point dot
            drawScope.drawCircle(
                color = pointColor.copy(alpha = baseAlpha),
                radius = pointRadius,
                center = Offset(proj.screenX, proj.screenY)
            )

            // Small crosshair indicator on prominent feature points
            if (pt.isAnchorCandidate && visualizationMode != PointCloudVisualizationMode.POINTS_ONLY) {
                val armLen = 4f
                drawScope.drawLine(
                    color = pointColor.copy(alpha = 0.6f),
                    start = Offset(proj.screenX - armLen, proj.screenY),
                    end = Offset(proj.screenX + armLen, proj.screenY),
                    strokeWidth = 1f
                )
                drawScope.drawLine(
                    color = pointColor.copy(alpha = 0.6f),
                    start = Offset(proj.screenX, proj.screenY - armLen),
                    end = Offset(proj.screenX, proj.screenY + armLen),
                    strokeWidth = 1f
                )
            }
        }
    }

    /**
     * Projects a screen coordinate ray into world space and computes intersection with ground plane
     */
    fun raycastToGroundPlane(
        screenX: Float,
        screenY: Float,
        screenWidth: Float,
        screenHeight: Float,
        camera: CameraParams,
        groundY: Float = -1.0f
    ): Vec3? {
        if (screenWidth <= 0f || screenHeight <= 0f) return null
        val fovRad = Math.toRadians(camera.fovDeg.toDouble()).toFloat()
        val f = 1.0f / tan(fovRad / 2.0f)
        val aspect = screenWidth / screenHeight

        val projX = (screenX / screenWidth) * 2.0f - 1.0f
        val projY = (screenY / screenHeight) * 2.0f - 1.0f

        // Ray direction in camera coordinate space
        val dirCam = Vec3((projX * aspect) / f, -projY / f, 1.0f).normalized()

        val radYaw = Math.toRadians(camera.yawDeg.toDouble()).toFloat()
        val radPitch = Math.toRadians(camera.pitchDeg.toDouble()).toFloat()

        // Transform ray to world space
        val worldRay = dirCam.rotateX(-radPitch).rotateY(radYaw).normalized()
        val camPos = Vec3(camera.posX, camera.posY, camera.posZ)

        if (abs(worldRay.y) < 0.001f) return null
        val t = (groundY - camPos.y) / worldRay.y
        if (t <= 0.15f || t > 35.0f) return null

        return camPos + (worldRay * t)
    }

    /**
     * Renders a world-space anchored surface pedestal, beacon rings, and lock status
     */
    fun renderSurfaceAnchor(
        drawScope: DrawScope,
        camera: CameraParams,
        anchorPos: Vec3,
        isLocked: Boolean = false,
        modelElevation: Float = 0f,
        sonarAnimProgress: Float = 0f,
        stereoOffset: Float = 0f
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height
        if (width <= 0 || height <= 0) return

        val groundAnchor = projectPoint(anchorPos, camera, width, height, stereoOffset) ?: return
        val modelBasePos = anchorPos + Vec3(0f, modelElevation, 0f)
        val elevatedAnchor = projectPoint(modelBasePos, camera, width, height, stereoOffset)

        val anchorThemeColor = if (isLocked) Color(0xFF10B981) else Color(0xFF00E5FF)

        // Concentric grounding rings around pinned world coordinate
        val ringCount = 3
        for (i in 1..ringCount) {
            val baseRadius = i * 0.28f
            val pulseRadius = baseRadius + sin(sonarAnimProgress * 6.28f + i) * 0.03f
            val ringPath = Path()
            var first = true
            for (step in 0..16) {
                val angle = (step * 2.0 * Math.PI / 16).toFloat()
                val rx = anchorPos.x + cos(angle) * pulseRadius
                val rz = anchorPos.z + sin(angle) * pulseRadius
                val pt = projectPoint(Vec3(rx, anchorPos.y + 0.005f, rz), camera, width, height, stereoOffset)
                if (pt != null) {
                    if (first) {
                        ringPath.moveTo(pt.screenX, pt.screenY)
                        first = false
                    } else {
                        ringPath.lineTo(pt.screenX, pt.screenY)
                    }
                }
            }
            if (!first) {
                ringPath.close()
                drawScope.drawPath(
                    ringPath,
                    color = anchorThemeColor.copy(alpha = 0.5f / i),
                    style = Stroke(width = if (i == 1) 2.2f else 1.2f)
                )
            }
        }

        // Vertical holographic beacon line if elevated above ground plane
        if (elevatedAnchor != null && modelElevation > 0.05f) {
            drawScope.drawLine(
                color = anchorThemeColor.copy(alpha = 0.7f),
                start = Offset(groundAnchor.screenX, groundAnchor.screenY),
                end = Offset(elevatedAnchor.screenX, elevatedAnchor.screenY),
                strokeWidth = 2.0f
            )
        }

        // Center Pin Marker
        drawScope.drawCircle(
            color = anchorThemeColor,
            radius = 7.0f,
            center = Offset(groundAnchor.screenX, groundAnchor.screenY)
        )
        drawScope.drawCircle(
            color = Color.White,
            radius = 3.0f,
            center = Offset(groundAnchor.screenX, groundAnchor.screenY)
        )
    }
}
