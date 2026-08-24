package com.example.math3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = if (scalar != 0f) Vec3(x / scalar, y / scalar, z / scalar) else Vec3(0f, 0f, 0f)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 0.00001f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }

    fun rotateX(rad: Float): Vec3 {
        val cos = cos(rad)
        val sin = sin(rad)
        return Vec3(x, y * cos - z * sin, y * sin + z * cos)
    }

    fun rotateY(rad: Float): Vec3 {
        val cos = cos(rad)
        val sin = sin(rad)
        return Vec3(x * cos + z * sin, y, -x * sin + z * cos)
    }

    fun rotateZ(rad: Float): Vec3 {
        val cos = cos(rad)
        val sin = sin(rad)
        return Vec3(x * cos - y * sin, x * sin + y * cos, z)
    }
}

data class Vec2(val x: Float, val y: Float)

data class ProjectedVertex(
    val screenX: Float,
    val screenY: Float,
    val depth: Float,
    val worldPos: Vec3
)

data class ProjectedTriangle(
    val p1: ProjectedVertex,
    val p2: ProjectedVertex,
    val p3: ProjectedVertex,
    val normal: Vec3,
    val color: Int,
    val avgDepth: Float
)
