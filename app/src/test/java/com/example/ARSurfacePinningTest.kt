package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.engine.CameraParams
import com.example.engine.Renderer3D
import com.example.math3d.Vec3
import com.example.viewmodel.MixedRealityViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ARSurfacePinningTest {

    @Test
    fun `test pinning 3D model to detected surface and locking anchor`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixedRealityViewModel(app)

        val targetSurfacePos = Vec3(0.5f, -1.0f, 1.2f)
        viewModel.pinModelToSurface(targetSurfacePos)

        assertTrue(viewModel.uiState.value.isARPlaced)
        assertEquals(0.5f, viewModel.uiState.value.arAnchorPos.x, 0.001f)
        assertEquals(-1.0f, viewModel.uiState.value.arAnchorPos.y, 0.001f)
        assertEquals(1.2f, viewModel.uiState.value.arAnchorPos.z, 0.001f)

        // Lock anchor pin
        viewModel.toggleAnchorLock()
        assertTrue(viewModel.uiState.value.isARAnchorLocked)

        // Attempt to pin to a new position while locked -> should ignore and stay at locked position
        viewModel.pinModelToSurface(Vec3(2.0f, -1.0f, 3.0f))
        assertEquals(0.5f, viewModel.uiState.value.arAnchorPos.x, 0.001f)
        assertEquals(1.2f, viewModel.uiState.value.arAnchorPos.z, 0.001f)

        // Unlock and verify repositioning works
        viewModel.toggleAnchorLock()
        assertFalse(viewModel.uiState.value.isARAnchorLocked)
        viewModel.pinModelToSurface(Vec3(2.0f, -1.0f, 3.0f))
        assertEquals(2.0f, viewModel.uiState.value.arAnchorPos.x, 0.001f)
        assertEquals(3.0f, viewModel.uiState.value.arAnchorPos.z, 0.001f)
    }

    @Test
    fun `test world space persistence as user walks and orbits around pinned model`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixedRealityViewModel(app)

        val pinnedWorldPos = Vec3(0f, -1.0f, 0f)
        viewModel.pinModelToSurface(pinnedWorldPos)

        val initialCamPos = viewModel.uiState.value.walkPos

        // Move camera forward and strafe sideways with joystick
        viewModel.walkMove(stickX = 1.0f, stickZ = 1.0f)
        val newCamPos = viewModel.uiState.value.walkPos

        // Camera position has moved
        assertNotEquals(initialCamPos.x, newCamPos.x, 0.0001f)
        assertNotEquals(initialCamPos.z, newCamPos.z, 0.0001f)

        // Pinned world anchor remains strictly invariant
        assertEquals(pinnedWorldPos.x, viewModel.uiState.value.arAnchorPos.x, 0.0001f)
        assertEquals(pinnedWorldPos.y, viewModel.uiState.value.arAnchorPos.y, 0.0001f)
        assertEquals(pinnedWorldPos.z, viewModel.uiState.value.arAnchorPos.z, 0.0001f)

        // Distance in diagnostics HUD is dynamically updated
        val calculatedDist = (newCamPos - pinnedWorldPos).length()
        assertEquals(calculatedDist, viewModel.uiState.value.spatialDiagnostics.distanceToAnchorM, 0.01f)
    }

    @Test
    fun `test screen raycast to ground plane intersection`() {
        val camera = CameraParams(
            posX = 0f,
            posY = 0.5f,
            posZ = -2.0f,
            yawDeg = 0f,
            pitchDeg = -25f, // Looking downward at ground
            isFirstPerson = true
        )

        val hitWorldPos = Renderer3D.raycastToGroundPlane(
            screenX = 540f,
            screenY = 960f, // Center of screen
            screenWidth = 1080f,
            screenHeight = 1920f,
            camera = camera,
            groundY = -1.0f
        )

        assertNotNull("Raycast should hit the ground plane in front of camera", hitWorldPos)
        assertEquals(-1.0f, hitWorldPos!!.y, 0.001f)
        assertTrue("Hit point should be in front of camera along Z-axis", hitWorldPos.z > camera.posZ)
    }

    @Test
    fun `test model elevation and rotation adjustments`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixedRealityViewModel(app)

        assertEquals(0f, viewModel.uiState.value.arModelElevation, 0.001f)
        viewModel.updateModelElevation(0.25f)
        assertEquals(0.25f, viewModel.uiState.value.arModelElevation, 0.001f)

        assertEquals(0f, viewModel.uiState.value.arModelRotationY, 0.001f)
        viewModel.rotateARModel(45f)
        assertEquals(45f, viewModel.uiState.value.arModelRotationY, 0.001f)
    }
}
