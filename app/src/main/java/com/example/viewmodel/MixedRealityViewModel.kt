package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.entity.ModelHistoryEntity
import com.example.data.repository.MixedRealityRepository
import com.example.engine.*
import com.example.math3d.*
import com.example.network.JokeRetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class AppDisplayMode(val title: String, val badge: String) {
    MODE_3D("3D Studio", "Interactive Orbit"),
    MODE_AR("AR Surface", "Ground & Walk-In"),
    MODE_STEREO("VR Stereo 3D", "Headset Split-Screen")
}

data class MixedRealityUiState(
    val displayMode: AppDisplayMode = AppDisplayMode.MODE_3D,
    val currentMesh: Mesh3D = MeshPresets.createArchitecturalPavilion(),
    val renderStyle: RenderStyle = RenderStyle.SHADED_LIT,
    val environmentType: EnvironmentType = EnvironmentType.DEEP_SPACE,
    val wireframeOnly: Boolean = false,
    val lightParams: LightParams = LightParams(),

    // 3D Orbit Camera
    val orbitYaw: Float = 30f,
    val orbitPitch: Float = 20f,
    val orbitDistance: Float = 3.5f,
    val isAutoRotating: Boolean = false,
    val autoRotateSpeed: Float = 1.0f,

    // AR Ground Placement & First-Person Walk-In
    val isARPlaced: Boolean = false,
    val arGroundY: Float = -1.0f,
    val arAnchorPos: Vec3 = Vec3(0f, -1.0f, 0f),
    val isARAnchorLocked: Boolean = false,
    val arModelElevation: Float = 0.0f,
    val arModelRotationY: Float = 0f,
    val arModelScale: Float = 1.0f,
    val walkPos: Vec3 = Vec3(0f, 0.0f, -3.0f),
    val walkYaw: Float = 0f,
    val walkPitch: Float = 0f,
    val arSonarProgress: Float = 0f,

    // Spatial Mapping & Point Cloud Diagnostics
    val spatialPoints: List<SpatialFeaturePoint> = SpatialMappingGenerator.generateInitialPointCloud(-1.0f),
    val spatialDiagnostics: SpatialDiagnostics = SpatialDiagnostics(),
    val pointCloudMode: PointCloudVisualizationMode = PointCloudVisualizationMode.FULL_POINT_CLOUD,
    val showSpatialDiagnostics: Boolean = true,
    val isSurfaceScanning: Boolean = false,

    // VR Stereo Settings
    val isSplitScreenStereo: Boolean = false,
    val stereoIPD: Float = 0.065f, // Eye separation offset in meters (e.g., 65mm)
    val stereoConvergence: Float = 2.5f, // Parallax convergence plane
    val stereoLensGuide: Boolean = true, // VR headset optical alignment reticles and divider
    val isGyroHeadTracking: Boolean = true,
    val barrelDistortion: Boolean = true,

    // HoloBot Joke Companion
    val jokeText: String? = null,
    val isJokeLoading: Boolean = false,
    val showJokeCard: Boolean = false,

    // Room Database Recent History
    val recentHistory: List<ModelHistoryEntity> = emptyList(),

    // Recording & Snapshot
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val showModelLibrary: Boolean = false,
    val showInspector: Boolean = false,
    val capturedSnapshot: Bitmap? = null,
    val showSnapshotDialog: Boolean = false
)

class MixedRealityViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = MixedRealityRepository(
        database.userPreferencesDao(),
        database.modelHistoryDao()
    )

    private val _uiState = MutableStateFlow(MixedRealityUiState())
    val uiState: StateFlow<MixedRealityUiState> = _uiState.asStateFlow()

    val sensorTracker = SensorTracker(application)

    private var autoRotateJob: Job? = null
    private var sonarAnimJob: Job? = null
    private var recordingJob: Job? = null

    init {
        sensorTracker.startTracking()

        // Observe Room recent history
        viewModelScope.launch {
            repository.recentHistoryFlow.collectLatest { historyList ->
                _uiState.update { it.copy(recentHistory = historyList) }
            }
        }

        // Restore saved preferences from Room database
        viewModelScope.launch {
            val pref = repository.getPreferences()
            if (pref != null) {
                val restoredMesh = MeshPresets.allPresets.find { it.id == pref.lastMeshId }
                    ?: MeshPresets.createArchitecturalPavilion()
                val restoredMode = try {
                    AppDisplayMode.valueOf(pref.lastDisplayMode)
                } catch (_: Exception) {
                    AppDisplayMode.MODE_3D
                }
                val restoredStyle = try {
                    RenderStyle.valueOf(pref.renderStyle)
                } catch (_: Exception) {
                    RenderStyle.SHADED_LIT
                }
                val restoredEnv = try {
                    EnvironmentType.valueOf(pref.environmentType)
                } catch (_: Exception) {
                    EnvironmentType.DEEP_SPACE
                }

                _uiState.update { current ->
                    current.copy(
                        currentMesh = restoredMesh,
                        displayMode = restoredMode,
                        renderStyle = restoredStyle,
                        environmentType = restoredEnv,
                        wireframeOnly = pref.wireframeOnly,
                        isAutoRotating = pref.autoRotate,
                        autoRotateSpeed = pref.autoRotateSpeed,
                        stereoIPD = pref.stereoIPD,
                        isGyroHeadTracking = pref.gyroTracking,
                        barrelDistortion = pref.barrelDistortion,
                        lightParams = LightParams(
                            azimuthDeg = pref.lightAzimuth,
                            elevationDeg = pref.lightElevation,
                            ambientIntensity = pref.ambientIntensity,
                            directionalIntensity = pref.directionalIntensity,
                            shadowsEnabled = pref.shadowsEnabled,
                            shadowIntensity = pref.shadowIntensity,
                            sceneIntensity = pref.sceneIntensity
                        )
                    )
                }

                if (pref.autoRotate) {
                    startAutoRotate()
                }
            }

            // Record initial history
            repository.recordModelView(_uiState.value.currentMesh, _uiState.value.displayMode)
        }

        // Sonar scanning wave and spatial diagnostics loop for AR
        sonarAnimJob = viewModelScope.launch {
            var step = 0
            while (true) {
                delay(30)
                step++
                _uiState.update { current ->
                    val nextSonar = (current.arSonarProgress + 0.02f) % 1.0f

                    // Calculate real-time spatial mapping diagnostics based on camera distance & motion
                    val distToAnchor = sqrt(
                        current.walkPos.x * current.walkPos.x +
                                (current.walkPos.y - current.arGroundY) * (current.walkPos.y - current.arGroundY) +
                                current.walkPos.z * current.walkPos.z
                    )
                    val baseConfidence = if (current.isSurfaceScanning) 0.65f else 0.94f
                    val confidenceVariation = (sin(step * 0.05f) * 0.03f).toFloat()
                    val dynamicConfidence = (baseConfidence + confidenceVariation).coerceIn(0.5f, 0.99f)
                    val isFeasible = distToAnchor in 0.8f..5.5f && dynamicConfidence > 0.70f

                    val updatedDiag = current.spatialDiagnostics.copy(
                        trackedPointsCount = if (current.isSurfaceScanning) (80 + (nextSonar * 45).toInt()) else current.spatialPoints.size,
                        pointDensityPerSqM = if (current.isSurfaceScanning) 34.0f + nextSonar * 18.0f else 48.5f,
                        planeConfidence = dynamicConfidence,
                        trackingStatus = if (current.isSurfaceScanning) "Scanning Plane..." else if (current.isARPlaced) "Anchor Locked" else "VIO Active",
                        surfaceTiltDeg = 0.3f + (cos(step * 0.03f) * 0.15f).toFloat(),
                        distanceToAnchorM = distToAnchor,
                        isPlacementFeasible = isFeasible,
                        mappedAreaSqM = if (current.isSurfaceScanning) 3.2f + nextSonar * 2.0f else 5.8f
                    )

                    current.copy(
                        arSonarProgress = nextSonar,
                        spatialDiagnostics = updatedDiag
                    )
                }
            }
        }

        // Fetch initial greeting joke
        fetchJoke()
    }

    override fun onCleared() {
        super.onCleared()
        sensorTracker.stopTracking()
        autoRotateJob?.cancel()
        sonarAnimJob?.cancel()
        recordingJob?.cancel()
    }

    private fun persistPreferences() {
        viewModelScope.launch {
            val state = _uiState.value
            repository.savePreferences(
                meshId = state.currentMesh.id,
                displayMode = state.displayMode,
                renderStyle = state.renderStyle,
                environmentType = state.environmentType,
                wireframeOnly = state.wireframeOnly,
                autoRotate = state.isAutoRotating,
                autoRotateSpeed = state.autoRotateSpeed,
                stereoIPD = state.stereoIPD,
                gyroTracking = state.isGyroHeadTracking,
                barrelDistortion = state.barrelDistortion,
                lightParams = state.lightParams
            )
        }
    }

    fun setDisplayMode(mode: AppDisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
        persistPreferences()
        viewModelScope.launch {
            repository.recordModelView(_uiState.value.currentMesh, mode)
        }
    }

    fun selectMesh(mesh: Mesh3D) {
        _uiState.update {
            it.copy(
                currentMesh = mesh,
                orbitDistance = 3.5f,
                walkPos = Vec3(0f, 0.0f, -3.0f),
                isARPlaced = true
            )
        }
        persistPreferences()
        viewModelScope.launch {
            repository.recordModelView(mesh, _uiState.value.displayMode)
        }
    }

    fun selectMeshById(meshId: String) {
        val mesh = MeshPresets.allPresets.find { it.id == meshId }
        if (mesh != null) {
            selectMesh(mesh)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun setRenderStyle(style: RenderStyle) {
        _uiState.update { it.copy(renderStyle = style) }
        persistPreferences()
    }

    fun setEnvironmentType(env: EnvironmentType) {
        _uiState.update { it.copy(environmentType = env) }
        persistPreferences()
    }

    fun toggleWireframe(enabled: Boolean) {
        _uiState.update { it.copy(wireframeOnly = enabled) }
        persistPreferences()
    }

    fun updateLightParams(params: LightParams) {
        _uiState.update { it.copy(lightParams = params) }
        persistPreferences()
    }

    fun toggleShadows(enabled: Boolean? = null) {
        _uiState.update {
            val nextState = enabled ?: !it.lightParams.shadowsEnabled
            it.copy(lightParams = it.lightParams.copy(shadowsEnabled = nextState))
        }
        persistPreferences()
    }

    fun setSceneIntensity(intensity: Float) {
        _uiState.update {
            it.copy(lightParams = it.lightParams.copy(sceneIntensity = intensity.coerceIn(0.1f, 2.0f)))
        }
        persistPreferences()
    }

    fun setShadowIntensity(intensity: Float) {
        _uiState.update {
            it.copy(lightParams = it.lightParams.copy(shadowIntensity = intensity.coerceIn(0.0f, 1.0f)))
        }
        persistPreferences()
    }

    fun rotateOrbit(deltaYaw: Float, deltaPitch: Float) {
        _uiState.update {
            val newYaw = (it.orbitYaw + deltaYaw) % 360f
            val newPitch = (it.orbitPitch + deltaPitch).coerceIn(-85f, 85f)
            it.copy(orbitYaw = newYaw, orbitPitch = newPitch)
        }
    }

    fun zoomOrbit(deltaDist: Float) {
        _uiState.update {
            val newDist = (it.orbitDistance + deltaDist).coerceIn(0.8f, 12.0f)
            it.copy(orbitDistance = newDist)
        }
    }

    fun zoomOrbitFactor(factor: Float) {
        if (factor <= 0f || factor == 1.0f) return
        _uiState.update {
            val newDist = (it.orbitDistance / factor).coerceIn(0.8f, 12.0f)
            it.copy(orbitDistance = newDist)
        }
    }

    fun resetOrbitCamera() {
        _uiState.update {
            it.copy(
                orbitYaw = 30f,
                orbitPitch = 20f,
                orbitDistance = 3.5f
            )
        }
    }

    fun toggleAutoRotate() {
        val next = !_uiState.value.isAutoRotating
        _uiState.update { it.copy(isAutoRotating = next) }
        if (next) {
            startAutoRotate()
        } else {
            autoRotateJob?.cancel()
        }
        persistPreferences()
    }

    private fun startAutoRotate() {
        autoRotateJob?.cancel()
        autoRotateJob = viewModelScope.launch {
            while (_uiState.value.isAutoRotating) {
                delay(16)
                _uiState.update {
                    it.copy(orbitYaw = (it.orbitYaw + 0.6f * it.autoRotateSpeed) % 360f)
                }
            }
        }
    }

    // AR Surface Placement & Spatial Mapping Diagnostics
    fun placeARModel() {
        _uiState.update { it.copy(isARPlaced = true) }
    }

    fun pinModelToSurface(worldPos: Vec3) {
        _uiState.update { current ->
            if (current.isARAnchorLocked) return@update current
            val dist = (current.walkPos - worldPos).length()
            val updatedDiag = current.spatialDiagnostics.copy(distanceToAnchorM = dist)
            current.copy(
                arAnchorPos = worldPos,
                isARPlaced = true,
                spatialDiagnostics = updatedDiag
            )
        }
    }

    fun pinModelAtScreenTap(screenX: Float, screenY: Float, screenWidth: Float, screenHeight: Float) {
        val current = _uiState.value
        if (current.isARAnchorLocked) return

        val camera = CameraParams(
            posX = current.walkPos.x,
            posY = current.walkPos.y,
            posZ = current.walkPos.z,
            yawDeg = current.walkYaw,
            pitchDeg = current.walkPitch,
            isFirstPerson = true
        )

        val hitWorldPos = Renderer3D.raycastToGroundPlane(
            screenX = screenX,
            screenY = screenY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            camera = camera,
            groundY = current.arGroundY
        )

        if (hitWorldPos != null) {
            // Find closest spatial feature point if within 0.35m
            val closestFeature = current.spatialPoints.minByOrNull { (it.pos - hitWorldPos).length() }
            val finalAnchor = if (closestFeature != null && (closestFeature.pos - hitWorldPos).length() < 0.35f) {
                closestFeature.pos
            } else {
                hitWorldPos
            }
            pinModelToSurface(finalAnchor)
        }
    }

    fun toggleAnchorLock() {
        _uiState.update { it.copy(isARAnchorLocked = !it.isARAnchorLocked) }
    }

    fun updateModelElevation(delta: Float) {
        _uiState.update { current ->
            val newElevation = (current.arModelElevation + delta).coerceIn(0f, 3.0f)
            current.copy(arModelElevation = newElevation)
        }
    }

    fun rotateARModel(deltaDeg: Float) {
        _uiState.update { current ->
            val newRotY = (current.arModelRotationY + deltaDeg) % 360f
            current.copy(arModelRotationY = newRotY)
        }
    }

    fun updateARModelScale(scaleMultiplier: Float) {
        _uiState.update { current ->
            val newScale = (current.arModelScale * scaleMultiplier).coerceIn(0.2f, 5.0f)
            current.copy(arModelScale = newScale)
        }
    }

    fun toggleSpatialDiagnostics() {
        _uiState.update { it.copy(showSpatialDiagnostics = !it.showSpatialDiagnostics) }
    }

    fun setPointCloudMode(mode: PointCloudVisualizationMode) {
        _uiState.update { it.copy(pointCloudMode = mode) }
    }

    fun cyclePointCloudMode() {
        _uiState.update {
            val modes = PointCloudVisualizationMode.values()
            val nextIndex = (it.pointCloudMode.ordinal + 1) % modes.size
            it.copy(pointCloudMode = modes[nextIndex])
        }
    }

    fun rescanSurface() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSurfaceScanning = true,
                    isARPlaced = false,
                    isARAnchorLocked = false,
                    arAnchorPos = Vec3(0f, it.arGroundY, 0f),
                    spatialPoints = SpatialMappingGenerator.generateInitialPointCloud(it.arGroundY)
                )
            }
            delay(1500)
            _uiState.update { it.copy(isSurfaceScanning = false) }
        }
    }

    fun resetARAnchor() {
        _uiState.update {
            val dist = (Vec3(0f, 0f, -3.0f) - Vec3(0f, it.arGroundY, 0f)).length()
            it.copy(
                walkPos = Vec3(0f, 0.0f, -3.0f),
                walkYaw = 0f,
                walkPitch = 0f,
                arAnchorPos = Vec3(0f, it.arGroundY, 0f),
                arModelElevation = 0f,
                arModelRotationY = 0f,
                isARPlaced = true,
                spatialDiagnostics = it.spatialDiagnostics.copy(distanceToAnchorM = dist)
            )
        }
    }

    // AR First-Person Walk-in Controls
    fun walkMove(stickX: Float, stickZ: Float) {
        val yawRad = Math.toRadians(_uiState.value.walkYaw.toDouble()).toFloat()
        val speed = 0.08f

        // Forward/backward vector
        val forwardX = sin(yawRad) * stickZ * speed
        val forwardZ = cos(yawRad) * stickZ * speed

        // Strafe vector
        val strafeX = cos(yawRad) * stickX * speed
        val strafeZ = -sin(yawRad) * stickX * speed

        _uiState.update { current ->
            val newPos = current.walkPos + Vec3(forwardX + strafeX, 0f, forwardZ + strafeZ)
            val dist = (newPos - current.arAnchorPos).length()
            current.copy(
                walkPos = newPos,
                spatialDiagnostics = current.spatialDiagnostics.copy(distanceToAnchorM = dist)
            )
        }
    }

    fun changeWalkHeight(deltaY: Float) {
        _uiState.update { current ->
            val newY = (current.walkPos.y + deltaY).coerceIn(-1.5f, 3.5f)
            current.copy(walkPos = current.walkPos.copy(y = newY))
        }
    }

    fun rotateLook(deltaYaw: Float, deltaPitch: Float) {
        _uiState.update { current ->
            val newYaw = (current.walkYaw + deltaYaw) % 360f
            val newPitch = (current.walkPitch + deltaPitch).coerceIn(-80f, 80f)
            current.copy(walkYaw = newYaw, walkPitch = newPitch)
        }
    }

    // VR Stereo Settings
    fun toggleSplitScreenStereo() {
        _uiState.update { it.copy(isSplitScreenStereo = !it.isSplitScreenStereo) }
    }

    fun setSplitScreenStereo(enabled: Boolean) {
        _uiState.update { it.copy(isSplitScreenStereo = enabled) }
    }

    fun updateStereoIPD(ipd: Float) {
        _uiState.update { it.copy(stereoIPD = ipd.coerceIn(0.040f, 0.090f)) }
        persistPreferences()
    }

    fun updateStereoConvergence(convergence: Float) {
        _uiState.update { it.copy(stereoConvergence = convergence.coerceIn(0.5f, 10.0f)) }
    }

    fun toggleStereoLensGuide() {
        _uiState.update { it.copy(stereoLensGuide = !it.stereoLensGuide) }
    }

    fun toggleGyroHeadTracking() {
        _uiState.update { it.copy(isGyroHeadTracking = !it.isGyroHeadTracking) }
        persistPreferences()
    }

    // HoloBot JokeAPI
    fun fetchJoke() {
        viewModelScope.launch {
            _uiState.update { it.copy(isJokeLoading = true, showJokeCard = true) }
            try {
                val response = JokeRetrofitClient.apiService.getRandomJoke()
                _uiState.update {
                    it.copy(
                        jokeText = response.fullJokeText,
                        isJokeLoading = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        jokeText = "Why do 3D renderers love jokes? Because they love high ray-tracing fidelity and good shaders!",
                        isJokeLoading = false
                    )
                }
            }
        }
    }

    fun toggleJokeCard(show: Boolean) {
        _uiState.update { it.copy(showJokeCard = show) }
    }

    // Dialogs & Sheets
    fun setModelLibraryVisible(visible: Boolean) {
        _uiState.update { it.copy(showModelLibrary = visible) }
    }

    fun setInspectorVisible(visible: Boolean) {
        _uiState.update { it.copy(showInspector = visible) }
    }

    fun captureSnapshot(canvasWidth: Int, canvasHeight: Int) {
        try {
            val bitmap = Bitmap.createBitmap(
                if (canvasWidth > 0) canvasWidth else 720,
                if (canvasHeight > 0) canvasHeight else 1280,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            val bgPaint = Paint().apply { color = android.graphics.Color.rgb(15, 23, 42) }
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), bgPaint)

            val textPaint = Paint().apply {
                color = android.graphics.Color.rgb(0, 229, 255)
                textSize = 36f
                isAntiAlias = true
            }
            val subPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 24f
                isAntiAlias = true
            }
            canvas.drawText("Mixed Reality Snapshot", 40f, 80f, textPaint)
            canvas.drawText("Model: ${_uiState.value.currentMesh.name} • ${_uiState.value.displayMode.title}", 40f, 125f, subPaint)

            _uiState.update {
                it.copy(
                    capturedSnapshot = bitmap,
                    showSnapshotDialog = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleRecording() {
        val next = !_uiState.value.isRecording
        if (next) {
            recordingJob?.cancel()
            _uiState.update { it.copy(isRecording = true, recordingSeconds = 0) }
            recordingJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
                }
            }
        } else {
            recordingJob?.cancel()
            _uiState.update { it.copy(isRecording = false) }
        }
    }

    fun clearCurrentScene() {
        resetOrbitCamera()
        resetARAnchor()
    }

    fun dismissSnapshotDialog() {
        _uiState.update { it.copy(showSnapshotDialog = false) }
    }
}

