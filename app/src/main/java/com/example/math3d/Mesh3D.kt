package com.example.math3d

import kotlin.math.*

data class Face(
    val v1: Int,
    val v2: Int,
    val v3: Int,
    val baseColor: Long = 0xFF00E5FF
)

data class BoundingBox(
    val min: Vec3,
    val max: Vec3
) {
    val size: Vec3 get() = max - min
    val center: Vec3 get() = (min + max) * 0.5f
    val maxDimension: Float get() = maxOf(size.x, size.y, size.z)
}

data class Mesh3D(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val vertices: List<Vec3>,
    val faces: List<Face>,
    val defaultScale: Float = 1.0f
) {
    val vertexCount: Int get() = vertices.size
    val faceCount: Int get() = faces.size

    val boundingBox: BoundingBox by lazy {
        if (vertices.isEmpty()) {
            BoundingBox(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 0f))
        } else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var maxZ = -Float.MAX_VALUE
            for (v in vertices) {
                if (v.x < minX) minX = v.x
                if (v.y < minY) minY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.x > maxX) maxX = v.x
                if (v.y > maxY) maxY = v.y
                if (v.z > maxZ) maxZ = v.z
            }
            BoundingBox(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
        }
    }

    /**
     * Return normalized mesh centered at origin with unit scale (fitting inside [-1, 1])
     */
    fun normalized(): Mesh3D {
        val bbox = boundingBox
        val center = bbox.center
        val maxDim = if (bbox.maxDimension > 0.001f) bbox.maxDimension else 1f
        val scale = 2.0f / maxDim
        val normVertices = vertices.map { (it - center) * scale }
        return copy(vertices = normVertices)
    }
}

object MeshPresets {

    /**
     * 1. Modern Architectural Pavilion: A structure with floor, colonnades, roof, and interior room
     */
    fun createArchitecturalPavilion(): Mesh3D {
        val verts = mutableListOf<Vec3>()
        val faces = mutableListOf<Face>()

        // Floor base (-2.5 to 2.5 on X and Z, Y = -1.0)
        val floorY = -1.0f
        val roofY = 1.2f
        val colorFloor = 0xFF1E293B
        val colorColumn = 0xFF0284C7
        val colorRoof = 0xFF38BDF8
        val colorGlass = 0xFF22D3EE
        val colorSteps = 0xFF334155

        // Floor slab
        val floorStartIndex = verts.size
        verts.add(Vec3(-2.2f, floorY, -2.2f)) // 0
        verts.add(Vec3(2.2f, floorY, -2.2f))  // 1
        verts.add(Vec3(2.2f, floorY, 2.2f))   // 2
        verts.add(Vec3(-2.2f, floorY, 2.2f))  // 3
        faces.add(Face(floorStartIndex, floorStartIndex + 1, floorStartIndex + 2, colorFloor))
        faces.add(Face(floorStartIndex, floorStartIndex + 2, floorStartIndex + 3, colorFloor))

        // Steps in front
        val stepStartIndex = verts.size
        verts.add(Vec3(-1.0f, floorY - 0.2f, 2.6f))
        verts.add(Vec3(1.0f, floorY - 0.2f, 2.6f))
        verts.add(Vec3(1.0f, floorY, 2.2f))
        verts.add(Vec3(-1.0f, floorY, 2.2f))
        faces.add(Face(stepStartIndex, stepStartIndex + 1, stepStartIndex + 2, colorSteps))
        faces.add(Face(stepStartIndex, stepStartIndex + 2, stepStartIndex + 3, colorSteps))

        // 6 Pillars / Columns
        val columnPositions = listOf(
            Vec3(-1.8f, 0f, -1.8f),
            Vec3(1.8f, 0f, -1.8f),
            Vec3(-1.8f, 0f, 1.8f),
            Vec3(1.8f, 0f, 1.8f),
            Vec3(-1.8f, 0f, 0f),
            Vec3(1.8f, 0f, 0f)
        )

        val colRadius = 0.12f
        for (col in columnPositions) {
            val baseIdx = verts.size
            for (i in 0 until 6) {
                val angle = (i * 2.0 * Math.PI / 6).toFloat()
                val cx = col.x + cos(angle) * colRadius
                val cz = col.z + sin(angle) * colRadius
                verts.add(Vec3(cx, floorY, cz))
                verts.add(Vec3(cx, roofY, cz))
            }
            for (i in 0 until 6) {
                val next = (i + 1) % 6
                val b1 = baseIdx + i * 2
                val t1 = b1 + 1
                val b2 = baseIdx + next * 2
                val t2 = b2 + 1
                faces.add(Face(b1, t1, t2, colorColumn))
                faces.add(Face(b1, t2, b2, colorColumn))
            }
        }

        // Roof Canopy Slab
        val roofStartIndex = verts.size
        verts.add(Vec3(-2.4f, roofY, -2.4f)) // 0
        verts.add(Vec3(2.4f, roofY, -2.4f))  // 1
        verts.add(Vec3(2.4f, roofY, 2.4f))   // 2
        verts.add(Vec3(-2.4f, roofY, 2.4f))  // 3
        verts.add(Vec3(0f, roofY + 0.6f, 0f)) // 4 Apex
        faces.add(Face(roofStartIndex, roofStartIndex + 1, roofStartIndex + 4, colorRoof))
        faces.add(Face(roofStartIndex + 1, roofStartIndex + 2, roofStartIndex + 4, colorRoof))
        faces.add(Face(roofStartIndex + 2, roofStartIndex + 3, roofStartIndex + 4, colorRoof))
        faces.add(Face(roofStartIndex + 3, roofStartIndex, roofStartIndex + 4, colorRoof))

        // Interior Center Sculpture / Pedestal
        val pedStart = verts.size
        val pedRadius = 0.35f
        for (i in 0 until 8) {
            val angle = (i * 2.0 * Math.PI / 8).toFloat()
            verts.add(Vec3(cos(angle) * pedRadius, floorY, sin(angle) * pedRadius))
            verts.add(Vec3(cos(angle) * (pedRadius * 0.7f), floorY + 0.7f, sin(angle) * (pedRadius * 0.7f)))
        }
        val apexIdx = verts.size
        verts.add(Vec3(0f, floorY + 1.1f, 0f))
        for (i in 0 until 8) {
            val next = (i + 1) % 8
            val b1 = pedStart + i * 2
            val t1 = b1 + 1
            val b2 = pedStart + next * 2
            val t2 = b2 + 1
            faces.add(Face(b1, t1, t2, colorGlass))
            faces.add(Face(b1, t2, b2, colorGlass))
            faces.add(Face(t1, apexIdx, t2, 0xFFA855F7))
        }

        return Mesh3D(
            id = "pavilion",
            name = "Architectural Pavilion",
            description = "Walkable modern open-air pavilion with glass skylights, colonnades, and centerpiece sculpture. Ideal for AR walk-ins.",
            category = "Architecture",
            vertices = verts,
            faces = faces
        ).normalized()
    }

    /**
     * 2. Sci-Fi Explorer Drone: High-tech quad-rotor reconnaissance craft
     */
    fun createSciFiDrone(): Mesh3D {
        val verts = mutableListOf<Vec3>()
        val faces = mutableListOf<Face>()

        val colorHull = 0xFF0F172A
        val colorAccent = 0xFF00E5FF
        val colorCore = 0xFFA855F7
        val colorRotor = 0xFF0284C7

        // Central Fuselage (Hexagonal Prism)
        val bodyStart = verts.size
        val bodyRadius = 0.5f
        val bodyHeight = 0.35f
        for (i in 0 until 6) {
            val angle = (i * 2.0 * Math.PI / 6).toFloat()
            verts.add(Vec3(cos(angle) * bodyRadius, -bodyHeight * 0.5f, sin(angle) * bodyRadius * 1.3f))
            verts.add(Vec3(cos(angle) * (bodyRadius * 0.85f), bodyHeight * 0.5f, sin(angle) * bodyRadius * 1.1f))
        }
        val topCenter = verts.size
        verts.add(Vec3(0f, bodyHeight * 0.7f, 0f)) // Glowing dome top
        val bottomCenter = verts.size
        verts.add(Vec3(0f, -bodyHeight * 0.7f, 0.1f)) // Sensor array bottom

        for (i in 0 until 6) {
            val next = (i + 1) % 6
            val b1 = bodyStart + i * 2
            val t1 = b1 + 1
            val b2 = bodyStart + next * 2
            val t2 = b2 + 1
            faces.add(Face(b1, t1, t2, colorHull))
            faces.add(Face(b1, t2, b2, colorHull))
            faces.add(Face(t1, topCenter, t2, colorCore))
            faces.add(Face(b1, b2, bottomCenter, colorAccent))
        }

        // 4 Rotor Struts & Thruster Rings
        val armOffsets = listOf(
            Vec3(1.1f, 0.05f, 0.9f),
            Vec3(-1.1f, 0.05f, 0.9f),
            Vec3(1.1f, 0.05f, -0.9f),
            Vec3(-1.1f, 0.05f, -0.9f)
        )

        for (arm in armOffsets) {
            // Strut from body
            val strutStart = verts.size
            verts.add(Vec3(arm.x * 0.3f, 0f, arm.z * 0.3f))
            verts.add(Vec3(arm.x * 0.3f, 0.1f, arm.z * 0.3f))
            verts.add(Vec3(arm.x, arm.y, arm.z))
            verts.add(Vec3(arm.x, arm.y + 0.1f, arm.z))
            faces.add(Face(strutStart, strutStart + 1, strutStart + 3, colorHull))
            faces.add(Face(strutStart, strutStart + 3, strutStart + 2, colorHull))

            // Rotor Ring
            val ringStart = verts.size
            val ringR = 0.35f
            for (i in 0 until 8) {
                val angle = (i * 2.0 * Math.PI / 8).toFloat()
                verts.add(Vec3(arm.x + cos(angle) * ringR, arm.y - 0.05f, arm.z + sin(angle) * ringR))
                verts.add(Vec3(arm.x + cos(angle) * ringR, arm.y + 0.05f, arm.z + sin(angle) * ringR))
            }
            for (i in 0 until 8) {
                val next = (i + 1) % 8
                val b1 = ringStart + i * 2
                val t1 = b1 + 1
                val b2 = ringStart + next * 2
                val t2 = b2 + 1
                faces.add(Face(b1, t1, t2, colorRotor))
                faces.add(Face(b1, t2, b2, colorRotor))
            }

            // Rotor blades
            val bladeStart = verts.size
            verts.add(Vec3(arm.x - ringR * 0.8f, arm.y, arm.z))
            verts.add(Vec3(arm.x + ringR * 0.8f, arm.y, arm.z))
            verts.add(Vec3(arm.x, arm.y + 0.02f, arm.z))
            faces.add(Face(bladeStart, bladeStart + 1, bladeStart + 2, colorAccent))
        }

        return Mesh3D(
            id = "drone",
            name = "Sci-Fi Explorer Drone",
            description = "Autonomous reconnaissance drone equipped with quad vectored thrusters, sensor optics, and pulse reactor.",
            category = "Sci-Fi",
            vertices = verts,
            faces = faces
        ).normalized()
    }

    /**
     * 3. Mars Planetary Rover: 6-wheeled robotic explorer
     */
    fun createPlanetaryRover(): Mesh3D {
        val verts = mutableListOf<Vec3>()
        val faces = mutableListOf<Face>()

        val colorChassis = 0xFFE2E8F0
        val colorWheel = 0xFF1E293B
        val colorSolar = 0xFF0284C7
        val colorMast = 0xFF64748B
        val colorCamera = 0xFFF59E0B

        // Main Body Box (-0.7 to 0.7 X, -0.2 to 0.3 Y, -1.0 to 0.8 Z)
        val bodyStart = verts.size
        val bx1 = -0.6f; val bx2 = 0.6f
        val by1 = -0.1f; val by2 = 0.35f
        val bz1 = -0.9f; val bz2 = 0.8f

        verts.add(Vec3(bx1, by1, bz1)) // 0
        verts.add(Vec3(bx2, by1, bz1)) // 1
        verts.add(Vec3(bx2, by2, bz1)) // 2
        verts.add(Vec3(bx1, by2, bz1)) // 3
        verts.add(Vec3(bx1, by1, bz2)) // 4
        verts.add(Vec3(bx2, by1, bz2)) // 5
        verts.add(Vec3(bx2, by2, bz2)) // 6
        verts.add(Vec3(bx1, by2, bz2)) // 7

        // Front / Back / Left / Right / Top / Bottom
        faces.add(Face(bodyStart + 0, bodyStart + 1, bodyStart + 2, colorChassis))
        faces.add(Face(bodyStart + 0, bodyStart + 2, bodyStart + 3, colorChassis))
        faces.add(Face(bodyStart + 5, bodyStart + 4, bodyStart + 7, colorChassis))
        faces.add(Face(bodyStart + 5, bodyStart + 7, bodyStart + 6, colorChassis))
        faces.add(Face(bodyStart + 4, bodyStart + 0, bodyStart + 3, colorChassis))
        faces.add(Face(bodyStart + 4, bodyStart + 3, bodyStart + 7, colorChassis))
        faces.add(Face(bodyStart + 1, bodyStart + 5, bodyStart + 6, colorChassis))
        faces.add(Face(bodyStart + 1, bodyStart + 6, bodyStart + 2, colorChassis))
        // Top Solar Array
        faces.add(Face(bodyStart + 3, bodyStart + 2, bodyStart + 6, colorSolar))
        faces.add(Face(bodyStart + 3, bodyStart + 6, bodyStart + 7, colorSolar))

        // Mast with Camera Head
        val mastStart = verts.size
        verts.add(Vec3(-0.3f, by2, 0.5f))
        verts.add(Vec3(-0.25f, by2, 0.5f))
        verts.add(Vec3(-0.3f, by2 + 0.7f, 0.5f))
        verts.add(Vec3(-0.25f, by2 + 0.7f, 0.5f))
        faces.add(Face(mastStart, mastStart + 1, mastStart + 3, colorMast))
        faces.add(Face(mastStart, mastStart + 3, mastStart + 2, colorMast))

        // Camera head box
        val camStart = verts.size
        verts.add(Vec3(-0.4f, by2 + 0.65f, 0.45f))
        verts.add(Vec3(-0.15f, by2 + 0.65f, 0.45f))
        verts.add(Vec3(-0.15f, by2 + 0.8f, 0.55f))
        verts.add(Vec3(-0.4f, by2 + 0.8f, 0.55f))
        faces.add(Face(camStart, camStart + 1, camStart + 2, colorCamera))
        faces.add(Face(camStart, camStart + 2, camStart + 3, colorCamera))

        // 6 Wheels
        val wheelOffsets = listOf(
            Vec3(-0.85f, -0.4f, 0.7f),
            Vec3(0.85f, -0.4f, 0.7f),
            Vec3(-0.85f, -0.4f, 0.0f),
            Vec3(0.85f, -0.4f, 0.0f),
            Vec3(-0.85f, -0.4f, -0.7f),
            Vec3(0.85f, -0.4f, -0.7f)
        )

        for (w in wheelOffsets) {
            val wStart = verts.size
            val r = 0.25f
            val thick = 0.12f
            for (i in 0 until 8) {
                val angle = (i * 2.0 * Math.PI / 8).toFloat()
                verts.add(Vec3(w.x - thick * 0.5f, w.y + sin(angle) * r, w.z + cos(angle) * r))
                verts.add(Vec3(w.x + thick * 0.5f, w.y + sin(angle) * r, w.z + cos(angle) * r))
            }
            for (i in 0 until 8) {
                val next = (i + 1) % 8
                val b1 = wStart + i * 2
                val t1 = b1 + 1
                val b2 = wStart + next * 2
                val t2 = b2 + 1
                faces.add(Face(b1, t1, t2, colorWheel))
                faces.add(Face(b1, t2, b2, colorWheel))
            }
        }

        return Mesh3D(
            id = "rover",
            name = "Planetary Mars Rover",
            description = "Exploration rover featuring 6 rocker-bogie wheels, high-gain telemetry mast, and dual panoramic stereo sensors.",
            category = "Robotics",
            vertices = verts,
            faces = faces
        ).normalized()
    }

    /**
     * 4. Cyberpunk Crystal Polyhedron: Faceted glowing crystal
     */
    fun createCyberpunkCrystal(): Mesh3D {
        val phi = (1.0f + sqrt(5.0f)) / 2.0f
        val verts = listOf(
            Vec3(-1f, phi, 0f), Vec3(1f, phi, 0f), Vec3(-1f, -phi, 0f), Vec3(1f, -phi, 0f),
            Vec3(0f, -1f, phi), Vec3(0f, 1f, phi), Vec3(0f, -1f, -phi), Vec3(0f, 1f, -phi),
            Vec3(phi, 0f, -1f), Vec3(phi, 0f, 1f), Vec3(-phi, 0f, -1f), Vec3(-phi, 0f, 1f)
        )

        val colors = listOf(
            0xFF00E5FF, 0xFFA855F7, 0xFF38BDF8, 0xFFEC4899,
            0xFF10B981, 0xFF6366F1, 0xFFF59E0B, 0xFF06B6D4
        )

        val faceIndices = listOf(
            Triple(0, 11, 5), Triple(0, 5, 1), Triple(0, 1, 7), Triple(0, 7, 10), Triple(0, 10, 11),
            Triple(1, 5, 9), Triple(5, 11, 4), Triple(11, 10, 2), Triple(10, 7, 6), Triple(7, 1, 8),
            Triple(3, 9, 4), Triple(3, 4, 2), Triple(3, 2, 6), Triple(3, 6, 8), Triple(3, 8, 9),
            Triple(4, 9, 5), Triple(2, 4, 11), Triple(6, 2, 10), Triple(8, 6, 7), Triple(9, 8, 1)
        )

        val faces = faceIndices.mapIndexed { index, (v1, v2, v3) ->
            Face(v1, v2, v3, colors[index % colors.size])
        }

        return Mesh3D(
            id = "crystal",
            name = "Cyberpunk Quantum Crystal",
            description = "Multi-faceted icosahedron matrix crystal with glowing chromatic facets and internal refraction vertices.",
            category = "Sci-Fi",
            vertices = verts,
            faces = faces
        ).normalized()
    }

    /**
     * 5. Classic Parametric Teapot
     */
    fun createUtahTeapot(): Mesh3D {
        val verts = mutableListOf<Vec3>()
        val faces = mutableListOf<Face>()

        val colorBody = 0xFFF59E0B
        val colorHandle = 0xFFD97706
        val colorSpout = 0xFFB45309

        // Spherical layered body
        val latRings = 10
        val lonSegments = 14
        val bodyStart = verts.size

        for (lat in 0..latRings) {
            val theta = (lat * Math.PI / latRings).toFloat()
            val y = cos(theta) * 0.7f
            val r = sin(theta) * (0.9f - 0.2f * (y * y))
            for (lon in 0 until lonSegments) {
                val phi = (lon * 2.0 * Math.PI / lonSegments).toFloat()
                val x = r * cos(phi)
                val z = r * sin(phi)
                verts.add(Vec3(x, y, z))
            }
        }

        for (lat in 0 until latRings) {
            for (lon in 0 until lonSegments) {
                val nextLon = (lon + 1) % lonSegments
                val i1 = bodyStart + lat * lonSegments + lon
                val i2 = bodyStart + lat * lonSegments + nextLon
                val i3 = bodyStart + (lat + 1) * lonSegments + nextLon
                val i4 = bodyStart + (lat + 1) * lonSegments + lon
                faces.add(Face(i1, i2, i3, colorBody))
                faces.add(Face(i1, i3, i4, colorBody))
            }
        }

        // Spout
        val spoutStart = verts.size
        for (i in 0..5) {
            val t = i / 5f
            val sx = 0.7f + t * 0.7f
            val sy = -0.1f + t * 0.6f + sin(t * 1.5f) * 0.2f
            val sr = 0.2f - t * 0.08f
            for (j in 0 until 6) {
                val angle = (j * 2.0 * Math.PI / 6).toFloat()
                verts.add(Vec3(sx, sy + sin(angle) * sr, cos(angle) * sr))
            }
        }
        for (i in 0 until 5) {
            for (j in 0 until 6) {
                val nextJ = (j + 1) % 6
                val i1 = spoutStart + i * 6 + j
                val i2 = spoutStart + i * 6 + nextJ
                val i3 = spoutStart + (i + 1) * 6 + nextJ
                val i4 = spoutStart + (i + 1) * 6 + j
                faces.add(Face(i1, i2, i3, colorSpout))
                faces.add(Face(i1, i3, i4, colorSpout))
            }
        }

        // Handle loop
        val handleStart = verts.size
        for (i in 0..6) {
            val angle = (i * Math.PI / 6).toFloat()
            val hx = -0.8f - sin(angle) * 0.5f
            val hy = cos(angle) * 0.5f + 0.1f
            for (j in 0 until 4) {
                val a2 = (j * 2.0 * Math.PI / 4).toFloat()
                verts.add(Vec3(hx + cos(a2) * 0.08f, hy, sin(a2) * 0.08f))
            }
        }
        for (i in 0 until 6) {
            for (j in 0 until 4) {
                val nextJ = (j + 1) % 4
                val i1 = handleStart + i * 4 + j
                val i2 = handleStart + i * 4 + nextJ
                val i3 = handleStart + (i + 1) * 4 + nextJ
                val i4 = handleStart + (i + 1) * 4 + j
                faces.add(Face(i1, i2, i3, colorHandle))
                faces.add(Face(i1, i3, i4, colorHandle))
            }
        }

        return Mesh3D(
            id = "teapot",
            name = "Utah Benchmark Teapot",
            description = "Standard computer graphics 3D geometry benchmark with curved Bezier body, handle, and pouring spout.",
            category = "Classic",
            vertices = verts,
            faces = faces
        ).normalized()
    }

    val allPresets: List<Mesh3D> by lazy {
        listOf(
            createArchitecturalPavilion(),
            createSciFiDrone(),
            createPlanetaryRover(),
            createCyberpunkCrystal(),
            createUtahTeapot()
        )
    }
}
