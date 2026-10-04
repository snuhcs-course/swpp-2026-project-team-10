package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager

/** View-lifetime sensor adapter. Start on resume and stop on pause; callbacks run on the main thread. */
class CameraLevelMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tracker = CameraLevelTracker()
    private var listener: ((CameraLevel?) -> Unit)? = null
    private var activeSensor: Sensor? = null

    fun start(onLevel: (CameraLevel?) -> Unit) {
        stop()
        listener = onLevel
        onLevel(null)
        val manager = sensorManager ?: return
        val sensors = listOfNotNull(
            manager.getDefaultSensor(Sensor.TYPE_GRAVITY),
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        )
        activeSensor = sensors.firstOrNull {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, mainHandler)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        activeSensor = null
        listener = null
        tracker.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor != activeSensor || event.values.size < 3) return
        listener?.invoke(
            tracker.update(
                event.values[0],
                event.values[1],
                event.values[2],
                event.timestamp,
                displayRotationDegrees(),
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION") // WindowManager supports the project's API 29 minimum and its display context.
    private fun displayRotationDegrees(): Int = when (windowManager?.defaultDisplay?.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
}
