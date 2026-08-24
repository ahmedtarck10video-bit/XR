package com.example.math3d

import java.io.InputStream

object ObjParser {

    /**
     * Parses standard Wavefront .OBJ text format into a Mesh3D instance
     */
    fun parse(content: String, name: String = "Imported 3D Model"): Mesh3D {
        val vertices = mutableListOf<Vec3>()
        val faces = mutableListOf<Face>()

        val colors = listOf(
            0xFF00E5FF, 0xFFA855F7, 0xFF38BDF8, 0xFF10B981, 0xFFF59E0B
        )
        var colorIdx = 0

        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("v ")) {
                val parts = trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (parts.size >= 4) {
                    val x = parts[1].toFloatOrNull() ?: 0f
                    val y = parts[2].toFloatOrNull() ?: 0f
                    val z = parts[3].toFloatOrNull() ?: 0f
                    vertices.add(Vec3(x, y, z))
                }
            } else if (trimmed.startsWith("f ")) {
                val parts = trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (parts.size >= 4) {
                    val indices = mutableListOf<Int>()
                    for (i in 1 until parts.size) {
                        val vertexIndexStr = parts[i].split("/")[0]
                        val idx = vertexIndexStr.toIntOrNull()
                        if (idx != null) {
                            // OBJ is 1-indexed; support negative indices
                            val actualIdx = if (idx > 0) idx - 1 else vertices.size + idx
                            indices.add(actualIdx)
                        }
                    }
                    // Fan triangulation for polygons with > 3 vertices
                    if (indices.size >= 3) {
                        val faceColor = colors[colorIdx % colors.size]
                        colorIdx++
                        for (i in 1 until indices.size - 1) {
                            faces.add(Face(indices[0], indices[i], indices[i + 1], faceColor))
                        }
                    }
                }
            }
        }

        return Mesh3D(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            description = "Custom imported OBJ model with ${vertices.size} vertices and ${faces.size} polygons.",
            category = "Custom",
            vertices = vertices,
            faces = faces
        ).normalized()
    }

    fun parseStream(stream: InputStream, name: String = "Imported 3D Model"): Mesh3D {
        val content = stream.bufferedReader().use { it.readText() }
        return parse(content, name)
    }
}
