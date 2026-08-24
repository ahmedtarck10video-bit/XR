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
class StereoscopicVRTest {

    @Test
    fun `test split screen stereoscopic view toggle in viewmodel`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixedRealityViewModel(app)

        assertFalse(viewModel.uiState.value.isSplitScreenStereo)

        viewModel.toggleSplitScreenStereo()
        assertTrue(viewModel.uiState.value.isSplitScreenStereo)

        viewModel.updateStereoIPD(0.068f)
        assertEquals(0.068f, viewModel.uiState.value.stereoIPD, 0.0001f)

        viewModel.toggleStereoLensGuide()
        assertFalse(viewModel.uiState.value.stereoLensGuide)

        viewModel.setSplitScreenStereo(false)
        assertFalse(viewModel.uiState.value.isSplitScreenStereo)
    }

    @Test
    fun `test stereoscopic left and right eye parallax projection`() {
        val camera = CameraParams(
            posX = 0f,
            posY = 0f,
            posZ = 0f,
            yawDeg = 0f,
            pitchDeg = 0f,
            orbitDistance = 3.0f,
            isFirstPerson = false
        )
        val targetPoint = Vec3(0f, 0f, 0f)
        val ipd = 0.065f

        val leftEyeProj = Renderer3D.projectPoint(
            point = targetPoint,
            camera = camera,
            screenWidth = 1080f,
            screenHeight = 1920f,
            stereoOffset = -ipd * 0.5f
        )

        val rightEyeProj = Renderer3D.projectPoint(
            point = targetPoint,
            camera = camera,
            screenWidth = 1080f,
            screenHeight = 1920f,
            stereoOffset = ipd * 0.5f
        )

        assertNotNull(leftEyeProj)
        assertNotNull(rightEyeProj)

        // Right eye should have a relative horizontal offset compared to left eye
        assertNotEquals(leftEyeProj!!.screenX, rightEyeProj!!.screenX, 0.001f)
        assertTrue("Left eye should project to a higher screenX relative to right eye shift", leftEyeProj.screenX > rightEyeProj.screenX)
    }
}
