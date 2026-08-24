package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

data class OrientationData(
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val isAvailable: Boolean = false
)

class SensorTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val _orientation = MutableStateFlow(OrientationData())
    val orientation: StateFlow<OrientationData> = _orientation.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var baseYawOffset = 0f
    private var isCalibrated = false

    fun startTracking() {
        if (rotationSensor != null && sensorManager != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            _orientation.value = _orientation.value.copy(isAvailable = true)
        }
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
    }

    fun resetOrientation() {
        isCalibrated = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR || event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // azimuth (yaw), pitch, roll
            var rawYaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val rawPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            val rawRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

            if (!isCalibrated) {
                baseYawOffset = rawYaw
                isCalibrated = true
            }

            var adjustedYaw = rawYaw - baseYawOffset
            if (adjustedYaw > 180f) adjustedYaw -= 360f
            if (adjustedYaw < -180f) adjustedYaw += 360f

            _orientation.value = OrientationData(
                pitch = rawPitch,
                yaw = adjustedYaw,
                roll = rawRoll,
                isAvailable = true
            )
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            val yaw = event.values[0]
            val pitch = event.values[1]
            val roll = event.values[2]
            _orientation.value = OrientationData(
                pitch = pitch,
                yaw = yaw,
                roll = roll,
                isAvailable = true
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
