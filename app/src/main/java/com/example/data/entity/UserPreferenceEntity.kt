package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val id: Int = 1,
    val lastMeshId: String = "pavilion",
    val lastDisplayMode: String = "MODE_3D",
    val renderStyle: String = "SHADED_LIT",
    val environmentType: String = "DEEP_SPACE",
    val wireframeOnly: Boolean = false,
    val autoRotate: Boolean = false,
    val autoRotateSpeed: Float = 1.0f,
    val stereoIPD: Float = 0.065f,
    val gyroTracking: Boolean = true,
    val barrelDistortion: Boolean = true,
    val lightAzimuth: Float = 45f,
    val lightElevation: Float = 45f,
    val ambientIntensity: Float = 0.35f,
    val directionalIntensity: Float = 0.75f,
    val shadowsEnabled: Boolean = true,
    val shadowIntensity: Float = 0.6f,
    val sceneIntensity: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
)
