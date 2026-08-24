package com.example.data.repository

import com.example.data.dao.ModelHistoryDao
import com.example.data.dao.UserPreferencesDao
import com.example.data.entity.ModelHistoryEntity
import com.example.data.entity.UserPreferenceEntity
import com.example.engine.EnvironmentType
import com.example.engine.LightParams
import com.example.engine.RenderStyle
import com.example.math3d.Mesh3D
import com.example.viewmodel.AppDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MixedRealityRepository(
    private val preferencesDao: UserPreferencesDao,
    private val historyDao: ModelHistoryDao
) {
    val preferencesFlow: Flow<UserPreferenceEntity?> = preferencesDao.getPreferencesFlow()
    val recentHistoryFlow: Flow<List<ModelHistoryEntity>> = historyDao.getRecentHistory()

    suspend fun getPreferences(): UserPreferenceEntity? = withContext(Dispatchers.IO) {
        preferencesDao.getPreferences()
    }

    suspend fun savePreferences(
        meshId: String,
        displayMode: AppDisplayMode,
        renderStyle: RenderStyle,
        environmentType: EnvironmentType,
        wireframeOnly: Boolean,
        autoRotate: Boolean,
        autoRotateSpeed: Float,
        stereoIPD: Float,
        gyroTracking: Boolean,
        barrelDistortion: Boolean,
        lightParams: LightParams
    ) = withContext(Dispatchers.IO) {
        val entity = UserPreferenceEntity(
            id = 1,
            lastMeshId = meshId,
            lastDisplayMode = displayMode.name,
            renderStyle = renderStyle.name,
            environmentType = environmentType.name,
            wireframeOnly = wireframeOnly,
            autoRotate = autoRotate,
            autoRotateSpeed = autoRotateSpeed,
            stereoIPD = stereoIPD,
            gyroTracking = gyroTracking,
            barrelDistortion = barrelDistortion,
            lightAzimuth = lightParams.azimuthDeg,
            lightElevation = lightParams.elevationDeg,
            ambientIntensity = lightParams.ambientIntensity,
            directionalIntensity = lightParams.directionalIntensity,
            shadowsEnabled = lightParams.shadowsEnabled,
            shadowIntensity = lightParams.shadowIntensity,
            sceneIntensity = lightParams.sceneIntensity,
            updatedAt = System.currentTimeMillis()
        )
        preferencesDao.insertOrUpdate(entity)
    }

    suspend fun recordModelView(mesh: Mesh3D, displayMode: AppDisplayMode) = withContext(Dispatchers.IO) {
        val item = ModelHistoryEntity(
            meshId = mesh.id,
            meshName = mesh.name,
            category = mesh.category,
            vertexCount = mesh.vertexCount,
            faceCount = mesh.faceCount,
            displayMode = displayMode.name,
            timestamp = System.currentTimeMillis()
        )
        historyDao.insertHistory(item)
    }

    suspend fun deleteHistoryItem(id: Long) = withContext(Dispatchers.IO) {
        historyDao.deleteById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDao.clearAll()
    }
}
